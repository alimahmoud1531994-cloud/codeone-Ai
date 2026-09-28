package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OllamaRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OllamaMessage>,
    @Json(name = "stream") val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OllamaMessage(
    @Json(name = "role") val role: String, // "system", "user", "assistant"
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OllamaResponse(
    @Json(name = "model") val model: String?,
    @Json(name = "message") val message: OllamaMessage?,
    @Json(name = "done") val done: Boolean?
)
