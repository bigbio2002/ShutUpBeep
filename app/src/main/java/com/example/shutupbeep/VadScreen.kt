package com.example.shutupbeep

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun VadScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VadViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val threshold by viewModel.threshold.collectAsStateWithLifecycle()

    val isSpeechActive = status == AppStatus.SPEECH_DETECTED

    // Plain background when idle/listening, turns angry red when tone/speech is activated
    val backgroundColor = if (isSpeechActive) {
        Color(0xFFD50000) // Angry vivid red
    } else {
        Color(0xFF141414) // Plain dark matte background
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Text(
                text = "ShutUpBeep",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSpeechActive) Color.White else Color(0xFF9E9E9E),
                modifier = Modifier.padding(top = 16.dp),
            )

            // Center Status Message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (status) {
                    AppStatus.SPEECH_DETECTED -> {
                        Text(
                            text = "SHUT UP!",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Speech Detected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFFCDD2),
                        )
                    }
                    AppStatus.LISTENING -> {
                        Text(
                            text = "LISTENING...",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Speak into the microphone to trigger tone",
                            fontSize = 15.sp,
                            color = Color(0xFF9E9E9E),
                            textAlign = TextAlign.Center,
                        )
                    }
                    AppStatus.IDLE -> {
                        Text(
                            text = "STANDBY",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFBDBDBD),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Press START to begin detecting speech",
                            fontSize = 15.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center,
                        )
                    }
                    AppStatus.PERMISSION_DENIED -> {
                        Text(
                            text = "PERMISSION REQUIRED",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Microphone access is needed for Silero VAD",
                            fontSize = 14.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Bottom Controls & Settings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                // Sensitivity Card (Speech Threshold)
                if (!isSpeechActive) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF212121),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Detection Threshold",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE0E0E0),
                                )
                                Text(
                                    text = String.format("%.2f", threshold),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64B5F6),
                                )
                            }
                            Slider(
                                value = threshold,
                                onValueChange = { viewModel.setThreshold(it) },
                                valueRange = 0.1f..0.9f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF64B5F6),
                                    activeTrackColor = Color(0xFF1976D2),
                                    inactiveTrackColor = Color(0xFF424242),
                                ),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "More sensitive (0.1)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF757575),
                                )
                                Text(
                                    text = "Less sensitive (0.9)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF757575),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Button
                if (status == AppStatus.IDLE || status == AppStatus.PERMISSION_DENIED) {
                    Button(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2979FF),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text = if (status == AppStatus.PERMISSION_DENIED) "Grant Permission" else "START LISTENING",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopListening() },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpeechActive) Color.White else Color(0xFFD32F2F),
                            contentColor = if (isSpeechActive) Color(0xFFD50000) else Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            text = "STOP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
