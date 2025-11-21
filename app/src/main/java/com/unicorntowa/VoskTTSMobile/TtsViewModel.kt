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

    var currentModelType by mutableStateOf(ModelType.VOSK_TTS)
        private set

    var isModelLoading by mutableStateOf(false)
        private set

    var loadingMessage by mutableStateOf("Initializing...")
        private set

    private var voskTTS: VoskTTS? = null

    private val audioPlayer = AudioPlayer(SAMPLE_RATE) {
        viewModelScope.launch(Dispatchers.Main) {
            isPlaying = false
        }
    }

    init {
        initializeAll()
    }

    private fun initializeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isModelLoading = true
                isInitialized = false
                initializationError = null
                loadingMessage = "Loading dictionary..."
            }

            try {
                // Загружаем словарь один раз
                if (!DictionaryManager.isInitialized()) {
                    DictionaryManager.initialize(getApplication())
                }

                withContext(Dispatchers.Main) {
                    loadingMessage = "Loading ${currentModelType.displayName}..."
                }

                // Загружаем модель
                voskTTS?.close()
                voskTTS = VoskTTS(getApplication(), currentModelType)
                voskTTS!!.initialize()

                withContext(Dispatchers.Main) {
                    isInitialized = true
                    isModelLoading = false
                    loadingMessage = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                withContext(Dispatchers.Main) {
                    initializationError = e.message
                    isModelLoading = false
                    loadingMessage = ""
                }
            }
        }
    }

    private fun switchModelOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isModelLoading = true
                isInitialized = false
                loadingMessage = "Loading ${currentModelType.displayName}..."
            }

            try {
                voskTTS?.close()
                voskTTS = VoskTTS(getApplication(), currentModelType)
                voskTTS!!.initialize()

                withContext(Dispatchers.Main) {
                    isInitialized = true
                    isModelLoading = false
                    loadingMessage = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model switch failed", e)
                withContext(Dispatchers.Main) {
                    initializationError = e.message
                    isModelLoading = false
                    loadingMessage = ""
                }
            }
        }
    }

    fun switchModelNext() {
        val allModels = ModelType.values()
        val currentIndex = allModels.indexOf(currentModelType)
        currentModelType = allModels[(currentIndex + 1) % allModels.size]
        reloadModel()
    }

    fun switchModelPrevious() {
        val allModels = ModelType.values()
        val currentIndex = allModels.indexOf(currentModelType)
        currentModelType = allModels[(currentIndex - 1 + allModels.size) % allModels.size]
        reloadModel()
    }
    private fun reloadModel() {
        // Останавливаем воспроизведение если идет
        if (isPlaying) {
            audioPlayer.stop()
            isPlaying = false
        }

        // Сбрасываем состояние синтеза
        hasSynthesizedAudio = false
        synthesisTime = null
        numTokens = null
        tokensPerSecond = null
        audioDurationMs = null
        rtf = null

        // Переинициализируем только модель (словарь уже загружен)
        switchModelOnly()
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
