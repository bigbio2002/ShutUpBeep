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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val toneEnabled by viewModel.toneEnabled.collectAsStateWithLifecycle()
    val speechProbability by viewModel.speechProbability.collectAsStateWithLifecycle()
    val microphoneLevel by viewModel.microphoneLevel.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

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

            // Center Status Message & Live Indicator
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
                            text = "Human Speech Detected",
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
                    AppStatus.STARTING -> {
                        Text(
                            text = "STARTING DETECTOR...",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E0E0),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Loading Silero and opening the microphone",
                            fontSize = 15.sp,
                            color = Color(0xFF9E9E9E),
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
                            text = "Microphone access is needed for live speech detection",
                            fontSize = 14.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                        )
                    }
                    AppStatus.ERROR -> {
                        Text(
                            text = "DETECTION ERROR",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5252),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = errorMessage.ifBlank {
                                "Silero VAD could not start. Check microphone access and try again."
                            },
                            fontSize = 14.sp,
                            color = Color(0xFFB0BEC5),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if ((status == AppStatus.LISTENING) || (status == AppStatus.SPEECH_DETECTED)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSpeechActive) Color(0x33000000) else Color(0xFF262626))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Speech Confidence",
                                fontSize = 12.sp,
                                color = if (isSpeechActive) Color.White else Color(0xFFB0BEC5),
                            )
                            Text(
                                text = String.format("%.3f", speechProbability),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSpeechActive) Color.White else Color(0xFF00E676),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { speechProbability.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isSpeechActive) Color.White else Color(0xFF00E676),
                            trackColor = Color(0xFF424242),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Mic level",
                                fontSize = 12.sp,
                                color = if (isSpeechActive) Color.White else Color(0xFFB0BEC5),
                            )
                            Text(
                                text = String.format("%.4f", microphoneLevel),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (microphoneLevel > 0.001f) Color(0xFF00E676) else Color(0xFFFF5252),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (microphoneLevel * 8f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (microphoneLevel > 0.001f) Color(0xFF00E676) else Color(0xFFFF5252),
                            trackColor = Color(0xFF424242),
                        )
                    }
                }
            }

            // Bottom Controls & Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
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
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Silero VAD with adaptive gain for soft speech",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE0E0E0),
                            )
                            Text(
                                text = "Keeps listening in the background. Use the ongoing notification to stop detection.",
                                fontSize = 11.sp,
                                color = Color(0xFF757575),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Button
                if ((status == AppStatus.IDLE) || (status == AppStatus.PERMISSION_DENIED) || (status == AppStatus.ERROR)) {
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
                            text = when (status) {
                                AppStatus.PERMISSION_DENIED -> "Grant Permission"
                                AppStatus.ERROR -> "RETRY DETECTION"
                                else -> "START LISTENING"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { viewModel.toggleTone() },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (toneEnabled) Color(0xFF388E3C) else Color(0xFF424242),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                        ) {
                            Text(
                                text = if (toneEnabled) "TONE ON" else "TONE OFF",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Button(
                            onClick = { viewModel.stopListening() },
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeechActive) Color.White else Color(0xFFD32F2F),
                                contentColor = if (isSpeechActive) Color(0xFFD50000) else Color.White,
                            ),
                            modifier = Modifier
                                .weight(1f)
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
}
