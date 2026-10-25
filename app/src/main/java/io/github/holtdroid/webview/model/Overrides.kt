package io.github.holtdroid.webview.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
    prettyPrint = false
}

enum class FieldType { BOOLEAN, INT, STRING, ENUM }

data class Field(val key: String, val type: FieldType, val options: List<String>)

@OptIn(ExperimentalSerializationApi::class)
object ProfileFields {
    private val descriptor: SerialDescriptor = Profile.serializer().descriptor

    val all: List<Field> = (0 until descriptor.elementsCount).map { i ->
        val d = descriptor.getElementDescriptor(i)
        val type = when (d.kind) {
            PrimitiveKind.BOOLEAN -> FieldType.BOOLEAN
            PrimitiveKind.INT -> FieldType.INT
            SerialKind.ENUM -> FieldType.ENUM
            else -> FieldType.STRING
        }
        val options = if (type == FieldType.ENUM) (0 until d.elementsCount).map { d.getElementName(it) } else emptyList()
        Field(descriptor.getElementName(i), type, options)
    }

    private val byKey = all.associateBy { it.key.lowercase() }

    fun find(key: String): Field? = byKey[key.lowercase().replace("-", "").replace("_", "")]
        ?: byKey[key.lowercase()]
}

fun Profile.toJson(): JsonObject = AppJson.encodeToJsonElement(Profile.serializer(), this).jsonObject

fun Profile.value(key: String): String? = (toJson()[key] as? JsonPrimitive)?.content

class OverrideResult(val profile: Profile, val applied: List<String>, val errors: List<String>)

fun parseBoolean(value: String): Boolean? = when (value.trim().lowercase()) {
    "true", "1", "on", "yes", "y" -> true
    "false", "0", "off", "no", "n" -> false
    else -> null
}

fun Profile.override(values: Map<String, String>): OverrideResult {
    val errors = mutableListOf<String>()
    val applied = mutableListOf<String>()
    val current = toJson()
    val patched = buildJsonObject {
        current.forEach { (k, v) -> put(k, v) }
        values.forEach { (rawKey, raw) ->
            val field = ProfileFields.find(rawKey)
            if (field == null) {
                errors += "unknown setting \"$rawKey\""
                return@forEach
            }
            val element = when (field.type) {
                FieldType.BOOLEAN -> parseBoolean(raw)?.let(::JsonPrimitive)
                FieldType.INT -> raw.trim().toIntOrNull()?.let(::JsonPrimitive)
                FieldType.ENUM -> raw.trim().lowercase().replace('_', '-')
                    .takeIf { it in field.options }?.let(::JsonPrimitive)
                FieldType.STRING -> JsonPrimitive(raw)
            }
            if (element == null) {
                val hint = when (field.type) {
                    FieldType.BOOLEAN -> "true or false"
                    FieldType.INT -> "a number"
                    FieldType.ENUM -> field.options.joinToString(" | ")
                    FieldType.STRING -> "text"
                }
                errors += "${field.key}: \"$raw\" is not $hint"
            } else {
                put(field.key, element)
                applied += field.key
            }
        }
    }
    val profile = AppJson.decodeFromJsonElement(Profile.serializer(), patched).normalized()
    return OverrideResult(profile, applied, errors)
}

fun Profile.normalized(): Profile = copy(
    width = width.coerceIn(0, 4096),
    height = height.coerceIn(0, 4096),
    scale = scale.coerceIn(0, 400),
    textZoom = textZoom.coerceIn(25, 400),
)

fun parseSize(value: String): Pair<Int, Int>? {
    val parts = value.lowercase().split('x', '×', '*', ',').map { it.trim() }
    if (parts.size != 2) return null
    val w = parts[0].toIntOrNull() ?: return null
    val h = parts[1].toIntOrNull() ?: return null
    return w to h
}
