package com.ade.meterreading

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// نفس ألوان بقية الشاشات (Screens.kt) — مكرّرة هنا محلياً لتفادي تغيير visibility هناك.
private val MAP_BLUE = Color(0xFF1565C0)
private val MAP_SUCCESS = Color(0xFF2E7D32)
private val MAP_WARNING = Color(0xFFF57F17)
private val MAP_SURFACE = Color(0xFFF5F5F5)
private val MAP_LINE = Color(0xFFE0E0E0)
private val MAP_MUTED = Color(0xFF757575)
private val MAP_WHITE = Color.White

/**
 * تبويب "📍 المواقع": خريطة تفاعلية (Leaflet + OpenStreetMap عبر WebView) لكل الزبائن
 * اللي عندهم موقع GPS مسجّل، زائد أزرار "ترتيب حسب الأقرب" و"أقرب زبون".
 * مطابقة لِـ tab-map (renderRouteMap / findNearestCustomer / sortRouteByNearest).
 *
 * ملاحظة: تحتاج اتصال بالإنترنت لعرض بلاطات الخريطة (OpenStreetMap) ومكتبة Leaflet،
 * تماماً بحال التطبيق الأصلي (لا يوجد مفتاح Google Maps API مطلوب).
 *
 * هاذ المكوّن بلا scroll خاص بيه — يُستدعى كـ"item" واحد داخل LazyColumn الرئيسية
 * (فـ ListScreen)، حتى يبقى كل شي (لوحة الإحصائيات + التبويبات + الخريطة + قائمة
 * المواقع) جزء من نفس التمرير الواحد، بلا ما يقطع ظهور شريط التبويبات.
 */
@Composable
fun MapTopSection(state: AppState) {
    val context = LocalContext.current
    var heatmapMode by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val meters = state.meters

    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            // العامل عطى الصلاحية دابا؛ الزر يحتاج يتضغط مرة أخرى (سلوك بسيط ومقبول).
            Toast.makeText(context, "اضغط الزر مرة أخرى", Toast.LENGTH_SHORT).show()
        }
    }

    fun withLocation(onGot: (Double, Double) -> Unit) {
        if (!LocationHelper.hasPermission(context)) {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (busy) return
        busy = true
        Toast.makeText(context, "📡 يتم تحديد موقعك...", Toast.LENGTH_SHORT).show()
        LocationHelper.requestOnce(context) { gps ->
            busy = false
            if (gps != null) {
                onGot(gps.lat, gps.lng)
            } else {
                Toast.makeText(context, "❌ تعذر تحديد موقعك الحالي. تأكد من تفعيل GPS والصلاحيات.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val withGps = meters.count { it.lat != null && it.lng != null }
    val withoutGps = meters.size - withGps

    Column(Modifier.fillMaxWidth()) {
        // شريط الأدوات
        Column(Modifier.fillMaxWidth().background(MAP_WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("🗺️ خريطة الجولة", fontSize = 14.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GhostButton(if (heatmapMode) "🌡️ حسب الحالة" else "🌡️ خريطة حرارية", Modifier.weight(1f)) {
                    heatmapMode = !heatmapMode
                }
                GhostButton("🔃 ترتيب حسب الأقرب", Modifier.weight(1f)) {
                    withLocation { lat, lng ->
                        val n = state.sortRouteByNearest(lat, lng)
                        if (n == null) {
                            Toast.makeText(context, "❌ يحتاج ترتيب المسار زبونين متبقيين على الأقل بموقع GPS مسجّل", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "✅ تم ترتيب $n عداد متبقٍ حسب الأقرب لموقعك الحالي", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                GhostButton("🧭 أقرب زبون", Modifier.weight(1f)) {
                    withLocation { lat, lng ->
                        val res = state.findNearestCustomer(lat, lng)
                        if (res == null) {
                            Toast.makeText(context, "❌ لا توجد مواقع مسجّلة للمقارنة", Toast.LENGTH_SHORT).show()
                        } else {
                            val (m, dist) = res
                            val label = if (dist >= 1000) fmt2(dist / 1000.0) + " كم" else "${Math.round(dist)} م"
                            Toast.makeText(context, "🧭 أقرب زبون: ${m.name} — $label", Toast.LENGTH_LONG).show()
                            focusPoint = (m.lat ?: 0.0) to (m.lng ?: 0.0)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MAP_LINE))

        // الخريطة
        Box(Modifier.fillMaxWidth().height(300.dp).background(MAP_SURFACE)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (e: Exception) {
                                        // لا يوجد تطبيق يفتح الرابط
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                    }
                },
                update = { web -> web.loadDataWithBaseURL(null, buildMapHtml(meters, heatmapMode, focusPoint), "text/html", "utf-8", null) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // مفتاح الألوان
        Row(
            Modifier.fillMaxWidth().background(MAP_WHITE).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("📍 $withGps موقع مسجّل", fontSize = 12.sp, color = MAP_MUTED)
            Text("⚠️ $withoutGps بدون GPS", fontSize = 12.sp, color = MAP_WARNING)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MAP_LINE))

        Text(
            "مواقع الزبائن المسجّلة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MAP_MUTED,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
fun MapEmptyState() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📍", fontSize = 32.sp)
        Text("لا توجد مواقع مسجّلة بعد.\nافتح أي زبون واضغط \"تسجيل الموقع الآن\".", color = MAP_MUTED)
    }
}

@Composable
fun NoGpsSectionHeader(count: Int) {
    Text(
        "⚠️ زبائن بدون موقع مسجّل ($count) — اضغط لفتح الملف وتسجيل الموقع",
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MAP_WHITE)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        // حدود بسيطة عبر خلفية داكنة قليلاً بدل border حتى نتفادى استيراد إضافي هنا
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MAP_BLUE, maxLines = 1)
    }
}

@Composable
fun MapLocationRow(m: Meter, context: android.content.Context) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(MAP_WHITE).padding(12.dp)
    ) {
        Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "${m.code} · ${String.format(java.util.Locale.US, "%.6f, %.6f", m.lat, m.lng)}" +
                (m.locAccuracy?.let { " · ±${it}م" } ?: ""),
            fontSize = 11.5.sp, color = MAP_MUTED
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MAP_BLUE)
                .clickable {
                    val lat = m.lat
                    val lng = m.lng
                    if (lat != null && lng != null) {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps?q=$lat,$lng")
                                )
                            )
                        } catch (e: Exception) {
                            // لا يوجد تطبيق خرائط
                        }
                    }
                }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("📍 فتح الموقع على الخريطة", color = MAP_WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NoGpsRow(m: Meter, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF8F0)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${m.code} · ${m.address.ifEmpty { "—" }}", fontSize = 11.5.sp, color = MAP_MUTED)
        }
        Text("⚠️", fontSize = 16.sp)
    }
}

/** يهرّب نصاً لإدراجه بأمان داخل HTML (قبل تهريب JS). */
private fun escapeHtmlForJs(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** يهرّب نصاً لإدراجه بأمان داخل سلسلة JS محاطة بعلامات اقتباس مفردة. */
private fun escapeJsString(s: String): String =
    s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ")

/**
 * يبني صفحة HTML كاملة تعرض خريطة Leaflet + بلاطات OpenStreetMap مع نقاط الزبائن،
 * مطابقة لِـ renderRouteMap() الأصلية (نفس الألوان، نفس منطق "الخريطة الحرارية" حسب
 * الاستهلاك، ونفس رابط "فتح الاتجاهات" على خرائط Google داخل الـ popup).
 */
private fun buildMapHtml(meters: List<Meter>, heatmapMode: Boolean, focus: Pair<Double, Double>?): String {
    val withLoc = meters.filter { it.lat != null && it.lng != null }
    val maxCons = withLoc.filter { it.hasReading }.map { it.consumption }.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    val markersJs = withLoc.joinToString(",\n") { m ->
        val color = if (heatmapMode) {
            if (!m.hasReading) {
                "#9e9e9e"
            } else {
                val ratio = (m.consumption / maxCons).coerceIn(0.0, 1.0)
                val hue = 120 - ratio * 120
                "hsl($hue,75%,45%)"
            }
        } else {
            when (m.status) {
                STATUS_DONE -> "#2e7d32"
                STATUS_ANOM -> "#f9a825"
                else -> "#1565c0"
            }
        }
        val lastReading = if (m.hasReading) {
            "${numToStr(m.newIndex ?: 0.0)} (استهلاك ${numToStr(m.consumption)} م³)"
        } else {
            "لم تُقرأ بعد"
        }
        val popupHtml = "<b>${escapeHtmlForJs(m.name)}</b><br>رمز الزبون: ${escapeHtmlForJs(m.code)}<br>" +
            "آخر قراءة: ${escapeHtmlForJs(lastReading)}<br>" +
            "<a href='https://www.google.com/maps?q=${m.lat},${m.lng}' target='_blank'>🧭 فتح الاتجاهات</a>"
        "{lat:${m.lat},lng:${m.lng},color:'$color',popup:'${escapeJsString(popupHtml)}'}"
    }
    val focusJs = if (focus != null) "{lat:${focus.first},lng:${focus.second}}" else "null"

    return """
<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css"/>
<style>html,body,#map{height:100%;margin:0;padding:0;}</style>
</head><body>
<div id="map"></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
<script>
  var markers = [$markersJs];
  var focus = $focusJs;
  var map = L.map('map', {zoomControl:true, attributionControl:true});
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom:19, attribution:'&copy; OpenStreetMap'}).addTo(map);
  var layer = L.layerGroup().addTo(map);
  var bounds = [];
  var focusMarker = null;
  markers.forEach(function(m){
    var mk = L.circleMarker([m.lat, m.lng], {radius:9, color:'#fff', weight:2, fillColor:m.color, fillOpacity:0.95}).bindPopup(m.popup);
    mk.addTo(layer);
    bounds.push([m.lat, m.lng]);
    if(focus && Math.abs(m.lat-focus.lat)<1e-9 && Math.abs(m.lng-focus.lng)<1e-9){ focusMarker = mk; }
  });
  if(bounds.length === 0){
    map.setView([28.0,2.0], 5);
  } else if(focus){
    map.setView([focus.lat, focus.lng], 17);
    if(focusMarker){ focusMarker.openPopup(); }
  } else if(bounds.length === 1){
    map.setView(bounds[0], 16);
  } else {
    map.fitBounds(bounds, {padding:[28,28]});
  }
</script>
</body></html>
""".trimIndent()
}
