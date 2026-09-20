package com.ade.meterreading

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.OutputStream

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

    var subscriberCode by remember { mutableStateOf("A01T3216") }
    var subscriberName by remember { mutableStateOf("محمد الأمين") }
    var previousReadingText by remember { mutableStateOf("1250.0") }
    var currentReadingText by remember { mutableStateOf("") }
    var anomalyCode by remember { mutableStateOf("") }

    val previousReading = previousReadingText.toDoubleOrNull() ?: 0.0
    val currentReading = currentReadingText.toDoubleOrNull() ?: 0.0
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
                    Text(text = "القراءة السابقة: $previousReading م³", style = MaterialTheme.typography.bodyMedium)
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
                    Text(text = "الاستهلاك: $consumption م³", style = MaterialTheme.typography.titleMedium)
                    Text(text = "المبلغ الإجمالي: $totalAmount د.ج", style = MaterialTheme.typography.titleLarge)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val fileContent = "$subscriberCode|$subscriberName|$previousReading|$currentReading|$consumption|$totalAmount|$anomalyCode\n"
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

fun saveTourTextFile(context: android.content.Context, fileName: String, contentText: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/ADE")
            }
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            ?: return false

        resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
            outputStream.write(contentText.toByteArray(Charsets.UTF_8))
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
