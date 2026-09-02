package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.FamilyMember
import com.example.data.RoomAudioStreamManager
import com.example.ui.theme.*

@Composable
fun RoomAudioMonitorControls(
    members: List<FamilyMember> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val isTransmitterActive by RoomAudioStreamManager.isTransmitterActive.collectAsState()
    val isListening by RoomAudioStreamManager.isListening.collectAsState()
    val activeListeningId by RoomAudioStreamManager.activeListeningMemberId.collectAsState()
    val decibels by RoomAudioStreamManager.currentDecibels.collectAsState()
    val statusMessage by RoomAudioStreamManager.statusMessage.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            RoomAudioStreamManager.startTransmitter()
        }
    }

    val otherMembers = remember(members) {
        members.filter { it.id != "me" }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🎧", fontSize = 20.sp)
                Column {
                    Text(
                        text = "Family Circle Audio",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "One-tap live audio for members in your family group",
                        color = SecondarySlate,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = DividerGray)

            // ─────────────────────────────────────────────────────────────
            // 1. FAMILY MEMBERS AUDIO LIST (e.g. Eloise, Isabel, Annette)
            // ─────────────────────────────────────────────────────────────
            if (otherMembers.isEmpty()) {
                Text(
                    text = "No other family members linked yet.",
                    color = SecondarySlate,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    otherMembers.forEach { member ->
                        val isListeningToThisMember = isListening && activeListeningId == member.id
                        val memberFirstName = member.name.replace(
                            Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE),
                            ""
                        ).trim().ifBlank { member.name }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isListeningToThisMember) Color(0xFF22172E) else Color(0xFF151822),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isListeningToThisMember) PrimaryCosmic.copy(alpha = 0.8f) else SlateBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(Color(0xFF202534), CircleShape)
                                                .border(1.dp, SlateBorder, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else memberFirstName.take(1).uppercase(),
                                                fontSize = 16.sp
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = memberFirstName,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isListeningToThisMember) "🔴 Live Audio Stream Active" else "Tap to listen in live",
                                                color = if (isListeningToThisMember) GlowingEmerald else SecondarySlate,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    if (isListeningToThisMember) {
                                        Button(
                                            onClick = { RoomAudioStreamManager.stopListening() },
                                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("⏹️", fontSize = 11.sp)
                                                Text("Stop", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                RoomAudioStreamManager.startListeningToMember(context, member.id, member.name)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("🎧", fontSize = 11.sp)
                                                Text("Listen", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                if (isListeningToThisMember) {
                                    AudioDecibelWaveMeter(decibels = decibels, name = memberFirstName)
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. BROADCAST MY AUDIO (Leave this phone transmitting)
            // ─────────────────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isTransmitterActive) Color(0xFF102820) else Color(0xFF151822),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isTransmitterActive) GlowingEmerald.copy(alpha = 0.5f) else SlateBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isTransmitterActive) "🎙️" else "🔕", fontSize = 16.sp)
                        Column {
                            Text(
                                text = "Broadcast My Audio to Family",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTransmitterActive) "🟢 Active — Family circle can listen in" else "Enable if leaving this phone to be monitored",
                                color = if (isTransmitterActive) GlowingEmerald else SecondarySlate,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = isTransmitterActive,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    RoomAudioStreamManager.startTransmitter()
                                }
                            } else {
                                RoomAudioStreamManager.stopTransmitter()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlowingEmerald,
                            uncheckedThumbColor = SecondarySlate,
                            uncheckedTrackColor = CosmicBlack
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioDecibelWaveMeter(decibels: Float, name: String = "") {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_anim"
    )

    val normalizedDb = (decibels / 100f).coerceIn(0.05f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F121C), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (decibels > 65f) ActiveAmber else GlowingEmerald, CircleShape)
                )
                Text(
                    text = if (decibels > 70f) "Loud Noise / Activity" else if (decibels > 45f) "Rustling / Quiet Sound" else "Quiet 😴",
                    color = if (decibels > 70f) ActiveAmber else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "${decibels.toInt()} dB",
                color = RadarCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Animated sound wave equalizer bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 18) {
                val heightFactor = ((kotlin.math.sin(i * 0.45 + decibels * 0.1) + 1.0) / 2.0).toFloat()
                val barHeight = (16f * normalizedDb * heightFactor * pulse).coerceIn(2f, 16f)
                val barColor = when {
                    decibels > 70f -> ErrorRed
                    decibels > 50f -> ActiveAmber
                    else -> GlowingEmerald
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                        .background(barColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}
