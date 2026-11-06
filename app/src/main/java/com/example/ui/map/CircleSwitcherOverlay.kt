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
    onSwitchCircle: (String) -> Unit,
    onOpenAddDeviceTab: (Int) -> Unit
) {
    if (!visible) return

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
            color = Color(0xFF1E232F),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, Color(0xFF384358)),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (groupPinMappings.isNotEmpty()) {
                    groupPinMappings.forEach { circle ->
                        val isActive = circle.pinCode == activeGroupPinCode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchCircle(circle.pinCode)
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) Color(0xFF2A3447) else Color(0xFF161922),
                            border = BorderStroke(1.dp, if (isActive) Color(0xFF00E5FF) else Color(0xFF2B3342))
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
                                        text = circle.groupName.ifBlank { "Family Circle" },
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "PIN: ${circle.pinCode}",
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF00E5FF).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFF323B4D), modifier = Modifier.padding(vertical = 2.dp))
                }

                // 👥 Join a Circle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenAddDeviceTab(0)
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF161922),
                    border = BorderStroke(1.dp, Color(0xFF2D3545))
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
                    color = Color(0xFF161922),
                    border = BorderStroke(1.dp, Color(0xFF2D3545))
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
                                text = "Share PIN ${activeGroupPinCode.ifBlank { "4666" }}",
                                color = Color(0xFF00E676),
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
                    color = Color(0xFF161922),
                    border = BorderStroke(1.dp, Color(0xFF2D3545))
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
