package com.joat917.qrdecoderlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.Sheet
import com.joat917.qrdecoderlite.ui.screen.MainScreen
import com.joat917.qrdecoderlite.ui.screen.ResultScreen
import com.joat917.qrdecoderlite.ui.theme.QRDecoderLiteTheme
import com.joat917.qrdecoderlite.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRDecoderLiteTheme {
                val viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)
                val uiState by viewModel.uiState.collectAsState()
                val decodeUiState by viewModel.decodeUiState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val sheet = uiState.activeSheet) {
                        null -> MainScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        is Sheet.Result -> ResultScreen(
                            decodeUiState = decodeUiState
                                ?: DecodeUiState(imageUri = sheet.imageUri),
                            onBackToMain = viewModel::onBackToMain,
                            onCopy = { content ->
                                viewModel.onCopy(content, applicationContext)
                            },
                            onOpenLink = { url ->
                                viewModel.onOpenLink(url, applicationContext)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
