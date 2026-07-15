package com.mickaell.respondon

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object DeepSeekClient {

    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
    // modelo de razonamiento: mas preciso que deepseek-chat a cambio de mas latencia
    private const val MODEL = "deepseek-reasoner"
    private const val SYSTEM_PROMPT =
        "Recibiras texto extraido por OCR de una captura de pantalla. " +
            "Si contiene una pregunta o ejercicio, respondelo de forma breve y directa. " +
            "Si hay varias preguntas, responde cada una en una linea. " +
            "Ignora el texto de interfaz irrelevante (hora, bateria, botones). " +
            "Si no hay ninguna pregunta, resume en una frase lo que dice el texto."

    // Llamada bloqueante: invocar siempre desde un hilo de fondo.
    fun ask(apiKey: String, ocrText: String): String {
        val body = JSONObject()
            .put("model", MODEL)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", ocrText))
            )

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 180_000 // el reasoner puede tardar en preguntas dificiles
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                throw IOException("DeepSeek HTTP $code: ${resp.take(300)}")
            }
            return parseAnswer(resp)
        } finally {
            conn.disconnect()
        }
    }

    fun parseAnswer(responseJson: String): String =
        JSONObject(responseJson)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
}
