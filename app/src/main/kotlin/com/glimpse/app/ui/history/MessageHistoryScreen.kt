package com.glimpse.app.ui.history

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.glimpse.app.R
import com.glimpse.app.data.model.Message
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val OUTGOING_SHAPE = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp
)
private val INCOMING_SHAPE = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp
)

private sealed interface HistoryItem {
    data class DateDivider(val label: String) : HistoryItem
    data class MessageRow(val message: Message, val isLast: Boolean) : HistoryItem
}

private fun groupByDate(messages: List<Message>): List<HistoryItem> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val items = mutableListOf<HistoryItem>()
    var lastDate: LocalDate? = null

    messages.forEachIndexed { index, message ->
        val date = Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
        if (date != lastDate) {
            val label = when {
                date == today -> "Today"
                date == today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            }
            items += HistoryItem.DateDivider(label)
            lastDate = date
        }
        items += HistoryItem.MessageRow(message, isLast = index == messages.lastIndex)
    }
    return items
}

private fun formatTime(epochMillis: Long): String {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHistoryScreen(
    uiState: HistoryUiState,
    onBack: () -> Unit,
    onDownloadImage: (String) -> Unit,
    onDownloadResultHandled: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadSuccessMessage = stringResource(R.string.history_download_success)
    val downloadFailedMessage = stringResource(R.string.history_download_failed)

    var pendingDownloadUrl by remember { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (granted && url != null) onDownloadImage(url)
    }

    fun requestDownload(url: String) {
        // WRITE_EXTERNAL_STORAGE is only declared (and only needed) up to
        // API 28 — MediaStore writes on 29+ don't require it at all.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onDownloadImage(url)
        } else {
            pendingDownloadUrl = url
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(uiState.downloadResult) {
        when (uiState.downloadResult) {
            DownloadResult.Success -> snackbarHostState.showSnackbar(downloadSuccessMessage)
            DownloadResult.Failure -> snackbarHostState.showSnackbar(downloadFailedMessage)
            null -> return@LaunchedEffect
        }
        onDownloadResultHandled()
    }

    val items = remember(uiState.messages) { groupByDate(uiState.messages) }
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is HistoryItem.DateDivider -> DateDividerRow(item.label)
                    is HistoryItem.MessageRow -> MessageBubbleRow(
                        message = item.message,
                        isMine = item.message.authorUid == uiState.myUid,
                        seenStatus = if (item.isLast) uiState.lastMessageSeenStatus else null,
                        onDownloadImage = { url -> requestDownload(url) }
                    )
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}

@Composable
private fun DateDividerRow(label: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun MessageBubbleRow(
    message: Message,
    isMine: Boolean,
    seenStatus: SeenStatus?,
    onDownloadImage: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            val shape = if (isMine) OUTGOING_SHAPE else INCOMING_SHAPE
            val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

            if (message.type == "photo" && message.photoUrl.isNotBlank()) {
                Surface(
                    color = bubbleColor,
                    shape = shape,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Box {
                            AsyncImage(
                                model = message.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.2f)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            SmallFloatingActionButton(
                                onClick = { onDownloadImage(message.photoUrl) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                            ) {
                                Text("⬇", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        if (message.caption.isNotBlank()) {
                            Text(
                                message.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = textColor,
                                modifier = Modifier.padding(8.dp, 6.dp, 8.dp, 2.dp)
                            )
                        }
                    }
                }
            } else {
                Surface(color = bubbleColor, shape = shape) {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(Modifier.size(2.dp))

            when (seenStatus) {
                is SeenStatus.Seen -> Text(
                    "✓ " + stringResource(R.string.history_seen, formatTime(seenStatus.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                SeenStatus.Sent -> Text(
                    stringResource(R.string.history_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                null -> Text(
                    formatTime(message.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
