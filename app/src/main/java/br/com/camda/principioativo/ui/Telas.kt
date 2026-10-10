package br.com.camda.principioativo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.camda.principioativo.core.Consulta
import br.com.camda.principioativo.core.FiltroEstoque
import br.com.camda.principioativo.core.Filtros
import br.com.camda.principioativo.core.GrupoPa
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.PaMatcher
import br.com.camda.principioativo.core.Produto
import br.com.camda.principioativo.core.Resultado
import br.com.camda.principioativo.core.TipoEmbalagem
import br.com.camda.principioativo.core.fmtInt
import br.com.camda.principioativo.core.fmtNum
import br.com.camda.principioativo.core.normaliza
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// Raiz
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun App(vm: MainViewModel, estado: Estado) {
    Box(Modifier.fillMaxSize().background(Cores.bg).safeDrawingPadding()) {
        if (!estado.conectado) {
            TelaConexao(estado, vm::conectar)
            return@Box
        }
        val filtrados = remember(estado.produtos, estado.filtros) {
            Consulta.aplicaFiltros(estado.produtos, estado.filtros)
        }
        val resultado = remember(filtrados) { Consulta.agrupar(filtrados) }

        val aberto = estado.paAberto
        if (aberto != null) {
            BackHandler { vm.abrir(null) }
            TelaDetalhe(aberto, resultado, estado, vm)
        } else {
            TelaInicial(resultado, estado, vm)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Conexão
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TelaConexao(estado: Estado, conectar: (String, String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Espaco(24)
            Text("Princípio Ativo", color = Cores.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Consulta do estoque CAMDA por princípio ativo", color = Cores.muted, fontSize = 14.sp)
            Espaco(24)
            Card {
                Rotulo("Conectar ao banco")
                Espaco(12)
                CampoConexao(url, { url = it }, "URL do banco (libsql://…turso.io)", senha = false)
                Espaco(10)
                CampoConexao(token, { token = it }, "Token de acesso", senha = true)
                Espaco(12)
                Text(
                    "Crie um token só para este app:\n" +
                        "turso db tokens create <banco>\n" +
                        "(com --read-only o app só consulta e não cadastra P.A.)\n\n" +
                        "A URL e o token ficam guardados criptografados neste aparelho. " +
                        "Nada vai no código nem no APK.",
                    color = Cores.muted, fontSize = 12.sp, lineHeight = 17.sp,
                )
                Espaco(16)
                Button(
                    onClick = { conectar(url, token) },
                    enabled = url.isNotBlank() && token.isNotBlank() && !estado.carregando,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RaioPilula,
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.accent, contentColor = Cores.onAccent),
                ) {
                    if (estado.carregando) CircularProgressIndicator(Modifier.size(20.dp), color = Cores.onAccent, strokeWidth = 2.dp)
                    else Text("Conectar", fontWeight = FontWeight.Bold)
                }
                estado.erro?.let {
                    Espaco(12)
                    Text(it, color = Cores.crit, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CampoConexao(valor: String, mudou: (String) -> Unit, rotulo: String, senha: Boolean) {
    OutlinedTextField(
        value = valor, onValueChange = mudou, label = { Text(rotulo) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (senha) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (senha) KeyboardType.Password else KeyboardType.Uri),
        shape = RaioLinha,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cores.text2, unfocusedBorderColor = Cores.line2,
            focusedLabelColor = Cores.text2, unfocusedLabelColor = Cores.label, cursorColor = Cores.text,
        ),
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// Tela inicial
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TelaInicial(res: Resultado, estado: Estado, vm: MainViewModel) {
    var painelAberto by rememberSaveable { mutableStateOf(false) }
    var config by remember { mutableStateOf(false) }
    val fatias = remember(res) { Consulta.fatias(res.grupos, 8) }
    val barras = remember(res) { res.grupos.filter { it.volume > 0 }.take(8).map { br.com.camda.principioativo.core.Fatia(it.nome, it.volume, it.chave) } }
    val series = remember(res, estado.snapshot) {
        Consulta.historico(res.grupos, estado.snapshot?.historico ?: emptyList(), 4)
    }
    val categorias = remember(estado.produtos) {
        estado.produtos.map { it.categoria }.filter { it.isNotBlank() }.distinct().sorted()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largo = maxWidth >= 720.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (largo) 24.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mod = Modifier.widthIn(max = 1100.dp).fillMaxWidth()
            item { Cabecalho(estado, vm, onConfig = { config = true }, modifier = mod) }
            item {
                Column(mod) {
                    CampoBusca(estado.filtros.busca) { vm.filtros(estado.filtros.copy(busca = it)) }
                    Espaco(8)
                    BotaoFiltros(estado.filtros.ativosNoPainel, painelAberto) { painelAberto = !painelAberto }
                    AnimatedVisibility(painelAberto) {
                        PainelFiltros(estado.filtros, categorias, vm::filtros)
                    }
                }
            }
            if (estado.produtos.isEmpty()) {
                item {
                    Card(mod) {
                        Text(
                            if (estado.carregando) "Carregando estoque…" else "Nenhum produto no estoque ainda.",
                            color = Cores.muted,
                        )
                    }
                }
                return@LazyColumn
            }
            item { Kpis(res, mod, largo) }

            if (fatias.isNotEmpty()) {
                item {
                    if (largo) {
                        Row(mod, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardRosca(fatias, vm, Modifier.weight(1.15f))
                            CardBarras(barras, vm, Modifier.weight(1f))
                        }
                    } else {
                        Column(mod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardRosca(fatias, vm, Modifier)
                            CardBarras(barras, vm, Modifier)
                        }
                    }
                }
            }
            if (series.isNotEmpty()) {
                item {
                    Card(mod) {
                        Rotulo("Evolução do estoque")
                        Text("Top ${series.size} P.A. · L + kg por dia de upload", color = Cores.label, fontSize = 11.sp)
                        Espaco(8)
                        LinhaEtiquetas {
                            series.forEachIndexed { i, s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Ponto(Cores.daSerie(i), 8); Spacer(Modifier.width(4.dp))
                                    Text(s.nome, color = Cores.text2, fontSize = 11.sp)
                                }
                            }
                        }
                        Espaco(8)
                        Linhas(series)
                    }
                }
            }

            item { Box(mod) { TituloSecao("Princípios ativos", "(${res.grupos.size})") } }
            if (res.grupos.isEmpty()) {
                item { Card(mod) { Text("Nenhum P.A. com os filtros atuais.", color = Cores.muted) } }
            }
            val maxVol = res.grupos.maxOfOrNull { it.volume }?.takeIf { it > 0 } ?: 1.0
            items(res.grupos, key = { it.chave }) { g ->
                val idx = res.grupos.indexOf(g)
                val cor = if (g.volume > 0 && idx < 8) Cores.daSerie(idx) else Cores.outros
                LinhaGrupo(g, cor, maxVol, mod) { vm.abrir(g.chave) }
            }
            item {
                Column(mod) {
                    TituloSecao("Falta mapear")
                    LinhaSemPa(res.semPa.size) { vm.abrir(CHAVE_SEM_PA) }
                    Espaco(10)
                    Text(
                        "Misturas (ex.: Diuron + Hexazinona) entram no grupo de cada P.A., então a soma " +
                            "dos grupos pode passar do total do estoque. Volume = litros + kg.",
                        color = Cores.label, fontSize = 11.sp, lineHeight = 15.sp,
                    )
                    Espaco(24)
                }
            }
        }
    }

    if (config) {
        AlertDialog(
            onDismissRequest = { config = false },
            containerColor = Cores.surface,
            title = { Text("Conexão", color = Cores.text) },
            text = {
                Text(
                    "Desconectar apaga a URL, o token e o cache deste aparelho. " +
                        "Você vai precisar digitar de novo.",
                    color = Cores.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { config = false; vm.desconectar() }) { Text("Desconectar", color = Cores.crit) }
            },
            dismissButton = { TextButton(onClick = { config = false }) { Text("Fechar", color = Cores.text2) } },
        )
    }
}

@Composable
private fun Cabecalho(estado: Estado, vm: MainViewModel, onConfig: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).height(36.dp).clip(RaioPilula).background(Cores.accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Princípios Ativos", color = Cores.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                val quando = if (estado.atualizadoEm > 0)
                    "atualizado " + SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(estado.atualizadoEm))
                else "sem dados ainda"
                Text(quando, color = Cores.label, fontSize = 12.sp)
            }
            IconButton(onClick = vm::atualizar, enabled = !estado.carregando) {
                Icon(Icons.Filled.Refresh, "Atualizar", tint = Cores.text2)
            }
            IconButton(onClick = onConfig) { Icon(Icons.Filled.Settings, "Conexão", tint = Cores.text2) }
        }
        if (estado.carregando) {
            Espaco(6)
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Cores.accent, trackColor = Cores.surface2)
        }
        estado.erro?.let {
            Espaco(8)
            Row(
                Modifier.fillMaxWidth().clip(RaioLinha).background(Cores.critSoft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Não deu pra atualizar: $it" + if (estado.produtos.isNotEmpty()) "\nMostrando a última leitura salva." else "",
                    color = Cores.text, fontSize = 12.sp, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::limparErro) { Icon(Icons.Filled.Close, "Fechar", tint = Cores.text2) }
            }
        }
        estado.snapshot?.avisos?.takeIf { it.isNotEmpty() }?.let { avisos ->
            Espaco(6)
            Text(avisos.joinToString("\n"), color = Cores.warn, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CampoBusca(valor: String, mudou: (String) -> Unit) {
    TextField(
        value = valor, onValueChange = mudou, singleLine = true,
        placeholder = { Text("P.A., nome ou código do produto…", color = Cores.label) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cores.label) },
        trailingIcon = {
            if (valor.isNotEmpty()) IconButton(onClick = { mudou("") }) { Icon(Icons.Filled.Close, "Limpar", tint = Cores.label) }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RaioPilula,
        modifier = Modifier.fillMaxWidth(),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Cores.surface, unfocusedContainerColor = Cores.surface,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Cores.text, unfocusedTextColor = Cores.text, cursorColor = Cores.text,
        ),
    )
}

@Composable
private fun BotaoFiltros(ativos: Int, aberto: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RaioPilula).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tune, null, tint = Cores.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Filtros", color = Cores.text2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (ativos > 0) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.clip(RaioPilula).background(Cores.accent).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("$ativos", color = Cores.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Icon(if (aberto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Cores.label)
    }
}

@Composable
private fun PainelFiltros(f: Filtros, categorias: List<String>, mudou: (Filtros) -> Unit) {
    Card(Modifier.padding(top = 6.dp)) {
        Rotulo("Estoque")
        Espaco(8)
        LinhaEtiquetas {
            FiltroEstoque.entries.forEach { e -> Pilula(e.rotulo, f.estoque == e) { mudou(f.copy(estoque = e)) } }
        }
        Espaco(14)
        Rotulo("Embalagem")
        Espaco(8)
        LinhaEtiquetas {
            TipoEmbalagem.entries.forEach { t ->
                Pilula(t.rotulo, t in f.embalagens) {
                    mudou(f.copy(embalagens = if (t in f.embalagens) f.embalagens - t else f.embalagens + t))
                }
            }
        }
        if (categorias.isNotEmpty()) {
            Espaco(14)
            Rotulo("Categoria")
            Espaco(8)
            LinhaEtiquetas {
                categorias.forEach { c ->
                    Pilula(c, c in f.categorias) {
                        mudou(f.copy(categorias = if (c in f.categorias) f.categorias - c else f.categorias + c))
                    }
                }
            }
        }
        if (f.ativosNoPainel > 0) {
            Espaco(10)
            TextButton(onClick = { mudou(Filtros(busca = f.busca)) }) { Text("Limpar filtros", color = Cores.text2) }
        }
    }
}

@Composable
private fun Kpis(res: Resultado, modifier: Modifier, largo: Boolean) {
    val litros = res.grupos.flatMap { it.produtos }.distinct().sumOf { it.quantidade.litros } +
        res.semPa.sumOf { it.quantidade.litros }
    val kg = res.grupos.flatMap { it.produtos }.distinct().sumOf { it.quantidade.kg } +
        res.semPa.sumOf { it.quantidade.kg }
    val itens = listOf(
        Triple("Princípios ativos", fmtInt(res.grupos.size.toDouble()), Cores.ok),
        Triple("Produtos", fmtInt(res.totalProdutos.toDouble()), Cores.info),
        Triple("Litros", fmtInt(litros), Cores.warn),
        Triple("Quilos", fmtNum(kg), Cores.purple),
    )
    if (largo) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itens.forEach { (t, v, c) -> Kpi(t, v, c, Modifier.weight(1f)) }
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itens.chunked(2).forEach { par ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    par.forEach { (t, v, c) -> Kpi(t, v, c, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun CardRosca(fatias: List<br.com.camda.principioativo.core.Fatia>, vm: MainViewModel, modifier: Modifier) {
    Card(modifier) {
        Rotulo("Distribuição por P.A.")
        Text("Volume (L + kg) · toque numa fatia para abrir", color = Cores.label, fontSize = 11.sp)
        Espaco(4)
        Rosca(fatias, onToque = vm::abrir)
    }
}

@Composable
private fun CardBarras(barras: List<br.com.camda.principioativo.core.Fatia>, vm: MainViewModel, modifier: Modifier) {
    Card(modifier) {
        Rotulo("Volume em estoque")
        Text("Top ${barras.size} P.A. · L + kg", color = Cores.label, fontSize = 11.sp)
        Espaco(12)
        BarrasPilula(barras, onToque = vm::abrir)
    }
}

@Composable
private fun LinhaGrupo(g: GrupoPa, cor: Color, maxVol: Double, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RaioLinha).background(Cores.surface).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ponto(cor)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(g.nome, color = Cores.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val n = g.produtos.size
                val comEst = g.produtos.count { it.temEstoque }
                Text("$n ${if (n == 1) "produto" else "produtos"} · $comEst com estoque",
                    color = Cores.label, fontSize = 12.sp)
            }
            Text(g.volumeTexto(), color = Cores.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cores.label)
        }
        if (g.volume > 0) {
            Espaco(8)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RaioPilula).background(Cores.surface2)) {
                Box(Modifier.fillMaxWidth((g.volume / maxVol).toFloat().coerceIn(0.02f, 1f)).height(4.dp)
                    .clip(RaioPilula).background(cor))
            }
        }
    }
}

@Composable
private fun LinhaSemPa(n: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RaioLinha).background(Cores.critSoft).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sem P.A. cadastrado", color = Cores.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("produtos novos para mapear · toque para cadastrar", color = Cores.muted, fontSize = 12.sp)
        }
        Text("$n", color = Cores.crit, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Cores.label)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Detalhe de um P.A.
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TelaDetalhe(chave: String, res: Resultado, estado: Estado, vm: MainViewModel) {
    val semPa = chave == CHAVE_SEM_PA
    val grupo = if (semPa) null else res.grupos.find { it.chave == chave }
    val produtos = if (semPa) res.semPa else grupo?.produtos ?: emptyList()
    val titulo = if (semPa) "Sem P.A. cadastrado" else grupo?.nome ?: "P.A."
    val info = remember(chave, estado.catalogo) { infoCatalogo(chave, estado.catalogo) }
    val conhecidos = remember(estado.produtos, estado.catalogo) { paConhecidos(estado.produtos, estado.catalogo) }
    var editando by remember { mutableStateOf<Produto?>(null) }

    editando?.let { p ->
        DialogoCadastroPa(
            produto = p,
            conhecidos = conhecidos,
            salvando = estado.salvando,
            erro = estado.aviso?.takeIf { !it.first }?.second,
            onSalvar = { pas, cat -> vm.salvarPa(p, pas, cat) { ok -> if (ok) editando = null } },
            onFechar = { editando = null; vm.limparAviso() },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largo = maxWidth >= 720.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = if (largo) 24.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val mod = Modifier.widthIn(max = 900.dp).fillMaxWidth()
            item {
                Row(mod, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.abrir(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Cores.text)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(titulo, color = Cores.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        val sub = if (grupo != null) "${produtos.size} produtos · ${grupo.volumeTexto()}"
                        else "${produtos.size} produtos"
                        Text(sub, color = Cores.label, fontSize = 12.sp)
                    }
                }
            }
            if (editando == null) estado.aviso?.let { a ->
                item { FaixaAviso(a, vm::limparAviso, mod) }
            }
            if (semPa) {
                item {
                    Text(
                        "Produtos novos que ainda não casaram com nenhum nome da tabela principios_ativos. " +
                            "Toque em \"Cadastrar P.A.\" no produto para registrar.",
                        mod, color = Cores.muted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
            }
            if (info.isNotEmpty()) {
                item {
                    Card(mod) {
                        info.forEach { (k, v) ->
                            Rotulo(k); Text(v, color = Cores.text2, fontSize = 13.sp); Espaco(8)
                        }
                    }
                }
            }
            if (grupo != null) {
                item {
                    Row(mod, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Kpi("Litros", fmtInt(grupo.litros), Cores.warn, Modifier.weight(1f))
                        Kpi("Quilos", fmtNum(grupo.kg), Cores.purple, Modifier.weight(1f))
                        Kpi("Com estoque", "${produtos.count { it.temEstoque }}/${produtos.size}", Cores.ok, Modifier.weight(1f))
                    }
                }
            }
            if (produtos.isEmpty()) {
                item { Card(mod) { Text("Nenhum produto com os filtros atuais.", color = Cores.muted) } }
            }
            items(produtos, key = { it.codigo + "|" + it.nome }) { p ->
                CardProduto(p, chave, mod, onEditar = { vm.limparAviso(); editando = p }) { outro ->
                    vm.abrir(normaliza(outro))
                }
            }
            item { Espaco(24) }
        }
    }
}

/** Grupo químico / mecanismo / HRAC do catálogo, quando o P.A. aparece sozinho nele. */
private fun infoCatalogo(chave: String, catalogo: List<InfoCatalogo>): List<Pair<String, String>> {
    val itens = catalogo.filter { c -> PaMatcher.separar(c.principioAtivo).map(::normaliza) == listOf(chave) }
    if (itens.isEmpty()) return emptyList()
    fun junta(sel: (InfoCatalogo) -> String) =
        itens.map(sel).filter { it.isNotBlank() && it != "-" }.distinct().joinToString(" · ")
    return listOf(
        "Grupo químico" to junta { it.grupoQuimico },
        "Mecanismo de ação" to junta { it.mecanismo },
        "HRAC" to junta { it.hrac },
    ).filter { it.second.isNotBlank() }
}

@Composable
private fun CardProduto(
    p: Produto, chaveAtual: String, modifier: Modifier, onEditar: () -> Unit, abrirPa: (String) -> Unit,
) {
    val q = p.quantidade
    Column(
        modifier.clip(RaioLinha).background(if (p.temEstoque) Cores.surface else Cores.surface.copy(alpha = 0.55f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Codigo(p.codigo)
            Spacer(Modifier.width(8.dp))
            Etiqueta(q.emb.rotulo)
            if (p.categoria.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(p.categoria, color = Cores.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Espaco(4)
        Text(p.nome, color = if (p.temEstoque) Cores.text else Cores.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Espaco(8)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (p.temEstoque) q.fisicoTexto() else "Sem estoque",
                color = if (p.temEstoque) Cores.text else Cores.crit, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
            )
            val vol = q.volumeTexto()
            if (p.temEstoque && vol.isNotEmpty()) Text(vol, color = Cores.warn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (p.locais.isNotEmpty()) {
            Espaco(10)
            Rotulo("Localização")
            Espaco(6)
            LinhaEtiquetas {
                p.locais.forEach { l ->
                    val qtd = l.quantidade?.takeIf { it > 0 }?.let { " · ${fmtNum(it)}${if (l.unidade.isNotBlank()) " " + l.unidade else ""}" } ?: ""
                    Etiqueta("📍 ${l.rotulo}$qtd", cor = Cores.info)
                }
            }
        }
        val outros = p.pas.filter { normaliza(it) != chaveAtual }
        if (outros.isNotEmpty()) {
            Espaco(10)
            Rotulo(if (chaveAtual == CHAVE_SEM_PA) "P.A." else "Também contém")
            Espaco(6)
            LinhaEtiquetas {
                outros.forEach { o -> Etiqueta(o, cor = Cores.ok, fundo = Cores.okSoft) { abrirPa(o) } }
            }
        }
        Espaco(10)
        if (p.pas.isEmpty()) {
            Button(
                onClick = onEditar, shape = RaioPilula, modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cores.accent, contentColor = Cores.onAccent),
            ) { Text("Cadastrar P.A.", fontWeight = FontWeight.Bold) }
        } else {
            Text(
                "Editar P.A.", color = Cores.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RaioPilula).clickable(onClick = onEditar)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}
