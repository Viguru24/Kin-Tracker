package com.example.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.GroupPinMapping

@Composable
fun CircleSwitcherOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    groupPinMappings: List<GroupPinMapping>,
    activeGroupPinCode: String,
    memberCount: Int = 0,
    onSwitchCircle: (String) -> Unit,
    onOpenAddDeviceTab: (Int) -> Unit
) {
    if (!visible) return

    val activeMapping = groupPinMappings.firstOrNull { it.pinCode == activeGroupPinCode }
        ?: groupPinMappings.firstOrNull { it.isActive }
    val resolvedPin = activeGroupPinCode.ifBlank { activeMapping?.pinCode.orEmpty() }
    val isInCircle = resolvedPin.isNotBlank()
    val resolvedName = when {
        activeMapping != null && activeMapping.groupName.isNotBlank() -> activeMapping.groupName
        resolvedPin == "4666" -> "Family Circle"
        isInCircle -> "Circle $resolvedPin"
        else -> "No Active Circle"
    }
    val otherCircles = groupPinMappings.filter { it.pinCode != resolvedPin }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() }
            .zIndex(200f)
    ) {
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 54.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f)
                .widthIn(max = 340.dp)
                .heightIn(max = 520.dp)
                .clickable(enabled = false) {},
            color = Color(0xFF121215),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF27272A)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── CURRENT CIRCLE HERO CARD (Always visible) ──
                Text(
                    text = "CURRENT CIRCLE",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(
                        1.2.dp,
                        if (isInCircle) Color(0xFF00E5FF) else Color(0xFF3F3F46)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isInCircle) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                                Text(
                                    text = resolvedName,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            if (isInCircle) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "PIN: $resolvedPin",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "• $memberCount ${if (memberCount == 1) "member" else "members"}",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text(
                                    text = "Join a circle below or create one",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isInCircle) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // ── OTHER CIRCLES (Switchable) ──
                if (otherCircles.isNotEmpty()) {
                    Text(
                        text = "SWITCH CIRCLE",
                        color = Color(0xFFA1A1AA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                    )
                    otherCircles.forEach { circle ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchCircle(circle.pinCode)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF18181B),
                            border = BorderStroke(1.dp, Color(0xFF27272A))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = circle.groupName.ifBlank { "Circle ${circle.pinCode}" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "PIN: ${circle.pinCode}",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "Switch →",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF27272A), modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "CIRCLE ACTIONS",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                // 👥 Join a Circle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenAddDeviceTab(0)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👥", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Join a Circle",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Enter 4-digit PIN or 6-char code",
                                color = Color(0xFF90A4AE),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ➕ Invite to This Circle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenAddDeviceTab(1)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("➕", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invite to This Circle",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isInCircle) "Share PIN $resolvedPin" else "Join or create a circle first",
                                color = if (isInCircle) Color(0xFF10B981) else Color(0xFFA1A1AA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 🗑️ Delete from Circle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenAddDeviceTab(2)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF18181B),
                    border = BorderStroke(1.dp, Color(0xFF27272A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑️", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Delete from Circle",
                                color = Color(0xFFFF5252),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Remove devices or members",
                                color = Color(0xFF90A4AE),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
