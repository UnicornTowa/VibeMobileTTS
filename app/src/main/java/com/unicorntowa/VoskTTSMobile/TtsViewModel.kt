package com.unicorntowa.VoskTTSMobile

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "TtsViewModel"
        private const val SAMPLE_RATE = 22050
    }

    var inputText by mutableStateOf("")
        private set

    var synthesisTime by mutableStateOf<Long?>(null)
        private set

    var numTokens by mutableStateOf<Int?>(null)
        private set

    var tokensPerSecond by mutableStateOf<Float?>(null)
        private set

    var audioDurationMs by mutableStateOf<Long?>(null)
        private set

    var rtf by mutableStateOf<Float?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isInitialized by mutableStateOf(false)
        private set

    var initializationError by mutableStateOf<String?>(null)
        private set

    var hasSynthesizedAudio by mutableStateOf(false)
        private set

    private var voskTTS: VoskTTS? = null

    private val audioPlayer = AudioPlayer(SAMPLE_RATE) {
        viewModelScope.launch(Dispatchers.Main) {
            isPlaying = false
        }
    }

    init {
        initializeModel()
    }

    private fun initializeModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                voskTTS = VoskTTS(getApplication())
                voskTTS!!.initialize()

                withContext(Dispatchers.Main) {
                    isInitialized = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model init failed", e)
                withContext(Dispatchers.Main) {
                    initializationError = e.message
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        inputText = text
    }

    fun synthesizeText() {
        if (!isInitialized || inputText.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                synthesisTime = null
                numTokens = null
                tokensPerSecond = null
                audioDurationMs = null
                rtf = null
                hasSynthesizedAudio = false
            }

            try {
                val start = System.currentTimeMillis()
                val (pcm, num_tokens) = voskTTS!!.synthesize(inputText)
                val end = System.currentTimeMillis()

                if (pcm.isEmpty()) {
                    Log.e(TAG, "TTS returned empty audio")
                    return@launch
                }

                audioPlayer.loadPcm(pcm)

                val audioDurationMsValue = (pcm.size * 1000L) / SAMPLE_RATE

                val synthesisTimeSec = (end - start) / 1000f

                val audioDurationSec = audioDurationMsValue / 1000f

                val rtfValue = if (audioDurationSec > 0f) synthesisTimeSec / audioDurationSec else 0f

                val tps = if (synthesisTimeSec > 0f) num_tokens / synthesisTimeSec else 0f

                withContext(Dispatchers.Main) {
                    numTokens = num_tokens
                    synthesisTime = end - start
                    tokensPerSecond = tps
                    audioDurationMs = audioDurationMsValue
                    rtf = rtfValue
                    hasSynthesizedAudio = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Synthesis failed", e)
            }
        }
    }

    fun onPlayPauseClicked() {
        if (!hasSynthesizedAudio) return

        if (isPlaying) {
            audioPlayer.stop()
            isPlaying = false
        } else {
            audioPlayer.play()
            isPlaying = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
        voskTTS?.close()
    }
}
