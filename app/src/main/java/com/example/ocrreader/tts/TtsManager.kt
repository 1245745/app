package com.example.ocrreader.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speechRate = 1.0f
    private var onTtsReady: (() -> Unit)? = null
    private var onSpeechCompleted: (() -> Unit)? = null
    private var onCharacterProgress: ((Int) -> Unit)? = null

    private var currentText: String = ""
    private var currentIndex: Int = 0
    private val handler = Handler(Looper.getMainLooper())

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
                    handler.post {
                        currentIndex++
                        onCharacterProgress?.invoke(currentIndex)
                        if (currentIndex < currentText.length) {
                            speakNextCharacter()
                        } else {
                            onSpeechCompleted?.invoke()
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    handler.post {
                        currentIndex++
                        if (currentIndex < currentText.length) {
                            speakNextCharacter()
                        } else {
                            onSpeechCompleted?.invoke()
                        }
                    }
                }
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

    fun setOnCharacterProgressListener(listener: (Int) -> Unit) {
        onCharacterProgress = listener
    }

    fun speak(text: String) {
        if (!isInitialized || tts == null) return
        if (text.isEmpty()) return

        tts?.stop()
        handler.removeCallbacksAndMessages(null)

        currentText = text
        currentIndex = 0

        onCharacterProgress?.invoke(0)
        speakNextCharacter()
    }

    private fun speakNextCharacter() {
        if (currentIndex >= currentText.length) {
            onSpeechCompleted?.invoke()
            return
        }

        val charToSpeak = currentText[currentIndex].toString()
        val params = HashMap<String, String>()
        params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "char_${currentIndex}"

        tts?.speak(charToSpeak, TextToSpeech.QUEUE_FLUSH, params)
    }

    fun pause() {
        tts?.stop()
        handler.removeCallbacksAndMessages(null)
    }

    fun stop() {
        tts?.stop()
        handler.removeCallbacksAndMessages(null)
        currentText = ""
        currentIndex = 0
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

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun getCurrentText(): String {
        return currentText
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
