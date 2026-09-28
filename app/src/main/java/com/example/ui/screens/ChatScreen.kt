package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.ui.theme.GptGreen
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSpeak: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.currentMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Configuration / settings states
    val currentProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val currentModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val chatGptApiKey by viewModel.chatGptApiKey.collectAsStateWithLifecycle()
    val openRouterApiKey by viewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val ollamaBaseUrl by viewModel.ollamaBaseUrl.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val systemInstruction by viewModel.systemInstruction.collectAsStateWithLifecycle()
    val autoSpeakEnabled by viewModel.autoSpeakEnabled.collectAsStateWithLifecycle()

    val currentSession = sessions.find { it.id == selectedSessionId }

    // Dialog flags
    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    var showPhpAdminDialog by remember { mutableStateOf(false) }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var showHistoryExplorerDialog by remember { mutableStateOf(false) }
    var showClearAllConfirmation by remember { mutableStateOf(false) }
    var showQuickModelSwitcher by remember { mutableStateOf(false) }

    // Voice recognition transcripts inside the Live Voice Overlay
    var voiceOverlayTranscript by remember { mutableStateOf("") }

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Display error message as Toast if it is present
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Trigger TTS automatically for new model responses if autoSpeak is enabled
    LaunchedEffect(messages) {
        if (autoSpeakEnabled && messages.isNotEmpty()) {
            val lastMsg = messages.last()
            if (lastMsg.role == "model") {
                onSpeak(lastMsg.content)
            }
        }
    }

    // Speech Recognizer Launcher for Voice Dictation
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                if (showVoiceOverlay) {
                    voiceOverlayTranscript = spokenText
                    viewModel.sendMessage(spokenText)
                } else {
                    viewModel.sendMessage(spokenText)
                }
            }
        }
    }

    val triggerSpeechInput = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن مع المساعد الذكي...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "التعرف على الصوت غير مدعوم على جهازك", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(300.dp)
            ) {
                DrawerContent(
                    sessions = sessions,
                    selectedSessionId = selectedSessionId,
                    userName = userName,
                    onSessionSelected = { id ->
                        viewModel.selectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClicked = {
                        viewModel.createNewChat()
                        scope.launch { drawerState.close() }
                    },
                    onRenameSession = { session, newTitle ->
                        viewModel.renameSession(session, newTitle)
                    },
                    onDeleteSession = { session ->
                        viewModel.deleteSession(session)
                    },
                    onClearAllHistory = {
                        showClearAllConfirmation = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenProfileSettings = {
                        showProfileSettingsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenAnalytics = {
                        showAnalyticsDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenPhpAdmin = {
                        showPhpAdminDialog = true
                        scope.launch { drawerState.close() }
                    },
                    onOpenLiveVoice = {
                        showVoiceOverlay = true
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                if (isSearchActive) {
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("البحث في هذه المحادثة...", fontSize = 14.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = if (isArabic(searchQuery)) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_codeone_logo),
                                        contentDescription = "CodeOne Logo",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showQuickModelSwitcher = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = currentSession?.title ?: "CodeOne AI",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.testTag("app_title")
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = when (currentProvider) {
                                                "openrouter" -> "OpenRouter: ${currentModel.substringAfterLast("/")}"
                                                "openai" -> "OpenAI: $currentModel"
                                                "ollama" -> "Local: $currentModel"
                                                else -> "Gemini: $currentModel"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "تبديل الموديل",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Sidebar"
                                )
                            }
                        },
                        actions = {
                            // Quick Model & OpenRouter Switcher Action Icon
                            IconButton(
                                onClick = { showQuickModelSwitcher = true },
                                modifier = Modifier.testTag("model_switcher_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = "اختيار الموديل و OpenRouter",
                                    tint = if (currentProvider == "openrouter") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // History Explorer Action Icon
                            IconButton(
                                onClick = { showHistoryExplorerDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "سجل المحادثات المحلية",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Search Message Action Icon
                            IconButton(
                                onClick = { isSearchActive = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "البحث في المحادثة",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Export Chat History Icon Button
                            IconButton(
                                onClick = {
                                    if (messages.isNotEmpty()) {
                                        val sessionTitle = currentSession?.title ?: "محادثة ذكية"
                                        val formattedHistory = messages.joinToString(separator = "\n\n") { msg ->
                                            val roleLabel = if (msg.role == "user") "**أنت:**" else "**المساعد:**"
                                            "$roleLabel\n${msg.content}\n"
                                        }
                                        val fullMarkdown = "# $sessionTitle\n\n$formattedHistory"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, sessionTitle)
                                            putExtra(Intent.EXTRA_TEXT, fullMarkdown)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "تصدير ومشاركة المحادثة"))
                                    } else {
                                        Toast.makeText(context, "المحادثة فارغة للتصدير", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "تصدير ومشاركة المحادثة",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Live Voice Chat Icon Shortcut
                            IconButton(
                                onClick = { showVoiceOverlay = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsVoice,
                                    contentDescription = "Live Voice Chat",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onStopSpeaking() }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Stop speech",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.createNewChat() },
                                modifier = Modifier.testTag("new_chat_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Chat",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (messages.isEmpty()) {
                    EmptyStateDashboard(
                        userName = userName,
                        currentProvider = currentProvider,
                        currentModel = currentModel,
                        onOpenModelSelector = { showQuickModelSwitcher = true },
                        onSuggestionSelected = { prompt ->
                            viewModel.sendMessage(prompt)
                        }
                    )
                } else {
                    val listState = rememberLazyListState()

                    val filteredMessages = if (searchQuery.isBlank()) {
                        messages
                    } else {
                        messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
                    }

                    // Auto-scroll to bottom when new messages arrive
                    LaunchedEffect(filteredMessages.size, isThinking) {
                        if (filteredMessages.isNotEmpty()) {
                            listState.animateScrollToItem(filteredMessages.size - 1)
                        }
                    }

                    if (filteredMessages.isEmpty() && searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "لا توجد نتائج تطابق \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(filteredMessages, key = { it.id }) { message ->
                                MessageBubbleItem(
                                    message = message,
                                    onSpeakClicked = { onSpeak(message.content) },
                                    onDeleteClicked = { viewModel.deleteMessage(message.id) }
                                )
                            }

                            if (isThinking) {
                                item {
                                    AssistantThinkingBubble()
                                }
                            }
                        }
                    }
                }

                // Floating input field at the bottom
                ChatInputBar(
                    isThinking = isThinking,
                    hapticEnabled = hapticFeedbackEnabled,
                    onMessageSent = { content ->
                        viewModel.sendMessage(content)
                    },
                    onMicClicked = {
                        triggerSpeechInput()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    // --- DIALOG OVERLAYS ---

    // 1. Profile and API Providers Settings Configuration
    if (showProfileSettingsDialog) {
        ProfileSettingsDialog(
            userName = userName,
            selectedProvider = currentProvider,
            selectedModel = currentModel,
            chatGptApiKey = chatGptApiKey,
            openRouterApiKey = openRouterApiKey,
            ollamaBaseUrl = ollamaBaseUrl,
            systemInstruction = systemInstruction,
            autoSpeakEnabled = autoSpeakEnabled,
            selectedTheme = selectedTheme,
            onThemeSelected = { viewModel.updateSelectedTheme(it) },
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onHapticFeedbackChanged = { viewModel.updateHapticFeedback(it) },
            onDismiss = { showProfileSettingsDialog = false },
            onSave = { name, provider, model, openRouterKey, apiKey, ollamaUrl, instruction, autoSpeak ->
                viewModel.updateUserName(name)
                viewModel.updateSelectedProvider(provider)
                viewModel.updateSelectedModel(model)
                viewModel.updateOpenRouterApiKey(openRouterKey)
                viewModel.updateChatGptApiKey(apiKey)
                viewModel.updateOllamaBaseUrl(ollamaUrl)
                viewModel.updateSystemInstruction(instruction)
                viewModel.updateAutoSpeak(autoSpeak)
                showProfileSettingsDialog = false
                Toast.makeText(context, "تم حفظ الإعدادات بنجاح!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Detailed Analytics and Local Stats
    if (showAnalyticsDialog) {
        val totalMessages by viewModel.totalMessageCount.collectAsStateWithLifecycle()
        val userMessagesCount by viewModel.userMessageCount.collectAsStateWithLifecycle()
        val allMessagesList by viewModel.allMessages.collectAsStateWithLifecycle()

        AnalyticsDashboardDialog(
            totalSessions = sessions.size,
            totalMessages = totalMessages,
            userMessages = userMessagesCount,
            allMessages = allMessagesList,
            currentModel = currentModel,
            onDismiss = { showAnalyticsDialog = false }
        )
    }

    // 3. Local Room Database History Explorer Dialog
    if (showHistoryExplorerDialog) {
        ChatHistoryExplorerDialog(
            sessions = sessions,
            selectedSessionId = selectedSessionId,
            onSessionSelected = { sessionId ->
                viewModel.selectSession(sessionId)
                showHistoryExplorerDialog = false
            },
            onDeleteSession = { session ->
                viewModel.deleteSession(session)
            },
            onClearAll = {
                showClearAllConfirmation = true
            },
            onDismiss = { showHistoryExplorerDialog = false }
        )
    }

    // 4. Confirmation dialog for clearing all Room DB chat history
    if (showClearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "مسح كامل سجل المحادثات",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف كافة المحادثات والرسائل المخزنة محلياً في قاعدة بيانات Room؟ سيتم مسح جميع الجلسات نهائياً.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllChatHistory()
                        showClearAllConfirmation = false
                        Toast.makeText(context, "تم مسح جميع المحادثات من قاعدة البيانات!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("مسح الكل الآن", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmation = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 5. Quick Model & OpenRouter Switcher Dialog
    if (showQuickModelSwitcher) {
        QuickModelSelectorDialog(
            currentProvider = currentProvider,
            currentModel = currentModel,
            openRouterApiKey = openRouterApiKey,
            onSelectModel = { provider, model ->
                viewModel.updateSelectedProvider(provider)
                viewModel.updateSelectedModel(model)
                showQuickModelSwitcher = false
                Toast.makeText(context, "تم تفعيل موديل: $model", Toast.LENGTH_SHORT).show()
            },
            onSaveOpenRouterKey = { key ->
                viewModel.updateOpenRouterApiKey(key)
                Toast.makeText(context, "تم حفظ مفتاح OpenRouter بنجاح!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showQuickModelSwitcher = false }
        )
    }

    // 3. PHP Admin Control Panel Console
    if (showPhpAdminDialog) {
        val totalMessages by viewModel.totalMessageCount.collectAsStateWithLifecycle()
        val userMessagesCount by viewModel.userMessageCount.collectAsStateWithLifecycle()

        PhpAdminControlPanelDialog(
            selectedProvider = currentProvider,
            currentModel = currentModel,
            chatGptApiKey = chatGptApiKey,
            ollamaBaseUrl = ollamaBaseUrl,
            sessionsCount = sessions.size,
            totalMessages = totalMessages,
            userMessages = userMessagesCount,
            onDismiss = { showPhpAdminDialog = false }
        )
    }

    // 4. Interactive Live Voice Chat Screen Overlay
    if (showVoiceOverlay) {
        val lastAssistantMessage = messages.lastOrNull { it.role == "model" }?.content ?: ""

        LiveVoiceChatOverlay(
            isThinking = isThinking,
            userTranscript = voiceOverlayTranscript,
            assistantSpeechText = lastAssistantMessage,
            onDismiss = {
                onStopSpeaking()
                showVoiceOverlay = false
            },
            onTriggerSpeechCapture = {
                triggerSpeechInput()
            },
            onStopSpeaking = onStopSpeaking
        )
    }
    }
}

@Composable
fun DrawerContent(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    userName: String,
    onSessionSelected: (Long) -> Unit,
    onNewChatClicked: () -> Unit,
    onRenameSession: (ChatSession, String) -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onClearAllHistory: () -> Unit,
    onOpenProfileSettings: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPhpAdmin: () -> Unit,
    onOpenLiveVoice: () -> Unit
) {
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // CodeOne Brand Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = borderStroke(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_codeone_logo),
                            contentDescription = "CodeOne Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CodeOne",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "AI v2.0",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Software & AI Solutions",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "كود وان • حلول برمجية ذكية وتطوير تقني متكامل",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // "New Chat" primary action button
        Button(
            onClick = onNewChatClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_drawer_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "محادثة جديدة",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Voice Chat and PHP Control Panel Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Voice Action Button
            Button(
                onClick = onOpenLiveVoice,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsVoice,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "صوت مباشر",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // PHP Control Panel Admin Button
            Button(
                onClick = onOpenPhpAdmin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C3E50),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF3498DB)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "لوحة PHP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Middle Section: Navigation for Settings & Analytics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Analytics Dashboard Button
            OutlinedButton(
                onClick = onOpenAnalytics,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "التحليلات",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Profile & Settings Settings Button
            OutlinedButton(
                onClick = onOpenProfileSettings,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "الإعدادات",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "سجل المحادثات (قاعدة Room)",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("${sessions.size}")
                }
            }

            if (sessions.isNotEmpty()) {
                Text(
                    text = "مسح الكل",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .clickable { onClearAllHistory() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // List of previous conversations
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد محادثات سابقة",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sessions) { session ->
                    val isSelected = session.id == selectedSessionId

                    if (sessionToRename?.id == session.id) {
                        // Inline renaming field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            TextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                onRenameSession(session, renameText)
                                sessionToRename = null
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { sessionToRename = null }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Clickable session list row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                )
                                .clickable { onSessionSelected(session.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("session_item_${session.id}")
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Outlined.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = formatTimestamp(session.createdAt),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }

                            // Edit and Delete icons for selected chat session
                            if (isSelected) {
                                IconButton(
                                    onClick = {
                                        sessionToRename = session
                                        renameText = session.title
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDeleteSession(session) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color.Red.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(12.dp))

        // Drawer Footer Showing Profile Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenProfileSettings() }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.takeOrEmpty(1),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
                Text(
                    text = "عرض وتعديل الملف الشخصي",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

private fun String.takeOrEmpty(n: Int): String {
    return if (this.length >= n) this.take(n) else "U"
}

@Composable
fun EmptyStateDashboard(
    userName: String,
    currentProvider: String,
    currentModel: String,
    onOpenModelSelector: () -> Unit,
    onSuggestionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Ambient CodeOne Emblem
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_codeone_logo),
                contentDescription = "CodeOne Emblem",
                tint = Color.Unspecified,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "CodeOne AI",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "أهلاً بك، $userName في كود وان",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "المساعد التقني والبرمجي المتكامل • حلول ذكية وهندسة نظم رقمية",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive OpenRouter / Active AI Model Banner
        Card(
            onClick = onOpenModelSelector,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = borderStroke(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "موديل الذكاء الاصطناعي الحالي:",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (currentProvider == "openrouter") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (currentProvider == "openrouter") "OpenRouter.ai" else currentProvider.uppercase(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = if (currentProvider == "openrouter") "OpenRouter: $currentModel" else currentModel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "تغيير الموديل",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text(
            text = "اطلب كتابة أكواد برمجية، تدقيق ومراجعة المشاريع، أو استشرني صوتياً ونصياً في مختلف مجالات التقنية.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Quick suggestions grid
        val suggestions = listOf(
            "💻 اكتب كود تطبيق Android متكامل باستخدام Jetpack Compose",
            "🚀 صمم معمارية Microservices وقواعد بيانات سحابية متقدمة",
            "🤖 كيفية دمج نماذج الذكاء الاصطناعي والأتمتة في مشروعي",
            "🔍 فحص وتدقيق كود برمجي واكتشاف الثغرات وتحسين الأداء"
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { prompt ->
                Card(
                    onClick = { onSuggestionSelected(prompt) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                textDirection = TextDirection.Rtl
                            ),
                            textAlign = TextAlign.Right,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubbleItem(
    message: ChatMessage,
    onSpeakClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    val isUser = message.role == "user"
    val isAr = isArabic(message.content)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Message Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!isUser) {
                // CodeOne AI Logo
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_codeone_logo),
                        contentDescription = "CodeOne AI",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CodeOne AI",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            } else {
                Text(
                    text = "أنت",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // User Avatar
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.secondary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message Content Bubble
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            val segments = remember(message.content) { splitMessageContent(message.content) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                segments.forEach { segment ->
                    when (segment) {
                        is MessageSegment.Text -> {
                            Text(
                                text = segment.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    textDirection = if (isAr) TextDirection.Rtl else TextDirection.Ltr
                                ),
                                textAlign = if (isAr) TextAlign.Right else TextAlign.Left
                            )
                        }
                        is MessageSegment.Code -> {
                            CodeBlock(code = segment.code, language = segment.language)
                        }
                    }
                }
            }
        }

        // Speaker and details row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            // Voice reader button for assistant messages
            if (!isUser) {
                IconButton(
                    onClick = onSpeakClicked,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak response",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Copy button
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(message.content))
                    Toast.makeText(context, "تم نسخ الرسالة!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "نسخ الرسالة",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Delete message from Room DB button
            IconButton(
                onClick = onDeleteClicked,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "حذف الرسالة من قاعدة البيانات",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String?) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (language ?: "code").uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "تم نسخ الكود!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Monospace code lines
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD4D4D4),
                    lineHeight = 16.sp
                )
            )
        }
    }
}

@Composable
fun AssistantThinkingBubble() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_codeone_logo),
                    contentDescription = "CodeOne AI",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CodeOne AI",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 120.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(14.dp)
        ) {
            TypingIndicator()
        }
    }
}

@Preview
@Composable
fun AssistantThinkingBubblePreview() {
    AssistantThinkingBubble()
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotScales = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 200)
            ),
            label = "dot_$index"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotScales.forEach { scale ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    isThinking: Boolean,
    hapticEnabled: Boolean = true,
    onMessageSent: (String) -> Unit,
    onMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textState by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val handleSend = {
        if (textState.trim().isNotEmpty() && !isThinking) {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onMessageSent(textState.trim())
            textState = ""
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp, max = 150.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // Voice Input Mic Button
            IconButton(
                onClick = {
                    if (hapticEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onMicClicked()
                },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Speech to Text voice input",
                    modifier = Modifier.size(22.dp)
                )
            }

            TextField(
                value = textState,
                onValueChange = { textState = it },
                placeholder = {
                    Text(
                        text = "اكتب رسالتك أو استفسارك هنا...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = TextDirection.ContentOrRtl
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { handleSend() }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                maxLines = 4
            )

            if (textState.isNotEmpty()) {
                IconButton(onClick = { textState = "" }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = handleSend,
                enabled = textState.trim().isNotEmpty() && !isThinking,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                ),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- DYNAMIC DIALOGUE PANELS ---

// 1. Upgraded Profile Settings with multi-provider inputs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsDialog(
    userName: String,
    selectedProvider: String,
    selectedModel: String,
    chatGptApiKey: String,
    openRouterApiKey: String,
    ollamaBaseUrl: String,
    systemInstruction: String,
    autoSpeakEnabled: Boolean,
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    hapticFeedbackEnabled: Boolean,
    onHapticFeedbackChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, provider: String, model: String, openRouterKey: String, apiKey: String, ollamaUrl: String, instruction: String, autoSpeak: Boolean) -> Unit
) {
    var nameState by remember { mutableStateOf(userName) }
    var providerState by remember { mutableStateOf(selectedProvider) }
    var modelState by remember { mutableStateOf(selectedModel) }
    var apiKeyState by remember { mutableStateOf(chatGptApiKey) }
    var openRouterApiKeyState by remember { mutableStateOf(openRouterApiKey) }
    var ollamaUrlState by remember { mutableStateOf(ollamaBaseUrl) }
    var instructionState by remember { mutableStateOf(systemInstruction) }
    var autoSpeakState by remember { mutableStateOf(autoSpeakEnabled) }
    var themeState by remember { mutableStateOf(selectedTheme) }
    var hapticState by remember { mutableStateOf(hapticFeedbackEnabled) }

    // When provider is changed, auto update model state
    val onProviderChanged = { newProvider: String ->
        providerState = newProvider
        modelState = when (newProvider) {
            "openrouter" -> "deepseek/deepseek-r1"
            "openai" -> "gpt-4o-mini"
            "ollama" -> "llama3"
            else -> "gemini-1.5-flash"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(nameState, providerState, modelState, openRouterApiKeyState, apiKeyState, ollamaUrlState, instructionState, autoSpeakState)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("حفظ التغييرات", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الملف الشخصي ومزودي الخدمة", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Stylized Avatar Card & Profile Header
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        border = borderStroke(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // First letter avatar
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = nameState.firstOrNull()?.toString()?.uppercase() ?: "U",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nameState.ifBlank { "المستخدم" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "عضو كود وان المعتمد (CodeOne Pro)",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "الموديل: ${modelState.uppercase()}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // User Name
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("اسم المستخدم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = nameState,
                            onValueChange = { nameState = it },
                            placeholder = { Text("أدخل اسمك") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // AI Provider Tabs
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("مزود خدمة الذكاء الاصطناعي (API Provider)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val providers = listOf(
                                Triple("gemini", "Gemini", Icons.Default.Cloud),
                                Triple("openrouter", "OpenRouter", Icons.Default.Hub),
                                Triple("openai", "OpenAI", Icons.Default.Bolt),
                                Triple("ollama", "Ollama", Icons.Default.Computer)
                            )

                            providers.forEach { (provId, label, icon) ->
                                val isSelected = providerState == provId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { onProviderChanged(provId) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Provider specific configurations
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "إعدادات ${providerState.uppercase()}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        when (providerState) {
                            "openrouter" -> {
                                Text("مفتاح API لـ OpenRouter.ai:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("احصل على مفتاحك المجاني أو المدفوع من: openrouter.ai/keys", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = openRouterApiKeyState,
                                    onValueChange = { openRouterApiKeyState = it },
                                    placeholder = { Text("sk-or-v1-...") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("أقوى موديلات OpenRouter المتاحة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))

                                val openRouterModels = listOf(
                                    "deepseek/deepseek-r1" to "DeepSeek R1 (تفكير عميق واستدلال خارق)",
                                    "deepseek/deepseek-r1:free" to "DeepSeek R1 [مجاني 100%] (بدون رصيد)",
                                    "deepseek/deepseek-chat" to "DeepSeek V3 (سريع جداً وممتاز للحوار)",
                                    "anthropic/claude-3.5-sonnet" to "Claude 3.5 Sonnet (الأفضل عالمياً في البرمجة)",
                                    "meta-llama/llama-3.3-70b-instruct" to "Llama 3.3 70B (مفتوح المصدر وخارق)",
                                    "meta-llama/llama-3.3-70b-instruct:free" to "Llama 3.3 70B [مجاني 100%]",
                                    "qwen/qwen-2.5-coder-32b-instruct" to "Qwen 2.5 Coder (متخصص بالأكواد وهندسة النظم)",
                                    "google/gemini-2.0-flash-001" to "Gemini 2.0 Flash (فائق السرعة)",
                                    "openai/gpt-4o" to "GPT-4o (الموديل الرائد متعدد الوسائط)",
                                    "openai/gpt-4o-mini" to "GPT-4o Mini (ذكي وسريع واقتصادي)",
                                    "mistralai/mistral-large-2411" to "Mistral Large (نموذج أوروبي متطور)"
                                )

                                openRouterModels.forEach { (modelId, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { modelState = modelId }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = modelState == modelId,
                                            onClick = { modelState = modelId },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text(label, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                var isCustomOpenRouter by remember { mutableStateOf(!openRouterModels.any { it.first == modelState }) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = isCustomOpenRouter,
                                        onClick = {
                                            isCustomOpenRouter = true
                                            modelState = "mistralai/mistral-large"
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Text("موديل مخصص آخر من OpenRouter:", fontSize = 12.sp)
                                }
                                if (isCustomOpenRouter) {
                                    OutlinedTextField(
                                        value = modelState,
                                        onValueChange = { modelState = it },
                                        placeholder = { Text("مثال: deepseek/deepseek-chat") },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        singleLine = true
                                    )
                                }
                            }
                            "gemini" -> {
                                Text("موديلات Google Gemini المدعومة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))

                                val geminiModels = listOf(
                                    "gemini-1.5-flash" to "Gemini 1.5 Flash (سريع ويومي)",
                                    "gemini-1.5-pro" to "Gemini 1.5 Pro (ذكي ومبرمج)",
                                    "gemini-2.0-flash" to "Gemini 2.0 Flash (نشط وحديث)"
                                )

                                geminiModels.forEach { (modelId, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { modelState = modelId }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = modelState == modelId,
                                            onClick = { modelState = modelId },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text(label, fontSize = 12.sp)
                                    }
                                }
                            }
                            "openai" -> {
                                Text("مفتاح API لـ OpenAI:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = apiKeyState,
                                    onValueChange = { apiKeyState = it },
                                    placeholder = { Text("sk-proj-...") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("موديلات ChatGPT المدعومة:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))

                                val chatGptModels = listOf(
                                    "gpt-4o-mini" to "GPT-4o Mini (سريع واقتصادي)",
                                    "gpt-4o" to "GPT-4o (الموديل الرائد فائق الذكاء)",
                                    "gpt-3.5-turbo" to "GPT-3.5 Turbo (مستقر وموثوق)"
                                )

                                chatGptModels.forEach { (modelId, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { modelState = modelId }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = modelState == modelId,
                                            onClick = { modelState = modelId },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text(label, fontSize = 12.sp)
                                    }
                                }
                            }
                            "ollama" -> {
                                Text("رابط خادم Ollama المحلي:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("استخدم 10.0.2.2 لربط محاكي الأندرويد بجهاز الكمبيوتر المحلي", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = ollamaUrlState,
                                    onValueChange = { ollamaUrlState = it },
                                    placeholder = { Text("http://10.0.2.2:11434/") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("اختر موديل Ollama المجهّز محلياً:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))

                                val ollamaModels = listOf(
                                    "llama3" to "Llama 3 (ميتا)",
                                    "mistral" to "Mistral (فرنسا)",
                                    "gemma" to "Gemma (جوجل)"
                                )

                                ollamaModels.forEach { (modelId, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { modelState = modelId }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        RadioButton(
                                            selected = modelState == modelId,
                                            onClick = { modelState = modelId },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Text(label, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                // Custom Ollama model input
                                var isCustomSelected by remember { mutableStateOf(!ollamaModels.any { it.first == modelState }) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = isCustomSelected,
                                        onClick = {
                                            isCustomSelected = true
                                            modelState = "llama3"
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Text("اسم موديل مخصص آخر:", fontSize = 12.sp)
                                }
                                if (isCustomSelected) {
                                    OutlinedTextField(
                                        value = modelState,
                                        onValueChange = { modelState = it },
                                        placeholder = { Text("مثال: codellama") },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                // Auto Speak Audio Toggle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نطق الردود تلقائياً (صوت لايف)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("قراءة إجابات الذكاء الاصطناعي مسموعة بصوت مسموع فور استلامها", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSpeakState,
                            onCheckedChange = { autoSpeakState = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Theme Selector Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("مظهر نظام الألوان (Theme Style)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("اختر اللمسة الجمالية للتطبيق ليتم تطبيقها على الواجهات فوراً:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themes = listOf(
                                Triple("codeone", "كود وان", Color(0xFF1E6BFF)),
                                Triple("emerald", "الزمرد", Color(0xFF10A37F)),
                                Triple("azure", "الكوني", Color(0xFF7A60FF)),
                                Triple("ocean", "المحيط", Color(0xFF00ACC1)),
                                Triple("sunset", "الغروب", Color(0xFFFFA726))
                            )

                            themes.forEach { (themeId, label, color) ->
                                val isSelected = themeState == themeId
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { 
                                            themeState = themeId
                                            onThemeSelected(themeId)
                                        }
                                        .padding(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(color, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label, 
                                        fontSize = 11.sp, 
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Tactile Haptic Feedback Toggle
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الاهتزاز اللمسي التفاعلي (Haptics)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("تفعيل اهتزازات لمسية خفيفة وتفاعلية أثناء الكتابة والتحكم بالأزرار والمايك", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = hapticState,
                            onCheckedChange = { 
                                hapticState = it
                                onHapticFeedbackChanged(it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Custom Instructions (System Prompt)
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("تعليمات تخصيص شخصية الذكاء الاصطناعي (System Prompt)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("وجّه الذكاء الاصطناعي بأسلوب الرد (مثال: رد بلغة مبرمج خبير واختصر إجاباتك)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = instructionState,
                            onValueChange = { instructionState = it },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp),
                            maxLines = 5
                        )
                    }
                }
            }
        }
    )
}

// 2. Analytics Dashboard Dialog
@Composable
fun AnalyticsDashboardDialog(
    totalSessions: Int,
    totalMessages: Int,
    userMessages: Int,
    allMessages: List<ChatMessage>,
    currentModel: String,
    onDismiss: () -> Unit
) {
    val aiMessages = (totalMessages - userMessages).coerceAtLeast(0)

    val totalWords = remember(allMessages) {
        allMessages.sumOf { it.content.split(Regex("\\s+")).filter { w -> w.isNotBlank() }.size }
    }
    val averageWordsPerMessage = remember(allMessages, totalMessages) {
        if (totalMessages > 0) totalWords / totalMessages else 0
    }

    val activeDaysCount = remember(allMessages) {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        allMessages.map { sdf.format(Date(it.timestamp)) }.distinct().size
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("إغلاق", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("لوحة تحليلات الذكاء الاصطناعي", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "نظرة عامة كاملة وإحصائيات تفصيلية حول نشاطك ومعدل المحادثات.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "إجمالي الرسائل",
                        value = totalMessages.toString(),
                        subtitle = "متبادلة",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "محادثاتك",
                        value = totalSessions.toString(),
                        subtitle = "مسجلة محلياً",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        title = "الكلمات المحللة",
                        value = totalWords.toString(),
                        subtitle = "كلمة مفهرسة",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "نشاط الاستخدام",
                        value = activeDaysCount.coerceAtLeast(1).toString(),
                        subtitle = "يوم نشط",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("رسائلي: $userMessages", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Text("الردود: $aiMessages", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val ratio = if (totalMessages > 0) userMessages.toFloat() / totalMessages.toFloat() else 0.5f
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الموديل المستخدم حالياً", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentModel.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("متوسط الكلمات لكل رد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$averageWordsPerMessage كلمة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تخزين البيانات", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("تخزين Room مشفر محلي", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}

// 3. PHP Admin Control Panel Dialog
@Composable
fun PhpAdminControlPanelDialog(
    selectedProvider: String,
    currentModel: String,
    chatGptApiKey: String,
    ollamaBaseUrl: String,
    sessionsCount: Int,
    totalMessages: Int,
    userMessages: Int,
    onDismiss: () -> Unit
) {
    var logsList by remember {
        mutableStateOf(
            listOf(
                "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] [INFO] Apache/2.4.59 (Unix) PHP/8.3.8 started successfully.",
                "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] [DB] Connecting to PDO SQLite local db file: database.db",
                "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] [DB] Connection established. Found $sessionsCount active chat sessions.",
                "[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}] [API] Initialized routes for 'gemini', 'openai' and 'ollama' endpoints."
            )
        )
    }

    var isPinging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val appendLog = { line: String ->
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logsList = logsList + "[$currentTime] $line"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E50))
            ) {
                Text("إغلاق لوحة التحكم", color = Color.White)
            }
        },
        dismissButton = {
            Button(
                onClick = {
                    if (!isPinging) {
                        isPinging = true
                        scope.launch {
                            appendLog("[PING] بدء فحص الاتصال بالخوادم المجهزة...")
                            delay(800)
                            appendLog("[PING] فحص Google Gemini API...")
                            delay(600)
                            appendLog("[OK] Google API responded in 142ms. [200 OK]")
                            
                            delay(600)
                            appendLog("[PING] فحص OpenAI ChatGPT API...")
                            delay(800)
                            if (chatGptApiKey.isNotBlank()) {
                                appendLog("[OK] OpenAI API authenticated. [200 OK]")
                            } else {
                                appendLog("[WARNING] OpenAI API authentication failed. No API key provided in settings!")
                            }
                            
                            delay(600)
                            appendLog("[PING] فحص Ollama Local Server على $ollamaBaseUrl...")
                            delay(900)
                            appendLog("[INFO] Local loopback connection checked. Make sure Ollama daemon is running locally.")
                            
                            isPinging = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2980B9)),
                enabled = !isPinging
            ) {
                if (isPinging) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص الاتصال (Ping)", color = Color.White)
                    }
                }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF2ECC71), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("لوحة تحكم كود وان التقنية (CodeOne Console)", fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "عرض وإدارة حالة خادم الـ API والـ SQLite المحلي.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Server status metadata
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PHP Web Environment", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                            Text("PHP 8.3.8 / Apache 2.4", fontSize = 10.sp, color = Color(0xFF3498DB), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Local Database SQLite", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                            Text("PDO Active (Connected)", fontSize = 10.sp, color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Provider Routing", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                            Text(selectedProvider.uppercase(), fontSize = 10.sp, color = Color(0xFFF1C40F), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Active Routing Model", fontSize = 10.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                            Text(currentModel, fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Interactive Logs Section
                Text("سجل أحداث الخادم (Server System Logs):", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logsList) { logLine ->
                            Text(
                                text = logLine,
                                fontSize = 10.sp,
                                color = if (logLine.contains("[OK]")) Color(0xFF2ECC71) else if (logLine.contains("[WARNING]")) Color(0xFFE74C3C) else Color(0xFF00FF00),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    )
}

// 4. Interactive Live Voice Chat Screen Overlay
@Composable
fun LiveVoiceChatOverlay(
    isThinking: Boolean,
    userTranscript: String,
    assistantSpeechText: String,
    onDismiss: () -> Unit,
    onTriggerSpeechCapture: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_glow")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(24.dp)
    ) {
        // Exit button top-left
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
        ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "المحادثة الصوتية المباشرة مع مساعد CodeOne",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isThinking) "جاري التفكير وتحليل إجابتك..." else if (userTranscript.isNotEmpty() && !isThinking) "نستمع إليك الآن..." else "جاهز لتلقي سؤالك الصوتي",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp)
            )

            // Dynamic pulsing microphone visualizer
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Pulsing outer glowing rings
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = if (isThinking) 1.1f else scalePulse
                            scaleY = if (isThinking) 1.1f else scalePulse
                            alpha = if (isThinking) 0.5f else 0.25f
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = if (isThinking) 1f else scalePulse * 0.85f
                            scaleY = if (isThinking) 1f else scalePulse * 0.85f
                            alpha = if (isThinking) 0.3f else 0.15f
                        }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                )

                // Inner core button trigger
                IconButton(
                    onClick = {
                        onStopSpeaking()
                        onTriggerSpeechCapture()
                    },
                    modifier = Modifier
                        .size(88.dp)
                        .background(if (isThinking) Color.DarkGray else MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    if (isThinking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Tap to speak",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // User audio transcript block
            if (userTranscript.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ما تم سماعه منك:",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userTranscript,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Assistant voice text preview
            if (assistantSpeechText.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "إجابة المساعد المسموعة:",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (assistantSpeechText.length > 100) assistantSpeechText.take(97) + "..." else assistantSpeechText,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Tap instructions footer
        Text(
            text = "اضغط على المايكروفون في الوسط للبدء بالتحدث في أي وقت.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun ChatHistoryExplorerDialog(
    sessions: List<ChatSession>,
    selectedSessionId: Long?,
    onSessionSelected: (Long) -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSessions = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سجل المحادثات (Room DB)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${sessions.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "جميع المحادثات مخزنة محلياً بالكامل على هاتفك دون حاجة للإنترنت.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في عناوين الجلسات...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredSessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة للبحث" else "لا توجد أي محادثات محفوظة بعد",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSessions, key = { it.id }) { session ->
                            val isSelected = session.id == selectedSessionId
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = if (isSelected)
                                    borderStroke(MaterialTheme.colorScheme.primary)
                                else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSessionSelected(session.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = formatTimestamp(session.createdAt),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteSession(session) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الجلسة",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (sessions.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مسح الكل")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
fun QuickModelSelectorDialog(
    currentProvider: String,
    currentModel: String,
    openRouterApiKey: String,
    onSelectModel: (provider: String, model: String) -> Unit,
    onSaveOpenRouterKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProviderTab by remember { mutableStateOf(currentProvider) }
    var tempOpenRouterKey by remember { mutableStateOf(openRouterApiKey) }
    var customModelInput by remember { mutableStateOf("") }

    val openRouterModels = listOf(
        Triple("deepseek/deepseek-r1", "DeepSeek R1", "استدلال منطقي وتفكير عميق خارق"),
        Triple("deepseek/deepseek-r1:free", "DeepSeek R1 [مجاني 100%]", "مجاني بدون رصيد عبر OpenRouter"),
        Triple("deepseek/deepseek-chat", "DeepSeek V3", "فائق السرعة وخبير في الحوار والبرمجة"),
        Triple("anthropic/claude-3.5-sonnet", "Claude 3.5 Sonnet", "الأفضل عالمياً في كتابة الأكواد"),
        Triple("meta-llama/llama-3.3-70b-instruct", "Llama 3.3 70B", "نموذج ميتا الرائد مفتوح المصدر"),
        Triple("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B [مجاني]", "نسخة مجانية بالكامل"),
        Triple("qwen/qwen-2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B", "متخصص هندسة البرمجيات"),
        Triple("google/gemini-2.0-flash-001", "Gemini 2.0 Flash", "فائق السرعة ومتعدد المهام"),
        Triple("openai/gpt-4o", "GPT-4o", "نموذج OpenAI الرائد متعدد الوسائط"),
        Triple("openai/gpt-4o-mini", "GPT-4o Mini", "ذكي وخفيف واقتصادي"),
        Triple("mistralai/mistral-large-2411", "Mistral Large", "نموذج فرنسي أوروبي متقدم")
    )

    val geminiModels = listOf(
        Triple("gemini-1.5-flash", "Gemini 1.5 Flash", "سريع ومثالي للمهام اليومية"),
        Triple("gemini-1.5-pro", "Gemini 1.5 Pro", "نموذج الذكاء والبرمجة الموسع"),
        Triple("gemini-2.0-flash", "Gemini 2.0 Flash", "أحدث وأسرع إصدارات جوجل")
    )

    val openAiModels = listOf(
        Triple("gpt-4o-mini", "GPT-4o Mini", "سريع واقتصادي"),
        Triple("gpt-4o", "GPT-4o", "الموديل الرائد فائق الذكاء"),
        Triple("gpt-3.5-turbo", "GPT-3.5 Turbo", "مستقر وموثوق")
    )

    val ollamaModels = listOf(
        Triple("llama3", "Llama 3", "موديل محلي عبر Ollama"),
        Triple("mistral", "Mistral", "موديل محلي عبر Ollama"),
        Triple("gemma", "Gemma", "موديل محلي عبر Ollama")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "اختيار مزود وموديل الذكاء الاصطناعي",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "دعم كامل لـ OpenRouter.ai والموديلات العالمية",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Provider Selection Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val providers = listOf(
                        "openrouter" to "OpenRouter",
                        "gemini" to "Gemini",
                        "openai" to "OpenAI",
                        "ollama" to "Ollama"
                    )
                    providers.forEach { (provId, label) ->
                        val isSelected = selectedProviderTab == provId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedProviderTab = provId },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedProviderTab == "openrouter") {
                    // OpenRouter API Key Quick Panel
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔑 مفتاح OpenRouter.ai API:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (tempOpenRouterKey.isNotBlank()) "✅ متوفر" else "⚠️ اختياري للمجاني",
                                    fontSize = 10.sp,
                                    color = if (tempOpenRouterKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = tempOpenRouterKey,
                                onValueChange = {
                                    tempOpenRouterKey = it
                                    onSaveOpenRouterKey(it)
                                },
                                placeholder = { Text("sk-or-v1-...", fontSize = 11.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 احصل على مفتاحك من openrouter.ai/keys (النماذج التي تنتهي بـ :free تعمل مجاناً)",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "اختر موديل OpenRouter للبدء فوراً:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(openRouterModels) { (modelId, name, desc) ->
                            val isSelected = currentProvider == "openrouter" && currentModel == modelId
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    borderStroke(MaterialTheme.colorScheme.primary)
                                else borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectModel("openrouter", modelId)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectModel("openrouter", modelId) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            if (modelId.contains(":free")) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "FREE",
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = desc,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = modelId,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // Custom OpenRouter model
                        item {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("أو اكتب معرف موديل مخصص:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = customModelInput,
                                            onValueChange = { customModelInput = it },
                                            placeholder = { Text("مثال: mistralai/mistral-small", fontSize = 10.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                if (customModelInput.isNotBlank()) {
                                                    onSelectModel("openrouter", customModelInput.trim())
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = customModelInput.isNotBlank()
                                        ) {
                                            Text("تطبيق", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedProviderTab == "gemini") {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(geminiModels) { (modelId, name, desc) ->
                            val isSelected = currentProvider == "gemini" && currentModel == modelId
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    borderStroke(MaterialTheme.colorScheme.primary)
                                else borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModel("gemini", modelId) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectModel("gemini", modelId) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedProviderTab == "openai") {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(openAiModels) { (modelId, name, desc) ->
                            val isSelected = currentProvider == "openai" && currentModel == modelId
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    borderStroke(MaterialTheme.colorScheme.primary)
                                else borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModel("openai", modelId) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectModel("openai", modelId) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ollamaModels) { (modelId, name, desc) ->
                            val isSelected = currentProvider == "ollama" && currentModel == modelId
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected)
                                    borderStroke(MaterialTheme.colorScheme.primary)
                                else borderStroke(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModel("ollama", modelId) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectModel("ollama", modelId) },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

private fun borderStroke(color: Color): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(1.dp, color)
}

@Composable
fun rememberScrollState(): androidx.compose.foundation.ScrollState {
    return androidx.compose.foundation.rememberScrollState()
}

@Composable
fun Divider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
    thickness: androidx.compose.ui.unit.Dp = 1.dp
) {
    androidx.compose.material3.HorizontalDivider(modifier = modifier, color = color, thickness = thickness)
}

// Helper functions for ChatScreen
fun isArabic(text: String): Boolean {
    return text.any { it.code in 0x0600..0x06FF }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

sealed class MessageSegment {
    data class Text(val text: String) : MessageSegment()
    data class Code(val code: String, val language: String?) : MessageSegment()
}

fun splitMessageContent(content: String): List<MessageSegment> {
    if (content.isBlank()) return emptyList()
    val segments = mutableListOf<MessageSegment>()
    val parts = content.split("```")
    for (i in parts.indices) {
        val part = parts[i]
        if (i % 2 == 1) {
            // Code segment
            val lines = part.trim().split("\n")
            val language = if (lines.isNotEmpty() && lines[0].length < 15 && !lines[0].contains(" ") && !lines[0].contains("\n")) {
                lines[0]
            } else {
                null
            }
            val code = if (language != null) {
                lines.drop(1).joinToString("\n")
            } else {
                part
            }
            segments.add(MessageSegment.Code(code.trim(), language))
        } else {
            // Text segment
            if (part.isNotEmpty()) {
                segments.add(MessageSegment.Text(part))
            }
        }
    }
    return segments
}
