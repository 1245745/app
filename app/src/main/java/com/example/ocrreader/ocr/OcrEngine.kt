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

    private var isInitialized = false
    private var initError: String? = null

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
                val buffer = ByteArray(8192)
                var length: Int
                var totalBytes = 0
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                    totalBytes += length
                }
                inputStream.close()
                outputStream.close()
                Log.d("OCR", "Language data copied successfully, size: $totalBytes bytes")
                if (totalBytes < 1000000) {
                    initError = "语言包文件太小，可能是无效文件"
                    Log.e("OCR", "Language data file too small: $totalBytes bytes")
                }
            } catch (e: Exception) {
                initError = "复制语言包失败: ${e.message}"
                Log.e("OCR", "Failed to copy language data: ${e.message}", e)
            }
        } else {
            val fileSize = langFile.length()
            Log.d("OCR", "Language data already exists, size: $fileSize bytes")
            if (fileSize < 1000000) {
                initError = "语言包文件太小，可能是无效文件"
                Log.e("OCR", "Language data file too small: $fileSize bytes")
            }
        }
    }

    fun recognizeImage(imageFile: File): OcrResult {
        if (initError != null) {
            return OcrResult("", initError)
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
        if (initError != null) {
            return OcrResult("", initError)
        }

        val tess = TessBaseAPI()
        return try {
            Log.d("OCR", "Initializing Tesseract with data path: $DATA_PATH")
            val initSuccess = tess.init(DATA_PATH, "chi_sim")
            if (!initSuccess) {
                Log.e("OCR", "Tesseract initialization failed")
                return OcrResult("", "OCR引擎初始化失败，请检查语言包")
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
