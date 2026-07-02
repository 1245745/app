package com.example.ocrreader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class OcrResult(
    val text: String,
    val error: String? = null
)

class OcrEngine(private val context: Context) {

    private val DATA_PATH = "${context.filesDir}/tesseract/"
    private val LANG_PATH = DATA_PATH + "tessdata/"
    private val LANG_FILE = "chi_sim.traineddata"
    private val MIN_FILE_SIZE = 5 * 1024 * 1024

    fun checkLanguageData(): Boolean {
        val langFile = File(LANG_PATH, LANG_FILE)
        return langFile.exists() && langFile.length() >= MIN_FILE_SIZE
    }

    fun prepareLanguageData(): Boolean {
        val langFile = File(LANG_PATH, LANG_FILE)

        if (langFile.exists() && langFile.length() >= MIN_FILE_SIZE) {
            Log.d("OCR", "Language data already exists")
            return true
        }

        try {
            langFile.parentFile?.mkdirs()

            val inputStream: InputStream = context.assets.open("tessdata/$LANG_FILE")
            val outputStream = FileOutputStream(langFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            inputStream.close()
            outputStream.close()

            Log.d("OCR", "Language data copied from assets, size: ${langFile.length()} bytes")

            if (langFile.length() >= MIN_FILE_SIZE) {
                return true
            } else {
                Log.e("OCR", "Language data too small: ${langFile.length()} bytes")
                langFile.delete()
                return false
            }
        } catch (e: Exception) {
            Log.e("OCR", "Failed to copy language data: ${e.message}", e)
            return false
        }
    }

    fun recognizeImage(imageFile: File): OcrResult {
        if (!checkLanguageData()) {
            return OcrResult("", "语言包未准备好")
        }

        return try {
            Log.d("OCR", "Recognizing image: ${imageFile.absolutePath}")
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                Log.e("OCR", "Failed to decode image file")
                return OcrResult("", "无法解码图片文件")
            }
            Log.d("OCR", "Image decoded: ${bitmap.width}x${bitmap.height}")
            recognizeBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("OCR", "Error recognizing image: ${e.message}", e)
            OcrResult("", "识别图片时出错: ${e.message}")
        }
    }

    fun recognizeBitmap(bitmap: Bitmap): OcrResult {
        if (!checkLanguageData()) {
            return OcrResult("", "语言包未准备好")
        }

        val tess = TessBaseAPI()
        return try {
            Log.d("OCR", "Initializing Tesseract with data path: $DATA_PATH")
            val initSuccess = tess.init(DATA_PATH, "chi_sim")
            if (!initSuccess) {
                Log.e("OCR", "Tesseract initialization failed")
                return OcrResult("", "OCR引擎初始化失败")
            }
            Log.d("OCR", "Tesseract initialized successfully")

            tess.setImage(bitmap)
            val result = tess.getUTF8Text()
            Log.d("OCR", "Raw OCR result length: ${result?.length ?: 0}")

            val filtered = filterChineseOnly(result ?: "")
            Log.d("OCR", "Filtered Chinese result length: ${filtered.length}")

            if (filtered.isEmpty() && !result.isNullOrEmpty()) {
                OcrResult("", "识别结果中未包含中文字符")
            } else {
                OcrResult(filtered, null)
            }
        } catch (e: Exception) {
            Log.e("OCR", "Error during OCR: ${e.message}", e)
            OcrResult("", "OCR识别出错: ${e.message}")
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
