package com.example.ocrreader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
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
                Log.d("OCR", "Language data copied successfully")
            } catch (e: Exception) {
                Log.e("OCR", "Failed to copy language data: ${e.message}", e)
            }
        } else {
            Log.d("OCR", "Language data already exists")
        }
    }

    fun recognizeImage(imageFile: File): String {
        return try {
            Log.d("OCR", "Recognizing image: ${imageFile.absolutePath}")
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                Log.e("OCR", "Failed to decode image file")
                return ""
            }
            Log.d("OCR", "Image decoded: ${bitmap.width}x${bitmap.height}")
            recognizeBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("OCR", "Error recognizing image: ${e.message}", e)
            ""
        }
    }

    fun recognizeBitmap(bitmap: Bitmap): String {
        val tess = TessBaseAPI()
        return try {
            Log.d("OCR", "Initializing Tesseract with data path: $DATA_PATH")
            val initSuccess = tess.init(DATA_PATH, "chi_sim")
            if (!initSuccess) {
                Log.e("OCR", "Tesseract initialization failed")
                return ""
            }
            Log.d("OCR", "Tesseract initialized successfully")

            tess.setImage(bitmap)
            val result = tess.getUTF8Text()
            Log.d("OCR", "Raw OCR result length: ${result?.length ?: 0}")

            val filtered = filterChineseOnly(result ?: "")
            Log.d("OCR", "Filtered Chinese result length: ${filtered.length}")

            filtered
        } catch (e: Exception) {
            Log.e("OCR", "Error during OCR: ${e.message}", e)
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
