package com.example.ocrreader.ocr

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

class PdfProcessor(private val ocrEngine: OcrEngine) {

    fun processPdf(pdfFile: File): String {
        return try {
            val document = Loader.loadPDF(pdfFile)
            val hasText = extractTextFromPdf(document).isNotEmpty()
            document.close()

            if (hasText) {
                extractTextFromPdfFile(pdfFile)
            } else {
                extractTextFromScannedPdf(pdfFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            extractTextFromScannedPdf(pdfFile)
        }
    }

    private fun extractTextFromPdf(document: PDDocument): String {
        return try {
            val stripper = PDFTextStripper()
            stripper.getText(document)
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTextFromPdfFile(pdfFile: File): String {
        return try {
            val document = Loader.loadPDF(pdfFile)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            ocrEngine.filterChineseOnly(text)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun extractTextFromScannedPdf(pdfFile: File): String {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val textBuilder = StringBuilder()

            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pageText = ocrEngine.recognizeBitmap(bitmap)
                textBuilder.append(pageText)
                page.close()
                bitmap.recycle()
            }

            renderer.close()
            pfd.close()
            textBuilder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
