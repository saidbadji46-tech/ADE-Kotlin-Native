package com.ade.meterreading

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeterReadingApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterReadingApp() {
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var subscriberCode by remember { mutableStateOf("A01T3216") }
    var subscriberName by remember { mutableStateOf("محمد الأمين") }
    var previousReadingText by remember { mutableStateOf("1250.0") }
    var currentReadingText by remember { mutableStateOf("") }
    var anomalyCode by remember { mutableStateOf("") }

    val previousReading = parseNum(previousReadingText)
    val currentReading = parseNum(currentReadingText)
    val pricePerUnit = 12.5

    val consumption = if (currentReading >= previousReading) currentReading - previousReading else 0.0
    val totalAmount = consumption * pricePerUnit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADE Meter Reading - تسجيل القراءة") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "رقم المشترك: $subscriberCode", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "اسم الزبون: $subscriberName", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "القراءة السابقة: ${fmt(previousReading)} م³", style = MaterialTheme.typography.bodyMedium)
                }
            }

            OutlinedTextField(
                value = currentReadingText,
                onValueChange = { currentReadingText = it },
                label = { Text("القراءة الحالية (م³)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = anomalyCode,
                onValueChange = { anomalyCode = it },
                label = { Text("رمز الملاحظة (مثال: INH, FC, VLD)") },
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "الاستهلاك: ${fmt(consumption)} م³", style = MaterialTheme.typography.titleMedium)
                    Text(text = "المبلغ الإجمالي: ${fmt(totalAmount)} د.ج", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (needsLegacyPermission(context)) {
                        permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        return@Button
                    }
                    val fileContent = "$subscriberCode|$subscriberName|${fmt(previousReading)}|${fmt(currentReading)}|${fmt(consumption)}|${fmt(totalAmount)}|$anomalyCode\n"
                    val success = saveTourTextFile(context, "tour_export.txt", fileContent)
                    if (success) {
                        Toast.makeText(context, "تم حفظ القراءة بنجاح في Documents/ADE", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "حدث خطأ أثناء حفظ الملف", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = currentReading >= previousReading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ وتصدير القراءة")
            }
        }
    }
}

fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)

fun parseNum(text: String): Double {
    val normalized = text.trim().map { c ->
        when (c) {
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
            '\u066B', '\u066C', ',' -> '.'
            else -> c
        }
    }.joinToString("")
    return normalized.toDoubleOrNull() ?: 0.0
}

fun needsLegacyPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED

/** يضيف السطر إلى نفس الملف (append) بدل إنشاء ملف جديد في كل مرة. */
fun saveTourTextFile(context: android.content.Context, fileName: String, contentText: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ADE")
            if (!dir.exists() && !dir.mkdirs()) return false
            File(dir, fileName).appendText(contentText, Charsets.UTF_8)
            return true
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relPath = "${Environment.DIRECTORY_DOCUMENTS}/ADE/"

        var existing: android.net.Uri? = null
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(fileName, relPath),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                existing = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
            }
        }

        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        }) ?: return false

        resolver.openOutputStream(uri, "wa")?.use { out ->
            out.write(contentText.toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
