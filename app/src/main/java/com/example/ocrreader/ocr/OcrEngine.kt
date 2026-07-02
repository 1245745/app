package com.example.ocrreader.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OcrResult(
    val text: String,
    val error: String? = null
)

interface LanguageDataDownloadListener {
    fun onProgress(progress: Int)
    fun onSuccess()
    fun onError(message: String)
}

class OcrEngine(private val context: Context) {

    private val DATA_PATH = "${context.filesDir}/tesseract/"
    private val LANG_PATH = DATA_PATH + "tessdata/"
    private val LANG_FILE = "chi_sim.traineddata"
    private val LANG_FILE_URLS = arrayOf(
        "https://cdn.jsdelivr.net/gh/tesseract-ocr/tessdata_fast@main/chi_sim.traineddata",
        "https://gcore.jsdelivr.net/gh/tesseract-ocr/tessdata_fast@main/chi_sim.traineddata",
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/chi_sim.traineddata"
    )
    private val MIN_FILE_SIZE = 5 * 1024 * 1024

    fun checkLanguageData(): Boolean {
        val langFile = File(LANG_PATH, LANG_FILE)
        return langFile.exists() && langFile.length() >= MIN_FILE_SIZE
    }

    fun downloadLanguageData(listener: LanguageDataDownloadListener) {
        Thread {
            for ((index, urlString) in LANG_FILE_URLS.withIndex()) {
                try {
                    val langFile = File(LANG_PATH, LANG_FILE)
                    langFile.parentFile?.mkdirs()

                    Log.d("OCR", "Trying download from mirror ${index + 1}: $urlString")

                    val url = URL(urlString)
                    val connection = openConnection(url)
                    val responseCode = connection.responseCode

                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Log.e("OCR", "HTTP response code: $responseCode")
                        connection.disconnect()
                        continue
                    }

                    val totalSize = connection.contentLength.toLong()
                    Log.d("OCR", "Downloading language data, size: $totalSize bytes")

                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(langFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedSize: Long = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        val progress = if (totalSize > 0) {
                            ((downloadedSize * 100) / totalSize).toInt()
                        } else {
                            ((downloadedSize / 1024).toInt() % 100)
                        }
                        listener.onProgress(progress)
                    }

                    inputStream.close()
                    outputStream.close()
                    connection.disconnect()

                    Log.d("OCR", "Download completed, actual size: ${langFile.length()} bytes")

                    if (langFile.length() >= MIN_FILE_SIZE) {
                        listener.onSuccess()
                        return@Thread
                    } else {
                        langFile.delete()
                        Log.e("OCR", "Downloaded file too small: ${langFile.length()} bytes")
                    }
                } catch (e: Exception) {
                    Log.e("OCR", "Download failed from mirror ${index + 1}: ${e.message}", e)
                }
            }

            listener.onError("所有下载源都失败了，请检查网络")
        }.start()
    }

    private fun openConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        connection.setRequestProperty("Accept", "*/*")
        return connection
    }

    fun recognizeImage(imageFile: File): OcrResult {
        if (!checkLanguageData()) {
            return OcrResult("", "语言包未准备好，请先下载")
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
            return OcrResult("", "语言包未准备好，请先下载")
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
