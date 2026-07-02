package com.example.ocrreader.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isChineseConfirmed = false
    private var speechRate = 1.0f
    private var onTtsReady: (() -> Unit)? = null
    private var onSpeechCompleted: (() -> Unit)? = null
    private var onCharacterProgress: ((Int) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val chineseLocales = listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.CHINA,
                Locale.CHINESE,
                Locale("zh", "CN"),
                Locale("zh", "HK"),
                Locale("zh", "TW")
            )

            for (locale in chineseLocales) {
                val result = tts?.setLanguage(locale)
                Log.d("TTS", "Trying locale: $locale, result: $result")

                if (result != TextToSpeech.LANG_MISSING_DATA && 
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isChineseConfirmed = true
                    Log.d("TTS", "Chinese TTS confirmed with locale: $locale")
                    break
                }
            }

            if (!isChineseConfirmed) {
                Log.w("TTS", "Chinese locale not explicitly supported, but will try anyway")
                tts?.setLanguage(Locale.getDefault())
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
        } else {
            Log.e("TTS", "Initialization failed with status: $status")
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

    fun isChineseConfirmed(): Boolean {
        return isChineseConfirmed
    }

    fun speak(text: String): Boolean {
        if (!isInitialized || tts == null) {
            Log.e("TTS", "TTS not initialized")
            return false
        }

        if (text.isEmpty()) {
            Log.e("TTS", "Empty text")
            return false
        }

        tts?.stop()

        val utteranceId = "utterance_${System.currentTimeMillis()}"

        val bundle = Bundle()
        bundle.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, utteranceId)
        Log.d("TTS", "Speak result: $result, text length: ${text.length}")

        if (result == TextToSpeech.SUCCESS) {
            return true
        } else {
            Log.e("TTS", "Speak failed, trying fallback API")
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            val fallbackResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            Log.d("TTS", "Fallback speak result: $fallbackResult")
            return fallbackResult == TextToSpeech.SUCCESS
        }
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
