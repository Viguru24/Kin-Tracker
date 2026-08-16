package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyMember
import com.example.data.ShoppingItem
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListControls(
    shoppingItems: List<ShoppingItem>,
    familyMembers: List<FamilyMember>,
    onAddItem: (String, String, String) -> Unit,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var newItemName by remember { mutableStateOf("") }
    
    // Pick the first available family member as default selected
    val defaultMember = familyMembers.firstOrNull { it.id == "me" } ?: familyMembers.firstOrNull()
    var selectedMember by remember(familyMembers) { mutableStateOf(defaultMember) }
    
    // Fallback if selectedMember gets deleted or not in list anymore
    LaunchedEffect(familyMembers) {
        if (selectedMember == null || !familyMembers.any { it.id == selectedMember?.id }) {
            selectedMember = defaultMember
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("shopping_list_controls_card"),
        colors = CardDefaults.cardColors(containerColor = CosmicSlateCard),
        border = BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🛒", fontSize = 16.sp)
                    Column {
                        Text(
                            text = "Shared Family Shopping List",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Contribute, check off, or purchase groceries together",
                            color = SecondarySlate,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // ── Circle Identity Picker ──
            Text(
                text = "Add Item On Behalf Of:",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(familyMembers, key = { it.id }) { member ->
                    val isSelected = selectedMember?.id == member.id
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) RadarCyan else SlateBorder,
                        label = "border_color"
                    )
                    val bgAlpha = if (isSelected) 0.08f else 0.02f
                    
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = bgAlpha))
                            .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(16.dp))
                            .clickable { selectedMember = member }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    Color(android.graphics.Color.parseColor(member.avatarColorHex)).copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.avatarEmoji.ifBlank { "👤" },
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (member.id == "me") "You (${member.name})" else member.name.substringBefore(" ("),
                            color = if (isSelected) RadarCyan else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Input box with Done Action ──
            fun triggerAddItem() {
                val name = newItemName.trim()
                val member = selectedMember
                if (name.isNotEmpty() && member != null) {
                    onAddItem(name, member.id, member.name)
                    newItemName = ""
                }
            }

            OutlinedTextField(
                value = newItemName,
                onValueChange = { newItemName = it },
                label = { Text("Add new shopping item...", fontSize = 11.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_shopping_item_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = RadarCyan,
                    unfocusedBorderColor = SlateBorder
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { triggerAddItem() }
                ),
                trailingIcon = {
                    if (newItemName.trim().isNotEmpty()) {
                        TextButton(
                            onClick = { triggerAddItem() },
                            modifier = Modifier.testTag("add_shopping_item_btn")
                        ) {
                            Text("Add", color = RadarCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            )

            if (shoppingItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = SlateBorder.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                // ── Shopping Items List ──
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shoppingItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (item.isChecked) Color.White.copy(alpha = 0.01f) else Color.White.copy(alpha = 0.03f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (item.isChecked) SlateBorder.copy(alpha = 0.5f) else SlateBorder
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { onToggleItem(item) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = RadarCyan,
                                        uncheckedColor = SecondarySlate,
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .testTag("shopping_item_checkbox_${item.id}")
                                )
                                
                                Column {
                                    Text(
                                        text = item.name,
                                        color = if (item.isChecked) SecondarySlate.copy(alpha = 0.6f) else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        text = "Added by ${item.addedByMemberName.substringBefore(" (")}",
                                        color = SecondarySlate.copy(alpha = 0.6f),
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDeleteItem(item) },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("delete_shopping_item_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Item",
                                    tint = GlowingMagenta.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
