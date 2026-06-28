package online.paychek.app.utils

import com.google.gson.JsonParser

object ApiErrorParser {
    fun parse(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null
        return try {
            val obj = JsonParser.parseString(errorBody).asJsonObject
            when {
                obj.has("message") && !obj.get("message").isJsonNull ->
                    obj.get("message").asString
                obj.has("error") && !obj.get("error").isJsonNull ->
                    obj.get("error").asString
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
