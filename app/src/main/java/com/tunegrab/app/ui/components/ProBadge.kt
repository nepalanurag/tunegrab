package com.tunegrab.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Small gold "PRO" pill marking a Pro-only option in the free build.
 * Tapping the option shows the Pro upsell dialog; it never performs
 * the action.
 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        "PRO",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF241A00),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFD9A92B))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
