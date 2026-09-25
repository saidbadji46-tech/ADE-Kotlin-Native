package com.ade.meterreading

import java.util.Locale

const val STATUS_PENDING = "pending"
const val STATUS_DONE = "done"
const val STATUS_ANOM = "anom"

data class Meter(
    val id: Long = 0L,
    val code: String,
    val name: String,
    val address: String = "",
    val subType: String = "10",
    val serial: String = "",
    val prevIndex: Double = 0.0,
    val avgConsumption: Int? = null,
    val meterStatus: String? = null,
    val phone: String = "",
    val nationalId: String = "",
    val signaturePath: String? = null,
    val newIndex: Double? = null,
    val consumption: Double = 0.0,
    val amount: Long = 0L,
    val status: String = STATUS_PENDING,
    val annot: String = "",
    val lowReason: String = "",
    val obs: String = "",
    val savedAt: Long? = null,
    val rawLine: String? = null,
    val photoPath: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val locAccuracy: Int? = null
) {
    val hasReading: Boolean get() = newIndex != null
}

data class Annotation(val code: String, val label: String)

val ANNOTATIONS = listOf(
    Annotation("INH", "مسكن خالي"),
    Annotation("AS", "غياب مشترك"),
    Annotation("N.F", "باب مغلق"),
    Annotation("AR", "تلف عداد"),
    Annotation("FC", "تسرب ماء"),
    Annotation("VLD", "سرقة الماء"),
    Annotation("C.cs", "زجاج مكسور"),
    Annotation("INC", "غيرممكن"),
    Annotation("ILS", "غير مرئي"),
    Annotation("TP", "رقم أقل"),
    Annotation("MLP", "تركيب خاطىء"),
    Annotation("CO", "مقطوع"),
    Annotation("RS", "محذوف"),
    Annotation("INV", "اتجاه معكوس")
)

val LOW_REASONS = listOf("INH", "alonveur", "Tp", "Nouveau compteu", "rtor aziro", "AR")

fun numToStr(d: Double): String =
    if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

/** نفس simpleHash() بالأصل (FNV-1a 32-bit) — تُستعمل لتخزين رمز PIN بشكل غير معكوس. */
fun simpleHash(s: String): String {
    var h = -2128831035 // 2166136261 كـ Int موقّع (32-bit)
    for (c in s) {
        h = h xor c.code
        h *= 16777619 // ضرب Int في Kotlin يفيض (overflow) بنفس منطق Math.imul
    }
    return (h.toLong() and 0xFFFFFFFFL).toString()
}

fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

/** المسافة بالأمتار بين نقطتين جغرافيتين (صيغة Haversine) — مطابقة لـ haversineMeters(). */
fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    fun toRad(x: Double) = x * Math.PI / 180.0
    val dLat = toRad(lat2 - lat1)
    val dLng = toRad(lng2 - lng1)
    val sinLat = Math.sin(dLat / 2)
    val sinLng = Math.sin(dLng / 2)
    val a = sinLat * sinLat + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * sinLng * sinLng
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** يحوّل نص الإدخال إلى رقم. يقبل الفاصلة والأرقام العربية. يرجع null إن كان فارغاً أو غير صالح. */
fun parseNum(text: String): Double? {
    val normalized = text.trim().map { c ->
        when (c) {
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
            '\u066B', '\u066C', ',' -> '.'
            else -> c
        }
    }.joinToString("")
    return normalized.toDoubleOrNull()
}

fun parseAnnots(annot: String): List<String> =
    annot.split(Regex("\\s*\\+\\s*|\\s*,\\s*|\\s*\\|\\s*")).filter { it.isNotBlank() }

/** حساب الفاتورة حسب شطور ADE (نفس منطق التطبيق الأصلي). */
object Tariff {
    private fun tranches(cons: Double, limits: DoubleArray, rates: DoubleArray): Double {
        var total = 0.0
        var remaining = cons
        var prev = 0.0
        for (i in limits.indices) {
            val sliceMax = limits[i] - prev
            val used = minOf(remaining, sliceMax)
            total += used * rates[i]
            remaining -= used
            prev = limits[i]
            if (remaining <= 0) break
        }
        if (remaining > 0) total += remaining * rates[rates.size - 1]
        return total
    }

    fun bill(cons: Double): Long {
        val eauLimits = doubleArrayOf(25.0, 55.0, 82.0)
        val eauRates = doubleArrayOf(6.30, 20.48, 34.65, 40.95)
        val fixeEau = 240.0
        val assLimits = doubleArrayOf(25.0, 55.0, 82.0)
        val assRates = doubleArrayOf(2.35, 7.64, 12.93, 15.28)
        val fixeAss = 60.0

        val htEau = fixeEau + tranches(cons, eauLimits, eauRates)
        val htAss = fixeAss + tranches(cons, assLimits, assRates)
        val htBase = htEau + htAss

        val tva = htBase * 0.09
        val revGest = cons * 3
        val baseQual = htEau - fixeEau
        val revQual = baseQual * 0.04
        val revEco = baseQual * 0.04

        return Math.round(htBase + tva + revGest + revQual + revEco)
    }
}
