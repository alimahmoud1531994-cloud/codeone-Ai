package com.example.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface OllamaApiService {
    @POST
    suspend fun generateChat(
        @Url url: String,
        @Body request: OllamaRequest
    ): OllamaResponse
}

object OllamaClient {
    // Standard Retrofit placeholder base URL; dynamic `@Url` parameter will override it.
    private const val PLACEHOLDER_URL = "http://127.0.0.1/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Local models can take longer to generate response
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: OllamaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OllamaApiService::class.java)
    }
}
