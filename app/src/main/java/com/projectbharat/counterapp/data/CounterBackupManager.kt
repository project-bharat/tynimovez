package com.projectbharat.counterapp.data

import android.content.Context
import android.net.Uri

object CounterBackupManager {
    suspend fun exportCsv(context: Context, uri: Uri) {
        val dao = CounterDatabase.get(context).dao()
        val categories = dao.allCategories().associateBy { it.id }
        val logs = dao.allLogs().sortedBy { it.timestamp }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Unable to create CSV file")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write('\uFEFF'.code)
            writer.write("date,timestamp,category_id,category_marathi,category_english,count,strength,note,session_id")
            writer.newLine()
            logs.forEach { log ->
                val category = categories[log.categoryId]
                val values = listOf(log.date, log.timestamp.toString(), log.categoryId.toString(),
                    category?.titleMr.orEmpty(), category?.titleEn.orEmpty(), log.count.toString(),
                    log.strength.orEmpty(), log.note.orEmpty(), log.sessionId?.toString().orEmpty())
                writer.write(values.joinToString(",") { csv(it) })
                writer.newLine()
            }
        }
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
