package com.example.data.repository

import com.example.data.api.*
import com.example.data.db.ChatDao
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    val totalMessageCount: Flow<Int> = chatDao.getTotalMessageCount()
    val userMessageCount: Flow<Int> = chatDao.getUserMessageCount()
    val allMessages: Flow<List<ChatMessage>> = chatDao.getAllMessages()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    fun searchMessages(query: String): Flow<List<ChatMessage>> {
        return chatDao.searchMessages(query)
    }

    fun getMessageCountForSession(sessionId: Long): Flow<Int> {
        return chatDao.getMessageCountForSession(sessionId)
    }

    suspend fun createNewSession(title: String): Long = withContext(Dispatchers.IO) {
        chatDao.insertSession(ChatSession(title = title))
    }

    suspend fun updateSession(session: ChatSession) = withContext(Dispatchers.IO) {
        chatDao.updateSession(session)
    }

    suspend fun deleteSession(session: ChatSession) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionWithMessages(session)
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessageById(messageId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        chatDao.clearAllData()
    }

    suspend fun insertMessage(message: ChatMessage): Long = withContext(Dispatchers.IO) {
        chatDao.insertMessage(message)
    }

    suspend fun getGeminiResponse(
        model: String,
        history: List<ChatMessage>,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        val contents = history.map { message ->
            Content(
                role = message.role,
                parts = listOf(Part(text = message.content))
            )
        }

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemInstruction?.let {
                Content(parts = listOf(Part(text = it)))
            }
        )

        val response = GeminiClient.apiService.generateContent(model, GeminiClient.apiKey, request)
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("No response received from model")
    }

    suspend fun getOpenAiResponse(
        model: String,
        history: List<ChatMessage>,
        apiKey: String,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("الرجاء إدخال مفتاح API لـ OpenAI في إعدادات CodeOne أولاً")
        }

        val messages = mutableListOf<OpenAiMessage>()
        
        // Add system instruction if provided
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(OpenAiMessage(role = "system", content = systemInstruction))
        }

        // Add history mapping role 'model' to 'assistant'
        history.forEach { msg ->
            val role = if (msg.role == "model") "assistant" else "user"
            messages.add(OpenAiMessage(role = role, content = msg.content))
        }

        val request = OpenAiRequest(
            model = model,
            messages = messages
        )

        val authHeader = "Bearer $apiKey"
        val response = OpenAiClient.apiService.generateChatCompletion(authHeader, request)
        response.choices?.firstOrNull()?.message?.content
            ?: throw Exception("لم يتم استلام أي رد من خادم OpenAI API")
    }

    suspend fun getOllamaResponse(
        model: String,
        history: List<ChatMessage>,
        baseUrl: String,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) {
            throw Exception("الرجاء إدخال رابط خادم Ollama في الإعدادات أولاً")
        }

        // Format and clean baseUrl to ensure it ends with slash
        val formattedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val requestUrl = "${formattedBaseUrl}api/chat"

        val messages = mutableListOf<OllamaMessage>()
        
        // Add system instruction if provided
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(OllamaMessage(role = "system", content = systemInstruction))
        }

        // Add history mapping role 'model' to 'assistant'
        history.forEach { msg ->
            val role = if (msg.role == "model") "assistant" else "user"
            messages.add(OllamaMessage(role = role, content = msg.content))
        }

        val request = OllamaRequest(
            model = model,
            messages = messages
        )

        val response = OllamaClient.apiService.generateChat(requestUrl, request)
        response.message?.content
            ?: throw Exception("No response received from Ollama. Make sure Ollama is running and model '$model' is pulled locally.")
    }

    suspend fun getOpenRouterResponse(
        model: String,
        history: List<ChatMessage>,
        apiKey: String,
        systemInstruction: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("الرجاء إدخال مفتاح OpenRouter API في إعدادات التطبيق أولاً (احصل عليه من openrouter.ai/keys)")
        }

        val messages = mutableListOf<OpenAiMessage>()
        
        // Add system instruction if provided
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(OpenAiMessage(role = "system", content = systemInstruction))
        }

        // Add history mapping role 'model' to 'assistant'
        history.forEach { msg ->
            val role = if (msg.role == "model") "assistant" else "user"
            messages.add(OpenAiMessage(role = role, content = msg.content))
        }

        val request = OpenAiRequest(
            model = model,
            messages = messages
        )

        val cleanKey = apiKey.trim()
        val authHeader = if (cleanKey.startsWith("Bearer ")) cleanKey else "Bearer $cleanKey"
        val response = OpenRouterClient.apiService.generateChatCompletion(
            authorization = authHeader,
            referer = "https://codeone.ai",
            title = "CodeOne AI",
            request = request
        )
        response.choices?.firstOrNull()?.message?.content
            ?: throw Exception("لم يتم استلام أي رد من خادم OpenRouter.ai")
    }
}
