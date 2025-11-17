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
    private lateinit var bertSession: OrtSession
    private val dictionary = mutableMapOf<String, String>()
    private val vocab = mutableMapOf<String, Int>()
    private val softLetters = setOf("я", "ё", "ю", "и", "ь", "е")
    private val startSyl = setOf("#", "ъ", "ь", "а", "я", "о", "ё", "у", "ю", "э", "е", "и", "ы", "-")
    private val others = setOf("#", "+", "-", "ь", "ъ")

    private val phonemeIdMap = mapOf(
        "_" to 0, "^" to 1, "$" to 2, " " to 3, "!" to 4,
        "'" to 5, "(" to 6, ")" to 7, "," to 8, "-" to 9,
        "." to 10, "..." to 11, ":" to 12, ";" to 13, "?" to 14,
        "a0" to 15, "a1" to 16, "b" to 17, "bj" to 18, "c" to 19,
        "ch" to 20, "d" to 21, "dj" to 22, "e0" to 23, "e1" to 24,
        "f" to 25, "fj" to 26, "g" to 27, "gj" to 28, "h" to 29,
        "hj" to 30, "i0" to 31, "i1" to 32, "j" to 33, "k" to 34,
        "kj" to 35, "l" to 36, "lj" to 37, "m" to 38, "mj" to 39,
        "n" to 40, "nj" to 41, "o0" to 42, "o1" to 43, "p" to 44,
        "pj" to 45, "r" to 46, "rj" to 47, "s" to 48, "sch" to 49,
        "sh" to 50, "sj" to 51, "t" to 52, "tj" to 53, "u0" to 54,
        "u1" to 55, "v" to 56, "vj" to 57, "y0" to 58, "y1" to 59,
        "z" to 60, "zh" to 61, "zj" to 62
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
            val bertFile = copyAssetToInternalStorage("models/bert/model.onnx", "bert_model.onnx")

            // Загружаем основную модель
            Log.d(TAG, "Loading main model from: ${modelFile.absolutePath}")
            Log.d(TAG, "Model file size: ${modelFile.length() / 1024 / 1024} MB")
            ortSession = ortEnvironment.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "Main model loaded successfully")

            // Загружаем BERT модель
            Log.d(TAG, "Loading BERT model from: ${bertFile.absolutePath}")
            Log.d(TAG, "BERT file size: ${bertFile.length() / 1024 / 1024} MB")
            bertSession = ortEnvironment.createSession(bertFile.absolutePath, sessionOptions)
            Log.d(TAG, "BERT model loaded successfully")

            // Загружаем словарь и vocab
            loadDictionary()
            loadVocab()

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

    private fun loadVocab() {
        Log.d(TAG, "Loading vocab...")
        context.assets.open("models/bert/vocab.txt").bufferedReader().use { reader ->
            reader.lineSequence().forEachIndexed { index, token ->
                vocab[token] = index
            }
        }
        Log.d(TAG, "Vocab loaded: ${vocab.size} tokens")
    }

    fun synthesize(text: String, speakerId: Int = 1): Pair<ShortArray, Int> {
        try {
            Log.d(TAG, "Starting synthesis for: $text")

            // Подготовка данных
            val (phonemeIds, bertEmbeddings) = g2pMultistream(text)

            // Конвертируем в нужный формат для ONNX
            val textArray = Array(5) { i ->
                LongArray(phonemeIds.size) { j ->
                    phonemeIds[j][i].toLong()
                }
            }

            val textTensor = OnnxTensor.createTensor(
                ortEnvironment,
                arrayOf(textArray)
            )

            val textLengthsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                longArrayOf(phonemeIds.size.toLong())
            )

            val scalesTensor = OnnxTensor.createTensor(
                ortEnvironment,
                floatArrayOf(0.8f, 1.0f, 0.8f) // noise_level, speed, duration_noise
            )

            val sidTensor = OnnxTensor.createTensor(
                ortEnvironment,
                longArrayOf(speakerId.toLong())
            )

            val bertTensor = OnnxTensor.createTensor(
                ortEnvironment,
                arrayOf(bertEmbeddings.transpose())
            )

            // Запускаем инференс
            Log.d(TAG, "Running inference...")
            val inputs = mapOf(
                "input" to textTensor,
                "input_lengths" to textLengthsTensor,
                "scales" to scalesTensor,
                "sid" to sidTensor,
                "bert" to bertTensor
            )

            val output = ortSession.run(inputs)
            val audioTensor = output[0].value as Array<*>

            // Конвертируем в ShortArray
            val audioFloat = when (audioTensor[0]) {
                is FloatArray -> audioTensor[0] as FloatArray
                is Array<*> -> (audioTensor[0] as Array<*>).flatMap {
                    when(it) {
                        is FloatArray -> it.toList()
                        is Float -> listOf(it)
                        else -> emptyList()
                    }
                }.toFloatArray()
                else -> floatArrayOf()
            }

            Log.d(TAG, "Audio generated: ${audioFloat.size} samples")

            // Закрываем тензоры
            textTensor.close()
            textLengthsTensor.close()
            scalesTensor.close()
            sidTensor.close()
            bertTensor.close()
            output.close()

            // Конвертируем в int16
            return audioFloatToInt16(audioFloat) to phonemeIds.size

        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
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

    private fun Array<FloatArray>.transpose(): Array<FloatArray> {
        if (isEmpty()) return this
        val rows = size
        val cols = this[0].size
        return Array(cols) { col ->
            FloatArray(rows) { row ->
                this[row][col]
            }
        }
    }

    fun close() {
        if (::ortSession.isInitialized) ortSession.close()
        if (::bertSession.isInitialized) bertSession.close()
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
    private fun getBertEmbeddings(text: String, phonemeCount: Int): Array<FloatArray> {
        return try {

            val tokens = text.lowercase()
                .replace(Regex("[^а-яёa-z\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }

            val tokenIds = mutableListOf<Long>()
            tokenIds += 101L // CLS
            for (t in tokens) {
                val id = vocab[t] ?: vocab["[UNK]"] ?: 100
                tokenIds += id.toLong()
            }
            tokenIds += 102L // SEP
            val seqLen = tokenIds.size

            val inputIds = Array(1) { LongArray(seqLen) }
            val attentionMask = Array(1) { LongArray(seqLen) }
            val tokenTypeIds = Array(1) { LongArray(seqLen) }
            for (i in 0 until seqLen) {
                inputIds[0][i] = tokenIds[i]
                attentionMask[0][i] = 1L
                tokenTypeIds[0][i] = 0L
            }

            val inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
            val attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMask)
            val tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnvironment, tokenTypeIds)

            val bertInputs = mapOf(
                "input_ids" to inputIdsTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeIdsTensor
            )

            Log.d(TAG, "Running BERT inference: seqLen=$seqLen tokens=${tokens.size}")
            val bertOutput = bertSession.run(bertInputs)
            val rawOutput = bertOutput[0].value

            val tokenEmbeddings: Array<FloatArray> = when (rawOutput) {
                is Array<*> -> {
                    if (rawOutput.isNotEmpty() && rawOutput[0] is Array<*>) {
                        // Output: float[1][seq_len][hidden]
                        @Suppress("UNCHECKED_CAST")
                        val embeddings3d = rawOutput as Array<Array<FloatArray>>
                        embeddings3d[0]
                    } else if (rawOutput[0] is FloatArray) {
                        // Output: float[seq_len][hidden] (batch dimension missing)
                        @Suppress("UNCHECKED_CAST")
                        rawOutput as Array<FloatArray>
                    } else {
                        throw IllegalStateException("Unexpected BERT output structure")
                    }
                }
                else -> throw IllegalStateException("Unexpected BERT output type: ${rawOutput?.javaClass}")
            }

            bertOutput.close()
            inputIdsTensor.close()
            attentionMaskTensor.close()
            tokenTypeIdsTensor.close()

            distributeEmbeddings(tokenEmbeddings, phonemeCount)

        } catch (e: Exception) {
            Log.e(TAG, "BERT inference failed → fallback", e)
            Array(phonemeCount) { FloatArray(768) { 0.01f } }
        }
    }
    private fun distributeEmbeddings(tokenEmbeddings: Array<FloatArray>, phonemeCount: Int): Array<FloatArray> {
        // Распределяем токен эмбеддинги равномерно по фонемам
        val result = Array(phonemeCount) { FloatArray(768) }

        if (tokenEmbeddings.isEmpty()) {
            return Array(phonemeCount) { FloatArray(768) { 0.01f } }
        }

        val ratio = tokenEmbeddings.size.toFloat() / phonemeCount

        for (i in 0 until phonemeCount) {
            val tokenIndex = (i * ratio).toInt().coerceIn(0, tokenEmbeddings.size - 1)
            result[i] = tokenEmbeddings[tokenIndex].clone()
        }

        return result
    }
    private fun g2pMultistream(text: String): Pair<List<IntArray>, Array<FloatArray>> {
        val phonemes = mutableListOf<IntArray>()
        val pattern = """(\.\.\.|[-\s,.?!;:"()])"""

        // Начальный токен
        var lastPunc = " "
        var lastSentencePunc = " "
        var inQuote = 0

        phonemes.add(intArrayOf(
            phonemeIdMap["^"] ?: 1,
            phonemeIdMap["_"] ?: 0,
            inQuote,
            phonemeIdMap[lastPunc] ?: 0,
            phonemeIdMap[lastSentencePunc] ?: 3
        ))

        // Разбиваем текст с сохранением пунктуации
        val words = text.lowercase().split(Regex(pattern))
            .filter { it.isNotEmpty() && !it.matches(Regex("\\s+")) }

        for (word in words) {
            // Проверяем пунктуацию
            when {
                word == "\"" -> {
                    inQuote = if (inQuote == 1) 0 else 1
                    continue
                }
                word in listOf(".", "!", "?", "...") -> {
                    lastSentencePunc = word
                    lastPunc = word
                    continue
                }
                word in listOf(",", ";", ":", "-") -> {
                    lastPunc = word
                    continue
                }
            }

            // Получаем фонемы для слова
            val wordPhonemes = if (dictionary.containsKey(word)) {
                dictionary[word]!!.split(" ")
            } else {
                convert(word).split(" ")
            }

            // Добавляем фонемы слова
            for (p in wordPhonemes) {
                val phonemeId = phonemeIdMap[p] ?: phonemeIdMap["_"] ?: 0
                phonemes.add(intArrayOf(
                    phonemeId,
                    phonemeIdMap["_"] ?: 0,
                    inQuote,
                    phonemeIdMap[lastPunc] ?: 0,
                    phonemeIdMap[lastSentencePunc] ?: 3
                ))
            }

            // Добавляем пробел после слова
            phonemes.add(intArrayOf(
                phonemeIdMap[" "] ?: 3,
                phonemeIdMap["_"] ?: 0,
                inQuote,
                phonemeIdMap[lastPunc] ?: 0,
                phonemeIdMap[lastSentencePunc] ?: 3
            ))
        }

        // Конечный токен
        phonemes.add(intArrayOf(
            phonemeIdMap["$"] ?: 2,
            phonemeIdMap["_"] ?: 0,
            0,
            phonemeIdMap[" "] ?: 3,
            phonemeIdMap[lastSentencePunc] ?: 3
        ))

        // Создаем BERT эмбеддинги
        val bertEmbeddings = getBertEmbeddings(text, phonemes.size)

        return phonemes to bertEmbeddings
    }
}
