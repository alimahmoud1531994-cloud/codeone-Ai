package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ChatViewModelFactory
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize TextToSpeech
        tts = TextToSpeech(this, this)

        // Initialize Local Storage & Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(database.chatDao())

        // Create ChatViewModel via Factory
        val viewModel = ViewModelProvider(
            this,
            ChatViewModelFactory(repository, applicationContext)
        )[ChatViewModel::class.java]

        setContent {
            val themeName by viewModel.selectedTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(themeName = themeName) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        viewModel = viewModel,
                        onSpeak = { text -> speakText(text) },
                        onStopSpeaking = { stopSpeaking() }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default Locale if Arabic is not installed
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsInitialized = true
        } else {
            Log.e("MainActivity", "TextToSpeech Initialization Failed!")
        }
    }

    private fun speakText(text: String) {
        if (!isTtsInitialized) return
        
        // Clean markdown elements from the response before reading it out loud
        val cleanText = text
            .replace(Regex("`{3}(.*?)\\n(.*?)\\n`{3}", RegexOption.DOT_MATCHES_ALL), " [كود برمجي] ") // skip reading large code blocks
            .replace(Regex("[*`#_~-]"), "")
            .trim()

        // Dynamically choose speech language based on content
        val locale = if (containsArabic(cleanText)) Locale("ar") else Locale.ENGLISH
        tts?.setLanguage(locale)

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "CodeOne_Speech")
    }

    private fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
