package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.i18n.AppStrings
import com.example.domain.i18n.englishStrings
import com.example.domain.i18n.hindiStrings
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = PurpleGlow,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1E7A),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = GoldSoft,
    onSecondary = Color(0xFF1F1600),
    secondaryContainer = Color(0xFF3B2B08),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = PaidGreen,
    onTertiary = Color.White,
    background = NavyDeep,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavyCard,
    surfaceContainerHigh = NavyCardElevated,
    outline = NavyBorder,
    outlineVariant = Color(0xFF23304C),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = MisradhanPurpleContainer,
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = InterestOrangeDark,
    onSecondary = Color.White,
    secondaryContainer = InterestOrangeContainer,
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = PaidGreenDark,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightCardVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightCardVariant,
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = OverdueRed,
    onError = Color.White,
    errorContainer = OverdueRedContainer,
    onErrorContainer = Color(0xFF7F1D1D)
)

val CreditLoggerShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val LocalAppStrings = staticCompositionLocalOf { hindiStrings() }

@Composable
fun CreditLoggerTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    language: AppLanguage = AppLanguage.HINDI,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val strings: AppStrings = if (language == AppLanguage.HINDI) hindiStrings() else englishStrings()
    val typography = buildCreditLoggerTypography(isHindi = language == AppLanguage.HINDI)

    CompositionLocalProvider(LocalAppStrings provides strings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = CreditLoggerShapes,
            content = content
        )
    }
}
