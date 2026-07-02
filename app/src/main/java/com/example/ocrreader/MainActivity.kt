package com.example.ocrreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.ocrreader.ocr.LanguageDataDownloadListener
import com.example.ocrreader.ocr.OcrEngine
import com.example.ocrreader.ocr.OcrResult
import com.example.ocrreader.ocr.PdfProcessor
import com.example.ocrreader.tts.TtsManager
import com.example.ocrreader.util.FileUtil
import com.example.ocrreader.util.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectFile: Button
    private lateinit var tvResult: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var sbSpeed: SeekBar

    private lateinit var ocrEngine: OcrEngine
    private lateinit var pdfProcessor: PdfProcessor
    private lateinit var ttsManager: TtsManager

    private var recognizedText: String = ""
    private var progressDialog: AlertDialog? = null
    private var downloadDialog: AlertDialog? = null

    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUEST_CODE_FILE_SELECT = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initOcrEngine()
        initTtsManager()
        checkPermissions()
        checkLanguageData()
    }

    private fun initViews() {
        btnSelectFile = findViewById(R.id.btnSelectFile)
        tvResult = findViewById(R.id.tvResult)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        sbSpeed = findViewById(R.id.sbSpeed)

        btnSelectFile.setOnClickListener { selectFile() }
        btnPlay.setOnClickListener { playText() }
        btnPause.setOnClickListener { pauseText() }
        btnStop.setOnClickListener { stopText() }

        sbSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress.toFloat() / 10f
                ttsManager.setSpeechRate(speed)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initOcrEngine() {
        ocrEngine = OcrEngine(this)
        pdfProcessor = PdfProcessor(ocrEngine)
    }

    private fun initTtsManager() {
        ttsManager = TtsManager(this)
        ttsManager.setOnTtsReadyListener {
            Log.d("TTS", "TTS initialized")
        }
    }

    private fun checkPermissions() {
        if (PermissionManager.shouldRequestPermissions() && !PermissionManager.hasAllPermissions(this)) {
            requestPermissions(PermissionManager.getRequiredPermissions(), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun checkLanguageData() {
        if (!ocrEngine.checkLanguageData()) {
            showDownloadDialog()
        }
    }

    private fun showDownloadDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_download, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val tvProgress = dialogView.findViewById<TextView>(R.id.tvProgress)

        downloadDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        downloadDialog?.show()

        ocrEngine.downloadLanguageData(object : LanguageDataDownloadListener {
            override fun onProgress(progress: Int) {
                runOnUiThread {
                    progressBar.progress = progress
                    tvProgress.text = "正在下载语言包... $progress%"
                }
            }

            override fun onSuccess() {
                runOnUiThread {
                    downloadDialog?.dismiss()
                    Toast.makeText(this@MainActivity, "语言包下载完成", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(message: String) {
                runOnUiThread {
                    downloadDialog?.dismiss()
                    showRetryDialog(message)
                }
            }
        })
    }

    private fun showRetryDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("下载失败")
            .setMessage("$errorMessage\n\n是否重试？")
            .setPositiveButton("重试") { _, _ ->
                showDownloadDialog()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                tvResult.text = "语言包下载失败，请检查网络后重启应用"
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val allGranted = PermissionManager.hasAllPermissions(this)
            if (allGranted) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permission_warning, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectFile() {
        if (!ocrEngine.checkLanguageData()) {
            Toast.makeText(this, "语言包未准备好，请先下载", Toast.LENGTH_SHORT).show()
            showDownloadDialog()
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf"))
        }
        startActivityForResult(intent, REQUEST_CODE_FILE_SELECT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FILE_SELECT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                processFile(uri)
            } ?: run {
                Toast.makeText(this, R.string.file_select_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processFile(uri: Uri) {
        val fileName = FileUtil.getFileName(this, uri)
        if (!FileUtil.isImageFile(fileName) && !FileUtil.isPdfFile(fileName)) {
            Toast.makeText(this, R.string.file_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog()

        CoroutineScope(Dispatchers.IO).launch {
            val tempFile = FileUtil.getTempFile(this@MainActivity, fileName)
            val copySuccess = FileUtil.copyUriToFile(this@MainActivity, uri, tempFile)

            if (!copySuccess) {
                withContext(Dispatchers.Main) {
                    hideProgressDialog()
                    Toast.makeText(this@MainActivity, R.string.file_read_error, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val result = try {
                if (FileUtil.isPdfFile(fileName)) {
                    pdfProcessor.processPdf(tempFile)
                } else {
                    ocrEngine.recognizeImage(tempFile)
                }
            } catch (e: Exception) {
                Log.e("OCR", "Process file error: ${e.message}", e)
                OcrResult("", "处理文件时出错: ${e.message}")
            } finally {
                tempFile.delete()
            }

            withContext(Dispatchers.Main) {
                hideProgressDialog()
                updateResult(result)
            }
        }
    }

    private fun showProgressDialog() {
        progressDialog = AlertDialog.Builder(this)
            .setMessage(R.string.recognizing)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun updateResult(result: OcrResult) {
        recognizedText = result.text
        if (result.error != null) {
            tvResult.text = result.error
            Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
        } else if (result.text.isEmpty()) {
            tvResult.text = getString(R.string.no_chinese_content)
        } else {
            tvResult.text = result.text
        }
    }

    private fun playText() {
        if (recognizedText.isEmpty()) {
            Toast.makeText(this, R.string.no_chinese_content, Toast.LENGTH_SHORT).show()
            return
        }
        ttsManager.speak(recognizedText)
    }

    private fun pauseText() {
        ttsManager.pause()
    }

    private fun stopText() {
        ttsManager.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        ocrEngine.release()
        ttsManager.release()
    }
}
