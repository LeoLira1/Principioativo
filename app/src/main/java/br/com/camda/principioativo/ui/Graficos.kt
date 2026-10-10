package br.com.camda.principioativo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.camda.principioativo.core.Fatia
import br.com.camda.principioativo.core.Serie
import br.com.camda.principioativo.core.fmtInt
import br.com.camda.principioativo.core.fmtNum
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** "12.345" → "12,3k"; abaixo de mil mostra inteiro. */
fun compacto(v: Double): String = when {
    v >= 1_000_000 -> fmtNum(v / 1_000_000) + "M"
    v >= 10_000 -> fmtNum((v / 1000).roundToInt().toDouble()) + "k"
    v >= 1_000 -> fmtNum(v / 1000) + "k"
    else -> fmtInt(v)
}

fun corDaFatia(f: Fatia, i: Int): Color = if (f.chave == null) Cores.outros else Cores.daSerie(i)

private fun raioRosca(w: Float, h: Float, densidade: Float): Float {
    val fator = if (w / densidade < 520f) 0.19f else 0.27f
    return min(w * fator, h * 0.40f)
}

/**
 * Rosca segmentada: percentual dentro de cada fatia e linha-guia ligando cada
 * fatia ao seu rótulo nas laterais. Toque numa fatia abre o P.A.
 */
@Composable
fun Rosca(fatias: List<Fatia>, onToque: (String) -> Unit, modifier: Modifier = Modifier) {
    val medidor = rememberTextMeasurer()
    val total = fatias.sumOf { it.valor }
    if (total <= 0) return
    // ângulos (graus, 0 = topo, sentido horário)
    val angulos = remember(fatias) {
        var acc = 0.0
        fatias.map { f -> val ini = acc; acc += f.valor / total * 360.0; ini to acc }
    }
    // altura cresce com o lado que tem mais rótulos
    val porLado = angulos.partition { (a, b) -> (a + b) / 2 <= 180.0 }
        .let { max(it.first.size, it.second.size) }
    val altura: Dp = max(260, 24 + porLado * 50).dp
    val estiloPct = TextStyle(color = Cores.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val estiloNome = TextStyle(color = Cores.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val estiloSub = TextStyle(color = Cores.label, fontSize = 10.sp)

    Canvas(
        modifier.fillMaxWidth().height(altura).pointerInput(fatias) {
            detectTapGestures { p ->
                val c = Offset(size.width / 2f, size.height / 2f)
                val dx = p.x - c.x
                val dy = p.y - c.y
                val raio = raioRosca(size.width.toFloat(), size.height.toFloat(), density)
                val d = hypot(dx, dy)
                if (d < raio * 0.5f || d > raio * 1.12f) return@detectTapGestures
                var ang = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
                if (ang < 0) ang += 360.0
                val i = angulos.indexOfFirst { ang >= it.first && ang < it.second }
                fatias.getOrNull(i)?.chave?.let(onToque)
            }
        },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val raio = raioRosca(size.width.toFloat(), size.height.toFloat(), density)
        val esp = raio * 0.36f
        val rMeio = raio - esp / 2
        val gap = if (fatias.size > 1) 1.6f else 0f

        // anéis-guia concêntricos discretos (como a referência)
        listOf(1.18f, 1.36f).forEach {
            drawCircle(Cores.line, radius = raio * it, center = c, style = Stroke(1.dp.toPx()))
        }
        drawCircle(Cores.surface2, radius = raio - esp - 6.dp.toPx(), center = c)

        fatias.forEachIndexed { i, f ->
            val (ini, fim) = angulos[i]
            val varre = (fim - ini).toFloat()
            drawArc(
                color = corDaFatia(f, i),
                startAngle = (ini - 90).toFloat() + gap / 2,
                sweepAngle = max(varre - gap, 0.4f),
                useCenter = false,
                topLeft = Offset(c.x - rMeio, c.y - rMeio),
                size = Size(rMeio * 2, rMeio * 2),
                style = Stroke(esp, cap = StrokeCap.Butt),
            )
            // % dentro da fatia quando cabe
            if (varre >= 16f) {
                val meio = Math.toRadians(((ini + fim) / 2) - 90)
                val txt = medidor.measure("${(f.valor / total * 100).roundToInt()}%", estiloPct)
                drawText(
                    txt,
                    topLeft = Offset(
                        c.x + (rMeio * cos(meio)).toFloat() - txt.size.width / 2,
                        c.y + (rMeio * sin(meio)).toFloat() - txt.size.height / 2,
                    ),
                )
            }
        }

        // ── rótulos laterais com linhas-guia ────────────────────────────────
        data class Rot(val i: Int, val ang: Double, val direita: Boolean, var y: Float)
        val rots = fatias.indices.map { i ->
            val meio = Math.toRadians(((angulos[i].first + angulos[i].second) / 2) - 90)
            Rot(i, meio, cos(meio) >= 0, c.y + (raio * 1.25f * sin(meio)).toFloat())
        }
        val alturaRot = 46.dp.toPx()
        val topo = 8.dp.toPx()
        val base = size.height - alturaRot
        for (lado in listOf(true, false)) {
            val grupo = rots.filter { it.direita == lado }.sortedBy { it.y }
            var ult = topo - alturaRot
            grupo.forEach { r -> r.y = max(r.y - alturaRot / 2, ult + alturaRot); ult = r.y }
            var limite = base
            grupo.asReversed().forEach { r -> r.y = min(r.y, limite); limite = r.y - alturaRot }
            grupo.forEach { r -> r.y = max(r.y, topo) }
        }
        val xDir = c.x + raio * 1.36f
        val xEsq = c.x - raio * 1.36f
        val larguraTexto = (size.width - xDir - 4.dp.toPx()).toInt().coerceAtLeast(40)
        rots.forEach { r ->
            val f = fatias[r.i]
            val cor = corDaFatia(f, r.i)
            val p0 = Offset(c.x + (raio * cos(r.ang)).toFloat(), c.y + (raio * sin(r.ang)).toFloat())
            val p1 = Offset(c.x + (raio * 1.18f * cos(r.ang)).toFloat(), c.y + (raio * 1.18f * sin(r.ang)).toFloat())
            val yLinha = r.y + 8.dp.toPx()
            val xFim = if (r.direita) xDir - 6.dp.toPx() else xEsq + 6.dp.toPx()
            val caminho = Path().apply {
                // sai radial, dobra em diagonal até a coluna do rótulo e segue reto
                val xJoelho = if (r.direita) max(p1.x, c.x + raio * 1.2f) else min(p1.x, c.x - raio * 1.2f)
                moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(xJoelho, yLinha); lineTo(xFim, yLinha)
            }
            drawPath(caminho, cor.copy(alpha = 0.8f), style = Stroke(1.dp.toPx()))
            drawCircle(cor, 3.dp.toPx(), Offset(xFim, yLinha))
            drawCircle(Cores.bg, 1.4.dp.toPx(), Offset(xFim, yLinha))

            val nome = medidor.measure(
                f.nome, estiloNome.copy(color = cor, lineHeight = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = larguraTexto),
            )
            val sub = medidor.measure(
                "${(f.valor / total * 100).roundToInt()}% · ${compacto(f.valor)}", estiloSub,
                constraints = Constraints(maxWidth = larguraTexto),
            )
            val x = if (r.direita) xDir else xEsq - max(nome.size.width, sub.size.width)
            drawText(nome, topLeft = Offset(x, r.y))
            drawText(sub, topLeft = Offset(if (r.direita) xDir else xEsq - sub.size.width, r.y + nome.size.height))
        }
    }
}

/**
 * Barras verticais em pílula: trilho de fundo, preenchimento arredondado e o
 * valor no topo de cada barra; pontos coloridos na linha pontilhada da base.
 */
@Composable
fun BarrasPilula(barras: List<Fatia>, onToque: (String) -> Unit, modifier: Modifier = Modifier) {
    if (barras.isEmpty()) return
    val medidor = rememberTextMeasurer()
    val maximo = barras.maxOf { it.valor }.takeIf { it > 0 } ?: 1.0
    val estiloValor = TextStyle(color = Cores.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val estiloNome = TextStyle(color = Cores.muted, fontSize = 9.sp, lineHeight = 10.sp)

    Canvas(
        modifier.fillMaxWidth().height(250.dp).pointerInput(barras) {
            detectTapGestures { p ->
                val slot = size.width / barras.size
                barras.getOrNull((p.x / slot).toInt())?.chave?.let(onToque)
            }
        },
    ) {
        val slot = size.width / barras.size
        val larg = min(slot * 0.62f, 40.dp.toPx())
        val topo = 4.dp.toPx()
        val baseTrilho = size.height - 54.dp.toPx()
        val altTrilho = baseTrilho - topo
        val yPontos = baseTrilho + 12.dp.toPx()

        // linha pontilhada da base
        drawLine(
            Cores.line2, Offset(slot / 2, yPontos), Offset(size.width - slot / 2, yPontos),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)),
        )

        barras.forEachIndexed { i, b ->
            val cor = corDaFatia(b, i)
            val cx = slot * i + slot / 2
            val raio = CornerRadius(larg / 2, larg / 2)
            drawRoundRect(Cores.surface2, Offset(cx - larg / 2, topo), Size(larg, altTrilho), raio)
            val alt = max((b.valor / maximo * altTrilho).toFloat(), larg)
            drawRoundRect(cor, Offset(cx - larg / 2, baseTrilho - alt), Size(larg, alt), raio)

            val v = medidor.measure(compacto(b.valor), estiloValor)
            val yv = baseTrilho - alt + (larg - v.size.height) / 2
            drawText(v, topLeft = Offset(cx - v.size.width / 2, yv))

            drawCircle(cor, 4.dp.toPx(), Offset(cx, yPontos))
            val nome = medidor.measure(
                b.nome, estiloNome, maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = (slot - 2.dp.toPx()).toInt().coerceAtLeast(10)),
            )
            drawText(nome, topLeft = Offset(cx - nome.size.width / 2, yPontos + 8.dp.toPx()))
        }
    }
}

/** Linhas do histórico de estoque, com área translúcida e destaque no último ponto. */
@Composable
fun Linhas(series: List<Serie>, modifier: Modifier = Modifier) {
    if (series.isEmpty()) return
    val medidor = rememberTextMeasurer()
    val estiloEixo = TextStyle(color = Cores.label, fontSize = 9.sp)
    val datas = series.first().pontos.map { it.first }
    val maximo = series.maxOf { s -> s.pontos.maxOf { it.second } }.takeIf { it > 0 } ?: 1.0

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val esq = 40.dp.toPx()
        val dir = size.width - 10.dp.toPx()
        val topo = 10.dp.toPx()
        val base = size.height - 22.dp.toPx()
        fun x(i: Int) = if (datas.size == 1) (esq + dir) / 2 else esq + (dir - esq) * i / (datas.size - 1)
        fun y(v: Double) = (base - (v / maximo) * (base - topo)).toFloat()

        for (k in 0..4) {
            val v = maximo * k / 4
            val yy = y(v)
            drawLine(Cores.line, Offset(esq, yy), Offset(dir, yy), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
            val t = medidor.measure(compacto(v), estiloEixo)
            drawText(t, topLeft = Offset(esq - t.size.width - 6.dp.toPx(), yy - t.size.height / 2))
        }
        val idxRotulos = listOf(0, datas.size / 2, datas.size - 1).distinct()
        idxRotulos.forEach { i ->
            val d = datas[i]
            val txt = if (d.length >= 10) "${d.substring(8, 10)}/${d.substring(5, 7)}" else d
            val t = medidor.measure(txt, estiloEixo)
            val xx = (x(i) - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(xx, base + 6.dp.toPx()))
        }

        series.forEachIndexed { si, s ->
            val cor = Cores.daSerie(si)
            val linha = Path()
            s.pontos.forEachIndexed { i, (_, v) ->
                if (i == 0) linha.moveTo(x(i), y(v)) else linha.lineTo(x(i), y(v))
            }
            val area = Path().apply {
                addPath(linha)
                lineTo(x(s.pontos.lastIndex), base); lineTo(x(0), base); close()
            }
            drawPath(area, cor.copy(alpha = 0.08f))
            drawPath(linha, cor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val ult = s.pontos.last()
            val pu = Offset(x(s.pontos.lastIndex), y(ult.second))
            drawCircle(cor.copy(alpha = 0.25f), 9.dp.toPx(), pu)
            drawCircle(cor, 4.dp.toPx(), pu)
            drawCircle(Cores.text, 1.6.dp.toPx(), pu)
        }
    }
}

