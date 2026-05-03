package com.example.facerecognition.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.facerecognition.data.entity.AttendanceLog
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportUtils {
    fun createAndShareCsv(context: Context, logs: List<AttendanceLog>) {
        val fileName = "attendance_report_${System.currentTimeMillis()}.csv"
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
        val file = File(reportsDir, fileName)

        try {
            val writer = FileWriter(file)
            writer.append("Date,Faculty ID,Name,Status,Dept\n")
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            logs.forEach { log ->
                val dateStr = sdf.format(Date(log.timestamp))
                val dept = log.departmentId.ifEmpty { "N/A" }
                // Basic CSV escaping for names with commas
                val name = if (log.staffName.contains(",")) "\"${log.staffName}\"" else log.staffName
                writer.append("$dateStr,${log.staffId},$name,${log.status},$dept\n")
            }
            writer.flush()
            writer.close()

            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FaceAttend Export Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share Report via")
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
