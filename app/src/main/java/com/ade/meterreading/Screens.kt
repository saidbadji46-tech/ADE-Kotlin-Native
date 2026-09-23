package com.ade.meterreading

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ألوان تطبيق HTML الأصلي
private val BLUE = Color(0xFF1565C0)
private val ACCENT = Color(0xFFFF6F00)
private val SUCCESS = Color(0xFF2E7D32)
private val WARNING = Color(0xFFF57F17)
private val DANGER = Color(0xFFC62828)
private val SURFACE = Color(0xFFF5F5F5)
private val LINE = Color(0xFFE0E0E0)
private val INK = Color(0xFF212121)
private val MUTED = Color(0xFF757575)
private val WHITE = Color.White

// ───────────────────────── الجذر ─────────────────────────

@Composable
fun AppRoot(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) { state.importFile(uri) }
                state.load()
                message = outcome.message
            }
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) state.exportResults() else "❌ لم يتم منح إذن الكتابة"
    }

    fun startImport() {
        picker.launch(arrayOf("*/*"))
    }

    fun startExport() {
        val needPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needPerm) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            message = state.exportResults()
        }
    }

    val id = state.openId
    val open = if (id != null) state.meters.firstOrNull { it.id == id } else null

    BackHandler(enabled = open != null) { state.openId = null }
    BackHandler(enabled = open == null && state.page != "list") { state.page = "list" }

    if (open != null) {
        MeterScreen(state, open)
    } else {
        Column(Modifier.fillMaxSize().background(SURFACE)) {
            BlueTopBar(
                title = "قراءات العدادات - " + state.worker,
                badge = if (state.page == "manage") "إدارة" else state.routeNum
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.page == "manage") {
                    ManageScreen(state, onImport = { startImport() }, onExport = { startExport() })
                } else {
                    ListScreen(state, onImport = { startImport() })
                }
            }
            BottomNav(state, onAdd = { showAdd = true })
        }
    }

    if (showAdd) {
        AddMeterDialog(state) { showAdd = false }
    }

    val msg = message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("حسناً") } }
        )
    }
}

// ───────────────────────── مكوّنات مشتركة ─────────────────────────

@Composable
fun BlueTopBar(title: String, badge: String? = null, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(BLUE).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1FFFFFFF)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("›", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title, color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black,
            maxLines = 1, modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x2EFFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(badge, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BottomNav(state: AppState, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(WHITE)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("☰", "القائمة", state.page == "list", Modifier.weight(1f)) { state.page = "list" }
            Box(
                Modifier.offset(y = (-14).dp).size(54.dp)
                    .shadow(8.dp, CircleShape).clip(CircleShape).background(ACCENT)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            NavItem("👤", "إدارة", state.page == "manage", Modifier.weight(1f)) { state.page = "manage" }
        }
    }
}

@Composable
fun NavItem(icon: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 20.sp, color = if (active) BLUE else MUTED)
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = if (active) BLUE else MUTED)
    }
}

@Composable
fun StdInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WHITE)
            .border(1.5.dp, LINE, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = MUTED, fontSize = 14.5.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(fontSize = 14.5.sp, color = INK),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 11.5.sp, color = MUTED, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun BlueButton(text: String, modifier: Modifier = Modifier, color: Color = BLUE, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(color).clickable(onClick = onClick).padding(13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── قائمة الجولة ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListScreen(state: AppState, onImport: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val all = state.meters
    val tabIndex = state.tab
    val queryText = state.query

    fun matchesQuery(m: Meter, q: String, qFlat: String): Boolean =
        q.isEmpty() ||
            m.name.lowercase().contains(q) ||
            m.code.lowercase().contains(q) ||
            m.address.lowercase().contains(q) ||
            m.serial.lowercase().replace(" ", "").contains(qFlat)

    // "التقدم في الجولة" يحسب من العدادات المقروءة ضمن نتيجة البحث الحالية فقط، تماماً مثل renderAll() الأصلية.
    val doneCount = remember(all, queryText) {
        val q = queryText.trim().lowercase()
        val qFlat = q.replace(" ", "")
        all.count { it.hasReading && matchesQuery(it, q, qFlat) }
    }

    val visible = remember(all, queryText, tabIndex) {
        val q = queryText.trim().lowercase()
        val qFlat = q.replace(" ", "")
        all.filter { m ->
            val matchQ = matchesQuery(m, q, qFlat)
            val matchTab = when (tabIndex) {
                1 -> m.hasReading
                2 -> m.status != STATUS_DONE && m.status != STATUS_ANOM
                3 -> m.status == STATUS_ANOM
                else -> true
            }
            matchQ && matchTab
        }
    }

    Column(Modifier.fillMaxSize()) {
        // شريط البحث
        Column(Modifier.fillMaxWidth().background(WHITE)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(SURFACE)
                        .border(1.dp, LINE, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (state.query.isEmpty()) {
                        Text("🔍  بحث بالاسم أو الرمز أو الرقم التسلسلي", color = MUTED, fontSize = 13.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = INK),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(BLUE)
                        .clickable { focusManager.clearFocus() }.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("بحث", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
        }

        if (all.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("🚰", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "لا توجد عدادات بعد.\nاستورد ملف الجولة (اسمه يبدأ بالحرف A) لبدء العمل.",
                    textAlign = TextAlign.Center, color = MUTED
                )
                Spacer(Modifier.height(14.dp))
                BlueButton("استيراد ملف الجولة", Modifier.fillMaxWidth(0.7f)) { onImport() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                StatsPanel(all)
                TabsBar(state)
                if (tabIndex == 4) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        MapScreen(state)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        item {
                            Column(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().background(WHITE).padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("التقدم في الجولة", fontSize = 12.sp, color = MUTED)
                                        Text("$doneCount / ${all.size}", fontSize = 12.sp, color = MUTED)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    val frac = if (all.isEmpty()) 0f else doneCount.toFloat() / all.size
                                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(LINE)) {
                                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(SUCCESS))
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                            }
                        }
                        if (visible.isEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("✅", fontSize = 32.sp)
                                    Text("لا توجد نتائج في هذه الفئة.", color = MUTED)
                                }
                            }
                        } else {
                            items(visible, key = { it.id }) { m ->
                                MeterRow(m) { state.openId = m.id }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabsBar(state: AppState) {
    val labels = listOf("الكل", "مرفوعة ✓", "معلّقة", "إشارات", "📍 المواقع")
    Row(Modifier.fillMaxWidth().background(BLUE).horizontalScroll(rememberScrollState())) {
        labels.forEachIndexed { i, label ->
            val selected = state.tab == i
            Box(
                Modifier.clickable { state.tab = i }.padding(horizontal = 16.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) WHITE else Color(0xB3FFFFFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (selected) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(WHITE)
                    )
                }
            }
        }
    }
}

/**
 * مطابقة لـ getRouteStats() / renderRouteStatsPanel() في التطبيق الأصلي:
 * - "عدادات مقروءة" = كل عداد فيه قراءة (hasReading)، حتى لو كان بإشارة.
 * - "عدادات متبقية" = العدد الكلي ناقص (status=done) ناقص (status=anom).
 * - "صفر استهلاك" و "فوق المعدل" تُحسبان فقط من العدادات المقروءة.
 */
@Composable
fun StatsPanel(meters: List<Meter>) {
    var open by remember { mutableStateOf(true) }
    val total = meters.size
    val statusDone = meters.count { it.status == STATUS_DONE }
    val statusAnom = meters.count { it.status == STATUS_ANOM }
    val pending = total - statusDone - statusAnom
    val readMeters = meters.filter { it.hasReading }
    val inh = meters.count { parseAnnots(it.annot).contains("INH") }
    val zero = readMeters.count { it.consumption == 0.0 }
    val over = readMeters.count {
        val avg = it.avgConsumption
        avg != null && avg > 0 && it.consumption > avg * 1.3
    }
    val sumCons = readMeters.sumOf { it.consumption }
    val sumAmount = readMeters.sumOf { it.amount }
    val leak = meters.count { parseAnnots(it.annot).contains("FC") }
    val withGps = meters.count { it.lat != null && it.lng != null }
    val pct = if (total == 0) 0 else Math.round(statusDone * 100.0 / total).toInt()
    val consStr = if (sumCons == Math.floor(sumCons)) sumCons.toLong().toString() else fmt1(sumCons)

    val boxes = listOf(
        Triple(total.toString(), "👥 عدد الزبائن", BLUE),
        Triple(readMeters.size.toString(), "✅ عدادات مقروءة", SUCCESS),
        Triple(pending.toString(), "⏳ عدادات متبقية", MUTED),
        Triple(inh.toString(), "🚫 عدد INH", WARNING),
        Triple(zero.toString(), "صفر استهلاك", WARNING),
        Triple(over.toString(), "🔺 استهلاك فوق المعدل", DANGER),
        // "—" مؤقتاً: هاد المؤشر مرتبط بميزة "استيراد ملف الإشارات" (مقارنة عدد AR مع الجولة
        // السابقة) اللي مازالت ماكاملناهاش. الأصل كيبان عندو "—" حتى الآن استورد ذاك الملف.
        Triple("—", "⛔ عدادات متوقفة (AR) مقارنة بالسابق", DANGER),
        Triple(consStr, "💧 كمية الاستهلاك م³", BLUE),
        Triple(fmt2(sumAmount.toDouble()), "💰 الثمن الكلي للجولة", SUCCESS),
        Triple(leak.toString(), "💧 تسربات FC", Color(0xFF0288D1)),
        Triple(withGps.toString(), "📍 مواقع مسجلة", BLUE),
        Triple("$pct%", "📊 نسبة الإنجاز", SUCCESS)
    )

    Box(
        Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(WHITE).border(1.dp, LINE, RoundedCornerShape(14.dp))
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎯 لوحة إحصائيات الجولة", fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(if (open) "▲" else "▼", fontSize = 12.sp, color = MUTED)
            }
            if (open) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📊 نسبة إنجاز الجولة", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MUTED)
                        Text("$pct%", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MUTED)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(LINE)) {
                        Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(SUCCESS))
                    }
                    boxes.chunked(3).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { b ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(SURFACE).padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(b.first, fontSize = 18.sp, fontWeight = FontWeight.Black, color = b.third, fontFamily = FontFamily.Monospace)
                                    Text(b.second, fontSize = 10.5.sp, color = MUTED, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeterRow(m: Meter, onClick: () -> Unit) {
    val barColor = when (m.status) {
        STATUS_DONE -> SUCCESS
        STATUS_ANOM -> WARNING
        else -> LINE
    }
    val chipText = when (m.status) {
        STATUS_DONE -> "مرفوعة"
        STATUS_ANOM -> m.annot.ifEmpty { "إشارة" }
        else -> "معلّقة"
    }
    val chipBg = when (m.status) {
        STATUS_DONE -> Color(0xFFE8F5E9)
        STATUS_ANOM -> Color(0xFFFFF8E1)
        else -> Color(0xFFF5F5F5)
    }
    val chipFg = when (m.status) {
        STATUS_DONE -> SUCCESS
        STATUS_ANOM -> WARNING
        else -> MUTED
    }
    Column(Modifier.fillMaxWidth().background(WHITE).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))
            Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text("#${m.code}  ·  ${m.subType}", fontSize = 11.sp, color = MUTED, fontWeight = FontWeight.SemiBold)
                Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text("🔢 ${m.serial.ifEmpty { "—" }}    📍 ${m.address.ifEmpty { "—" }}", fontSize = 11.5.sp, color = MUTED, maxLines = 2)
                if (m.hasReading) {
                    Text("📊 استهلاك ${numToStr(m.consumption)} م³", fontSize = 11.5.sp, color = MUTED)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(chipBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(chipText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = chipFg)
                }
            }
            val idx = m.newIndex
            Box(Modifier.padding(start = 6.dp, end = 14.dp)) {
                Text(
                    if (idx != null) numToStr(idx) else "——",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (idx != null) SUCCESS else MUTED,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (idx != null) Color(0xFFE8F5E9) else SURFACE)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
    }
}

// ───────────────────────── الإدارة ─────────────────────────

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(WHITE)
            .border(1.dp, LINE, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Black)
        content()
    }
}

@Composable
fun ManageScreen(state: AppState, onImport: () -> Unit, onExport: () -> Unit) {
    val context = LocalContext.current
    var worker by remember { mutableStateOf(state.worker) }
    var route by remember { mutableStateOf(state.routeNum) }
    var triplet by remember { mutableStateOf(state.triplet) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("🛣️ معلومات الجولة") {
            Column {
                FieldLabel("اسم القارئ (يُكتب في ملف التصدير)")
                StdInput(worker, { worker = it })
            }
            Column {
                FieldLabel("رقم الجولة")
                StdInput(route, { route = it })
            }
            Column {
                FieldLabel("الثلاثي (1 إلى 4)")
                StdInput(triplet, { triplet = it }, keyboardType = KeyboardType.Number)
            }
            BlueButton("حفظ معلومات الجولة", Modifier.fillMaxWidth()) {
                state.saveSettings(worker, route, triplet)
                Toast.makeText(context, "تم حفظ معلومات الجولة", Toast.LENGTH_SHORT).show()
            }
        }
        SectionCard("📁 الملفات") {
            BlueButton("استيراد ملف الجولة", Modifier.fillMaxWidth()) { onImport() }
            BlueButton("تصدير النتائج (ADE)", Modifier.fillMaxWidth(), SUCCESS) { onExport() }
        }
        SectionCard("🗑️ البيانات") {
            BlueButton("مسح كل البيانات", Modifier.fillMaxWidth(), DANGER) { confirmClear = true }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("مسح كل البيانات؟") },
            text = { Text("سيتم حذف جميع الزبائن والقراءات المحفوظة. تأكد أنك صدّرت النتائج قبل ذلك.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    state.clearAll()
                }) { Text("نعم، امسح") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun AddMeterDialog(state: AppState, onClose: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var prev by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var subType by remember { mutableStateOf("10") }
    val types = listOf("10" to "سكني", "20" to "إداري", "30" to "تجاري", "40" to "صناعي")

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("➕ إضافة عداد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StdInput(name, { name = it }, placeholder = "اسم المشترك")
                StdInput(code, { code = it }, placeholder = "رمز الزبون (مثل T00123)")
                StdInput(serial, { serial = it }, placeholder = "الرقم التسلسلي للعداد")
                StdInput(address, { address = it }, placeholder = "العنوان")
                StdInput(phone, { phone = it }, placeholder = "الهاتف (اختياري)", keyboardType = KeyboardType.Phone)
                StdInput(nationalId, { nationalId = it }, placeholder = "رقم التعريف الوطني (اختياري)", keyboardType = KeyboardType.Number)
                StdInput(prev, { prev = it }, placeholder = "القراءة السابقة", keyboardType = KeyboardType.Decimal)
                FieldLabel("نوع المشترك")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { t ->
                        val sel = subType == t.first
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (sel) Color(0xFFE3F2FD) else SURFACE)
                                .border(1.5.dp, if (sel) BLUE else LINE, RoundedCornerShape(8.dp))
                                .clickable { subType = t.first }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(t.first + "\n" + t.second, fontSize = 11.sp, textAlign = TextAlign.Center, color = if (sel) BLUE else INK)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = state.addMeter(code, name, serial, address, subType, parseNum(prev) ?: 0.0, phone, nationalId)
                if (err != null) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "تمت إضافة العداد", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

// ───────────────────────── شاشة القراءة ─────────────────────────

@Composable
fun MeterScreen(state: AppState, meter: Meter) {
    val context = LocalContext.current
    var indexText by remember(meter.id) { mutableStateOf(meter.newIndex?.let { numToStr(it) } ?: "") }
    var annots by remember(meter.id) { mutableStateOf(parseAnnots(meter.annot)) }
    var obs by remember(meter.id) { mutableStateOf(meter.obs) }
    var phone by remember(meter.id) { mutableStateOf(meter.phone) }
    var nationalId by remember(meter.id) { mutableStateOf(meter.nationalId) }
    var highMsg by remember(meter.id) { mutableStateOf<String?>(null) }
    var lowOpen by remember(meter.id) { mutableStateOf(false) }
    var gpsBusy by remember(meter.id) { mutableStateOf(false) }
    val focus = remember(meter.id) { FocusRequester() }

    val locPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun captureLocation() {
        if (!LocationHelper.hasPermission(context)) {
            locPermLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        gpsBusy = true
        LocationHelper.requestOnce(context) { gps ->
            gpsBusy = false
            if (gps != null) {
                state.saveLocation(meter.id, gps)
            } else {
                Toast.makeText(context, "❌ تعذّر تحديد الموقع", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null) {
            state.savePhoto(meter.id, bitmap)
            captureLocation()
        }
    }

    LaunchedEffect(meter.id) {
        if (meter.newIndex == null) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // لا شيء
            }
        }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    fun commit(lowReason: String) {
        val v = parseNum(indexText)
        val hasSignal = annots.any { it != "00" }
        val cons = if (v != null) Math.round(maxOf(0.0, v - meter.prevIndex) * 1000) / 1000.0 else 0.0
        val amount = if (v != null) Tariff.bill(cons) else 0L
        val updated = meter.copy(
            newIndex = v,
            consumption = cons,
            amount = amount,
            lowReason = lowReason,
            obs = obs,
            phone = phone.trim(),
            nationalId = nationalId.trim(),
            // في الأصل: selectedAnnots.length ? selectedAnnots.join('+') : 'INH' — إذا ماخترش
            // العامل أي رمز ملاحظة، يُسجَّل 'INH' افتراضياً (نفس سلوك index.html بالضبط).
            annot = if (annots.isEmpty()) "INH" else annots.joinToString("+"),
            status = if (hasSignal) STATUS_ANOM else STATUS_DONE,
            savedAt = System.currentTimeMillis()
        )
        state.saveMeter(updated)
        toast("✅ تم الحفظ")
        val next = state.nextPendingAfter(meter.id)
        if (next != null) {
            state.openId = next
        } else {
            toast("🎉 تم الانتهاء من كل العدادات المعلّقة")
            state.openId = null
        }
    }

    fun attemptSave(highConfirmed: Boolean) {
        val v = parseNum(indexText)
        val hasSignal = annots.any { it != "00" }

        if (v != null && !highConfirmed) {
            val cons = v - meter.prevIndex
            val avg = meter.avgConsumption
            if (avg != null && avg > 0 && cons > avg * 1.3) {
                highMsg = "الاستهلاك ${numToStr(Math.round(cons * 1000) / 1000.0)} م³ أعلى من معدل هذا الزبون ($avg م³) بأكثر من 30%."
                return
            }
        }

        if (!hasSignal) {
            if (v == null) {
                toast("أدخل القراءة الجديدة")
                return
            }
            if (v <= meter.prevIndex) {
                lowOpen = true
                return
            }
            commit("")
        } else {
            if (v != null && v <= meter.prevIndex) {
                lowOpen = true
                return
            }
            commit("")
        }
    }

    Column(Modifier.fillMaxSize().background(SURFACE)) {
        BlueTopBar(
            title = "تسجيل القراءة",
            badge = "#" + meter.code,
            onBack = { state.openId = null }
        )

        // ترويسة الزبون
        Column(Modifier.fillMaxWidth().background(WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${meter.code}  ·  ${meter.subType}", fontSize = 13.sp, color = BLUE, fontWeight = FontWeight.Bold)
                when (meter.meterStatus) {
                    "EM" -> {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE8F5E9)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("⚙️ يعمل (EM)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SUCCESS)
                        }
                    }
                    "AR" -> {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFEBEE)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("⛔ متوقف (AR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DANGER)
                        }
                    }
                }
            }
            Text(meter.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            Text("📍 ${meter.address.ifEmpty { "—" }}", fontSize = 12.sp, color = MUTED, modifier = Modifier.padding(top = 2.dp))
            Text("🔢 ${meter.serial.ifEmpty { "—" }}", fontSize = 12.sp, color = MUTED)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel("📞 هاتف الزبون")
                    StdInput(phone, { phone = it }, placeholder = "05xxxxxxxx", keyboardType = KeyboardType.Phone)
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel("🪪 رقم التعريف الوطني")
                    StdInput(nationalId, { nationalId = it }, placeholder = "اختياري", keyboardType = KeyboardType.Number)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            // القراءة الجديدة
            // ملاحظة: في index.html الحالي (الكامل)، القراءة السابقة ومعاينة الاستهلاك/المبلغ
            // مخفية عمداً بـ style="display:none" (dPrevIndex و .cons-preview) — العامل
            // لا يراها أثناء التسجيل رغم أنها تُحسب داخلياً. نطابق نفس السلوك هنا.
            Column(Modifier.fillMaxWidth().background(WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("القراءة الجديدة (م³)", fontSize = 11.5.sp, color = MUTED, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(WHITE)
                        .border(2.dp, BLUE, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = indexText,
                        onValueChange = { indexText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = TextStyle(
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp, color = INK, textAlign = TextAlign.Start
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp))

            // رمز الملاحظة
            Column(Modifier.fillMaxWidth().background(WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
                FieldLabel("رمز الملاحظة")
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ANNOTATIONS.chunked(3).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            rowItems.forEach { a ->
                                val selected = annots.contains(a.code)
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) Color(0xFFE3F2FD) else SURFACE)
                                        .border(1.5.dp, if (selected) BLUE else LINE, RoundedCornerShape(8.dp))
                                        .clickable {
                                            annots = if (selected) annots.filter { it != a.code } else annots + a.code
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(a.code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) BLUE else INK)
                                    Text(a.label, fontSize = 10.sp, color = if (selected) BLUE else MUTED, textAlign = TextAlign.Center)
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(8.dp))

            // ملاحظة
            Column(Modifier.fillMaxWidth().background(WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
                FieldLabel("ملاحظة")
                StdInput(obs, { obs = it }, placeholder = "أضف ملاحظة إن لزم")
            }
            Box(Modifier.fillMaxWidth().height(8.dp))

            // صورة العداد والموقع
            Column(Modifier.fillMaxWidth().background(WHITE).padding(horizontal = 14.dp, vertical = 12.dp)) {
                FieldLabel("صورة العداد")
                val photoPath = meter.photoPath
                val bmp = remember(photoPath) {
                    if (photoPath != null) BitmapFactory.decodeFile(photoPath) else null
                }
                Box(
                    Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp))
                        .background(SURFACE).border(2.dp, LINE, RoundedCornerShape(10.dp))
                        .clickable { cameraLauncher.launch(null) },
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📷", fontSize = 26.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("اضغط لالتقاط صورة العداد", fontSize = 12.5.sp, color = MUTED)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                FieldLabel("الموقع الجغرافي")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SURFACE)
                        .border(1.dp, LINE, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val lat = meter.lat
                    val lng = meter.lng
                    if (lat != null && lng != null) {
                        Text(
                            "📍 " + String.format(java.util.Locale.US, "%.6f, %.6f", lat, lng) +
                                (meter.locAccuracy?.let { " · ±${it}م" } ?: ""),
                            fontSize = 12.sp, color = INK
                        )
                    } else {
                        Text(if (gpsBusy) "⏳ جارٍ تحديد الموقع..." else "لم يُسجَّل الموقع بعد", fontSize = 12.sp, color = MUTED)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(BLUE)
                            .clickable(enabled = !gpsBusy) { captureLocation() }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(if (meter.lat != null) "تحديث" else "تحديد", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // شريط الحفظ
        Column(Modifier.fillMaxWidth().background(WHITE)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlueButton("💾 حفظ والتالي", Modifier.weight(1f)) { attemptSave(false) }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(SURFACE)
                        .border(1.5.dp, LINE, RoundedCornerShape(8.dp))
                        .clickable {
                            // مطابق لـ skipMeter(): ينتقل لأقرب عداد "معلّق" التالي (وليس مجرد التالي فالترتيب)،
                            // وإذا ماكانش، يرجع للقائمة (بحال goBack()).
                            toast("تم تخطي هذا العداد")
                            val nid = state.nextPendingAfter(meter.id)
                            state.openId = nid
                        }
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("تخطي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MUTED)
                }
            }
        }
    }

    val hm = highMsg
    if (hm != null) {
        AlertDialog(
            onDismissRequest = { highMsg = null },
            title = { Text("⚠️ تحقق من القراءة") },
            text = { Text(hm + "\n\nهل تريد الحفظ رغم ذلك؟") },
            confirmButton = {
                TextButton(onClick = {
                    highMsg = null
                    attemptSave(true)
                }) { Text("حفظ رغم ذلك") }
            },
            dismissButton = { TextButton(onClick = { highMsg = null }) { Text("تعديل القراءة") } }
        )
    }

    if (lowOpen) {
        AlertDialog(
            onDismissRequest = { lowOpen = false },
            title = { Text("⚠️ القراءة أقل أو تساوي السابقة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر سبباً لحفظ هذه القراءة:")
                    LOW_REASONS.chunked(2).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { r ->
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(SURFACE)
                                        .border(1.5.dp, LINE, RoundedCornerShape(9.dp))
                                        .clickable {
                                            lowOpen = false
                                            commit(r)
                                        }
                                        .padding(vertical = 11.dp, horizontal = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(r, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                            repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { lowOpen = false }) { Text("إلغاء") } }
        )
    }
}
