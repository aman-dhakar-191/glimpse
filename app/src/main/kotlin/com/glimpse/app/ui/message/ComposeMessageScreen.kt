package com.glimpse.app.ui.message

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.glimpse.app.R
import java.io.File

private fun createCameraImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}

private val QUICK_EMOJIS = listOf("❤️", "😊", "👍", "😂", "🎉")

@Composable
fun ComposeMessageScreen(
    uiState: ComposeUiState,
    onSend: (String) -> Unit,
    onSendPhoto: (Uri, String) -> Unit,
    onSentHandled: () -> Unit,
    onOpenGuide: () -> Unit,
    onLogout: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.compose_sent)
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) selectedImageUri = uri }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) selectedImageUri = pendingCameraUri }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is ComposeUiState.Sent) {
            text = ""
            selectedImageUri = null
            pendingCameraUri = null
            snackbarHostState.showSnackbar(sentMessage)
            onSentHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(onClick = onOpenGuide) {
                        Text(stringResource(R.string.compose_widget_guide_link))
                    }
                    TextButton(onClick = onLogout) {
                        Text(stringResource(R.string.guide_logout))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.compose_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    selectedImageUri?.let { uri ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    "✕",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(
                                    if (selectedImageUri != null) {
                                        R.string.compose_caption_placeholder
                                    } else {
                                        R.string.compose_placeholder
                                    }
                                )
                            )
                        },
                        minLines = if (selectedImageUri != null) 1 else 3
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { launchCamera() }) {
                            Text("📸")
                        }
                        OutlinedButton(onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Text("🖼️")
                        }
                        QUICK_EMOJIS.forEach { emoji ->
                            OutlinedButton(onClick = { text += emoji }) {
                                Text(emoji)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState is ComposeUiState.Error) {
                Text(
                    uiState.message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val canSend = uiState !is ComposeUiState.Sending &&
                (selectedImageUri != null || text.isNotBlank())

            Button(
                onClick = {
                    val uri = selectedImageUri
                    if (uri != null) {
                        onSendPhoto(uri, text)
                    } else {
                        onSend(text)
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is ComposeUiState.Sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.compose_send))
                }
            }
        }
    }
}
