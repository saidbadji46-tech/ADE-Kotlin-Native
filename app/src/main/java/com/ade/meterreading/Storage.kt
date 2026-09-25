package com.ade.meterreading

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

private const val DB_VERSION = 5

/** قاعدة بيانات SQLite محلية لحفظ الزبائن والإعدادات. */
class Db(context: Context) : SQLiteOpenHelper(context, "ade_meters.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE meters (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "code TEXT NOT NULL, name TEXT NOT NULL, address TEXT, sub_type TEXT, serial TEXT, " +
                "prev_index REAL, avg_cons INTEGER, new_index REAL, consumption REAL, amount INTEGER, " +
                "status TEXT, annot TEXT, low_reason TEXT, obs TEXT, saved_at INTEGER, raw_line TEXT, " +
                "photo_path TEXT, lat REAL, lng REAL, loc_accuracy INTEGER, " +
                "meter_status TEXT, phone TEXT, national_id TEXT, sort_order INTEGER, signature_path TEXT)"
        )
        db.execSQL("CREATE INDEX idx_meters_code ON meters(code)")
        db.execSQL("CREATE TABLE settings (k TEXT PRIMARY KEY NOT NULL, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            for (col in listOf(
                "photo_path TEXT", "lat REAL", "lng REAL", "loc_accuracy INTEGER"
            )) {
                try {
                    db.execSQL("ALTER TABLE meters ADD COLUMN $col")
                } catch (e: Exception) {
                    // العمود موجود مسبقاً على الأغلب
                }
            }
        }
        if (oldVersion < 3) {
            for (col in listOf(
                "meter_status TEXT", "phone TEXT", "national_id TEXT"
            )) {
                try {
                    db.execSQL("ALTER TABLE meters ADD COLUMN $col")
                } catch (e: Exception) {
                    // العمود موجود مسبقاً على الأغلب
                }
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE meters ADD COLUMN sort_order INTEGER")
            } catch (e: Exception) {
                // العمود موجود مسبقاً على الأغلب
            }
            // نحافظ على ترتيب id الحالي كترتيب افتراضي أول مرة.
            db.execSQL("UPDATE meters SET sort_order = id WHERE sort_order IS NULL")
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE meters ADD COLUMN signature_path TEXT")
            } catch (e: Exception) {
                // العمود موجود مسبقاً على الأغلب
            }
        }
    }

    private fun toValues(m: Meter): ContentValues {
        val cv = ContentValues()
        cv.put("code", m.code)
        cv.put("name", m.name)
        cv.put("address", m.address)
        cv.put("sub_type", m.subType)
        cv.put("serial", m.serial)
        cv.put("prev_index", m.prevIndex)
        if (m.avgConsumption == null) cv.putNull("avg_cons") else cv.put("avg_cons", m.avgConsumption)
        if (m.newIndex == null) cv.putNull("new_index") else cv.put("new_index", m.newIndex)
        cv.put("consumption", m.consumption)
        cv.put("amount", m.amount)
        cv.put("status", m.status)
        cv.put("annot", m.annot)
        cv.put("low_reason", m.lowReason)
        cv.put("obs", m.obs)
        if (m.savedAt == null) cv.putNull("saved_at") else cv.put("saved_at", m.savedAt)
        if (m.rawLine == null) cv.putNull("raw_line") else cv.put("raw_line", m.rawLine)
        if (m.photoPath == null) cv.putNull("photo_path") else cv.put("photo_path", m.photoPath)
        if (m.lat == null) cv.putNull("lat") else cv.put("lat", m.lat)
        if (m.lng == null) cv.putNull("lng") else cv.put("lng", m.lng)
        if (m.locAccuracy == null) cv.putNull("loc_accuracy") else cv.put("loc_accuracy", m.locAccuracy)
        if (m.meterStatus == null) cv.putNull("meter_status") else cv.put("meter_status", m.meterStatus)
        cv.put("phone", m.phone)
        cv.put("national_id", m.nationalId)
        if (m.signaturePath == null) cv.putNull("signature_path") else cv.put("signature_path", m.signaturePath)
        return cv
    }

    private fun readMeter(c: Cursor): Meter {
        fun str(col: String): String = c.getString(c.getColumnIndexOrThrow(col)) ?: ""
        fun dbl(col: String): Double = c.getDouble(c.getColumnIndexOrThrow(col))
        fun dblOrNull(col: String): Double? {
            val i = c.getColumnIndexOrThrow(col)
            return if (c.isNull(i)) null else c.getDouble(i)
        }
        fun intOrNull(col: String): Int? {
            val i = c.getColumnIndexOrThrow(col)
            return if (c.isNull(i)) null else c.getInt(i)
        }
        fun longOrNull(col: String): Long? {
            val i = c.getColumnIndexOrThrow(col)
            return if (c.isNull(i)) null else c.getLong(i)
        }
        fun strOrNull(col: String): String? {
            val i = c.getColumnIndexOrThrow(col)
            return if (c.isNull(i)) null else c.getString(i)
        }
        return Meter(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            code = str("code"),
            name = str("name"),
            address = str("address"),
            subType = str("sub_type").ifEmpty { "10" },
            serial = str("serial"),
            prevIndex = dbl("prev_index"),
            avgConsumption = intOrNull("avg_cons"),
            newIndex = dblOrNull("new_index"),
            consumption = dbl("consumption"),
            amount = c.getLong(c.getColumnIndexOrThrow("amount")),
            status = str("status").ifEmpty { STATUS_PENDING },
            annot = str("annot"),
            lowReason = str("low_reason"),
            obs = str("obs"),
            savedAt = longOrNull("saved_at"),
            rawLine = strOrNull("raw_line"),
            photoPath = strOrNull("photo_path"),
            lat = dblOrNull("lat"),
            lng = dblOrNull("lng"),
            locAccuracy = intOrNull("loc_accuracy"),
            meterStatus = strOrNull("meter_status"),
            phone = str("phone"),
            nationalId = str("national_id"),
            signaturePath = strOrNull("signature_path")
        )
    }

    fun allMeters(): List<Meter> {
        val list = ArrayList<Meter>()
        readableDatabase.rawQuery("SELECT * FROM meters ORDER BY sort_order, id", null).use { c ->
            while (c.moveToNext()) list.add(readMeter(c))
        }
        return list
    }

    /**
     * يعيد ترتيب العدادات بشكل دائم حسب القائمة الكاملة من المعرّفات (بنفس الترتيب المطلوب).
     * تُستعمل من "ترتيب حسب الأقرب" (sortRouteByNearest) — مطابقة لِـ `meters=[...ordered,...rest]; saveMeters()` بالأصل.
     */
    fun reorderMeters(orderedIds: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            orderedIds.forEachIndexed { index, id ->
                val cv = ContentValues()
                cv.put("sort_order", index)
                db.update("meters", cv, "id = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateMeter(m: Meter) {
        writableDatabase.update("meters", toValues(m), "id = ?", arrayOf(m.id.toString()))
    }

    fun deleteAllMeters() {
        writableDatabase.delete("meters", null, null)
    }

    /** يرجع (المضاف، المحدَّث، المتجاوَز). */
    fun importRecords(records: List<Meter>): Triple<Int, Int, Int> {
        var added = 0
        var updated = 0
        var skipped = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            var nextOrder: Long
            db.rawQuery("SELECT COALESCE(MAX(sort_order), -1) FROM meters", null).use { c ->
                nextOrder = if (c.moveToFirst()) c.getLong(0) + 1 else 0L
            }
            for (rec in records) {
                if (rec.name.isEmpty() || rec.code.isEmpty()) {
                    skipped++
                    continue
                }
                var existingId = -1L
                var exSerial = ""
                var exRaw: String? = null
                var exAvg: Int? = null
                var exStatus: String? = null
                db.rawQuery(
                    "SELECT id, serial, raw_line, avg_cons, meter_status FROM meters WHERE code = ? COLLATE NOCASE LIMIT 1",
                    arrayOf(rec.code)
                ).use { c ->
                    if (c.moveToFirst()) {
                        existingId = c.getLong(0)
                        exSerial = c.getString(1) ?: ""
                        exRaw = if (c.isNull(2)) null else c.getString(2)
                        exAvg = if (c.isNull(3)) null else c.getInt(3)
                        exStatus = if (c.isNull(4)) null else c.getString(4)
                    }
                }
                if (existingId >= 0) {
                    val cv = ContentValues()
                    var touched = false
                    if (rec.serial.isNotEmpty() && exSerial != rec.serial) {
                        cv.put("serial", rec.serial)
                        touched = true
                    }
                    if (rec.rawLine != null && exRaw == null) {
                        cv.put("raw_line", rec.rawLine)
                        touched = true
                    }
                    if (rec.avgConsumption != null && exAvg != rec.avgConsumption) {
                        cv.put("avg_cons", rec.avgConsumption)
                        touched = true
                    }
                    if (rec.meterStatus != null && exStatus != rec.meterStatus) {
                        cv.put("meter_status", rec.meterStatus)
                        touched = true
                    }
                    if (touched) {
                        db.update("meters", cv, "id = ?", arrayOf(existingId.toString()))
                        updated++
                    } else {
                        skipped++
                    }
                } else {
                    val cv = toValues(rec)
                    cv.put("sort_order", nextOrder)
                    nextOrder++
                    db.insert("meters", null, cv)
                    added++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return Triple(added, updated, skipped)
    }

    fun getSetting(key: String, def: String): String {
        readableDatabase.rawQuery("SELECT v FROM settings WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: def
        }
        return def
    }

    fun setSetting(key: String, value: String) {
        val cv = ContentValues()
        cv.put("k", key)
        cv.put("v", value)
        writableDatabase.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

/** حفظ ملف في المجلد العام Documents/ADE. */
object Exporter {
    private const val FOLDER = "ADE"

    fun saveToDocuments(context: Context, fileName: String, bytes: ByteArray): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), FOLDER)
                if (!dir.exists() && !dir.mkdirs()) {
                    false
                } else {
                    File(dir, fileName).writeBytes(bytes)
                    true
                }
            } else {
                saveViaMediaStore(context, fileName, bytes)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun findExisting(context: Context, collection: Uri, fileName: String, relPath: String): Uri? {
        val selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND (" +
            MediaStore.MediaColumns.RELATIVE_PATH + "=? OR " +
            MediaStore.MediaColumns.RELATIVE_PATH + "=?)"
        val args = arrayOf(fileName, relPath, relPath.trimEnd('/'))
        context.contentResolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID), selection, args, null
        )?.use { c ->
            if (c.moveToFirst()) return ContentUris.withAppendedId(collection, c.getLong(0))
        }
        return null
    }

    private fun saveViaMediaStore(context: Context, fileName: String, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relPath = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/"

        val existing = findExisting(context, collection, fileName, relPath)
        if (existing != null) {
            try {
                val out = resolver.openOutputStream(existing, "wt")
                if (out != null) {
                    out.use { it.write(bytes) }
                    return true
                }
            } catch (e: Exception) {
                // ملف قديم لا نملك صلاحية تعديله: ننشئ ملفاً جديداً
            }
        }

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
        val uri = resolver.insert(collection, values) ?: return false
        val out = resolver.openOutputStream(uri, "wt") ?: return false
        out.use { it.write(bytes) }
        return true
    }
}

/** حفظ صور العدادات داخل تخزين التطبيق الخاص (لا يحتاج إذن تخزين). */
object PhotoStore {
    private const val DIR_NAME = "meter_photos"

    fun save(context: Context, meterId: Long, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "m_" + meterId + "_" + System.currentTimeMillis() + ".jpg")
            val scaled = scaleDown(bitmap, 900)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(path: String?) {
        if (path == null) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            // تجاهل
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, h, true)
    }
}

/** تخزين توقيع الزبون كملف PNG (نفس نمط PhotoStore، بصيغة PNG بدل JPEG للحفاظ على وضوح الخطوط). */
object SignatureStore {
    private const val DIR_NAME = "meter_signatures"

    fun save(context: Context, meterId: Long, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "sig_" + meterId + "_" + System.currentTimeMillis() + ".png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun delete(path: String?) {
        if (path == null) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            // تجاهل
        }
    }
}
