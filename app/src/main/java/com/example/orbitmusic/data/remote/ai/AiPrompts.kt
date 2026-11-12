package com.example.orbitmusic.data.remote.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Prompt text and response formats for the playlist curator. */
object AiPrompts {

    const val TEMPERATURE = 0.7

    /**
     * Room for ~30 songs plus name and description. Reasoning models (gpt-oss) also spend
     * part of this on hidden reasoning: at 1,600 a 30-song answer was cut off mid-JSON.
     */
    const val MAX_TOKENS = 4_096

    /**
     * Reasoning effort for models that support it. "low" keeps answers fast (~2-3s for 30
     * songs on gpt-oss-120b) and leaves the token budget for the playlist itself.
     */
    fun reasoningEffortFor(model: String): String? =
        if (model.startsWith("openai/gpt-oss")) "low" else null

    fun system(allowExplicit: Boolean): String = buildString {
        append("You are a music curator. Reply with JSON only. ")
        append("Suggest only real, well-known songs you are confident exist. Never invent songs. ")
        append("Never include video IDs or URLs. Return 10-30 songs unless asked otherwise. ")
        append("Respect the explicit-content setting: ")
        append(
            if (allowExplicit) {
                "explicit songs are allowed."
            } else {
                "explicit songs are NOT allowed; use clean songs only."
            },
        )
        // Spelled out for the json_object fallback, where no schema is enforced.
        append(" The JSON must have exactly this shape: ")
        append("""{"playlistName": string, "description": string, "songs": [{"title": string, "artist": string}]}.""")
    }

    /** Sent when a reply couldn't be parsed, before giving up. */
    const val JSON_REMINDER =
        "Return valid JSON only, in the shape {\"playlistName\", \"description\", \"songs\": [{\"title\", \"artist\"}]}."

    /** Structured outputs: the model must follow the playlist schema. */
    val jsonSchemaFormat: JsonObject = buildJsonObject {
        put("type", "json_schema")
        putJsonObject("json_schema") {
            put("name", "playlist")
            putJsonObject("schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("playlistName") { put("type", "string") }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("songs") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("title") { put("type", "string") }
                                putJsonObject("artist") { put("type", "string") }
                            }
                            putJsonArray("required") {
                                add("title")
                                add("artist")
                            }
                            put("additionalProperties", false)
                        }
                    }
                }
                putJsonArray("required") {
                    add("playlistName")
                    add("description")
                    add("songs")
                }
                put("additionalProperties", false)
            }
        }
    }

    /** Fallback for models without json_schema support (the prompt says "JSON"). */
    val jsonObjectFormat: JsonObject = buildJsonObject { put("type", "json_object") }
}
