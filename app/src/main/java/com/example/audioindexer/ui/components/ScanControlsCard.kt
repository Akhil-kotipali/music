package com.example.audioindexer.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioindexer.service.ScanProgress
import com.example.audioindexer.service.ServiceStatus
import com.example.audioindexer.service.ServiceStatusState
import com.example.audioindexer.ui.theme.AccentGreen
import com.example.audioindexer.ui.theme.DarkSurfaceBorder
import com.example.audioindexer.ui.theme.PrimaryCyan
import com.example.audioindexer.ui.theme.SecondaryViolet
import com.example.audioindexer.ui.theme.TertiaryAmber

@Composable
fun ScanControlsCard(
    serviceStatus: ServiceStatus,
    scanProgress: ScanProgress,
    isObserverActive: Boolean,
    onStartFullScan: () -> Unit,
    onStartDeltaCheck: () -> Unit,
    onToggleObserver: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBusy = serviceStatus.state == ServiceStatusState.SCANNING ||
            serviceStatus.state == ServiceStatusState.DELTA_CHECKING

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (serviceStatus.state) {
                                    ServiceStatusState.SCANNING -> PrimaryCyan
                                    ServiceStatusState.DELTA_CHECKING -> TertiaryAmber
                                    ServiceStatusState.ERROR -> Color.Red
                                    ServiceStatusState.IDLE -> if (isObserverActive) AccentGreen else Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (serviceStatus.state) {
                            ServiceStatusState.SCANNING -> "Scanning Storage..."
                            ServiceStatusState.DELTA_CHECKING -> "Running Delta Check..."
                            ServiceStatusState.ERROR -> "Service Error"
                            ServiceStatusState.IDLE -> if (isObserverActive) "File Observer Active" else "Scanner Idle"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle status message
            Text(
                text = if (scanProgress.message.isNotBlank()) scanProgress.message else serviceStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress Bar if scanning
            AnimatedVisibility(visible = isBusy) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (serviceStatus.state == ServiceStatusState.SCANNING) PrimaryCyan else TertiaryAmber,
                        trackColor = DarkSurfaceBorder
                    )
                    if (scanProgress.currentDirectory.isNotBlank()) {
                        Text(
                            text = scanProgress.currentDirectory,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Buttons: Full Scan & Delta Check
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartFullScan,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryCyan,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Scan", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onStartDeltaCheck,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(TertiaryAmber)
                    )
                ) {
                    Icon(Icons.Default.ChangeCircle, contentDescription = null, tint = TertiaryAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delta Check", color = TertiaryAmber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // FileObserver toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceBorder.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isObserverActive) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Real-time FileObserver",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isObserverActive)
                                "Monitoring ${serviceStatus.watchedDirectoryCount} audio folders"
                            else
                                "Staging new tracks on arrival",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isObserverActive,
                    onCheckedChange = onToggleObserver,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentGreen,
                        checkedTrackColor = AccentGreen.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
