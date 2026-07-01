package com.example.ocrreader

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.example.ocrreader.ocr.OcrEngine
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

    private val REQUEST_CODE_PERMISSIONS = 1001
    private val REQUEST_CODE_FILE_SELECT = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initOcrEngine()
        initTtsManager()
        checkPermissions()
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
        if (!PermissionManager.hasAllPermissions(this)) {
            requestPermissions(PermissionManager.getRequiredPermissions(), REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!PermissionManager.hasAllPermissions(this)) {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectFile() {
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
            }
        }
    }

    private fun processFile(uri: android.net.Uri) {
        val fileName = FileUtil.getFileName(this, uri)
        if (!FileUtil.isImageFile(fileName) && !FileUtil.isPdfFile(fileName)) {
            Toast.makeText(this, R.string.file_not_supported, Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog()

        CoroutineScope(Dispatchers.IO).launch {
            val tempFile = FileUtil.getTempFile(this@MainActivity, fileName)
            FileUtil.copyUriToFile(this@MainActivity, uri, tempFile)

            val result = if (FileUtil.isPdfFile(fileName)) {
                pdfProcessor.processPdf(tempFile)
            } else {
                ocrEngine.recognizeImage(tempFile)
            }

            tempFile.delete()

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

    private fun updateResult(text: String) {
        recognizedText = text
        if (text.isEmpty()) {
            tvResult.text = getString(R.string.no_chinese_content)
        } else {
            tvResult.text = text
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
