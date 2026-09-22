package com.ade.meterreading

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
        modifier.fillMaxWidth().clip(RoundedCornerSh
