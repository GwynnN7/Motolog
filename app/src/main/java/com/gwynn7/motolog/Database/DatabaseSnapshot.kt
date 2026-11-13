package com.gwynn7.motolog.Database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import java.io.File

/** A transactional logical snapshot includes committed WAL data, without closing live Room instances. */
suspend fun RoomDatabase.snapshotTo(file: File) = withTransaction {
    file.parentFile?.mkdirs()
    val source = openHelper.writableDatabase
    SQLiteDatabase.openOrCreateDatabase(file, null).use { target ->
        // Export only the main database file, regardless of the device's SQLite defaults.
        target.disableWriteAheadLogging()
        target.rawQuery("PRAGMA journal_mode=DELETE", null).use {
            check(it.moveToFirst() && it.getString(0).equals("delete", ignoreCase = true)) {
                "Could not create a self-contained database snapshot"
            }
        }
        target.execSQL("DROP TABLE IF EXISTS android_metadata")
        target.beginTransaction()
        try {
            val tables = mutableListOf<Pair<String, String>>()
            source.query("SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND sql IS NOT NULL").use { cursor ->
                while (cursor.moveToNext()) tables += cursor.getString(0) to cursor.getString(1)
            }
            for ((name, sql) in tables) {
                target.execSQL(sql)
                source.query("SELECT * FROM ${sqlIdentifier(name)}").use { cursor ->
                    val columns = cursor.columnNames.joinToString(",", transform = ::sqlIdentifier)
                    val placeholders = List(cursor.columnCount) { "?" }.joinToString(",")
                    target.compileStatement("INSERT INTO ${sqlIdentifier(name)} ($columns) VALUES ($placeholders)").use { statement ->
                        while (cursor.moveToNext()) {
                            statement.clearBindings()
                            for (column in 0 until cursor.columnCount) when (cursor.getType(column)) {
                                Cursor.FIELD_TYPE_NULL -> statement.bindNull(column + 1)
                                Cursor.FIELD_TYPE_INTEGER -> statement.bindLong(column + 1, cursor.getLong(column))
                                Cursor.FIELD_TYPE_FLOAT -> statement.bindDouble(column + 1, cursor.getDouble(column))
                                Cursor.FIELD_TYPE_BLOB -> statement.bindBlob(column + 1, cursor.getBlob(column))
                                else -> statement.bindString(column + 1, cursor.getString(column))
                            }
                            statement.executeInsert()
                        }
                    }
                }
            }
            // Preserve deleted high IDs as well as existing rows.
            source.query("SELECT name FROM sqlite_master WHERE name = 'sqlite_sequence'").use { exists ->
                if (exists.moveToFirst()) {
                    target.execSQL("DELETE FROM sqlite_sequence")
                    source.query("SELECT name, seq FROM sqlite_sequence").use { cursor ->
                        while (cursor.moveToNext()) target.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf(cursor.getString(0), cursor.getLong(1)))
                    }
                }
            }
            source.query("SELECT sql FROM sqlite_master WHERE type IN ('index', 'trigger', 'view') AND sql IS NOT NULL").use { cursor ->
                while (cursor.moveToNext()) target.execSQL(cursor.getString(0))
            }
            target.version = source.version
            target.setTransactionSuccessful()
        } finally { target.endTransaction() }
    }
}

private fun sqlIdentifier(value: String) = "\"${value.replace("\"", "\"\"")}\""
