package com.example.ocrreader.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speechRate = 1.0f
    private var engineName: String = ""
    private var initStatus: Int = -2
    private var onTtsReady: (() -> Unit)? = null
    private var onSpeechCompleted: (() -> Unit)? = null
    private var onCharacterProgress: ((Int) -> Unit)? = null

    init {
        tryInitTts()
    }

    private fun tryInitTts() {
        Log.d("TTS", "Starting TTS initialization")
        tts = TextToSpeech(context, this)
    }

    fun retryInit() {
        if (tts != null) {
            tts?.shutdown()
            tts = null
        }
        isInitialized = false
        initStatus = -2
        tryInitTts()
    }

    override fun onInit(status: Int) {
        initStatus = status
        Log.d("TTS", "onInit called with status: $status")

        if (status == TextToSpeech.SUCCESS) {
            engineName = tts?.defaultEngine ?: "Unknown"
            Log.d("TTS", "Engine name: $engineName")

            val chineseLocales = listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.CHINA,
                Locale.CHINESE,
                Locale("zh", "CN")
            )

            var localeSet = false
            for (locale in chineseLocales) {
                val result = tts?.setLanguage(locale)
                Log.d("TTS", "Trying locale: $locale, result: $result")

                if (result != TextToSpeech.LANG_MISSING_DATA && 
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    localeSet = true
                    Log.d("TTS", "Locale set successfully: $locale")
                    break
                }
            }

            if (!localeSet) {
                tts?.setLanguage(Locale.getDefault())
                Log.w("TTS", "Using default locale")
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onSpeechCompleted?.invoke()
                    onCharacterProgress?.invoke(-1)
                }
                override fun onError(utteranceId: String?) {
                    onSpeechCompleted?.invoke()
                    onCharacterProgress?.invoke(-1)
                }
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    onCharacterProgress?.invoke(end)
                }
            })

            onTtsReady?.invoke()

            testSpeak()
        } else {
            Log.e("TTS", "Initialization failed with status: $status")
        }
    }

    private fun testSpeak() {
        val testResult = tts?.speak("语音引擎已就绪", TextToSpeech.QUEUE_FLUSH, null)
        Log.d("TTS", "Test speak result: $testResult")
    }

    fun setOnTtsReadyListener(listener: () -> Unit) {
        if (isInitialized) {
            listener.invoke()
        } else {
            onTtsReady = listener
        }
    }

    fun setOnSpeechCompletedListener(listener: () -> Unit) {
        onSpeechCompleted = listener
    }

    fun setOnCharacterProgressListener(listener: (Int) -> Unit) {
        onCharacterProgress = listener
    }

    fun getEngineName(): String {
        return engineName
    }

    fun isInitialized(): Boolean {
        return isInitialized
    }

    fun getInitStatus(): Int {
        return initStatus
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) {
            Log.e("TTS", "TTS not initialized")
            return
        }

        if (text.isEmpty()) {
            Log.e("TTS", "Empty text")
            return
        }

        tts?.stop()

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        Log.d("TTS", "Speak result: $result, text length: ${text.length}")
    }

    fun pause() {
        tts?.stop()
    }

    fun stop() {
        tts?.stop()
        onCharacterProgress?.invoke(-1)
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun getSpeechRate(): Float {
        return speechRate
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
