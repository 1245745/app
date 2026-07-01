package com.example.ocrreader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class OcrEngine(private val context: Context) {

    private val DATA_PATH = "${context.filesDir}/tesseract/"
    private val LANG_PATH = DATA_PATH + "tessdata/"
    private val LANG_FILE = "chi_sim.traineddata"

    init {
        prepareLanguageData()
    }

    private fun prepareLanguageData() {
        val langFile = File(LANG_PATH, LANG_FILE)
        if (!langFile.exists()) {
            try {
                langFile.parentFile?.mkdirs()
                val inputStream: InputStream = context.assets.open("tessdata/$LANG_FILE")
                val outputStream = FileOutputStream(langFile)
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
                inputStream.close()
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recognizeImage(imageFile: File): String {
        return try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            recognizeBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun recognizeBitmap(bitmap: Bitmap): String {
        val tess = TessBaseAPI()
        return try {
            val initSuccess = tess.init(DATA_PATH, "chi_sim")
            if (!initSuccess) {
                return ""
            }
            tess.setImage(bitmap)
            val result = tess.getUTF8Text()
            filterChineseOnly(result)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            tess.recycle()
        }
    }

    fun filterChineseOnly(text: String): String {
        val chineseRegex = "[\\u4e00-\\u9fa5]+".toRegex()
        val matches = chineseRegex.findAll(text)
        return matches.joinToString("") { it.value }
    }

    fun release() {}
}
