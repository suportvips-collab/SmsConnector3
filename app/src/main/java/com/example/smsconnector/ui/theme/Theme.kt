package com.example.smsconnector.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    secondary = NeonPurpleDark,
    tertiary = NeonPurple,
    background = VipBlack,
    surface = VipBlack,
    onPrimary = VipWhite,
    onSecondary = VipWhite,
    onBackground = VipWhite,
    onSurface = VipWhite,
)

// Forçamos o LightColorScheme a ser igual ao Dark para manter o visual Neon sempre ativo
private val LightColorScheme = DarkColorScheme

@Composable
fun SmsConnectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Desativado para manter a identidade visual roxa
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
