package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*

@Composable
fun TelemetryDashboard(
    members: List<FamilyMember>,
    selectedMemberId: String?,
    onSelectMember: (String?) -> Unit,
    onCommuteHome: (String) -> Unit,
    onSendAway: (String, String) -> Unit,
    onInstantCheckIn: (String) -> Unit,
    onPing: (String) -> Unit,
    onUpdateMember: (FamilyMember) -> Unit,
    onDeleteMember: (String) -> Unit,
    onTriggerAlarm: (String) -> Unit = {},
    onOpenWhatsApp: (FamilyMember) -> Unit = {},
    onTriggerSOS: () -> Unit = {},
    onSendReaction: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    homeLat: Double = 51.332308,
    homeLng: Double = -0.117188
) {
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMember?>(null) }

    // Edit Dialog
    memberToEdit?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { memberToEdit = null },
            onSave = onUpdateMember
        )
    }

    // Delete Confirmation Dialog
    memberToDelete?.let { member ->
        MemberDeleteDialog(
            member = member,
            onDismiss = { memberToDelete = null },
            onConfirm = {
                onDeleteMember(member.id)
                memberToDelete = null
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Family Members (${members.size})",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            if (selectedMemberId != null) {
                TextButton(
                    onClick = { onSelectMember(null) },
                    colors = ButtonDefaults.textButtonColors(contentColor = RadarCyan),
                    modifier = Modifier.testTag("clear_selection_btn")
                ) {
                    Text("Clear Selection", fontSize = 11.sp)
                }
            }
        }

        if (members.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RadarCyan)
            }
        } else {
            members.forEach { member ->
                MemberCard(
                    member = member,
                    isSelected = member.id == selectedMemberId,
                    homeLat = homeLat,
                    homeLng = homeLng,
                    onSelectMember = onSelectMember,
                    onCommuteHome = onCommuteHome,
                    onSendAway = onSendAway,
                    onInstantCheckIn = onInstantCheckIn,
                    onPing = onPing,
                    onEditMember = { memberToEdit = it },
                    onDeleteMember = { memberToDelete = it },
                    onTriggerAlarm = onTriggerAlarm,
                    onOpenWhatsApp = onOpenWhatsApp,
                    onTriggerSOS = onTriggerSOS,
                    onSendReaction = onSendReaction
                )
            }
        }
    }
}
