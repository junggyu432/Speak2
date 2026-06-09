package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.api.GeminiService
import com.example.data.database.AppDatabase
import com.example.data.database.StudyRepository
import com.example.ui.BYODHomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BYODViewModel
import com.example.viewmodel.BYODViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Database, Repository, and GeminiService
    val database = AppDatabase.getDatabase(applicationContext)
    val repository = StudyRepository(database.wordDao())
    val geminiService = GeminiService()
    
    // Instantiate ViewModel via Factory (with context support!)
    val factory = BYODViewModelFactory(repository, geminiService, applicationContext)
    val viewModel = ViewModelProvider(this, factory)[BYODViewModel::class.java]

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          BYODHomeScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

