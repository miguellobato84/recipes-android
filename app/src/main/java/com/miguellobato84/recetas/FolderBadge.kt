package com.miguellobato84.recetas

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun FolderBadge(appearance: FolderAppearance) {
    val colors = appearance.colors(isSystemInDarkTheme())
    Surface(color = Color(colors.background), shape = RoundedCornerShape(12.dp)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(appearance.icon.drawable), contentDescription = null,
                tint = Color(colors.foreground), modifier = Modifier.size(24.dp))
        }
    }
}
