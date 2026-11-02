package ma.carnet.inspection.natif.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

/** Palette d'un thème (mêmes thèmes que le carnet web : Réglages → Apparence). */
data class Pal(
    val k: String, val label: String, val desc: String, val dark: Boolean,
    val bg: Color, val surface: Color, val brand: Color, val accent2: Color, val ink: Color, val inkFaint: Color
)

private fun c(hex: Long) = Color(hex)

val SKINS = listOf(
    Pal("classique", "Classique", "Thème d'origine", false, c(0xFFF6F4EE), c(0xFFFFFFFF), c(0xFF2F6F62), c(0xFFB5762A), c(0xFF1F2523), c(0xFF6B7470)),
    Pal("futur", "Futuriste", "Sombre, néon", true, c(0xFF070B16), c(0xFF111A2E), c(0xFF22E3C4), c(0xFFA78BFA), c(0xFFE6F1FF), c(0xFF8FA3BF)),
    Pal("oasis", "Oasis", "Sable, zellige, terre cuite", false, c(0xFFF3ECDF), c(0xFFFFFBF4), c(0xFF0F7B6C), c(0xFFB9692A), c(0xFF2B2118), c(0xFF75685A)),
    Pal("majorelle", "Majorelle", "Bleu Majorelle et jaune", false, c(0xFFEEF0FA), c(0xFFFFFFFF), c(0xFF4A3FD1), c(0xFFF2C230), c(0xFF141A33), c(0xFF5D6383)),
    Pal("aurore", "Aurore", "Clair, dégradés doux", false, c(0xFFFBF6FB), c(0xFFFFFFFF), c(0xFF8A3FD8), c(0xFFFF7AA8), c(0xFF2A1F33), c(0xFF6E6478)),
    Pal("nuit", "Nuit", "Sombre sobre, bleu nuit", true, c(0xFF0E131D), c(0xFF172031), c(0xFF7AA7FF), c(0xFFB29BFF), c(0xFFE4E9F2), c(0xFF94A0B5)),
    Pal("sahara", "Sahara", "Sombre chaud, ocre", true, c(0xFF1B1510), c(0xFF261E17), c(0xFFE4A853), c(0xFFEE806B), c(0xFFF3E8D8), c(0xFFB3A390)),
    Pal("contraste", "Contraste élevé", "Lisibilité maximale", false, c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFF003FA3), c(0xFFA0001B), c(0xFF000000), c(0xFF2B2B2B))
)
private val CLASSIQUE_SOMBRE = Pal("classique", "Classique", "", true, c(0xFF111715), c(0xFF1B2421), c(0xFF5FB3A1), c(0xFFD9A25B), c(0xFFE8EEEC), c(0xFF98A5A0))
val ACCENTS = listOf("#2F6F62", "#0F7B6C", "#1BAF7A", "#2A78D6", "#4A3FD1", "#8A3FD8", "#E87BA4", "#C8365E", "#EB6834", "#E4A853")

object ThemeState {
    val skin = mutableStateOf("classique")
    val mode = mutableStateOf("")        // "" auto, light, dark (thème classique)
    val accent = mutableStateOf("")      // "" = couleur du thème
    val scale = mutableFloatStateOf(1f)  // taille du texte
    val corners = mutableStateOf("")     // droit, "", rond
    val systemDark = mutableStateOf(false)

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE)
        skin.value = p.getString("skin", "classique") ?: "classique"
        mode.value = p.getString("lookMode", "") ?: ""
        accent.value = p.getString("accent", "") ?: ""
        scale.floatValue = p.getFloat("textScale", 1f)
        corners.value = p.getString("corners", "") ?: ""
    }
    fun save(ctx: Context) {
        ctx.getSharedPreferences("carnet", Context.MODE_PRIVATE).edit().putString("skin", skin.value).putString("lookMode", mode.value)
            .putString("accent", accent.value).putFloat("textScale", scale.floatValue).putString("corners", corners.value).apply()
    }
    fun reset(ctx: Context) { skin.value = "classique"; mode.value = ""; accent.value = ""; scale.floatValue = 1f; corners.value = ""; save(ctx) }

    fun pal(): Pal {
        var base = SKINS.firstOrNull { it.k == skin.value } ?: SKINS[0]
        if (base.k == "classique" && (mode.value == "dark" || (mode.value == "" && systemDark.value))) base = CLASSIQUE_SOMBRE
        val acc = accent.value
        if (acc.isNotEmpty()) try { base = base.copy(brand = Color(android.graphics.Color.parseColor(acc))) } catch (_: Exception) {}
        return base
    }
}

fun hexToColor(h: String): Color = try { Color(android.graphics.Color.parseColor(h)) } catch (_: Exception) { Color.Gray }

// Couleurs lues par tous les écrans : elles suivent le thème choisi.
val Brand: Color get() = ThemeState.pal().brand
val OnBrand: Color get() = if (ThemeState.pal().brand.luminance() > 0.5f) Color(0xFF0B1212) else Color.White
val BrandSoft: Color get() = ThemeState.pal().let { p -> if (p.dark) p.brand.copy(alpha = 0.22f) else p.brand.copy(alpha = 0.13f) }
val Paper: Color get() = ThemeState.pal().bg
val Surface: Color get() = ThemeState.pal().surface
val Ink: Color get() = ThemeState.pal().ink
val InkFaint: Color get() = ThemeState.pal().inkFaint
val Track: Color get() = ThemeState.pal().let { if (it.dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f) }
val Danger: Color get() = if (ThemeState.pal().dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
val Warn: Color get() = if (ThemeState.pal().dark) Color(0xFFFFB74D) else Color(0xFFB26A00)
val FemColor: Color get() = if (ThemeState.pal().dark) Color(0xFFF48FB1) else Color(0xFFB0427A)
val HomColor: Color get() = if (ThemeState.pal().dark) Color(0xFF90CAF9) else Color(0xFF2A6FB0)
val Earth: Color get() = if (ThemeState.pal().dark) Color(0xFFD7C29A) else Color(0xFF6A5D3F)

@Composable
fun CarnetTheme(content: @Composable () -> Unit) {
    ThemeState.systemDark.value = isSystemInDarkTheme()
    val p = ThemeState.pal()
    val scheme = if (p.dark) darkColorScheme(
        primary = p.brand, onPrimary = OnBrand, primaryContainer = BrandSoft, onPrimaryContainer = p.ink, secondary = p.accent2,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink, surfaceVariant = p.surface.copy(alpha = 0.7f),
        onSurfaceVariant = p.inkFaint, surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, error = Danger
    ) else lightColorScheme(
        primary = p.brand, onPrimary = OnBrand, primaryContainer = BrandSoft, onPrimaryContainer = p.ink, secondary = p.accent2,
        background = p.bg, onBackground = p.ink, surface = p.surface, onSurface = p.ink, surfaceVariant = Track.copy(alpha = 0.5f),
        onSurfaceVariant = p.inkFaint, surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerLow = p.surface, error = Danger
    )
    val r = when (ThemeState.corners.value) { "droit" -> 2.dp; "rond" -> 22.dp; else -> 12.dp }
    val shapes = Shapes(small = RoundedCornerShape(r * 0.6f), medium = RoundedCornerShape(r), large = RoundedCornerShape(r * 1.4f))
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        @Suppress("DEPRECATION") run { w.statusBarColor = p.brand.toArgb(); w.navigationBarColor = p.bg.toArgb() }
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = p.brand.luminance() > 0.5f; isAppearanceLightNavigationBars = !p.dark }
    }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * ThemeState.scale.floatValue)) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
