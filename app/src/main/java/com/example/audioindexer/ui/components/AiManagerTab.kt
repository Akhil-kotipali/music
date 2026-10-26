package com.example.audioindexer.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiLog
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import java.io.File

@Composable
fun AiManagerTab(
    aiKeys: List<AiKeyConfig>,
    aiLogs: List<AiLog>,
    keyTestingState: Map<Long, String>,
    onAddKey: (provider: String, label: String, apiKey: String, model: String) -> Unit,
    onReplaceKey: (id: Long, provider: String, label: String, apiKey: String, model: String) -> Unit,
    onToggleKeyActive: (id: Long, isActive: Boolean) -> Unit,
    onTestKeyHealth: (AiKeyConfig) -> Unit,
    onDeleteKey: (id: Long) -> Unit,
    onDeleteLog: (id: Long) -> Unit,
    onClearAllLogs: () -> Unit,
    onExportLogs: ((File) -> Unit) -> Unit,
    onFetchLiveModels: (provider: String, apiKey: String) -> Unit = { _, _ -> },
    liveModels: List<String> = emptyList(),
    isFetchingModels: Boolean = false,
    modelFetchError: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var keyToReplace by remember { mutableStateOf<AiKeyConfig?>(null) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Keys Management Header & Actions
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryViolet.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Provider Keys (${aiKeys.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Supports Gemini, Groq, and OpenAI. Keys are monitored for rate limits (429) and rotated automatically on error.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Key items
        if (aiKeys.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceBorder.copy(alpha = 0.3f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No AI Keys Configured",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add a Gemini, Groq, or OpenAI API key to enable mood/genre categorization.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(aiKeys, key = { it.id }) { keyConfig ->
                AiKeyCard(
                    keyConfig = keyConfig,
                    onToggleActive = { onToggleKeyActive(keyConfig.id, it) },
                    onTestHealth = { onTestKeyHealth(keyConfig) },
                    onDelete = { onDeleteKey(keyConfig.id) },
                    onReplace = { keyToReplace = keyConfig },
                    testResult = keyTestingState[keyConfig.id]
                )
            }
        }

        // Section 2: Audit Logs & Export Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.List, contentDescription = null, tint = SecondaryViolet)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Audit Logs (${aiLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (aiLogs.isNotEmpty()) {
                        IconButton(onClick = onClearAllLogs, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Button(
                        onClick = {
                            onExportLogs { file ->
                                exportStatusMessage = "Exported to: ${file.name}"
                                shareExportedFile(context, file)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SecondaryViolet, contentColor = Color.White),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export .TXT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (exportStatusMessage != null) {
                Text(
                    text = exportStatusMessage ?: "",
                    fontSize = 11.sp,
                    color = PrimaryCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Log Items
        if (aiLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceBorder.copy(alpha = 0.2f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No AI interactions logged yet. Approve categorization or test a key to see logs here.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(aiLogs, key = { it.id }) { log ->
                AiLogCard(
                    log = log,
                    onDelete = { onDeleteLog(log.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp)) // Padding for bottom mini playback bar
        }
    }

    // Add Key Dialog
    if (showAddDialog) {
        AddOrReplaceKeyDialog(
            onDismiss = { showAddDialog = false },
            onSave = { p, l, k, m -> onAddKey(p, l, k, m) },
            onFetchLiveModels = onFetchLiveModels,
            liveModels = liveModels,
            isFetchingModels = isFetchingModels,
            modelFetchError = modelFetchError
        )
    }

    // Replace Key Dialog
    keyToReplace?.let { keyConfig ->
        AddOrReplaceKeyDialog(
            initialKey = keyConfig,
            onDismiss = { keyToReplace = null },
            onSave = { p, l, k, m -> onReplaceKey(keyConfig.id, p, l, k, m) },
            onFetchLiveModels = onFetchLiveModels,
            liveModels = liveModels,
            isFetchingModels = isFetchingModels,
            modelFetchError = modelFetchError
        )
    }
}

private fun shareExportedFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share AI Audit Logs (.txt)"))
    } catch (e: Exception) {
        // Fallback to simple view or share
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, file.readText())
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share AI Audit Logs"))
    }
}
