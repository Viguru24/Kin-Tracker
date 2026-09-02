package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppLogger
import com.example.ui.theme.*

/**
 * Enterprise UI Error Boundary.
 * Catches rendering / state evaluation faults in subtree composables to prevent app fatal aborts,
 * displaying a graceful fallback recovery card.
 */
@Composable
fun ErrorBoundary(
    componentName: String = "Component",
    modifier: Modifier = Modifier,
    hasError: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    fallback: (@Composable (String, () -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (hasError) {
        val message = errorMessage ?: "$componentName encountered an unexpected error"
        AppLogger.e("ErrorBoundary", "UI Fallback rendered for $componentName: $message")

        if (fallback != null) {
            fallback(message, onRetry)
        } else {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = CosmicSlateCard,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("⚠️", fontSize = 24.sp)
                    Text(
                        text = "$componentName encountered an issue",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = message,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCosmic),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Retry", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
        content()
    }
}
