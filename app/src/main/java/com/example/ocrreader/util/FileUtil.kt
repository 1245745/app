package com.example.ocrreader.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtil {

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown"
    }

    fun getFileExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot == -1) "" else fileName.substring(lastDot + 1).lowercase()
    }

    fun isImageFile(fileName: String): Boolean {
        val ext = getFileExtension(fileName)
        return ext in listOf("jpg", "jpeg", "png", "bmp", "gif")
    }

    fun isPdfFile(fileName: String): Boolean {
        return getFileExtension(fileName) == "pdf"
    }

    fun copyUriToFile(context: Context, uri: Uri, destination: File): Boolean {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getTempFile(context: Context, fileName: String): File {
        val ext = getFileExtension(fileName)
        val tempFileName = "temp_${System.currentTimeMillis()}.$ext"
        return File(context.cacheDir, tempFileName)
    }

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "ocr_temp").apply {
            if (!exists()) mkdirs()
        }
    }
}
