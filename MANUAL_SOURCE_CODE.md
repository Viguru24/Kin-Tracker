# Group PIN & Kick Feature Source Code modifications

This guide contains the exact copy-pasteable contents of every single modified and created file for the 4-digit PIN system and user WhatsApp phone number updates. 

All of these files have been modified physically in your `kin-tracker` repository, but if your Android Studio VFS or OneDrive sync engine is caching old copies, you can copy-paste directly from this document.

---

## 1. [OnboardingScreen.kt](file:///c:/Users/louis/Documents/GitHub/kin-tracker/app/src/main/java/com/example/ui/OnboardingScreen.kt)
**Path:** `app/src/main/java/com/example/ui/OnboardingScreen.kt`

```kotlin
package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun OnboardingScreen(
    viewModel: FamilyViewModel,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var myName      by remember { mutableStateOf("") }
    var myEmoji     by remember { mutableStateOf("👤") }
    var myColor     by remember { mutableStateOf("#AA22FF") }
    var joinToken   by remember { mutableStateOf("") }
    var chosenFlow  by remember { mutableStateOf("") } // "create" | "join"

    val generatedKey by viewModel.groupSyncToken.collectAsState()
    val activeGroupPinCode by viewModel.activeGroupPinCode.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ), label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0D1A), Color(0xFF111128), Color(0xFF0A0A16))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            },
            label = "onboarding_step"
        ) { currentStep ->
            when (currentStep) {
                0 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(Modifier.height(32.dp))
                    Box(
                        modifier = Modifier
                            .size((80 * pulseScale).dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFF4F46E5), Color.Transparent)
                                ),
                                CircleShape
                            )
                            .border(2.dp, Color(0xFF7C3AED).copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📍", fontSize = 36.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Welcome to Pulse Tracker",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Real-time family location sharing.\nNo subscriptions. No tracking servers.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }

                    OutlinedTextField(
                        value = myName,
                        onValueChange = { myName = it },
                        label = { Text("What's your name?", color = SecondarySlate) },
                        placeholder = { Text("e.g. Mum, Dad, Louis…", color = SecondarySlate.copy(alpha = 0.5f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_field"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = SlateBorder,
                            cursorColor = Color(0xFF7C3AED),
                            focusedContainerColor = Color.White.copy(alpha = 0.04f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                        )
                    )

                    Text("Choose your icon", color = SecondarySlate, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    val emojiOptions = listOf("👨","👩","👦","👧","👴","👵","👨‍💻","👩‍💻","🧑","🧒","👶","🐱")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        emojiOptions.forEach { emo ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (myEmoji == emo) Color(0xFF7C3AED).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
                                    .border(if (myEmoji == emo) 2.dp else 1.dp, if (myEmoji == emo) Color(0xFF7C3AED) else SlateBorder, CircleShape)
                                    .clickable { myEmoji = emo },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emo, fontSize = 18.sp)
                            }
                        }
                    }

                    Text("Pick your map colour", color = SecondarySlate, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                    val colourOptions = listOf("#AA22FF", "#EC407A", "#26A69A", "#42A5F5", "#FF9800", "#00FF87")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        colourOptions.forEach { hex ->
                            val col = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(if (myColor == hex) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { myColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (myColor == hex) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val finalName = myName.trim().ifBlank { "My Device" }
                            viewModel.signInUser(finalName, "")
                            viewModel.myDeviceName.value = finalName
                            viewModel.myDeviceEmoji.value = myEmoji
                            viewModel.myDeviceColor.value = myColor
                            step = 1
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_next_btn")
                    ) {
                        Text("Continue →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                1 -> Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(Modifier.height(48.dp))
                    Text("👨‍👩‍👧‍👦", fontSize = 56.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Set up your\nFamily Circle", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 34.sp)
                        Spacer(Modifier.height(10.dp))
                        Text("Everyone in your circle shares live locations with each other using a unique Family Key.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosenFlow = "create"
                                viewModel.createGroupWithPin("Family Circle")
                                step = 2
                            }
                            .testTag("onboarding_create_btn"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF7C3AED))
                    ) {
                        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✨", fontSize = 32.sp)
                            Column {
                                Text("Create a Family Circle", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("I'm the first one — I'll invite everyone else", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosenFlow = "join"
                                step = 2
                            }
                            .testTag("onboarding_join_btn"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF26A69A).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF26A69A))
                    ) {
                        Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔗", fontSize = 32.sp)
                            Column {
                                Text("Join a Family Circle", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("I have a Family Key from someone in my circle", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                    TextButton(onClick = { step = 0 }) { Text("← Back", color = SecondarySlate, fontSize = 12.sp) }
                }

                2 -> if (chosenFlow == "create") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text("🎉", fontSize = 56.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Your Family Circle\nis ready!", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Share this 4-digit PIN with everyone you want in your circle. They'll tap \"Join a Family Circle\" and enter it to join instantly.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your 4-Digit PIN Code", color = SecondarySlate, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(activeGroupPinCode.ifBlank { "----" }, color = Color(0xFFB39DDB), fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                            }
                        }

                        Button(
                            onClick = {
                                try {
                                    clipboard.setText(AnnotatedString(activeGroupPinCode))
                                    val inviteText = "Hey! I've set up Pulse Tracker so we can see each other on a live map. Download the app, tap \"Join a Family Circle\" and enter this 4-digit PIN:\n\n$activeGroupPinCode"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://api.whatsapp.com/send?text=" + android.net.Uri.encode(inviteText)))
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    clipboard.setText(AnnotatedString(activeGroupPinCode))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("💬", fontSize = 16.sp)
                                Text("Share PIN via WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(activeGroupPinCode)) },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("📋  Copy PIN Only", fontSize = 13.sp, color = SecondarySlate)
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.completeOnboarding()
                                onComplete()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_open_map_btn")
                        ) {
                            Text("Open My Map →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        TextButton(onClick = { step = 1 }) { Text("← Back", color = SecondarySlate, fontSize = 12.sp) }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text("👥", fontSize = 56.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Join Your\nFamily Circle", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 32.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Ask the person who created the circle to send you their 4-digit PIN, then enter it below.", color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }

                        OutlinedTextField(
                            value = joinToken,
                            onValueChange = { input ->
                                if (input.length <= 4 && input.all { it.isDigit() }) {
                                    joinToken = input
                                }
                            },
                            label = { Text("4-Digit PIN", color = SecondarySlate) },
                            placeholder = { Text("e.g. 1234", color = SecondarySlate.copy(alpha = 0.5f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_join_key_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF26A69A),
                                unfocusedBorderColor = SlateBorder,
                                cursorColor = Color(0xFF26A69A),
                                focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                            )
                        )

                        TextButton(
                            onClick = {
                                val fromClip = clipboard.getText()?.text ?: ""
                                val digitsOnly = fromClip.filter { it.isDigit() }.take(4)
                                if (digitsOnly.isNotBlank()) joinToken = digitsOnly
                            }
                        ) {
                            Text("📋  Paste PIN from clipboard", color = RadarCyan, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (joinToken.length == 4) {
                                    viewModel.joinGroupWithPin(joinToken) { success, _ ->
                                        if (success) {
                                            viewModel.completeOnboarding()
                                            onComplete()
                                        }
                                    }
                                }
                            },
                            enabled = joinToken.length == 4,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26A69A), disabledContainerColor = SlateBorder),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_join_submit_btn")
                        ) {
                            Text("Join Circle & Open Map →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        TextButton(onClick = { step = 1 }) { Text("← Back", color = SecondarySlate, fontSize = 12.sp) }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0, 1, 2).forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (step == i) 22.dp else 7.dp, 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (step >= i) Color(0xFF7C3AED) else Color.White.copy(alpha = 0.2f))
                )
            }
        }
    }
}
```

---

## 2. [CloudSyncControls.kt](file:///c:/Users/louis/Documents/GitHub/kin-tracker/app/src/main/java/com/example/ui/CloudSyncControls.kt)
**Path:** `app/src/main/java/com/example/ui/CloudSyncControls.kt`

```kotlin
package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncControls(
    isCloudSyncEnabled: Boolean,
    groupSyncToken: String,
    myDeviceName: String,
    myDeviceColorHex: String,
    myDeviceEmoji: String,
    myDevicePhone: String = "",
    cloudStatusText: String,
    onToggleCloudSync: (enabled: Boolean, token: String, myName: String, myColor: String, myEmoji: String, myPhone: String) -> Unit,
    onGenerateGroupKey: () -> Unit,
    isUserSignedIn: Boolean,
    userDisplayName: String,
    userEmail: String,
    onSignIn: (name: String, email: String) -> Unit,
    onSignOut: () -> Unit,
    ghostModeExpiryTime: Long,
    onToggleGhostMode: (Boolean) -> Unit,
    groupPinMappings: List<com.example.data.GroupPinMapping> = emptyList(),
    activeGroupPinCode: String = "",
    onCreateGroupWithPin: (String) -> Unit = {},
    onJoinGroupWithPin: (String) -> Unit = {},
    onDeleteGroupPinFromHistory: (com.example.data.GroupPinMapping) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tokenInput by remember { mutableStateOf(groupSyncToken) }
    var nameInput by remember { mutableStateOf(myDeviceName) }
    var phoneInput by remember { mutableStateOf(myDevicePhone) }
    var selectedColorHex by remember { mutableStateOf(myDeviceColorHex) }
    var selectedEmoji by remember(myDeviceEmoji) { mutableStateOf(myDeviceEmoji) }
    var expandedSetup by remember { mutableStateOf(false) }

    val isGhostMode = System.currentTimeMillis() < ghostModeExpiryTime
    var timeLeftString by remember { mutableStateOf("") }
    LaunchedEffect(ghostModeExpiryTime) {
        while (System.currentTimeMillis() < ghostModeExpiryTime) {
            val diffMs = ghostModeExpiryTime - System.currentTimeMillis()
            val hours = diffMs / 3600000
            val minutes = (diffMs % 3600000) / 60000
            val seconds = (diffMs % 60000) / 1000
            timeLeftString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            kotlinx.coroutines.delay(1000L)
        }
        timeLeftString = ""
    }

    var authNameInput by remember { mutableStateOf("") }
    var authEmailInput by remember { mutableStateOf("") }

    var pinToJoinInput by remember { mutableStateOf("") }
    var groupToCreateNameInput by remember { mutableStateOf("") }
    var activeTabCreateGroup by remember { mutableStateOf(false) }

    LaunchedEffect(groupSyncToken) {
        if (groupSyncToken.isNotBlank() && tokenInput != groupSyncToken) {
            tokenInput = groupSyncToken
        }
    }

    val clipboardManager = LocalClipboardManager.current

    val colorsList = listOf(
        "#AA22FF",
        "#EC407A",
        "#26A69A",
        "#42A5F5",
        "#FF9800",
        "#00FF87"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cloud_sync_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(
            1.dp,
            if (isGhostMode) ActiveAmber.copy(alpha = 0.6f) else GlowingEmerald.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (isUserSignedIn) {
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
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(selectedColorHex))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = selectedEmoji, fontSize = 18.sp)
                            }
                            Column {
                                Text(
                                    text = userDisplayName,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = userEmail,
                                    color = SecondarySlate,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GlowingEmerald, CircleShape)
                            )
                            Text(
                                text = "LINKED PERMANENTLY",
                                color = GlowingEmerald,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSignIn(authNameInput, authEmailInput) },
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OFFLINE - TAP TO SET UP YOUR ACCOUNT",
                            color = ActiveAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

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
                            .size(10.dp)
                            .background(
                                if (isGhostMode) ActiveAmber else GlowingEmerald,
                                CircleShape
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                    Column {
                        Text(
                            text = "MAP SHARING WITH FAMILY DEVICES",
                            color = if (isGhostMode) ActiveAmber else GlowingEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isGhostMode) "ONLINE • SHARING GHOSTED" else "ONLINE • SHARING ACTIVE",
                            color = if (isGhostMode) ActiveAmber else RadarCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Button(
                    onClick = { expandedSetup = !expandedSetup },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (expandedSetup) "Hide Setup" else "Setup Details",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            if (!expandedSetup) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Your Family Sharing Key is active. Other family members can see your live location on their map.",
                            color = SecondarySlate,
                            fontSize = 10.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (activeGroupPinCode.isNotBlank()) "Active PIN Code: $activeGroupPinCode" else "Stable sharing key: ${groupSyncToken.take(15)}...",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy code",
                                tint = RadarCyan,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(if (activeGroupPinCode.isNotBlank()) activeGroupPinCode else groupSyncToken))
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ghost Mode (Pause Location)",
                        color = if (isGhostMode) ActiveAmber else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isGhostMode) "Deactivates in $timeLeftString" else "Temporarily override location to 0.0 for 8 hours",
                        color = if (isGhostMode) ActiveAmber else SecondarySlate,
                        fontSize = 9.sp
                    )
                }
                Switch(
                    checked = isGhostMode,
                    onCheckedChange = { checked ->
                        onToggleGhostMode(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ActiveAmber,
                        uncheckedThumbColor = SecondarySlate,
                        uncheckedTrackColor = SlateBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = expandedSetup) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!activeTabCreateGroup) PrimaryCosmic else Color.Transparent)
                                .clickable { activeTabCreateGroup = false }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Join with PIN 👥",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTabCreateGroup) PrimaryCosmic else Color.Transparent)
                                .clickable { activeTabCreateGroup = true }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Group with PIN 👑",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!activeTabCreateGroup) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Enter a 4-digit PIN generated by a family creator to link to their map permanently.",
                                color = SecondarySlate,
                                fontSize = 10.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pinToJoinInput,
                                    onValueChange = { if (it.length <= 4) pinToJoinInput = it },
                                    label = { Text("4-digit PIN") },
                                    placeholder = { Text("e.g. 5729") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (pinToJoinInput.length == 4) {
                                            onJoinGroupWithPin(pinToJoinInput)
                                            pinToJoinInput = ""
                                        }
                                    },
                                    enabled = pinToJoinInput.length == 4,
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Join", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Instantiate a new private circle! A unique 4-digit PIN is generated instantly, linking other devices seamlessly.",
                                color = SecondarySlate,
                                fontSize = 10.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = groupToCreateNameInput,
                                    onValueChange = { groupToCreateNameInput = it },
                                    label = { Text("Group Name") },
                                    placeholder = { Text("e.g. Home Team") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        focusedBorderColor = RadarCyan,
                                        unfocusedBorderColor = SlateBorder
                                    ),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val name = groupToCreateNameInput.trim().ifBlank { "Family Group" }
                                        onCreateGroupWithPin(name)
                                        groupToCreateNameInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Create", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                    if (groupPinMappings.isNotEmpty()) {
                        Text(
                            text = "My PIN Group Registry Database",
                            fontSize = 11.sp,
                            color = SecondarySlate,
                            fontWeight = FontWeight.Bold
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, SlateBorder.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                            ) {
                                groupPinMappings.forEach { mapping ->
                                    val isActive = groupSyncToken == mapping.groupToken
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isActive) Color.White.copy(alpha = 0.05f) else Color.Transparent)
                                            .clickable {
                                                onToggleCloudSync(true, mapping.groupToken, nameInput, selectedColorHex, selectedEmoji, phoneInput)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (mapping.isOwner) "👑" else "👥", fontSize = 14.sp)
                                            Column {
                                                Text(
                                                    text = mapping.groupName,
                                                    color = TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "PIN: ${mapping.pinCode}",
                                                    color = RadarCyan,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(GlowingEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text("ACTIVE", color = GlowingEmerald, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(
                                                onClick = { onDeleteGroupPinFromHistory(mapping) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete registry item",
                                                    tint = Color.Red.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))

                    Text(
                        text = "My Personal Map Pin Color Accent",
                        fontSize = 11.sp,
                        color = SecondarySlate,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorsList.forEach { col ->
                            val isSel = selectedColorHex == col
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(col)))
                                    .border(
                                        width = if (isSel) 2.5.dp else 0.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = col }
                            )
                        }
                    }

                    Text(text = "My Personal Profile Picture Emoji", fontSize = 11.sp, color = SecondarySlate, fontWeight = FontWeight.Bold)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val userEmojisList = listOf("👨", "👨‍💻", "👩", "👩‍💻", "👦", "👧", "👶", "👵", "👴", "🐱", "🐶", "🚗", "🚲", "🏡", "🦊", "🦸")
                        val chunks = userEmojisList.chunked(8)
                        chunks.forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowEmojis.forEach { emo ->
                                    val isSel = selectedEmoji == emo
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) PrimaryCosmic else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = if (isSel) RadarCyan else SlateBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedEmoji = emo },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emo, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("My Phone Number (WhatsApp)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_device_phone_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("My Screen Display Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_device_identity_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RadarCyan,
                            unfocusedBorderColor = SlateBorder
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            onToggleCloudSync(true, tokenInput, nameInput, selectedColorHex, selectedEmoji, phoneInput)
                            expandedSetup = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("apply_sync_custom_btn")
                    ) {
                        Text(
                            text = "Save Device Setup Sync Settings",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
```
