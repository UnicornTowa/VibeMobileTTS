package com.unicorntowa.VoskTTSMobile

enum class ModelType(val displayName: String, val modelPath: String, val fileName: String) {
    VOSK_TTS("VoskTTS", "models/model.onnx", "model.onnx"),
    VOSK_TTS_QUANTIZED("VoskTTS Int8 PTQ-TW ", "models/model_int8.onnx", "model_int8.onnx")
}
