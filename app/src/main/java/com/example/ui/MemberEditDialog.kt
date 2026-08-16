package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.ui.theme.*
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberEditDialog(
    member: FamilyMember,
    onDismiss: () -> Unit,
    onSave: (FamilyMember) -> Unit
) {
    val context = LocalContext.current
    var editName by remember(member.id) { mutableStateOf(member.name) }
    var editStatus by remember(member.id) { mutableStateOf(member.statusText) }
    var editColorHex by remember(member.id) { mutableStateOf(member.avatarColorHex) }
    var editEmoji by remember(member.id) { mutableStateOf(member.avatarEmoji) }
    var editPhone by remember(member.id) { mutableStateOf(member.phoneNumber) }
    var editPhotoPath by remember(member.id) { mutableStateOf(member.photoPath) }
    var cropZoom by remember(member.id) { mutableStateOf(1.0f) }
    var rotationAngle by remember(member.id) { mutableStateOf(0f) }
    var rawPhotoPath by remember(member.id) { mutableStateOf(member.photoPath) }
    var panXFraction by remember(member.id) { mutableStateOf(0f) }
    var panYFraction by remember(member.id) { mutableStateOf(0f) }

    val colorsList = listOf("#EC407A", "#26A69A", "#42A5F5", "#FF9800", "#FFEA00", "#E040FB", "#00FF87")

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val localFile = saveUriToInternalStorage(context, uri)
            if (localFile != null) {
                rawPhotoPath = localFile.absolutePath
                editPhotoPath = localFile.absolutePath
                cropZoom = 1.0f
                rotationAngle = 0f
                panXFraction = 0f
                panYFraction = 0f
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Tracker Settings",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Centered large circular photo preview with color-coded border matching selected tag
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val borderOutlineColor = try {
                        Color(android.graphics.Color.parseColor(editColorHex))
                    } catch (e: Exception) {
                        RadarCyan
                    }
                    
                    BoxWithConstraints(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(borderOutlineColor.copy(alpha = 0.15f))
                            .border(2.dp, borderOutlineColor, CircleShape)
                            .clickable { launcher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val containerSizePx = with(LocalDensity.current) { 100.dp.toPx() }
                        
                        if (editPhotoPath.isNotEmpty() && File(editPhotoPath).exists()) {
                            val bitmap = remember(editPhotoPath) {
                                try {
                                    android.graphics.BitmapFactory.decodeFile(editPhotoPath)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            
                            if (bitmap != null) {
                                val isRotatedSideways = (rotationAngle.toInt() / 90) % 2 != 0
                                val visualWidth = if (isRotatedSideways) bitmap.height else bitmap.width
                                val visualHeight = if (isRotatedSideways) bitmap.width else bitmap.height
                                
                                val baseScale = maxOf(containerSizePx / visualWidth.toFloat(), containerSizePx / visualHeight.toFloat())
                                val scaledWidth = visualWidth * baseScale * cropZoom
                                val scaledHeight = visualHeight * baseScale * cropZoom
                                
                                val maxPanX = ((scaledWidth - containerSizePx) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((scaledHeight - containerSizePx) / 2f).coerceAtLeast(0f)
                                
                                val translationXValue = panXFraction * maxPanX
                                val translationYValue = panYFraction * maxPanY
                                
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Profile Photo Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(cropZoom, rotationAngle, maxPanX, maxPanY) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val currentX = panXFraction * maxPanX
                                                val currentY = panYFraction * maxPanY
                                                val targetX = (currentX + dragAmount.x).coerceIn(-maxPanX, maxPanX)
                                                val targetY = (currentY + dragAmount.y).coerceIn(-maxPanY, maxPanY)
                                                panXFraction = if (maxPanX > 0f) targetX / maxPanX else 0f
                                                panYFraction = if (maxPanY > 0f) targetY / maxPanY else 0f
                                            }
                                        }
                                        .graphicsLayer {
                                            scaleX = cropZoom
                                            scaleY = cropZoom
                                            rotationZ = rotationAngle
                                            translationX = translationXValue
                                            translationY = translationYValue
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(text = editEmoji, fontSize = 40.sp)
                            }
                        } else {
                            Text(text = editEmoji, fontSize = 40.sp)
                        }
                    }
                }

                // Spacious Photo Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { launcher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("📷 Choose Photo", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    if (editPhotoPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("🔄 Rotate 90°", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (rawPhotoPath.isNotEmpty() && rawPhotoPath != member.photoPath) {
                                    try { File(rawPhotoPath).delete() } catch (e: Exception) {}
                                }
                                editPhotoPath = ""
                                rawPhotoPath = ""
                                cropZoom = 1.0f
                                rotationAngle = 0f
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("🗑️ Remove", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dedicated Crop Zoom Slider
                if (editPhotoPath.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Crop Zoom Level:", color = SecondarySlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format(java.util.Locale.US, "%.1f", cropZoom)}x", color = RadarCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = cropZoom,
                            onValueChange = { cropZoom = it },
                            valueRange = 1.0f..3.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = RadarCyan,
                                activeTrackColor = RadarCyan,
                                inactiveTrackColor = SlateBorder
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                    }
                }

                // Name Input
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name", fontSize = 11.sp, color = SecondarySlate) },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = SlateBorder,
                        cursorColor = RadarCyan,
                        focusedLabelColor = RadarCyan,
                        unfocusedLabelColor = SecondarySlate
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_name_input")
                )

                OutlinedTextField(
                    value = editPhone,
                    onValueChange = { editPhone = it },
                    label = { Text("Phone Number (WhatsApp)", fontSize = 11.sp, color = SecondarySlate) },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = SlateBorder,
                        cursorColor = RadarCyan,
                        focusedLabelColor = RadarCyan,
                        unfocusedLabelColor = SecondarySlate
                    ),
                    singleLine = true,
                    placeholder = { Text("+447803171262", color = SecondarySlate.copy(alpha = 0.5f), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_phone_input")
                )

                OutlinedTextField(
                    value = editStatus,
                    onValueChange = { editStatus = it },
                    label = { Text("Current Status Text", fontSize = 11.sp, color = SecondarySlate) },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = SlateBorder,
                        cursorColor = RadarCyan,
                        focusedLabelColor = RadarCyan,
                        unfocusedLabelColor = SecondarySlate
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(62.dp).testTag("edit_status_input")
                )


                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Radar Color Tag:", color = SecondarySlate, fontSize = 11.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        colorsList.forEach { rgbHex ->
                            val rgbColor = try {
                                Color(android.graphics.Color.parseColor(rgbHex))
                            } catch (e: Exception) {
                                Color(0xFF26A69A)
                            }
                            val isChosen = rgbHex == editColorHex
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(rgbColor, CircleShape)
                                    .border(
                                        width = if (isChosen) 2.dp else 0.dp,
                                        color = if (isChosen) TextPrimary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { editColorHex = rgbHex }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Profile Picture Icon:", color = SecondarySlate, fontSize = 11.sp)
                    val editEmojisList = listOf("👨", "👩", "👦", "👧", "👶", "👵", "👴", "🐱", "🐶", "🚗", "🚲", "🏡", "🦊", "🐼", "🦸", "🚀")
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        editEmojisList.chunked(8).forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                chunk.forEach { emo ->
                                    val isSelected = editEmoji == emo
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(if (isSelected) PrimaryCosmic else Color.White.copy(alpha = 0.05f), CircleShape)
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) RadarCyan else SlateBorder,
                                                shape = CircleShape
                                            )
                                            .clickable { editEmoji = emo },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emo, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (editName.isNotBlank()) {
                        var finalPhotoPath = editPhotoPath
                        
                        val hasNewPhotoSelected = rawPhotoPath.isNotEmpty() && rawPhotoPath != member.photoPath
                        val hasCropOrRotationApplied = cropZoom != 1.0f || rotationAngle != 0f
                        
                        if (rawPhotoPath.isNotEmpty() && (hasNewPhotoSelected || hasCropOrRotationApplied || panXFraction != 0f || panYFraction != 0f)) {
                            val processedFile = saveProcessedProfileImage(context, rawPhotoPath, cropZoom, rotationAngle, panXFraction, panYFraction)
                            if (processedFile != null) {
                                finalPhotoPath = processedFile.absolutePath
                                
                                if (hasNewPhotoSelected) {
                                    try {
                                        File(rawPhotoPath).delete()
                                    } catch (e: Exception) {}
                                }
                                
                                if (member.photoPath.isNotEmpty() && member.photoPath != rawPhotoPath) {
                                    try {
                                        File(member.photoPath).delete()
                                    } catch (e: Exception) {}
                                }
                            }
                        } else if (editPhotoPath.isEmpty() && member.photoPath.isNotEmpty()) {
                            try {
                                File(member.photoPath).delete()
                            } catch (e: Exception) {}
                        }
                        
                        val updated = member.copy(
                            name = editName,
                            statusText = editStatus,
                            avatarColorHex = editColorHex,
                            avatarEmoji = editEmoji,
                            phoneNumber = editPhone,
                            photoPath = finalPhotoPath
                        )
                        onSave(updated)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RadarCyan),
                shape = RoundedCornerShape(8.dp),
                enabled = editName.isNotBlank(),
                modifier = Modifier.testTag("save_edit_btn")
            ) {
                Text("Save Changes", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_edit_btn")
            ) {
                Text("Cancel", color = SecondarySlate, fontSize = 12.sp)
            }
        },
        containerColor = CosmicSlateCard,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    )
}

private fun saveUriToInternalStorage(context: android.content.Context, uri: Uri): File? {
    return try {
        // 1. Get EXIF orientation from original stream
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                val ori = exif.getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
                when (ori) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }

        // 2. Decode image with downscaling to avoid OutOfMemoryError
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, options)
        }

        val maxDim = 1000
        var scale = 1
        if (options.outWidth > maxDim || options.outHeight > maxDim) {
            scale = maxOf(options.outWidth / maxDim, options.outHeight / maxDim)
        }

        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = scale
        }

        var bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // 3. Rotate bitmap upright based on original EXIF orientation
        if (orientation != 0) {
            val matrix = android.graphics.Matrix().apply { postRotate(orientation.toFloat()) }
            val rotated = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            bitmap = rotated
        }

        // 4. Save to files directory as compressed JPEG
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        file
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun saveProcessedProfileImage(
    context: android.content.Context,
    sourcePath: String,
    zoom: Float,
    rotation: Float,
    panXFraction: Float,
    panYFraction: Float
): File? {
    return try {
        val orig = android.graphics.BitmapFactory.decodeFile(sourcePath) ?: return null
        
        // 1. Rotate the original bitmap first to align with the visual preview
        val rotatedOrig = if (rotation != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
            val rotated = android.graphics.Bitmap.createBitmap(orig, 0, 0, orig.width, orig.height, matrix, true)
            if (rotated != orig) {
                orig.recycle()
            }
            rotated
        } else {
            orig
        }
        
        val Wr = rotatedOrig.width
        val Hr = rotatedOrig.height
        
        // 2. Calculate crop size and max pan values on the rotated bitmap
        val cropSize = (minOf(Wr, Hr) / zoom).toInt()
        val maxPanXImage = (Wr - cropSize) / 2
        val maxPanYImage = (Hr - cropSize) / 2
        
        // 3. Compute top-left corner coordinates using drag fractions
        val x = ((Wr - cropSize) / 2 - panXFraction * maxPanXImage).toInt()
        val y = ((Hr - cropSize) / 2 - panYFraction * maxPanYImage).toInt()
        
        val safeX = x.coerceIn(0, Wr - cropSize)
        val safeY = y.coerceIn(0, Hr - cropSize)
        
        // 4. Crop the selected region
        val cropped = android.graphics.Bitmap.createBitmap(rotatedOrig, safeX, safeY, cropSize, cropSize)
        
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        val destFile = File(context.filesDir, fileName)
        FileOutputStream(destFile).use { out ->
            cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        if (cropped != rotatedOrig) {
            cropped.recycle()
        }
        rotatedOrig.recycle()
        destFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

