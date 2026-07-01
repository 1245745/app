package com.example.ocrreader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speechRate = 1.0f
    private var onTtsReady: (() -> Unit)? = null
    private var onSpeechCompleted: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINA)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onSpeechCompleted?.invoke()
                }
                override fun onError(utteranceId: String?) {}
            })
            onTtsReady?.invoke()
        }
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

    fun speak(text: String) {
        if (!isInitialized || tts == null) return

        if (text.isEmpty()) return

        tts?.stop()

        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "utterance_${System.currentTimeMillis()}"

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
    }

    fun pause() {
        tts?.stop()
    }

    fun stop() {
        tts?.stop()
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
