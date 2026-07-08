package com.joat917.qrdecoderlite

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.joat917.qrdecoderlite.model.DecodeUiState
import com.joat917.qrdecoderlite.model.Sheet
import com.joat917.qrdecoderlite.ui.screen.MainScreen
import com.joat917.qrdecoderlite.ui.screen.ResultScreen
import com.joat917.qrdecoderlite.ui.theme.QRDecoderLiteTheme
import com.joat917.qrdecoderlite.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRDecoderLiteTheme {
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

        handleSendIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    if (uri != null) viewModel.onImageSelected(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra  (Intent.EXTRA_STREAM)
                    }
                    if (!uris.isNullOrEmpty()) {
                        viewModel.onImageSelected(uris[0])
                    }
                }
            }
        }
    }
}
