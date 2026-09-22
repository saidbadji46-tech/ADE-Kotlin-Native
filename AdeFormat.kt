package com.ade.meterreading

import java.util.Calendar
import java.util.Locale

// قراءة ملف ADE ثابت العرض وكتابة سطور التصدير بنفس تنسيق التطبيق الأصلي.
object AdeFormat {

    private val RE_START7 = Regex("^\\d{7}")
    private val RE_TOUR = Regex("^T\\d{5}\\z", RegexOption.IGNORE_CASE)
    private val RE_TAIL = Regex("(\\d+)(EM|AR)\\s+(\\d+)\\s*\\z", RegexOption.IGNORE_CASE)
    private val RE_SERIAL = Regex("20\\d{8}")
    private val RE_UNDERSCORE_EDGES = Regex("^_+|_+\\z")

    private fun sub(s: String, from: Int, to: Int = s.length): String {
        val a = from.coerceIn(0, s.length)
        val b = to.coerceIn(0, s.length)
        return if (a >= b) "" else s.substring(a, b)
    }

    private fun padRight(s: String, len: Int): String {
        return if (s.length >= len) s.substring(0, len) else s + " ".repeat(len - s.length)
    }

    // يرجع قائمة الزبائن، أو null إن لم يكن الملف بتنسيق ADE.
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
                var prevIndex = 0.0
                var avg: Int? = null
                var meterStatus: String? = null
                if (m != null) {
                    val big = m.groupValues[3].toLongOrNull()
                    if (big != null) prevIndex = (big / 10L).toDouble()
                    avg = m.groupValues[1].toIntOrNull()
                    // EM = "en marche" (يشتغل)، AR = "à l'arrêt" (متوقف) — حالة العداد الميكانيكية حسب ADE
                    meterStatus = m.groupValues[2].uppercase()
                }
                val serials = RE_SERIAL.findAll(raw).map { it.value }.toList()
                var serial = ""
                if (serials.size >= 2) {
                    serial = serials[1]
                } else if (serials.size == 1) {
                    serial = serials[0]
                }
                val parts = ArrayList<String>()
                if (addr1.isNotEmpty()) parts.add(addr1)
                if (addr2.isNotEmpty()) parts.add(addr2)
                val address = parts.joinToString("، ")
                records.add(
                    Meter(
                        code = meterSeg,
                        name = name,
                        address = address,
                        subType = subType,
                        serial = serial,
                        prevIndex = prevIndex,
                        avgConsumption = avg,
                        meterStatus = meterStatus,
                        rawLine = raw
                    )
                )
            }
        }
        return if (total > 0 && matched >= total * 0.5) records else null
    }

    // رقم الجولة (آخر 3 أرقام).
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
        val fm = Regex("(?:^|[_-])(?:T|CH)?(\\d{1,4})(?:\\.txt)?\\z", RegexOption.IGNORE_CASE).find(fileName)
        if (fm != null) return fm.groupValues[1]
        return ""
    }

    // الرقم الثلاثي (1..4) من اسم الملف، مثل A01T3216 يعطي 3.
    fun detectTriplet(fileName: String, lines: List<String>): String {
        val src = fileName + " " + lines.take(20).joinToString(" ")
        val re = Regex(
            "(?:^|[^A-Za-z0-9])(?:A\\d+)?T(\\d)(?=\\d{2,4}(?:\\.txt)?(?:\\s|\\z))",
            RegexOption.IGNORE_CASE
        )
        val m = re.find(src)
        if (m != null) return m.groupValues[1]
        return ""
    }

    fun exportName(routeNum: String, triplet: String): String {
        var clean = routeNum.trim()
        clean = clean.replace(Regex("^R\\d+T?", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("^T", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("^CH", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("[^0-9A-Za-z_-]"), "")
        if (clean.isEmpty()) clean = "00000"
        val t = if (Regex("^[1-4]\\z").matches(triplet)) triplet else "1"
        return "R" + t + "T" + clean
    }

    // رمز الحالة في ملف التصدير: EM أو AR أو CI أو II أو null (بدون تغيير).
    private fun statusCode(m: Meter): String? {
        if (m.newIndex != null) return "EM"
        if (m.status != STATUS_ANOM) return null
        val codes = m.annot.split("+").map { it.trim() }
        if (codes.contains("AR")) return "AR"
        if (codes.contains("INH")) return "CI"
        return "II"
    }

    // سطر واحد من ملف التصدير.
    fun exportLine(m: Meter, worker: String): String {
        val cal = Calendar.getInstance()
        val stamp = m.savedAt
        cal.timeInMillis = if (stamp != null) stamp else System.currentTimeMillis()
        val dateStr = String.format(
            Locale.US, "%04d%02d%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val timeStr = String.format(
            Locale.US, "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )

        val status2 = statusCode(m)
        val newIdx = m.newIndex
        val val2 = if (newIdx != null) numToStr(newIdx) else ""

        val raw = m.rawLine
        if (raw != null) {
            if (status2 == null) return raw
            val tailStart = 149
            val tail = sub(raw, tailStart)
            val oldMatch = Regex("^\\s*\\d+[A-Z]{2}").find(tail)
            val oldPart = if (oldMatch != null) oldMatch.value else ""
            val afterOld = tail.substring(oldPart.length)
            val bigMatch = Regex("^\\s*\\d+").find(afterOld)
            val bigPart = if (bigMatch != null) bigMatch.value else ""
            val prefixLen = tailStart + oldPart.length + bigPart.length
            val prefix = sub(raw, 0, prefixLen)
            val remain = raw.length - prefixLen
            val suffixFull = "  " + dateStr + timeStr + padRight(worker, 17) + padRight("unknown", 10) + status2 + val2
            val suffix = if (suffixFull.length >= remain) {
                suffixFull.substring(0, maxOf(remain, 0))
            } else {
                suffixFull + " ".repeat(remain - suffixFull.length)
            }
            return prefix + suffix
        }

        val st = if (status2 != null) status2 else "II"
        val line = padRight("", 11) + padRight(m.code, 6) + padRight(m.name, 30) +
            padRight(m.address, 60) + padRight("", 42) +
            "  " + dateStr + timeStr + padRight(worker, 17) + padRight("unknown", 10) + st + val2
        return padRight(line, 256)
    }

    /**
     * ترتيب تصدير الأسطر داخل ملف .txt (لا يغيّر ترتيب أي قائمة أخرى):
     * 1) استهلاك عادي، 2) فوق المعدل، 3) صفر/سالب، 4) فيها إشارة، 5) لم تُقرأ بعد.
     * نفس منطق exportSortCategory / sortMetersForExport في التطبيق الأصلي.
     */
    private fun sortCategory(m: Meter): Int {
        if (m.status == STATUS_PENDING) return 5
        if (m.status == STATUS_ANOM) return 4
        val cons = m.consumption
        if (cons <= 0) return 3
        val avg = m.avgConsumption
        if (avg != null && avg > 0 && cons > avg * 1.3) return 2
        return 1
    }

    /** ترتيب مستقر (stable) — عند تساوي الفئة يبقى الترتيب الأصلي كما هو. */
    fun sortForExport(list: List<Meter>): List<Meter> =
        list.withIndex().sortedWith(compareBy({ sortCategory(it.value) }, { it.index })).map { it.value }
}
