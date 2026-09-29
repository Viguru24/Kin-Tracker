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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val activeTransmitters by RoomAudioStreamManager.activeTransmittingMembers.collectAsState()
    val bytesReceived by RoomAudioStreamManager.bytesReceived.collectAsState()
    val lastError by RoomAudioStreamManager.lastError.collectAsState()
    val diagnosticLog by RoomAudioStreamManager.diagnosticLog.collectAsState()
    val isToneTestActive by RoomAudioStreamManager.isToneTestActive.collectAsState()

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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEFF3FA), CircleShape)
                        .border(1.dp, Color(0xFFDCE2EF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎧", fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Family Circle Audio",
                        color = Color(0xFF1E2430),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Live sovereign audio feed linked via VPS backend",
                        color = Color(0xFF5A6275),
                        fontSize = 11.sp
                    )
                }
                if (statusMessage.isNotBlank() && statusMessage != "Idle" && statusMessage != "Transmitter Stopped" && statusMessage != "Stopped Listening") {
                    Surface(
                        color = Color(0xFFE8F1FC),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Text(
                            text = statusMessage.take(28),
                            color = RadarCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = DividerGray)

            // ─────────────────────────────────────────────────────────────
            // 1. FAMILY MEMBERS AUDIO LIST (e.g. Eloise, Isabel, Annette)
            // ─────────────────────────────────────────────────────────────
            if (otherMembers.isEmpty()) {
                Text(
                    text = "No other family members linked yet.",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    otherMembers.forEach { member ->
                        val isListeningToThisMember = isListening && activeListeningId == member.id
                        val isTransmitting = remember(activeTransmitters, member.id, member.name) {
                            RoomAudioStreamManager.isMemberTransmitting(member.id, member.name)
                        }
                        val memberFirstName = member.name.replace(
                            Regex("\\s*\\((You|Wife|Dad|Mama|Daughter|Older Daughter|Younger Daughter|Sister|Son|Mom|Mother|Father)\\)", RegexOption.IGNORE_CASE),
                            ""
                        ).trim().ifBlank { member.name }

                        val memberIp = RoomAudioStreamManager.getMemberIp(member.id, member.name)

                        // Rich grey styling avoiding pitch black
                        val cardBg = when {
                            isListeningToThisMember -> Color(0xFFF0FDF4)
                            isTransmitting -> Color(0xFFF4F7FC)
                            else -> Color(0xFFF7F8FA)
                        }
                        val cardBorder = when {
                            isListeningToThisMember -> GlowingEmerald
                            isTransmitting -> RadarCyan.copy(alpha = 0.5f)
                            else -> Color(0xFFE5E7EB)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = cardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder)
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
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFFE9ECF2), CircleShape)
                                                .border(1.dp, Color(0xFFD1D6E2), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (member.avatarEmoji.isNotBlank()) member.avatarEmoji else memberFirstName.take(1).uppercase(),
                                                fontSize = 17.sp
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = memberFirstName,
                                                color = Color(0xFF1E2430),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                when {
                                                    isListeningToThisMember -> {
                                                        Box(modifier = Modifier.size(6.dp).background(Color(0xFFE53935), CircleShape))
                                                        Text(
                                                            text = "Live Audio Stream Active",
                                                            color = GlowingEmerald,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    isTransmitting -> {
                                                        Box(modifier = Modifier.size(6.dp).background(GlowingEmerald, CircleShape))
                                                        Text(
                                                            text = "Broadcasting Live" + if (!memberIp.isNullOrBlank()) " • $memberIp" else "",
                                                            color = GlowingEmerald,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    else -> {
                                                        Text(
                                                            text = if (!memberIp.isNullOrBlank()) "Standby • IP: $memberIp" else "Standby (Tap to connect)",
                                                            color = Color(0xFF5A6275),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
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
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isTransmitting) PrimaryCosmic else Color(0xFF334155)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("🎧", fontSize = 11.sp)
                                                Text(
                                                    text = if (isTransmitting) "Listen Live" else "Listen",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
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
                color = if (isTransmitterActive) Color(0xFFF0FDF4) else Color(0xFFF7F8FA),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isTransmitterActive) GlowingEmerald else Color(0xFFE5E7EB))
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(if (isTransmitterActive) Color(0xFFD1FAE5) else Color(0xFFE9ECF2), CircleShape)
                                .border(1.dp, if (isTransmitterActive) Color(0xFFA7F3D0) else Color(0xFFD1D6E2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isTransmitterActive) "🎙️" else "🔕", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "Broadcast My Audio to Family",
                                color = Color(0xFF1E2430),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTransmitterActive) "🟢 Active — Broadcasting to VPS & Family Circle" else "Enable if leaving this phone to be monitored",
                                color = if (isTransmitterActive) GlowingEmerald else Color(0xFF5A6275),
                                fontSize = 11.sp
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
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. AUDIO DIAGNOSTICS PANEL
            // ─────────────────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF0F4FF),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFCDD6F4))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔬 Audio Diagnostics",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E2430)
                        )
                        if (isListening) {
                            Text(
                                text = "${bytesReceived / 1024} KB received",
                                fontSize = 11.sp,
                                color = if (bytesReceived > 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Test tone button — isolates speaker vs network issues
                    Button(
                        onClick = { RoomAudioStreamManager.playTestTone(context) },
                        enabled = !isToneTestActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isToneTestActive) Color(0xFF6B7280) else Color(0xFF4F46E5)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isToneTestActive) "⏳" else "🔊", fontSize = 14.sp)
                            Column {
                                Text(
                                    text = if (isToneTestActive) "Playing 440 Hz tone..." else "Test Speaker (440 Hz beep)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isToneTestActive) "Listen — if silent, routing is broken" else "Tap to confirm speaker works independently",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Last error
                    if (lastError.isNotBlank()) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = "⚠️ $lastError",
                                fontSize = 10.sp,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Live log
                    if (diagnosticLog.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                diagnosticLog.forEach { line ->
                                    Text(
                                        text = line,
                                        fontSize = 9.sp,
                                        color = when {
                                            line.contains("ERROR") || line.contains("error") -> Color(0xFFFC8181)
                                            line.contains("✓") || line.contains("GRANTED") -> Color(0xFF86EFAC)
                                            line.contains("TEST TONE") -> Color(0xFFFBBF24)
                                            else -> Color(0xFF94A3B8)
                                        },
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sovereign VPS indicator footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("🔒", fontSize = 10.sp)
                Text(
                    text = "VPS Audio Bridge: api.cosmowhisper.com • Port 18884 Sovereign P2P",
                    color = Color(0xFF6B7280),
                    fontSize = 10.sp
                )
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
            .background(Color(0xFFEBF0F6), RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, Color(0xFFD5DFEC)), RoundedCornerShape(10.dp))
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
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (decibels > 65f) ActiveAmber else GlowingEmerald, CircleShape)
                )
                Text(
                    text = if (decibels > 70f) "Loud Noise / Activity" else if (decibels > 45f) "Rustling / Quiet Sound" else "Quiet 😴",
                    color = Color(0xFF1E2430),
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
