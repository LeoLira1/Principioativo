package br.com.camda.principioativo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta escura do CAMDA Estoque (theme.py `PALETTE`). */
object Cores {
    val bg = Color(0xFF111214)
    val surface = Color(0xFF1C1D21)
    val surface2 = Color(0xFF24262B)
    val line = Color(0xFF2E3036)
    val line2 = Color(0xFF3A3C43)
    val text = Color(0xFFF2F2F2)
    val text2 = Color(0xFFC8C9CC)
    val muted = Color(0xFFA3A5AB)
    val label = Color(0xFF8E9096)
    val accent = Color(0xFFD7F000)   // limão: só preenchimento, texto preto por cima
    val onAccent = Color(0xFF111111)
    val crit = Color(0xFFFF5A5A)
    val warn = Color(0xFFFFB020)
    val ok = Color(0xFF4CC38A)
    val info = Color(0xFF7B9BFF)
    val purple = Color(0xFFB69CFF)
    val teal = Color(0xFF3CCFBF)
    val critSoft = Color(0x29FF5A5A)
    val okSoft = Color(0x294CC38A)

    /** Série categórica dos gráficos (laranja, vermelho, verde… como a referência). */
    val serie = listOf(
        warn, crit, ok, teal, info, purple, Color(0xFFF472B6), accent, Color(0xFFFF8A4C), Color(0xFF9AD0FF),
    )
    val outros = Color(0xFF55585F)

    fun daSerie(i: Int): Color = serie[i % serie.size]
}

@Composable
fun TemaPa(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cores.accent,
            onPrimary = Cores.onAccent,
            secondary = Cores.info,
            background = Cores.bg,
            onBackground = Cores.text,
            surface = Cores.surface,
            onSurface = Cores.text,
            surfaceVariant = Cores.surface2,
            onSurfaceVariant = Cores.muted,
            outline = Cores.line2,
            error = Cores.crit,
        ),
        content = content,
    )
}
