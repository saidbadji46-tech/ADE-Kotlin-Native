package com.ade.meterreading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection

// خط Tajawal (نفس خط index.html الأصلي بالضبط) — ملفات .ttf فـ res/font/.
private val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_black, FontWeight.Black)
)

// نطبّق Tajawal على كل أنماط النصوص فـ Material3، باش أي Text() بلا style مخصَّص
// (وهو غالبية استعمالات التطبيق) يورث الخط أوتوماتيكياً — بما فيها عناوين ونصوص
// AlertDialog والحوارات المبنية على Material مباشرة.
private val base = Typography()
private val TajawalTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Tajawal),
    displayMedium = base.displayMedium.copy(fontFamily = Tajawal),
    displaySmall = base.displaySmall.copy(fontFamily = Tajawal),
    headlineLarge = base.headlineLarge.copy(fontFamily = Tajawal),
    headlineMedium = base.headlineMedium.copy(fontFamily = Tajawal),
    headlineSmall = base.headlineSmall.copy(fontFamily = Tajawal),
    titleLarge = base.titleLarge.copy(fontFamily = Tajawal),
    titleMedium = base.titleMedium.copy(fontFamily = Tajawal),
    titleSmall = base.titleSmall.copy(fontFamily = Tajawal),
    bodyLarge = base.bodyLarge.copy(fontFamily = Tajawal),
    bodyMedium = base.bodyMedium.copy(fontFamily = Tajawal),
    bodySmall = base.bodySmall.copy(fontFamily = Tajawal),
    labelLarge = base.labelLarge.copy(fontFamily = Tajawal),
    labelMedium = base.labelMedium.copy(fontFamily = Tajawal),
    labelSmall = base.labelSmall.copy(fontFamily = Tajawal)
)

class MainActivity : ComponentActivity() {
    // عضو فالـclass (مش متغير محلي فـonCreate) باش نقدر نستعملو فـonStop() لقفل PIN
    // تلقائياً كل ما يخرج التطبيق للخلفية.
    private lateinit var state: AppState

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(applicationContext)
        state.load()
        setContent {
            // ملاحظة: الوضع الليلي أساسي حالياً (يبدّل ألوان شريط الحالة/شريط النظام
            // وMaterial defaults)، الشاشات المخصَّصة (القائمة، الخريطة...) عندها ألوان
            // ثابتة بعدها. نسخة كاملة بألوان مظلمة لكل شاشة راح تجي فمرحلة لاحقة.
            window.statusBarColor = if (state.darkMode) {
                android.graphics.Color.parseColor("#0A0A0A")
            } else {
                android.graphics.Color.parseColor("#0D47A1")
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val scheme = if (state.darkMode) {
                    darkColorScheme(primary = Color(0xFF64B5F6))
                } else {
                    lightColorScheme(primary = Color(0xFF1565C0))
                }
                MaterialTheme(colorScheme = scheme, typography = TajawalTypography) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (state.darkMode) Color(0xFF121212) else Color(0xFFF5F5F5)
                    ) {
                        AppRoot(state)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // بحال قفل تطبيقات الأمان: كل ما التطبيق يخرج للخلفية (مكالمة، تطبيق آخر...)
        // ويكون PIN مفعّلاً، نقفلو مباشرة.
        if (::state.isInitialized) {
            state.lockNow()
        }
    }
}
