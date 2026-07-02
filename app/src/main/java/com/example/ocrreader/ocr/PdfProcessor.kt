package com.example.ocrreader.ocr

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PdfProcessor(private val ocrEngine: OcrEngine) {

    fun processPdf(pdfFile: File): OcrResult {
        return try {
            Log.d("PDF", "Processing PDF: ${pdfFile.absolutePath}")
            val document = Loader.loadPDF(pdfFile)
            val rawText = extractTextFromPdf(document)
            document.close()
            Log.d("PDF", "Raw text from PDF: ${rawText.length} characters")

            if (rawText.isNotEmpty()) {
                val filteredText = ocrEngine.filterChineseOnly(rawText)
                if (filteredText.isEmpty()) {
                    Log.d("PDF", "No Chinese characters found in text PDF")
                    OcrResult("", "PDF文本中未包含中文字符")
                } else {
                    Log.d("PDF", "Filtered Chinese text: ${filteredText.length} characters")
                    OcrResult(filteredText, null)
                }
            } else {
                Log.d("PDF", "PDF appears to be scanned, performing OCR")
                extractTextFromScannedPdf(pdfFile)
            }
        } catch (e: Exception) {
            Log.e("PDF", "Error processing PDF: ${e.message}", e)
            try {
                extractTextFromScannedPdf(pdfFile)
            } catch (ocrEx: Exception) {
                Log.e("PDF", "OCR fallback also failed: ${ocrEx.message}", ocrEx)
                OcrResult("", "处理PDF失败: ${e.message}")
            }
        }
    }

    private fun extractTextFromPdf(document: PDDocument): String {
        return try {
            val stripper = PDFTextStripper()
            stripper.getText(document)
        } catch (e: Exception) {
            Log.e("PDF", "Error extracting text from PDF: ${e.message}", e)
            ""
        }
    }

    private fun extractTextFromScannedPdf(pdfFile: File): OcrResult {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            Log.d("PDF", "Scanned PDF has $pageCount pages")

            val textBuilder = StringBuilder()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pageResult = ocrEngine.recognizeBitmap(bitmap)
                if (pageResult.error != null) {
                    renderer.close()
                    pfd.close()
                    return pageResult
                }
                textBuilder.append(pageResult.text)

                page.close()
                bitmap.recycle()
                Log.d("PDF", "Page $i OCR result: ${pageResult.text.length} characters")
            }

            renderer.close()
            pfd.close()

            val result = textBuilder.toString()
            if (result.isEmpty()) {
                OcrResult("", "扫描PDF中未识别到中文内容")
            } else {
                OcrResult(result, null)
            }
        } catch (e: Exception) {
            Log.e("PDF", "Error extracting text from scanned PDF: ${e.message}", e)
            OcrResult("", "处理扫描PDF失败: ${e.message}")
        }
    }
}
