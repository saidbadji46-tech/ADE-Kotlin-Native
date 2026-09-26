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
    var pinHash by mutableStateOf<String?>(null)
    var isLocked by mutableStateOf(false)
    var darkMode by mutableStateOf(false)

    fun load() {
        meters = db.allMeters()
        routeNum = db.getSetting("route", "T00001")
        triplet = db.getSetting("triplet", "1")
        worker = db.getSetting("worker", "BENAMMAR Said")
        pinHash = db.getSetting("pinHash", "").ifEmpty { null }
        isLocked = pinHash != null
        darkMode = db.getSetting("darkMode", "0") == "1"
    }

    /** يفعّل قفل PIN (رمز من 4 أرقام) — مطابق لِـ pinKey() عند تأكيد setup2. */
    fun setupPin(pin: String) {
        val h = simpleHash(pin)
        pinHash = h
        db.setSetting("pinHash", h)
    }

    /** يلغي قفل PIN — مطابق لِـ disablePin(). */
    fun disablePin() {
        pinHash = null
        db.setSetting("pinHash", "")
    }

    /** يتحقق من الرمز المُدخل؛ يفتح القفل عند الصحّة ويرجع true. */
    fun tryUnlock(pin: String): Boolean {
        if (simpleHash(pin) == pinHash) {
            isLocked = false
            return true
        }
        return false
    }

    /** يُستدعى عند خروج التطبيق للخلفية (onStop) — يقفل التطبيق إذا كان PIN مفعّلاً. */
    fun lockNow() {
        if (pinHash != null) isLocked = true
    }

    /** مطابق لِـ toggleDarkMode(). */
    fun toggleDarkMode() {
        darkMode = !darkMode
        db.setSetting("darkMode", if (darkMode) "1" else "0")
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

    /** يحفظ توقيع الزبون كصورة PNG — مطابق لِـ saveSignatureOnly(). */
    fun saveSignature(meterId: Long, bitmap: android.graphics.Bitmap) {
        val m = meters.firstOrNull { it.id == meterId } ?: return
        val path = SignatureStore.save(context, meterId, bitmap) ?: return
        SignatureStore.delete(m.signaturePath)
        saveMeter(m.copy(signaturePath = path))
    }

    /** يحدّث الرقم التسلسلي للعداد (من مسح الباركود/QR) — مطابق لِـ restartBarcodeScanner(). */
    fun setSerial(meterId: Long, serial: String) {
        val m = meters.firstOrNull { it.id == meterId } ?: return
        saveMeter(m.copy(serial = serial))
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

    /**
     * ينتقل للعداد المجاور فنفس ترتيب القائمة (بغضّ النظر عن حالته)، بتدوير عند الطرفين.
     * مطابقة لِـ goToAdjacentMeter(step) — تُستعمل لزرّي "⬅️ عودة" / "➡️ التالي" فأعلى شاشة القراءة.
     */
    fun adjacent(id: Long, step: Int): Long? {
        val list = meters
        if (list.isEmpty()) return null
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return null
        return list[(idx + step + list.size) % list.size].id
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

    /**
     * يستورد ملف مواقع JSON (المُصدَّر سابقاً من "تصدير ملف المواقع") ويملأ موقع كل زبون
     * ماعندوش موقع مسجّل بعد (بلا ما يبدّل موقع مسجَّل حياً مسبقاً). مطابق لِـ importLocationsFile().
     */
    fun importLocations(uri: Uri): String {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.let { String(it, Charsets.UTF_8).removePrefix("\uFEFF") }
        } catch (e: Exception) {
            null
        } ?: return "❌ تعذّرت قراءة الملف."

        val arr = try {
            org.json.JSONArray(text)
        } catch (e: Exception) {
            return "❌ فشل استيراد ملف المواقع: صيغة الملف غير صحيحة."
        }

        // key = رمز الزبون (meter/code) → {lat, lng, locAccuracy}
        val byCode = HashMap<String, Triple<Double, Double, Int?>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val code = obj.optString("meter", "")
            if (code.isEmpty()) continue
            if (!obj.has("lat") || !obj.has("lng")) continue
            val lat = obj.optDouble("lat", Double.NaN)
            val lng = obj.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val acc = if (obj.isNull("locAccuracy")) null else obj.optInt("locAccuracy")
            byCode[code] = Triple(lat, lng, acc)
        }

        var matched = 0
        var filled = 0
        var skippedHasLocation = 0
        for (m in meters) {
            val entry = byCode[m.code] ?: continue
            matched++
            if (m.lat != null && m.lng != null) {
                skippedHasLocation++
                continue
            }
            db.updateMeter(m.copy(lat = entry.first, lng = entry.second, locAccuracy = entry.third))
            filled++
        }
        meters = db.allMeters()

        return if (matched == 0) {
            "⚠️ لم يُطابَق أي زبون من ملف المواقع مع الجولة الحالية."
        } else {
            "✅ تم استيراد المواقع: $filled زبون تم ملء موقعه" +
                (if (skippedHasLocation > 0) " (تخطينا $skippedHasLocation عندهم موقع محفوظ مسبقاً)" else "") + "."
        }
    }


    fun exportLocations(): String {
        val withLoc = meters.filter { it.lat != null && it.lng != null }
        if (withLoc.isEmpty()) return "لا توجد مواقع محفوظة للتصدير"
        val baseName = "مواقع_" + AdeFormat.exportName(routeNum, triplet)
        val json = buildString {
            append("[\n")
            withLoc.forEachIndexed { i, m ->
                append("  {\"meter\":\"").append(jsonEscape(m.code)).append("\",")
                append("\"lat\":").append(m.lat).append(",")
                append("\"lng\":").append(m.lng).append(",")
                append("\"locAccuracy\":").append(m.locAccuracy?.toString() ?: "null")
                append("}")
                if (i != withLoc.lastIndex) append(",")
                append("\n")
            }
            append("]\n")
        }
        val okJson = Exporter.saveToDocuments(context, "$baseName.json", json.toByteArray(Charsets.UTF_8))
        // ملف HTML مرفق: خريطة تفاعلية + قائمة مواقع، يُفتح مباشرة (بلا حاجة للتطبيق) لمعاينة
        // المواقع أو فتحها على خرائط Google وأنت فالميدان.
        val html = buildLocationsHtml(withLoc, baseName)
        val okHtml = Exporter.saveToDocuments(context, "$baseName.html", html.toByteArray(Charsets.UTF_8))
        return when {
            okJson && okHtml -> "✅ تم حفظ ملفي المواقع داخل Documents/ADE:\n$baseName.json (لإعادة الاستيراد)\n$baseName.html (للمعاينة المباشرة) — ${withLoc.size} موقع"
            okJson -> "✅ تم حفظ $baseName.json، لكن فشل حفظ ملف HTML"
            else -> "❌ فشل حفظ ملفات المواقع"
        }
    }

    private fun buildLocationsHtml(withLoc: List<Meter>, title: String): String {
        fun escHtml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun escJs(s: String) = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")

        val markersJs = withLoc.joinToString(",\n") { m ->
            val popup = "<b>${escHtml(m.name)}</b><br>${escHtml(m.code)}<br>" +
                "<a href='https://www.google.com/maps?q=${m.lat},${m.lng}' target='_blank'>🧭 فتح الاتجاهات</a>"
            "{lat:${m.lat},lng:${m.lng},popup:'${escJs(popup)}'}"
        }
        val rowsHtml = withLoc.joinToString("\n") { m ->
            "<div class='row'><div><b>${escHtml(m.name)}</b><br><span class='muted'>${escHtml(m.code)} · " +
                String.format(java.util.Locale.US, "%.6f, %.6f", m.lat, m.lng) +
                (m.locAccuracy?.let { " · ±${it}م" } ?: "") + "</span></div>" +
                "<a class='btn' href='https://www.google.com/maps?q=${m.lat},${m.lng}' target='_blank'>🗺️ فتح</a></div>"
        }
        return """
<!DOCTYPE html>
<html dir="rtl" lang="ar"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"/>
<style>
  body{margin:0;font-family:sans-serif;background:#f5f5f5;}
  #map{height:45vh;width:100%;}
  .row{display:flex;justify-content:space-between;align-items:center;background:#fff;border-radius:10px;padding:12px;margin:8px 12px;box-shadow:0 1px 3px rgba(0,0,0,.08);}
  .muted{color:#757575;font-size:12.5px;}
  .btn{background:#1565c0;color:#fff;text-decoration:none;padding:8px 14px;border-radius:8px;font-size:13px;white-space:nowrap;margin-right:10px;}
  h2{padding:14px 16px 4px;margin:0;}
</style></head><body>
<h2>📍 $title (${withLoc.size} موقع)</h2>
<div id="map"></div>
<div id="list">
$rowsHtml
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
  var markers = [$markersJs];
  var map = L.map('map');
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'&copy; OpenStreetMap'}).addTo(map);
  var bounds = [];
  markers.forEach(function(m){
    L.marker([m.lat, m.lng]).addTo(map).bindPopup(m.popup);
    bounds.push([m.lat, m.lng]);
  });
  if(bounds.length === 1){ map.setView(bounds[0], 16); } else { map.fitBounds(bounds, {padding:[24,24]}); }
</script>
</body></html>
""".trimIndent()
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

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
