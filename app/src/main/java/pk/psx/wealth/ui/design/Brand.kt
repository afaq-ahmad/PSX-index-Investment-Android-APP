package pk.psx.wealth.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** A compact, code-native mark: portfolio bars plus a rising index line. */
@Composable
fun PsxWealthLogo(
    modifier: Modifier = Modifier,
    contentDescription: String = "PSX Wealth",
) {
    val green = MaterialTheme.colorScheme.primary
    val gold = Color(0xFFD5A521)
    Canvas(modifier.size(36.dp).semantics { this.contentDescription = contentDescription }) {
        drawCircle(green)
        val barWidth = size.width * .10f
        listOf(
            .24f to .68f,
            .41f to .55f,
            .58f to .43f,
        ).forEach { (x, top) ->
            drawLine(
                color = Color.White.copy(alpha = .92f),
                start = Offset(size.width * x, size.height * .75f),
                end = Offset(size.width * x, size.height * top),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
        val path = Path().apply {
            moveTo(size.width * .17f, size.height * .62f)
            lineTo(size.width * .38f, size.height * .45f)
            lineTo(size.width * .55f, size.height * .52f)
            lineTo(size.width * .80f, size.height * .27f)
        }
        drawPath(path, gold, style = Stroke(size.width * .075f, cap = StrokeCap.Round))
        drawLine(
            gold,
            Offset(size.width * .80f, size.height * .27f),
            Offset(size.width * .69f, size.height * .29f),
            size.width * .06f,
            StrokeCap.Round,
        )
        drawLine(
            gold,
            Offset(size.width * .80f, size.height * .27f),
            Offset(size.width * .78f, size.height * .38f),
            size.width * .06f,
            StrokeCap.Round,
        )
    }
}
