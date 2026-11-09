@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ma.carnet.inspection.natif.ui

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Texte sur une seule ligne, à taille fixe : s'il est trop long pour sa vignette, il défile doucement
 * (bandeau) au lieu d'agrandir la vignette ou d'être coupé.
 */
@Composable
fun MarqueeText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null, center: Boolean = false, velocity: Dp = 32.dp
) {
    Box(modifier.fillMaxWidth(), contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
        Text(
            text, color = if (color == Color.Unspecified) LocalContentColor.current else color, fontSize = fontSize, fontWeight = fontWeight,
            maxLines = 1, softWrap = false,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, animationMode = MarqueeAnimationMode.Immediately,
                repeatDelayMillis = 1800, initialDelayMillis = 1500, velocity = velocity)
        )
    }
}
