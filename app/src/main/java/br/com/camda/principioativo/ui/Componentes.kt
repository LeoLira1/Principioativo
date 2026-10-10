package br.com.camda.principioativo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RaioCard = RoundedCornerShape(20.dp)
val RaioLinha = RoundedCornerShape(12.dp)
val RaioPilula = RoundedCornerShape(999.dp)

/** Card do dashboard: fundo #1C1D21, sem borda, raio 20. */
@Composable
fun Card(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RaioCard).background(Cores.surface).padding(16.dp),
        content = conteudo,
    )
}

/** Rótulo pequeno em maiúsculas (TOTAL, OK…). */
@Composable
fun Rotulo(texto: String, modifier: Modifier = Modifier, cor: Color = Cores.label) {
    Text(
        texto.uppercase(), modifier, color = cor, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
    )
}

@Composable
fun Kpi(titulo: String, valor: String, cor: Color, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier.clip(RaioLinha).background(Cores.surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(titulo, color = Cores.label, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(valor, color = cor, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = Cores.label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pílula selecionável: ativa = limão com texto preto. */
@Composable
fun Pilula(texto: String, ativa: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RaioPilula)
            .background(if (ativa) Cores.accent else Cores.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            texto, color = if (ativa) Cores.onAccent else Cores.text2, fontSize = 13.sp,
            fontWeight = if (ativa) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** Etiqueta pequena (P.A. da mistura, embalagem, localização). */
@Composable
fun Etiqueta(texto: String, cor: Color = Cores.text2, fundo: Color = Cores.surface2, onClick: (() -> Unit)? = null) {
    Box(
        Modifier.clip(RaioPilula).background(fundo)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(texto, color = cor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinhaEtiquetas(conteudo: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        conteudo()
    }
}

@Composable
fun Ponto(cor: Color, tamanho: Int = 10) {
    Box(Modifier.size(tamanho.dp).clip(CircleShape).background(cor))
}

@Composable
fun Codigo(texto: String) {
    Text(texto, color = Cores.label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
}

@Composable
fun Espaco(dp: Int) = Spacer(Modifier.height(dp.dp).width(dp.dp))

@Composable
fun TituloSecao(texto: String, extra: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Rotulo(texto)
        if (extra != null) {
            Spacer(Modifier.width(6.dp))
            Text(extra, color = Cores.label, fontSize = 11.sp)
        }
    }
}
