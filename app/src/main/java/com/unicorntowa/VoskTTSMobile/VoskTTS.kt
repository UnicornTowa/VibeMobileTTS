package com.unicorntowa.VoskTTSMobile

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import java.io.File

class VoskTTS(private val context: Context) {
    companion object {
        private const val TAG = "VoskTTS"
    }

    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var ortSession: OrtSession
    private val dictionary = mutableMapOf<String, String>()
    private val softLetters = setOf("я", "ё", "ю", "и", "ь", "е")
    private val startSyl = setOf("#", "ъ", "ь", "а", "я", "о", "ё", "у", "ю", "э", "е", "и", "ы", "-")
    private val others = setOf("#", "+", "-", "ь", "ъ")

    private val phonemeIdMap = mapOf(
        "_" to 0, "^" to 1, "$" to 2, " " to 3, "!" to 4,
        "'" to 5, "(" to 6, ")" to 7, "," to 8, "-" to 9,
        "." to 10, ":" to 11, ";" to 12, "?" to 13,
        "a0" to 14, "a1" to 15, "b" to 16, "bj" to 17, "c" to 18,
        "ch" to 19, "d" to 20, "dj" to 21, "e0" to 22, "e1" to 23,
        "f" to 24, "fj" to 25, "g" to 26, "gj" to 27, "h" to 28,
        "hj" to 29, "i0" to 30, "i1" to 31, "j" to 32, "k" to 33,
        "kj" to 34, "l" to 35, "lj" to 36, "m" to 37, "mj" to 38,
        "n" to 39, "nj" to 40, "o0" to 41, "o1" to 42, "p" to 43,
        "pj" to 44, "r" to 45, "rj" to 46, "s" to 47, "sch" to 48,
        "sh" to 49, "sj" to 50, "t" to 51, "tj" to 52, "u0" to 53,
        "u1" to 54, "v" to 55, "vj" to 56, "y0" to 57, "y1" to 58,
        "z" to 59, "zh" to 60, "zj" to 61
    )
    private val softHardCons = mapOf(
        "б" to "b", "в" to "v", "г" to "g", "Г" to "g",
        "д" to "d", "з" to "z", "к" to "k", "л" to "l",
        "м" to "m", "н" to "n", "п" to "p", "р" to "r",
        "с" to "s", "т" to "t", "ф" to "f", "х" to "h"
    )

    private val otherCons = mapOf(
        "ж" to "zh", "ц" to "c", "ч" to "ch",
        "ш" to "sh", "щ" to "sch", "й" to "j"
    )

    private val vowels = mapOf(
        "а" to "a", "я" to "a", "у" to "u", "ю" to "u",
        "о" to "o", "ё" to "o", "э" to "e", "е" to "e",
        "и" to "i", "ы" to "y"
    )

    fun initialize() {
        try {
            Log.d(TAG, "Initializing ONNX Runtime...")
            ortEnvironment = OrtEnvironment.getEnvironment()

            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(2)

//            val availableProviders = OrtEnvironment.getAvailableProviders()
//            if (availableProviders.contains(OrtProvider.NNAPI)) {
//                sessionOptions.addNnapi()
//                Log.d(TAG, "Using NNAPI")
//            }

            // Копируем модели во внутреннее хранилище и загружаем оттуда
            Log.d(TAG, "Copying models to internal storage...")
            val modelFile = copyAssetToInternalStorage("models/model.onnx", "model.onnx")

            // Загружаем основную модель
            Log.d(TAG, "Loading main model from: ${modelFile.absolutePath}")
            Log.d(TAG, "Model file size: ${modelFile.length() / 1024 / 1024} MB")
            ortSession = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "Main model loaded successfully")

            // Загружаем словарь и vocab
            loadDictionary()

            Log.d(TAG, "Initialization complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization", e)
            throw e
        }
    }

    private fun copyAssetToInternalStorage(assetPath: String, fileName: String): File {
        val outputFile = File(context.filesDir, fileName)

        // Если файл уже существует и не пустой, не копируем заново
        if (outputFile.exists() && outputFile.length() > 0) {
            Log.d(TAG, "File already exists: ${outputFile.absolutePath} (${outputFile.length() / 1024 / 1024} MB)")
            return outputFile
        }

        Log.d(TAG, "Copying $assetPath to ${outputFile.absolutePath}")

        try {
            context.assets.open(assetPath).use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192) // 8KB buffer
                    var bytesRead: Int
                    var totalBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        // Логируем прогресс каждые 10MB
                        if (totalBytes % (10 * 1024 * 1024) == 0L) {
                            Log.d(TAG, "Copied ${totalBytes / 1024 / 1024} MB...")
                        }
                    }

                    Log.d(TAG, "File copied successfully: ${totalBytes / 1024 / 1024} MB")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset: $assetPath", e)
            // Удаляем недокопированный файл
            outputFile.delete()
            throw e
        }

        return outputFile
    }

    private fun loadDictionary() {
        Log.d(TAG, "Loading dictionary...")
        val probs = mutableMapOf<String, Float>()

        context.assets.open("models/dictionary").bufferedReader().use { reader ->
            reader.lineSequence().forEach { line ->
                val items = line.split(" ", limit = 3)
                if (items.size >= 3) {
                    val word = items[0]
                    val prob = items[1].toFloatOrNull() ?: 0f
                    val phonemes = items[2]

                    if (probs.getOrDefault(word, 0f) < prob) {
                        dictionary[word] = phonemes
                        probs[word] = prob
                    }
                }
            }
        }
        Log.d(TAG, "Dictionary loaded: ${dictionary.size} words")
    }

    fun synthesize(text: String, speakerId: Int = 1): Pair<ShortArray, Int> {
        try {
            Log.d(TAG, "Starting synthesis for: $text")

            // Подготовка данных - получаем только ID фонем
            val phonemeIds = g2pNoembed(text)
            Log.d(TAG, "Phoneme IDs count: ${phonemeIds.size}")
            Log.d(TAG, "Phoneme IDs: $phonemeIds")

            // Конвертируем в нужный формат для ONNX
            val textArray = LongArray(phonemeIds.size) { i ->
                phonemeIds[i].toLong()
            }

            // Создаем 2D тензор [1, sequence_length]
            val shape = longArrayOf(1, phonemeIds.size.toLong())
            val textTensor = OnnxTensor.createTensor(
                ortEnvironment,
                java.nio.LongBuffer.wrap(textArray),
                shape
            )

            val textLengthsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                longArrayOf(phonemeIds.size.toLong())
            )

            val scalesTensor = OnnxTensor.createTensor(
                ortEnvironment,
                floatArrayOf(0.667f, 1.0f, 0.8f)
            )

            val sidTensor = OnnxTensor.createTensor(
                ortEnvironment,
                longArrayOf(speakerId.toLong())
            )

            // Логируем информацию о модели
            Log.d(TAG, "Model input names: ${ortSession.inputNames}")
            Log.d(TAG, "Model output names: ${ortSession.outputNames}")

            // Запускаем инференс
            Log.d(TAG, "Running inference with ${phonemeIds.size} phonemes...")
            val inputs = mapOf(
                "input" to textTensor,
                "input_lengths" to textLengthsTensor,
                "scales" to scalesTensor,
                "sid" to sidTensor
            )

            val output = ortSession.run(inputs)
            Log.d(TAG, "Output count: ${output.size()}")

            if (output.size() == 0) {
                Log.e(TAG, "No output from model!")
                return shortArrayOf() to 0
            }

            val audioTensor = output[0].value
            Log.d(TAG, "Audio tensor type: ${audioTensor?.javaClass}")

            // Конвертируем в FloatArray
            val audioFloat = when (audioTensor) {
                is Array<*> -> {
                    Log.d(TAG, "Array dimensions: ${audioTensor.size}")

                    // Рекурсивно распаковываем вложенные массивы
                    fun flattenArray(arr: Any?): List<Float> {
                        return when (arr) {
                            is FloatArray -> arr.toList()
                            is Array<*> -> arr.flatMap { flattenArray(it) }
                            is Float -> listOf(arr)
                            else -> emptyList()
                        }
                    }

                    val flattened = flattenArray(audioTensor).toFloatArray()

                    Log.d(TAG, "Flattened audio samples: ${flattened.size}")
                    if (flattened.isNotEmpty()) {
                        Log.d(TAG, "Audio range: ${flattened.minOrNull()} to ${flattened.maxOrNull()}")
                    }

                    flattened
                }
                is FloatArray -> {
                    Log.d(TAG, "Direct FloatArray size: ${audioTensor.size}")
                    audioTensor
                }
                else -> {
                    Log.e(TAG, "Unexpected audio tensor type: ${audioTensor?.javaClass}")
                    floatArrayOf()
                }
            }

            Log.d(TAG, "Audio float samples: ${audioFloat.size}")
            if (audioFloat.isNotEmpty()) {
                Log.d(TAG, "Audio range: ${audioFloat.minOrNull()} to ${audioFloat.maxOrNull()}")
            }

            // Закрываем тензоры
            textTensor.close()
            textLengthsTensor.close()
            scalesTensor.close()
            sidTensor.close()
            output.close()

            // Конвертируем в int16
            return audioFloatToInt16(audioFloat) to phonemeIds.size

        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            e.printStackTrace()
            return shortArrayOf() to 0
        }
    }


    private fun audioFloatToInt16(audio: FloatArray): ShortArray {
        val maxWavValue = 32767.0f
        return ShortArray(audio.size) { i ->
            val normalized = (audio[i] * maxWavValue).coerceIn(-maxWavValue, maxWavValue)
            normalized.toInt().toShort()
        }
    }

    fun close() {
        if (::ortSession.isInitialized) ortSession.close()
        if (::ortEnvironment.isInitialized) ortEnvironment.close()
    }

    private fun convert(stressword: String): String {
        val phones = "#$stressword#"

        // Assign stress marks
        val stressPhones = mutableListOf<Pair<String, Int>>()
        var stress = 0
        for (char in phones) {
            when (char) {
                '+' -> stress = 1
                else -> {
                    stressPhones.add(char.toString() to stress)
                    stress = 0
                }
            }
        }

        // Pallatize
        pallatize(stressPhones)

        // Convert vowels
        val convertedPhones = convertVowels(stressPhones)

        // Filter
        return convertedPhones.filterNot { it in others }.joinToString(" ")
    }
    private fun pallatize(phones: MutableList<Pair<String, Int>>) {
        for (i in 0 until phones.size - 1) {
            val phone = phones[i]
            val nextPhone = phones[i + 1]

            if (phone.first in softHardCons) {
                phones[i] = if (nextPhone.first in softLetters) {
                    (softHardCons[phone.first]!! + "j") to 0
                } else {
                    softHardCons[phone.first]!! to 0
                }
            }

            if (phone.first in otherCons) {
                phones[i] = otherCons[phone.first]!! to 0
            }
        }
    }
    private fun convertVowels(phones: List<Pair<String, Int>>): List<String> {
        val newPhones = mutableListOf<String>()
        var prev = ""

        for (phone in phones) {
            if (prev in startSyl) {
                if (phone.first in setOf("я", "ю", "е", "ё")) {
                    newPhones.add("j")
                }
            }

            if (phone.first in vowels) {
                newPhones.add(vowels[phone.first]!! + phone.second.toString())
            } else {
                newPhones.add(phone.first)
            }

            prev = phone.first
        }

        return newPhones
    }
    fun g2pNoembed(text: String): List<Int> {
        val pattern = Regex("([,.?!;:\"() ])")
        val phonemes = mutableListOf<String>()

        phonemes.add("^")

        val parts = text.lowercase().split(pattern)

        for (word in parts) {
            if (word.isEmpty()) continue

            when {
                word.matches(pattern) || word == "-" -> {
                    phonemes.add(word)
                }
                dictionary.containsKey(word) -> {
                    dictionary[word]!!.split(" ")
                        .filter { it.isNotEmpty() }
                        .forEach { phonemes.add(it) }
                }
                else -> {
                    convert(word).split(" ")
                        .filter { it.isNotEmpty() }
                        .forEach { phonemes.add(it) }
                }
            }
        }

        phonemes.add("$")

        val phonemeIds = mutableListOf<Int>()

        val firstId = phonemeIdMap[phonemes[0]] ?: 0
        phonemeIds.add(firstId)

        for (i in 1 until phonemes.size) {
            phonemeIds.add(0) // blank token
            val phonemeId = phonemeIdMap[phonemes[i]] ?: 0
            phonemeIds.add(phonemeId)
        }

        Log.d(TAG, "Text: $text")
        Log.d(TAG, "Phonemes: ${phonemes.joinToString(" ")}")
        Log.d(TAG, "Phoneme IDs: ${phonemeIds.joinToString(", ")}")

        return phonemeIds
    }
}
