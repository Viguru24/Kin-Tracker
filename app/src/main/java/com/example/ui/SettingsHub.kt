package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLog
import com.example.data.FamilyMember
import com.example.data.GroupPinMapping
import com.example.ui.settings.*
import com.example.ui.theme.*

enum class SettingsTab(val title: String, val emoji: String) {
    CIRCLE("Circle", "👥"),
    PROFILE("Profile", "👤"),
    PLACES("Places", "🏠"),
    ALERTS("Alerts", "🔔"),
    TOOLS("Tools", "🛠️"),
    SYSTEM("System", "⚙️")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHub(
    myDeviceName: String,
    myDeviceColorHex: String,
    myDeviceEmoji: String,
    myDevicePhone: String,
    onUpdateProfile: (name: String, color: String, emoji: String, phone: String, photoPath: String) -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    activeGroupPinCode: String,
    groupPinMappings: List<GroupPinMapping>,
    members: List<FamilyMember>,
    myDeviceUUID: String,
    activeGroupCreatorId: String,
    onJoinGroupWithPin: (String) -> Unit,
    onCreateGroupWithPin: (String) -> Unit,
    onDeleteGroupPinFromHistory: (GroupPinMapping) -> Unit,
    onKickMember: (String) -> Unit,
    onUpdateActiveGroupSettings: (String, String) -> Unit,
    onSelectActiveCircle: (String) -> Unit,
    onOpenAddDevice: () -> Unit,
    homeLat: Double,
    homeLng: Double,
    homeRadiusMeters: Double,
    onUpdateHomeRadius: (Double) -> Unit,
    onCalibrateHome: () -> Unit,
    workLat: Double,
    workLng: Double,
    isWorkCalibrated: Boolean,
    workRadiusMeters: Double,
    onUpdateWorkRadius: (Double) -> Unit,
    onCalibrateWork: () -> Unit,
    onClearWork: () -> Unit,
    isDepartureAlertsEnabled: Boolean,
    onToggleDepartureAlerts: (Boolean) -> Unit,
    isVoiceAnnouncementsEnabled: Boolean,
    onToggleVoiceAnnouncements: (Boolean) -> Unit,
    notificationVolume: Int = 80,
    onUpdateNotificationVolume: (Int) -> Unit = {},
    onTestNotificationSound: () -> Unit = {},
    proximityAlertDistanceMeters: Int,
    onUpdateProximityAlertDistance: (Int) -> Unit,
    isLocationPaused: Boolean,
    onToggleLocationPaused: (Boolean) -> Unit,
    isCloudSyncEnabled: Boolean,
    groupSyncToken: String,
    cloudStatusText: String,
    onToggleCloudSync: (Boolean) -> Unit,
    activityLogs: List<ActivityLog>,
    onClearLogs: () -> Unit,
    onOpenFeedback: () -> Unit,
    onCleanDuplicates: (() -> Unit)? = null,
    onDeleteMember: ((FamilyMember) -> Unit)? = null,
    onToggleMemberTracking: ((String) -> Unit)? = null,
    onUpdateMember: ((FamilyMember) -> Unit)? = null,
    onOpenGoogleMaps: ((FamilyMember) -> Unit)? = null,
    onSelectTrip: ((com.example.data.CloudCompletedTrip) -> Unit)? = null,
    serverUrl: String = "",
    onUpdateServerUrl: ((String) -> Unit)? = null,
    mapTypeMode: String = "streets",
    onSelectMapType: (String) -> Unit = {},
    onOpenCircleDigest: (() -> Unit)? = null,
    onOpenShoppingList: (() -> Unit)? = null,
    onOpenAddSafeZone: (() -> Unit)? = null,
    shoppingActiveCount: Int = 0,
    onLoadTrips: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.CIRCLE) }
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── CATEGORY PILL SELECTOR ──────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color.Transparent,
            contentColor = RadarCyan,
            edgePadding = 0.dp,
            divider = {},
            indicator = {}
        ) {
            SettingsTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                Surface(
                    onClick = { selectedTab = tab },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) RadarCyan else Color.White,
                    border = BorderStroke(1.dp, if (isSelected) RadarCyan else SlateBorder),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(tab.emoji, fontSize = 13.sp)
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // ── TAB CONTENT WITH SMOOTH CROSSFADE ──────────────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settings_tab_content"
        ) { tab ->
            when (tab) {
                SettingsTab.CIRCLE -> CircleTabContent(
                    pinCode = activeGroupPinCode,
                    members = members,
                    myDeviceUUID = myDeviceUUID,
                    onOpenAddDevice = onOpenAddDevice,
                    onJoinGroupWithPin = onJoinGroupWithPin,
                    onCleanDuplicates = onCleanDuplicates,
                    onDeleteMember = onDeleteMember,
                    isLocationPaused = isLocationPaused,
                    onToggleLocationPaused = onToggleLocationPaused,
                    onToggleMemberTracking = onToggleMemberTracking,
                    onOpenGoogleMaps = onOpenGoogleMaps,
                    onEditMember = { memberToEdit = it }
                )
                SettingsTab.PROFILE -> ProfileTabContent(
                    name = myDeviceName,
                    colorHex = myDeviceColorHex,
                    emoji = myDeviceEmoji,
                    phone = myDevicePhone,
                    onUpdate = onUpdateProfile,
                    ghostModeExpiryTime = ghostModeExpiryTime,
                    onToggleGhostMode = onToggleGhostMode,
                    members = members,
                    myDeviceUUID = myDeviceUUID,
                    onUpdateMember = onUpdateMember,
                    onEditMember = { memberToEdit = it }
                )
                SettingsTab.PLACES -> PlacesTabContent(
                    homeLat = homeLat,
                    homeLng = homeLng,
                    homeRadiusMeters = homeRadiusMeters,
                    onUpdateHomeRadius = onUpdateHomeRadius,
                    onCalibrateHome = onCalibrateHome,
                    workLat = workLat,
                    workLng = workLng,
                    isWorkCalibrated = isWorkCalibrated,
                    workRadiusMeters = workRadiusMeters,
                    onUpdateWorkRadius = onUpdateWorkRadius,
                    onCalibrateWork = onCalibrateWork,
                    onClearWork = onClearWork
                )
                SettingsTab.ALERTS -> AlertsTabContent(
                    isDepartureAlertsEnabled = isDepartureAlertsEnabled,
                    onToggleDepartureAlerts = onToggleDepartureAlerts,
                    isVoiceAnnouncementsEnabled = isVoiceAnnouncementsEnabled,
                    onToggleVoiceAnnouncements = onToggleVoiceAnnouncements,
                    notificationVolume = notificationVolume,
                    onUpdateNotificationVolume = onUpdateNotificationVolume,
                    onTestNotificationSound = onTestNotificationSound,
                    proximityDistanceMeters = proximityAlertDistanceMeters,
                    onUpdateProximityDistance = onUpdateProximityAlertDistance,
                    members = members
                )
                SettingsTab.TOOLS -> ToolsTabContent(
                    mapTypeMode = mapTypeMode,
                    onSelectMapType = onSelectMapType,
                    onOpenCircleDigest = onOpenCircleDigest,
                    onOpenShoppingList = onOpenShoppingList,
                    onOpenAddSafeZone = onOpenAddSafeZone,
                    shoppingActiveCount = shoppingActiveCount
                )
                SettingsTab.SYSTEM -> SystemTabContent(
                    isLocationPaused = isLocationPaused,
                    onToggleLocationPaused = onToggleLocationPaused,
                    cloudStatusText = cloudStatusText,
                    activityLogs = activityLogs,
                    onClearLogs = onClearLogs,
                    onOpenFeedback = onOpenFeedback,
                    onSelectTrip = onSelectTrip,
                    serverUrl = serverUrl,
                    onUpdateServerUrl = onUpdateServerUrl,
                    onLoadTrips = onLoadTrips
                )
            }
        }
    }

    // Modal Profile & Photo Editor Dialog for any circle member (including daughter Isabel and dad)
    memberToEdit?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { memberToEdit = null },
            onSave = { updated ->
                onUpdateMember?.invoke(updated)
                val isMe = updated.id == "me" || updated.id == myDeviceUUID || updated.id.endsWith("_$myDeviceUUID")
                if (isMe) {
                    onUpdateProfile(updated.name, updated.avatarColorHex, updated.avatarEmoji, updated.phoneNumber, updated.photoPath)
                }
                memberToEdit = null
            }
        )
    }
}
