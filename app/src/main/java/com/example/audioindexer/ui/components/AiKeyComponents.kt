package com.example.audioindexer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audioindexer.data.model.AiKeyConfig
import com.example.audioindexer.data.model.AiProvider
import com.example.audioindexer.ui.theme.AccentGreen
import com.example.audioindexer.ui.theme.AccentOrange
import com.example.audioindexer.ui.theme.AccentRed
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import com.example.audioindexer.ui.theme.TertiaryAmber

@Composable
fun AiKeyCard(
    keyConfig: AiKeyConfig,
    onToggleActive: (Boolean) -> Unit,
    onTestHealth: () -> Unit,
    onDelete: () -> Unit,
    onReplace: () -> Unit,
    isTesting: Boolean = false,
    testResult: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (keyConfig.isRateLimited) AccentOrange
                else if (keyConfig.isActive) PrimaryCyan.copy(alpha = 0.4f)
                else DarkSurfaceBorder,
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Provider, Label & Active Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (keyConfig.provider.uppercase()) {
                                    "GEMINI" -> PrimaryCyan.copy(alpha = 0.2f)
                                    "GROQ" -> AccentOrange.copy(alpha = 0.2f)
                                    "OPENAI" -> AccentGreen.copy(alpha = 0.2f)
                                    else -> SecondaryViolet.copy(alpha = 0.2f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = keyConfig.provider.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (keyConfig.provider.uppercase()) {
                                "GEMINI" -> PrimaryCyan
                                "GROQ" -> AccentOrange
                                "OPENAI" -> AccentGreen
                                else -> SecondaryViolet
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = keyConfig.label.ifBlank { "API Key #${keyConfig.id}" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = keyConfig.isActive,
                        onCheckedChange = onToggleActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryCyan,
                            checkedTrackColor = PrimaryCyan.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Key info and rate limit badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyConfig.maskedKey,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Rate limiting badge
                if (keyConfig.isRateLimited) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentOrange.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rate-Limited (429)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Rate Limit: Healthy", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                    }
                }
            }

            if (keyConfig.errorCount > 0 && !keyConfig.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Errors: ${keyConfig.errorCount} (${keyConfig.lastError})",
                    fontSize = 11.sp,
                    color = AccentRed,
                    maxLines = 1
                )
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = testResult,
                    fontSize = 11.sp,
                    color = if (testResult.startsWith("Key valid")) AccentGreen else AccentOrange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons: Test Health, Replace, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTestHealth,
                    enabled = !isTesting,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check Status", fontSize = 11.sp)
                    }
                }

                OutlinedButton(
                    onClick = onReplace,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Replace", fontSize = 11.sp)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AccentRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrReplaceKeyDialog(
    initialKey: AiKeyConfig? = null,
    onDismiss: () -> Unit,
    onSave: (provider: String, label: String, apiKey: String, model: String) -> Unit
) {
    var provider by remember { mutableStateOf(initialKey?.provider ?: "GEMINI") }
    var label by remember { mutableStateOf(initialKey?.label ?: "") }
    var apiKey by remember { mutableStateOf(initialKey?.apiKey ?: "") }
    var model by remember { mutableStateOf(initialKey?.model ?: "") }
    var isExpanded by remember { mutableStateOf(false) }

    val providers = listOf(
        AiProvider.GEMINI to "Google Gemini (gemini-1.5-flash)",
        AiProvider.GROQ to "Groq (llama-3.3-70b-versatile)",
        AiProvider.OPENAI to "OpenAI (gpt-4o-mini)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initialKey != null) "Replace API Key" else "Add AI Provider Key",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure Gemini, Groq, or OpenAI keys with rate-limit failover.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Provider Dropdown
                ExposedDropdownMenuBox(
                    expanded = isExpanded,
                    onExpandedChange = { isExpanded = it }
                ) {
                    OutlinedTextField(
                        value = providers.firstOrNull { it.first.name == provider }?.second ?: provider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = isExpanded,
                        onDismissRequest = { isExpanded = false }
                    ) {
                        for ((p, title) in providers) {
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    provider = p.name
                                    model = p.defaultModel
                                    if (label.isBlank()) label = "${p.displayName} Key"
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Key Label / Alias") },
                    placeholder = { Text("e.g. Primary Gemini Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Secret Key") },
                    placeholder = { Text("Paste API key here") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (apiKey.isNotBlank()) {
                                onSave(provider, label.ifBlank { "$provider Key" }, apiKey.trim(), model)
                                onDismiss()
                            }
                        },
                        enabled = apiKey.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = Color.Black)
                    ) {
                        Text(if (initialKey != null) "Update Key" else "Save Key", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
