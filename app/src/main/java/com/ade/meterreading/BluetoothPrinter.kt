package com.ade.meterreading

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

/**
 * طباعة إيصال قصير على طابعة حرارية عبر بلوتوث كلاسيكي (SPP)، مطابقة لِـ
 * buildReceiptBytes() و printReceiptViaBluetooth() في التطبيق الأصلي، بنفس محتوى
 * الإيصال وأوامر ESC/POS (تهيئة الطابعة + قصّ الورق).
 *
 * ملاحظة: الأصل يستعمل Web Bluetooth (BLE فقط، لأن المتصفح لا يدعم غير ذلك)، بينما
 * هنا نستعمل بلوتوث كلاسيكي (SPP) لأنه الأوسع توافقاً مع طابعات الإيصالات الحرارية
 * الرخيصة المنتشرة ميدانياً. هذه ميزة تجريبية — التوافق يختلف حسب طراز الطابعة، بحال
 * ما هو مكتوب فالأصل بالضبط.
 */
object BluetoothPrinter {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BluetoothDevice> {
        if (!hasPermission(context)) return emptyList()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** نص الإيصال — مطابق لِـ buildReceiptBytes() سطراً بسطر. */
    fun buildReceiptText(m: Meter, routeNum: String): String {
        val money = fmt2(m.amount.toDouble())
        val lines = listOf(
            "ADE - Meter Reading Receipt",
            "--------------------------------",
            "Route: $routeNum",
            "Customer code: ${m.code}",
            "Name: ${m.name}",
            "Serial: ${m.serial.ifEmpty { "-" }}",
            "Prev index: " + numToStr(m.prevIndex),
            "New index: " + (if (m.hasReading) numToStr(m.newIndex ?: 0.0) else "-"),
            "Consumption: " + (if (m.hasReading) numToStr(m.consumption) else "-") + " m3",
            "Amount: $money DA",
            "Date: " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date()),
            "--------------------------------",
            "Thank you / Merci"
        )
        return lines.joinToString("\n") + "\n\n\n"
    }

    /** يرسل نص الإيصال للطابعة عبر SPP. يُشغَّل من Thread/coroutine خلفي (I/O يحجب). */
    @SuppressLint("MissingPermission")
    fun printText(context: Context, device: BluetoothDevice, text: String): Boolean {
        if (!hasPermission(context)) return false
        var socket: android.bluetooth.BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val out = socket.outputStream
            val esc = 0x1B
            val gs = 0x1D
            out.write(byteArrayOf(esc.toByte(), 0x40)) // ESC @ — تهيئة الطابعة
            out.write(text.toByteArray(Charsets.UTF_8))
            out.write(byteArrayOf(gs.toByte(), 0x56, 0x42, 0x00)) // GS V B 0 — قصّ جزئي للورق
            out.flush()
            true
        } catch (e: IOException) {
            false
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // تجاهل
            }
        }
    }
}
