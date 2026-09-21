package com.ade.meterreading

import java.util.Calendar
import java.util.Locale

/** قراءة ملف ADE ثابت العرض وكتابة سطور التصدير بنفس تنسيق التطبيق الأصلي. */
object AdeFormat {

    private val RE_START7 = Regex("^\\d{7}")
    private val RE_TOUR = Regex("^T\\d{5}\$", RegexOption.IGNORE_CASE)
    private val RE_TAIL = Regex("(\\d+)(EM|AR)\\s+(\\d+)\\s*\$", RegexOption.IGNORE_CASE)
    private val RE_SERIAL = Regex("20\\d{8}")
    private val RE_UNDERSCORE_EDGES = Regex("^_+|_+\$")

    private fun sub(s: String, from: Int, to: Int = s.length): String {
        val a = from.coerceIn(0, s.length)
        val b = to.coerceIn(0, s.length)
        return if (a >= b) "" else s.substring(a, b)
    }

    private fun padRight(s: String, len: Int): String =
        if (s.length >= len) s.substring(0, len) else s + " ".repeat(len - s.length)

    /** يرجع قائمة الزبائن، أو null إن لم يكن الملف بتنسيق ADE. */
    fun parse(lines: List<String>): List<Meter>? {
        val records = ArrayList<Meter>()
        var matched = 0
        var total = 0
        for (raw in lines) {
            if (raw.trim().length < 20) continue
            total++
            val meterSeg = sub(raw, 11, 17).trim()
            if (RE_START7.containsMatchIn(raw) && RE_TOUR.matches(meterSeg)) {
                matched++
                val name = sub(raw, 17, 47).trim().replace("\uFFFD", "")
                val addr1 = sub(raw, 47, 77).trim().replace("\uFFFD", "")
                val addr2 = sub(raw, 77, 107).replace(RE_UNDERSCORE_EDGES, "").trim().replace("\uFFFD", "")
                val tail = sub(raw, 107)
                val subType = sub(tail, 0, 2).trim().ifEmpty { "10" }
                val restTail = sub(tail, 42)
                val m = RE_TAIL.find(restTail)
                val prevIndex = m?.groupValues?.get(3)?.toLongOrNull()?.div(10)?.toDouble() ?: 0.0
                val avg = m?.groupValues?.get(1)?.toIntOrNull()
                val serials = RE_SERIAL.findAll(raw).map { it.value }.toList()
                val serial = when {
                    serials.size >= 2 -> serials[1]
                    serials.size == 1 -> serials[0]
                    else -> ""
                }
                val address = listOf(addr1, addr2).filter { it.isNotEmpty() }.joinToString("، ")
                records.add(
                    Meter(
                        code = meterSeg,
                        name = name,
                        address = address,
                        subType = subType,
                        serial = serial,
                        prevIndex = prevIndex,
                        avgConsumption = avg,
                        rawLine = raw
                    )
                )
            }
        }
        return if (total > 0 && matched >= total * 0.5) records else null
    }

    /** رقم الجولة (آخر 3 أرقام). */
    fun detectRoute(lines: List<String>, fileName: String): String {
        val reA = Regex("^(\\d{7})\\s+T\\d{5}", RegexOption.IGNORE_CASE)
        val reB = Regex("^(\\d{7})")
        val reT = Regex("T\\d{5}", RegexOption.IGNORE_CASE)
        for (raw in lines.take(30)) {
            val line = raw.trim()
            val m = reA.find(line)
            if (m != null) return m.groupValues[1].takeLast(3)
            val m2 = reB.find(line)
            if (m2 != null && reT.containsMatchIn(line)) return m2.groupValues[1].takeLast(3)
        }
        val fm = Regex("(?:^|[_-])(?:T|CH)?(\\d{1,4})(?:\\.txt)?\$", RegexOption.IGNORE_CASE).find(fileName)
        return fm?.groupValues?.get(1) ?: ""
    }

    /** الرقم الثلاثي (1..4) من اسم الملف مثل A01T3216 -> 3. */
    fun detectTriplet(fileName: String, lines: List<String>): String {
        val src = fileName + " " + lines.take(20).joinToString(" ")
        val re = Regex(
            "(?:^|[^A-Za-z0-9])(?:A\\d+)?T(\\d)(?=\\d{2,4}(?:\\.txt)?(?:\\s|\$))",
            RegexOption.IGNORE_CASE
        )
        return re.find(src)?.groupValues?.get(1) ?: ""
    }

    fun exportName(routeNum: String, triplet: String): String {
        var clean = routeNum.trim()
            .replace(Regex("^R\\d+T?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^T", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^CH", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[^0-9A-Za-z_-]"), "")
        if (clean.isEmpty()) clean = "00000"
        val t = if (Regex("^[1-4]\$").matches(triplet)) triplet else "1"
        return "R${t}T$clean"
    }

    /** سطر واحد من ملف التصدير. */
    fun exportLine(m: Meter, worker: String): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = m.savedAt ?: System.currentTimeMillis()
        val dateStr = String.format(
            Locale.US, "%04d%02d%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val timeStr = String.format(
            Locale.US, "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )

        var status2: String? = null
        var val2 = ""
        val newIdx = m.newIndex
        if (newIdx != null) {
            status2 = "EM"
            val2 = numToStr(newIdx)
        } else if (m.status == STATUS_ANOM) {
