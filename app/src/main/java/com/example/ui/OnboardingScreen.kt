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

// ─── Entry point ─────────────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    viewModel: FamilyViewModel,
    onComplete: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    // step 0 = welcome / name
    // step 1 = create or join
    // step 2a = created (show key)
    // step 2b = join (enter key)

    var myName      by remember { mutableStateOf("") }
    var myEmoji     by remember { mutableStateOf("👤") }
    var myColor     by remember { mutableStateOf("#AA22FF") }
    var joinToken   by remember { mutableStateOf("") }
    var chosenFlow  by remember { mutableStateOf("") } // "create" | "join"

    val generatedKey by viewModel.groupSyncToken.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Pulse animation for the logo ring
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

                // ── STEP 0: Welcome + Your Name ───────────────────────────────
                0 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(Modifier.height(32.dp))

                    // App logo ring
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
                            "Welcome to KinTracker",
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

                    // Name field
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

                    // Emoji picker — who are you?
                    Text(
                        "Choose your icon",
                        color = SecondarySlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                    .background(
                                        if (myEmoji == emo) Color(0xFF7C3AED).copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        if (myEmoji == emo) 2.dp else 1.dp,
                                        if (myEmoji == emo) Color(0xFF7C3AED) else SlateBorder,
                                        CircleShape
                                    )
                                    .clickable { myEmoji = emo },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(emo, fontSize = 18.sp)
                            }
                        }
                    }

                    // Colour accent
                    Text(
                        "Pick your map colour",
                        color = SecondarySlate,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val colourOptions = listOf(
                        "#AA22FF", "#EC407A", "#26A69A",
                        "#42A5F5", "#FF9800", "#00FF87"
                    )
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
                                    .border(
                                        if (myColor == hex) 3.dp else 0.dp,
                                        Color.White,
                                        CircleShape
                                    )
                                    .clickable { myColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (myColor == hex) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
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

                    Spacer(Modifier.height(16.dp))
                }

                // ── STEP 1: Create or Join ────────────────────────────────────
                1 -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(Modifier.height(48.dp))

                    Text("👨‍👩‍👧‍👦", fontSize = 56.sp)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Set up your\nFamily Circle",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Everyone in your circle shares live locations with each other using a unique Family Key.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }

                    // CREATE card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosenFlow = "create"
                                viewModel.generateNewGroupKey()
                                step = 2
                            }
                            .testTag("onboarding_create_btn"),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF7C3AED))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✨", fontSize = 32.sp)
                            Column {
                                Text(
                                    "Create a Family Circle",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "I'm the first one — I'll invite everyone else",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // JOIN card
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
                        Row(
                            modifier = Modifier.padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔗", fontSize = 32.sp)
                            Column {
                                Text(
                                    "Join a Family Circle",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "I have a Family Key from someone in my circle",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    TextButton(onClick = { step = 0 }) {
                        Text("← Back", color = SecondarySlate, fontSize = 12.sp)
                    }
                }

                // ── STEP 2: Create = show key / Join = enter key ─────────────
                2 -> if (chosenFlow == "create") {
                    // ── Created: show the key and share button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(48.dp))

                        Text("🎉", fontSize = 56.sp)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Your Family Circle\nis ready!",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Share this key with everyone you want on your map. They'll tap \"Join a Family Circle\" and paste it in.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        // Key display box
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.06f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF7C3AED).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Your Family Key",
                                    color = SecondarySlate,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    generatedKey,
                                    color = Color(0xFFB39DDB),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Share via WhatsApp
                        Button(
                            onClick = {
                                try {
                                    clipboard.setText(AnnotatedString(generatedKey))
                                    val inviteText = "Hey! I've set up KinTracker so we can see each other on a live map. Download the app, tap \"Join a Family Circle\" and enter this key:\n\n$generatedKey"
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://api.whatsapp.com/send?text=" + android.net.Uri.encode(inviteText))
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    clipboard.setText(AnnotatedString(generatedKey))
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💬", fontSize = 16.sp)
                                Text("Share Key via WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Copy only
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(generatedKey)) },
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("📋  Copy Key Only", fontSize = 13.sp, color = SecondarySlate)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Enter the map
                        Button(
                            onClick = {
                                viewModel.completeOnboarding()
                                viewModel.toggleCloudSync(true, generatedKey, myName.trim().ifBlank { "My Device" }, myColor, myEmoji)
                                onComplete()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_open_map_btn")
                        ) {
                            Text("Open My Map →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        TextButton(onClick = { step = 1 }) {
                            Text("← Back", color = SecondarySlate, fontSize = 12.sp)
                        }
                    }

                } else {
                    // ── Join: paste / type the key
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(48.dp))

                        Text("🔗", fontSize = 56.sp)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Join Your\nFamily Circle",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ask the person who created the circle to send you their Family Key, then paste it below.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }

                        OutlinedTextField(
                            value = joinToken,
                            onValueChange = { joinToken = it.trim() },
                            label = { Text("Family Key", color = SecondarySlate) },
                            placeholder = { Text("Paste the key here…", color = SecondarySlate.copy(alpha = 0.5f)) },
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

                        // Paste from clipboard shortcut
                        TextButton(
                            onClick = {
                                val fromClip = clipboard.getText()?.text ?: ""
                                if (fromClip.isNotBlank()) joinToken = fromClip.trim()
                            }
                        ) {
                            Text("📋  Paste from clipboard", color = RadarCyan, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (joinToken.isNotBlank()) {
                                    viewModel.completeOnboarding()
                                    viewModel.toggleCloudSync(true, joinToken, myName.trim().ifBlank { "My Device" }, myColor, myEmoji)
                                    onComplete()
                                }
                            },
                            enabled = joinToken.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF26A69A),
                                disabledContainerColor = SlateBorder
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_join_submit_btn")
                        ) {
                            Text("Join Circle & Open Map →", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        TextButton(onClick = { step = 1 }) {
                            Text("← Back", color = SecondarySlate, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Step indicator dots at top
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(0, 1, 2).forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (step == i) 22.dp else 7.dp, 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (step >= i) Color(0xFF7C3AED) else Color.White.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}
