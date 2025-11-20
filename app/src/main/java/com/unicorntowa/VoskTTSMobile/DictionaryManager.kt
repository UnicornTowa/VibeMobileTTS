package com.unicorntowa.VoskTTSMobile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DictionaryManager {
    private const val TAG = "DictionaryManager"

    private val dictionary = mutableMapOf<String, String>()
    private var initialized = false

    suspend fun initialize(context: Context) {
        if (initialized) {
            Log.d(TAG, "Dictionary already initialized")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading dictionary...")
                val startTime = System.currentTimeMillis()

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

                initialized = true
                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Dictionary loaded: ${dictionary.size} words in ${loadTime}ms")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionary", e)
                throw e
            }
        }
    }

    fun getPhonemes(word: String): String? {
        return dictionary[word]
    }

    fun isInitialized(): Boolean = initialized

    fun clear() {
        dictionary.clear()
        initialized = false
    }
}
