package com.ade.meterreading

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GREEN = Color(0xFF2E7D32)
private val ORANGE = Color(0xFFEF6C00)
private val GRAY = Color(0xFF757575)

// ───────────────────────── قائمة الجولة ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

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

    val all = state.meters
    val doneCount = all.count { it.hasReading }
    val pendingCount = all.count { it.status != STATUS_DONE && it.status != STATUS_ANOM }
    val anomCount = all.count { it.status == STATUS_ANOM }
    val tabIndex = state.tab
    val queryText = state.query

    val visible = remember(all, queryText, tabIndex) {
        val q = queryText.trim().lowercase()
        val qFlat = q.replace(" ", "")
        all.filter { m ->
            val matchQ = q.isEmpty() ||
                m.name.lowercase().contains(q) ||
                m.code.lowercase().contains(q) ||
                m.address.lowercase().contains(q) ||
                m.serial.lowercase().replace(" ", "").contains(qFlat)
            val matchTab = when (tabIndex) {
                1 -> m.status != STATUS_DONE && m.status != STATUS_ANOM
                2 -> m.hasReading
                3 -> m.status == STATUS_ANOM
                else -> true
            }
            matchQ && matchTab
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("قراءات العدادات", fontWeight = FontWeight.Bold)
                        Text("الجولة ${state.routeNum}  •  R${state.triplet}", fontSize = 12.sp, color = GRAY)
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("استيراد ملف الجولة") },
                                onClick = {
                                    menuOpen = false
                                    picker.launch(arrayOf("*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تصدير النتائج (ADE)") },
                                onClick = {
                                    menuOpen = false
                                    startExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الإعدادات") },
                                onClick = {
                                    menuOpen = false
                                    showSettings = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مسح كل البيانات") },
                                onClick = {
                                    menuOpen = false
                                    confirmClear = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("📊 نسبة الإنجاز", fontWeight = FontWeight.Bold)
                    Text("$doneCount / ${all.size}")
                }
                Spacer(Modifier.height(6.dp))
                val frac = if (all.isEmpty()) 0f else doneCount.toFloat() / all.size
                Box(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color(0xFFE0E0E0))
                ) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(GREEN))
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = { state.query = it },
                singleLine = true,
                placeholder = { Text("بحث بالاسم أو الرمز أو الرقم التسلسلي") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(6.dp))

            val labels = listOf(
                "الكل (${all.size})",
                "معلّقة ($pendingCount)",
                "مقروءة ($doneCount)",
                "ملاحظات ($anomCount)"
            )
            TabRow(selectedTabIndex = state.tab) {
                labels.forEachIndexed { i, label ->
                    Tab(
                        selected = state.tab == i,
                        onClick = { state.tab = i },
                        text = { Text(label, fontSize = 12.sp, maxLines = 1) }
                    )
                }
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
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("استيراد ملف الجولة") }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { m ->
                        MeterRow(m) { state.openId = m.id }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(state) { showSettings = false }
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

    val msg = message
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("حسناً") } }
        )
    }
}

@Composable
fun MeterRow(m: Meter, onClick: () -> Unit) {
    val barColor = when (m.status) {
        STATUS_DONE -> GREEN
        STATUS_ANOM -> ORANGE
        else -> GRAY
    }
    val chip = when (m.status) {
        STATUS_DONE -> "مرفوعة"
        STATUS_ANOM -> m.annot.ifEmpty { "إشارة" }
        else -> "معلّقة"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(barColor))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text("#${m.code} · ${m.subType}", fontSize = 12.sp, color = GRAY)
                Text(m.name, fontWeight = FontWeight.Bold)
                Text("🔢 ${m.serial.ifEmpty { "—" }}   📍 ${m.address.ifEmpty { "—" }}", fontSize = 12.sp, color = GRAY)
                if (m.hasReading) Text("💧 استهلاك ${fmt2(m.consumption)} م³", fontSize = 12.sp, color = GRAY)
                Text(chip, fontSize = 12.sp, color = barColor, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                val idx = m.newIndex
                Text(
                    if (idx != null) numToStr(idx) else "——",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (idx != null) GREEN else GRAY
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(state: AppState, onClose: () -> Unit) {
    var worker by remember { mutableStateOf(state.worker) }
    var route by remember { mutableStateOf(state.routeNum) }
    var triplet by remember { mutableStateOf(state.triplet) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("الإعدادات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = worker, onValueChange = { worker = it }, singleLine = true,
                    label = { Text("اسم القارئ (يُكتب في ملف التصدير)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = route, onValueChange = { route = it }, singleLine = true,
                    label = { Text("رقم الجولة") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = triplet, onValueChange = { triplet = it }, singleLine = true,
                    label = { Text("الثلاثي (1 إلى 4)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.saveSettings(worker, route, triplet)
                onClose()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

// ───────────────────────── شاشة القراءة ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeterScreen(state: AppState, meter: Meter) {
    val context = LocalContext.current
    var indexText by remember(meter.id) { mutableStateOf(meter.newIndex?.let { numToStr(it) } ?: "") }
    var annots by remember(meter.id) { mutableStateOf(parseAnnots(meter.annot)) }
    var obs by remember(meter.id) { mutableStateOf(meter.obs) }
    var highMsg by remember(meter.id) { mutableStateOf<String?>(null) }
    var lowOpen by remember(meter.id) { mutableStateOf(false) }
    val focus = remember(meter.id) { FocusRequester() }

    LaunchedEffect(meter.id) {
        if (meter.newIndex == null) {
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // لا شيء: قد لا تكون الشاشة جاهزة بعد
            }
        }
    }

    val value = parseNum(indexText)
    val previewCons = if (value != null && value > meter.prevIndex) Math.round((value - meter.prevIndex) * 1000) / 1000.0 else 0.0
    val previewAmount = if (previewCons > 0) Tariff.bill(previewCons) else 0L

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
            annot = annots.joinToString("+"),
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(meter.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("#${meter.code}", fontSize = 12.sp, color = GRAY)
                    }
                },
                navigationIcon = {
                    TextButton(onClick = { state.openId = null }) { Text("رجوع") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("رقم الجولة: ${state.routeNum}   •   النوع: ${meter.subType}")
                    Text("📍 ${meter.address.ifEmpty { "—" }}")
                    Text("🔢 الرقم التسلسلي: ${meter.serial.ifEmpty { "—" }}")
                    Text("القراءة السابقة: ${numToStr(meter.prevIndex)} م³", fontWeight = FontWeight.Bold)
                    val avg = meter.avgConsumption
                    if (avg != null) Text("المعدل المرجعي: $avg م³", fontSize = 12.sp, color = GRAY)
                }
            }

            OutlinedTextField(
                value = indexText,
                onValueChange = { indexText = it },
                singleLine = true,
                label = { Text("القراءة الجديدة (م³)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("الاستهلاك: ${fmt2(previewCons)} م³", fontWeight = FontWeight.Bold)
                    Text("المبلغ الإجمالي: $previewAmount د.ج", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text("رمز الملاحظة", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ANNOTATIONS.forEach { a ->
                    val selected = annots.contains(a.code)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            annots = if (selected) annots.filter { it != a.code } else annots + a.code
                        },
                        label = { Text(a.code + " · " + a.label, fontSize = 12.sp) }
                    )
                }
            }

            OutlinedTextField(
                value = obs,
                onValueChange = { obs = it },
                label = { Text("ملاحظة / observation") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val id = state.adjacent(meter.id, -1)
                    if (id != null) state.openId = id
                }) { Text("السابق") }
                Button(onClick = { attemptSave(false) }, modifier = Modifier.weight(1f)) {
                    Text("💾 حفظ والتالي")
                }
                OutlinedButton(onClick = {
                    val id = state.adjacent(meter.id, 1)
                    if (id != null) state.openId = id
                }) { Text("التالي") }
            }
            Spacer(Modifier.height(24.dp))
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اختر سبباً لحفظ هذه القراءة:")
                    LOW_REASONS.forEach { r ->
                        OutlinedButton(
                            onClick = {
                                lowOpen = false
                                commit(r)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(r) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { lowOpen = false }) { Text("إلغاء") } }
        )
    }
}
