package online.paychek.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

class BooleanDeserializer : JsonDeserializer<Boolean> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Boolean {
        if (json.isJsonPrimitive) {
            val primitive = json.asJsonPrimitive
            if (primitive.isBoolean) return primitive.asBoolean
            if (primitive.isNumber) return primitive.asInt == 1
            if (primitive.isString) {
                val str = primitive.asString
                return str.equals("true", ignoreCase = true) || str == "1"
            }
        }
        return false
    }
}

object GsonUtils {
    val gson: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Boolean::class.java, BooleanDeserializer())
            .registerTypeAdapter(Boolean::class.javaObjectType, BooleanDeserializer())
            .create()
    }
}
