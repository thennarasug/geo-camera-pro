package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camera.GeoCamViewModel
import com.example.ui.GeoCamScreen
import com.example.ui.theme.GeoCamTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      val viewModel: GeoCamViewModel = viewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val isDark = uiState.preferences.isDarkTheme

      LaunchedEffect(isDark) {
        val style =
          if (isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(
              android.graphics.Color.TRANSPARENT,
              android.graphics.Color.TRANSPARENT,
            )
          }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
      }

      GeoCamTheme(darkTheme = isDark) {
        GeoCamScreen(viewModel = viewModel)
      }
    }
  }
}
