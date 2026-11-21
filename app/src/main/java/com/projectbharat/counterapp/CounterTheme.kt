package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class AppTab(val en: String, val mr: String, val icon: ImageVector) {
    Movements("Movements", "हालचाली", Icons.Default.Favorite),
    Timeline("Timeline", "कालरेषा", Icons.Default.DateRange),
    History("History", "इतिहास", Icons.Default.BarChart),
    Settings("Settings", "सेटिंग्ज", Icons.Default.Settings)
}

internal enum class SettingsDetail { None, Reminders, Backup }

internal val Sand = Color(0xFFC49963)

internal fun counterColors(dark: Boolean): ColorScheme {
    val ink = if (dark) Color(0xFFF4F4F1) else Color(0xFF191A19)
    val surface = if (dark) Color(0xFF212221) else Color.White
    val background = if (dark) Color(0xFF131413) else Color(0xFFF5F5F3)
    val muted = if (dark) Color(0xFF333432) else Color(0xFFECEDE9)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(primary = ink, onPrimary = surface, background = background, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = muted, surfaceContainer = surface,
        surfaceContainerHighest = surface, onSurfaceVariant = if (dark) Color.LightGray else Color.DarkGray,
        secondaryContainer = muted, onSecondaryContainer = ink, secondary = ink, onSecondary = surface)
}

@Composable
internal fun SettingsRow(label: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowRight, null)
        }
    }
}
