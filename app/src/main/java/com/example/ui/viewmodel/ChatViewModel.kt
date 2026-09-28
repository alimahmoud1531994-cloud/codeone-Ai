package com.example.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("chatgpt_prefs", Context.MODE_PRIVATE)

    // All available chat sessions
    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected session ID
    private val _selectedSessionId = MutableStateFlow<Long?>(null)
    val selectedSessionId: StateFlow<Long?> = _selectedSessionId.asStateFlow()

    // Messages for the selected session
    val currentMessages: StateFlow<List<ChatMessage>> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Analytics state flows
    val totalMessageCount: StateFlow<Int> = repository.totalMessageCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userMessageCount: StateFlow<Int> = repository.userMessageCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allMessages: StateFlow<List<ChatMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User Settings State Flows
    private val _selectedProvider = MutableStateFlow(prefs.getString("provider_name", "gemini") ?: "gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("model_name", "gemini-1.5-flash") ?: "gemini-1.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _chatGptApiKey = MutableStateFlow(prefs.getString("chatgpt_api_key", "") ?: "")
    val chatGptApiKey: StateFlow<String> = _chatGptApiKey.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow(prefs.getString("openrouter_api_key", "") ?: "")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _ollamaBaseUrl = MutableStateFlow(prefs.getString("ollama_base_url", "http://10.0.2.2:11434/") ?: "http://10.0.2.2:11434/")
    val ollamaBaseUrl: StateFlow<String> = _ollamaBaseUrl.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_name", "المستخدم") ?: "المستخدم")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val defaultSystemInstruction = """
        أنت "مساعد CodeOne الذكي" (CodeOne AI Assistant)، المساعد التقني والبرمجي المتطور والخاص بشركة كود وان (CodeOne).
        مجالات تخصصك الرئيسية:
        1. هندسة البرمجيات وتطوير تطبيقات الويب، تطبيقات الأجهزة الذكية (Android / iOS)، والأنظمة السحابية.
        2. كتابة الأكواد النظيفة (Clean Code)، حل المشكلات البرمجية، وتصحيح الأخطاء (Debugging) في مختلف لغات البرمجة (Kotlin, Python, JavaScript, TypeScript, PHP, وغيرها).
        3. حلول الذكاء الاصطناعي، نماذج التعلم الآلي، وأتمتة العمليات الرقمية الذكية (Business Automation).
        4. تصميم المعمارية البرمجية (Software Architecture) وقواعد البيانات وتحسين الأداء والحماية.
        تتميز بالدقة العالية، الاحترافية، والرد بأسلوب تقني منظم مع تنسيق الأكواد بنظام Markdown الأنيق وإعطاء أمثلة تطبيقية وشروحات واضحة.
    """.trimIndent()

    private val _systemInstruction = MutableStateFlow(
        prefs.getString("system_instruction", defaultSystemInstruction) ?: defaultSystemInstruction
    )
    val systemInstruction: StateFlow<String> = _systemInstruction.asStateFlow()

    private val _autoSpeakEnabled = MutableStateFlow(prefs.getBoolean("auto_speak", false))
    val autoSpeakEnabled: StateFlow<Boolean> = _autoSpeakEnabled.asStateFlow()

    private val _selectedTheme = MutableStateFlow(prefs.getString("selected_theme", "codeone") ?: "codeone")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(prefs.getBoolean("haptic_feedback", true))
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    // UI State for API interaction
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Automatically select the latest chat session if it exists on startup
        viewModelScope.launch {
            sessions.collectFirst { list ->
                if (list.isNotEmpty() && _selectedSessionId.value == null) {
                    _selectedSessionId.value = list.first().id
                }
            }
        }
    }

    private suspend fun <T> Flow<T>.collectFirst(action: suspend (T) -> Unit) {
        val flow = this
        flow.take(1).collect(action)
    }

    fun selectSession(sessionId: Long?) {
        _selectedSessionId.value = sessionId
        clearError()
    }

    fun createNewChat() {
        _selectedSessionId.value = null
        clearError()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _isThinking.value) return

        viewModelScope.launch {
            _isThinking.value = true
            clearError()

            try {
                var sessionId = _selectedSessionId.value

                // If no session exists, create a new one automatically
                if (sessionId == null) {
                    // Generate a smart title from the first message
                    val initialTitle = if (content.length > 25) {
                        content.take(22) + "..."
                    } else {
                        content
                    }
                    sessionId = repository.createNewSession(initialTitle)
                    _selectedSessionId.value = sessionId
                }

                // Insert the user message
                val userMsg = ChatMessage(sessionId = sessionId, role = "user", content = content)
                repository.insertMessage(userMsg)

                // Get entire session history to send as context
                val currentHistory = currentMessages.value

                // If this is the very first user message, update the session title to match it
                val session = sessions.value.find { it.id == sessionId }
                if (session != null && (session.title.startsWith("New Chat") || session.title == "Conversation" || session.title == "ChatGPT AI" || session.title == "CodeOne AI" || session.title == "محادثة جديدة")) {
                    val smartTitle = if (content.length > 25) {
                        content.take(22) + "..."
                    } else {
                        content
                    }
                    repository.updateSession(session.copy(title = smartTitle))
                }

                // Route API call depending on the selected provider
                val responseContent = when (_selectedProvider.value) {
                    "openrouter" -> {
                        repository.getOpenRouterResponse(
                            model = _selectedModel.value,
                            history = currentHistory + userMsg,
                            apiKey = _openRouterApiKey.value,
                            systemInstruction = _systemInstruction.value
                        )
                    }
                    "openai" -> {
                        repository.getOpenAiResponse(
                            model = _selectedModel.value,
                            history = currentHistory + userMsg,
                            apiKey = _chatGptApiKey.value,
                            systemInstruction = _systemInstruction.value
                        )
                    }
                    "ollama" -> {
                        repository.getOllamaResponse(
                            model = _selectedModel.value,
                            history = currentHistory + userMsg,
                            baseUrl = _ollamaBaseUrl.value,
                            systemInstruction = _systemInstruction.value
                        )
                    }
                    else -> {
                        repository.getGeminiResponse(
                            model = _selectedModel.value,
                            history = currentHistory + userMsg,
                            systemInstruction = _systemInstruction.value
                        )
                    }
                }

                // Insert assistant response
                val assistantMsg = ChatMessage(sessionId = sessionId, role = "model", content = responseContent)
                repository.insertMessage(assistantMsg)

            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to get response. Please try again."
            } finally {
                _isThinking.value = false
            }
        }
    }

    fun renameSession(session: ChatSession, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateSession(session.copy(title = newTitle))
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            if (_selectedSessionId.value == session.id) {
                // Select another one if available, otherwise clear selection
                val remaining = sessions.value.filter { it.id != session.id }
                if (remaining.isNotEmpty()) {
                    _selectedSessionId.value = remaining.first().id
                } else {
                    _selectedSessionId.value = null
                }
            }
        }
    }

    // Settings modifiers
    fun updateSelectedProvider(provider: String) {
        _selectedProvider.value = provider
        prefs.edit().putString("provider_name", provider).apply()

        // Auto-select standard default model for the selected provider
        val defaultModel = when (provider) {
            "openrouter" -> "deepseek/deepseek-r1"
            "openai" -> "gpt-4o-mini"
            "ollama" -> "llama3"
            else -> "gemini-1.5-flash"
        }
        updateSelectedModel(defaultModel)
    }

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
        prefs.edit().putString("model_name", model).apply()
    }

    fun updateChatGptApiKey(key: String) {
        _chatGptApiKey.value = key
        prefs.edit().putString("chatgpt_api_key", key).apply()
    }

    fun updateOpenRouterApiKey(key: String) {
        _openRouterApiKey.value = key
        prefs.edit().putString("openrouter_api_key", key).apply()
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch {
            repository.clearAllData()
            _selectedSessionId.value = null
        }
    }

    fun searchMessages(query: String): Flow<List<ChatMessage>> {
        return repository.searchMessages(query)
    }

    fun updateOllamaBaseUrl(url: String) {
        _ollamaBaseUrl.value = url
        prefs.edit().putString("ollama_base_url", url).apply()
    }

    fun updateUserName(name: String) {
        _userName.value = name
        prefs.edit().putString("user_name", name).apply()
    }

    fun updateSystemInstruction(instruction: String) {
        _systemInstruction.value = instruction
        prefs.edit().putString("system_instruction", instruction).apply()
    }

    fun updateAutoSpeak(enabled: Boolean) {
        _autoSpeakEnabled.value = enabled
        prefs.edit().putBoolean("auto_speak", enabled).apply()
    }

    fun updateSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        prefs.edit().putString("selected_theme", theme).apply()
    }

    fun updateHapticFeedback(enabled: Boolean) {
        _hapticFeedbackEnabled.value = enabled
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
    }
}

class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
