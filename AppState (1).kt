package com.ade.meterreading

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ImportOutcome(val message: String)

/** الحالة العامة للتطبيق: الزبائن والإعدادات والشاشة المفتوحة. */
class AppState(private val context: Context) {
    private val db = Db(context)

    var meters by mutableStateOf<List<Meter>>(emptyList())
    var routeNum by mutableStateOf("T00001")
    var triplet by mutableStateOf("1")
    var worker by mutableStateOf("BENAMMAR Said")
    var page by mutableStateOf("list")
    var query by mutableStateOf("")
    var tab by mutableStateOf(0)
    var openId by mutableStateOf<Long?>(null)

    fun load() {
        meters = db.allMeters()
        routeNum = db.getSetting("route", "T00001")
        triplet = db.getSetting("triplet", "1")
        worker = db.getSetting("worker", "BENAMMAR Said")
    }

    fun saveSettings(newWorker: String, newRoute: String, newTriplet: String) {
        worker = newWorker.trim()
        routeNum = newRoute.trim().ifEmpty { "T00001" }
        triplet = newTriplet.trim().ifEmpty { "1" }
        db.setSetting("worker", worker)
        db.setSetting("route", routeNum)
        db.setSetting("triplet", triplet)
    }

    fun saveMeter(m: Meter) {
        db.updateMeter(m)
        meters = meters.map { if (it.id == m.id) m else it }
    }

    /** يرجع رسالة خطأ، أو null عند النجاح. */
    fun addMeter(
        code: String,
        name: String,
        serial: String,
        address: String,
        subType: String,
        prev: Double,
        phone: String = "",
        nationalId: String = ""
    ): String? {
        val c = code.trim()
        if (name.isBlank()) return "أدخل اسم المشترك"
        if (c.isEmpty()) return "أدخل رمز الزبون"
        if (meters.any { it.code.equals(c, ignoreCase = true) }) return "رمز الزبون موجود مسبقاً"
        val m = Meter(
            code = c,
            name = name.trim(),
            address = address.trim(),
            subType = subType,
            serial = serial.trim(),
            prevIndex = prev,
            phone = phone.trim(),
            nationalId = nationalId.trim()
        )
        db.importRecords(listOf(m))
        meters = db.allMeters()
        return null
    }

    fun clearAll() {
        for (m in meters) PhotoStore.delete(m.photoPath)
        db.deleteAllMeters()
        meters = emptyList()
        openId = null
    }

    fun savePhoto(meterId: Long, bitmap: android.graphics.Bitmap) {
        val m = meters.firstOrNull { it.id == meterId } ?: return
        val path = PhotoStore.save(context, meterId, bitmap) ?: return
        PhotoStore.delete(m.photoPath)
        saveMeter(m.copy(photoPath = path))
    }

    fun saveLocation(meterId: Long, gps: GpsResult) {
        val m = meters.firstOrNull { it.id == meterId } ?: return
        saveMeter(m.copy(lat = gps.lat, lng = gps.lng, locAccuracy = gps.accuracy))
    }

    /**
     * أقرب زبون لموقع (myLat, myLng): يفضّل الزبائن "المعلّقين" (غير مرفوعين/بدون إشارة)
     * أولاً، وإن ماكانش، يبحث بين كل من له موقع مسجّل. مطابق لـ findNearestCustomer().
     */
    fun findNearestCustomer(myLat: Double, myLng: Double): Pair<Meter, Double>? {
        var candidates = meters.filter { it.lat != null && it.lng != null && it.status != STATUS_DONE && it.status != STATUS_ANOM }
        if (candidates.isEmpty()) candidates = meters.filter { it.lat != null && it.lng != null }
        if (candidates.isEmpty()) return null
        var best: Meter? = null
        var bestDist = Double.MAX_VALUE
        for (m in candidates) {
            val d = haversineMeters(myLat, myLng, m.lat!!, m.lng!!)
            if (d < bestDist) {
                bestDist = d
                best = m
            }
        }
        val b = best ?: return null
        return b to bestDist
    }

    /**
     * يرتّب العدادات "المعلّقة" وعندها موقع GPS حسب الأقرب لموقع (myLat, myLng) بمنطق جشع
     * (nearest-neighbor)، ثم يحفظ الترتيب الجديد بشكل دائم. مطابق لـ sortRouteByNearest().
     * يرجع عدد العدادات المُرتَّبة، أو null إذا كان العدد أقل من 2.
     */
    fun sortRouteByNearest(myLat: Double, myLng: Double): Int? {
        val pendingWithGps = meters.filter { it.lat != null && it.lng != null && it.status != STATUS_DONE && it.status != STATUS_ANOM }
        if (pendingWithGps.size < 2) return null
        val remaining = pendingWithGps.toMutableList()
        val ordered = ArrayList<Meter>()
        var curLat = myLat
        var curLng = myLng
        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestDist = Double.MAX_VALUE
            remaining.forEachIndexed { i, m ->
                val d = haversineMeters(curLat, curLng, m.lat!!, m.lng!!)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            val next = remaining.removeAt(bestIdx)
            ordered.add(next)
            curLat = next.lat!!
            curLng = next.lng!!
        }
        val orderedIds = ordered.map { it.id }.toHashSet()
        val rest = meters.filter { it.id !in orderedIds }
        val fullOrder = ordered + rest
        db.reorderMeters(fullOrder.map { it.id })
        meters = fullOrder
        return ordered.size
    }

    fun nextPendingAfter(id: Long): Long? {
        val list = meters
        if (list.isEmpty()) return null
        val idx = list.indexOfFirst { it.id == id }
        for (i in 1..list.size) {
            val cand = list[(idx + i + list.size) % list.size]
            if (cand.status != STATUS_DONE && cand.status != STATUS_ANOM && cand.id != id) return cand.id
        }
        return null
    }

    /** يُستدعى على خيط الخلفية. */
    fun importFile(uri: Uri): ImportOutcome {
        val name = queryName(uri)
        if (!name.trim().uppercase().startsWith("A")) {
            return ImportOutcome("❌ لا يمكن استيراد هذا الملف: اسمه \"$name\" لا يبدأ بالحرف A.\nمثال صحيح: A01T3216.TXT")
        }
        var data: ByteArray? = null
        try {
            data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val bytes = data ?: return ImportOutcome("❌ تعذّرت قراءة الملف.")

        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = text.split(Regex("\r?\n"))
        val records = AdeFormat.parse(lines)
            ?: return ImportOutcome("❌ تنسيق الملف غير معروف. هذه النسخة تدعم ملفات ADE النصية (TXT) فقط.")

        val route = AdeFormat.detectRoute(lines, name)
        val triplet = AdeFormat.detectTriplet(name, lines)
        val (added, updated, skipped) = db.importRecords(records)
        if (route.isNotEmpty()) db.setSetting("route", route)
        if (triplet.isNotEmpty()) db.setSetting("triplet", triplet)

        val sb = StringBuilder("✅ تم الاستيراد")
        if (route.isNotEmpty()) sb.append(" — الجولة ").append(route)
        sb.append("\nأُضيف ").append(added).append(" زبون")
        if (updated > 0) sb.append("\nتحدّث ").append(updated).append(" زبون")
        if (skipped > 0) sb.append("\nتم تجاوز ").append(skipped).append(" (مكرر أو ناقص)")
        return ImportOutcome(sb.toString())
    }

    fun exportResults(): String {
        if (meters.isEmpty()) return "لا توجد بيانات لتصديرها"
        val fileName = AdeFormat.exportName(routeNum, triplet) + ".txt"
        val ordered = AdeFormat.sortForExport(meters)
        val text = ordered.joinToString("\r\n") { AdeFormat.exportLine(it, worker) }
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val ok = Exporter.saveToDocuments(context, fileName, bom + text.toByteArray(Charsets.UTF_8))
        return if (ok) "✅ تم حفظ الملف داخل Documents/ADE:\n$fileName" else "❌ فشل حفظ الملف $fileName"
    }

    private fun queryName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i) ?: ""
            }
        }
        return uri.lastPathSegment ?: ""
    }
}
