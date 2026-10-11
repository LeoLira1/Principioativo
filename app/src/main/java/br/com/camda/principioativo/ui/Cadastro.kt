package br.com.camda.principioativo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.camda.principioativo.core.Difflib
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.PaMatcher
import br.com.camda.principioativo.core.Produto
import br.com.camda.principioativo.core.normaliza
import br.com.camda.principioativo.core.normalizaCodigo

/** Todos os P.A. conhecidos (estoque + catálogo), para sugerir enquanto digita. */
fun paConhecidos(produtos: List<Produto>, catalogo: List<InfoCatalogo>): List<String> =
    (produtos.flatMap { it.pas } + catalogo.flatMap { PaMatcher.separar(it.principioAtivo) })
        .distinctBy { normaliza(it) }
        .sortedBy { normaliza(it) }

/** Sugestões para o texto digitado: começa com > contém; sem repetir os já escolhidos. */
fun sugerirPa(digitado: String, conhecidos: List<String>, escolhidos: List<String>, max: Int = 6): List<String> {
    val q = normaliza(digitado)
    if (q.isEmpty()) return emptyList()
    val ja = escolhidos.map(::normaliza).toSet()
    val livres = conhecidos.filter { normaliza(it) !in ja }
    return (livres.filter { normaliza(it).startsWith(q) } + livres.filter { normaliza(it).contains(q) })
        .distinct().take(max)
}

/**
 * Busca inteligente de produto do estoque para vincular o P.A.: sem acento nem
 * maiúsculas, palavras em qualquer ordem, por nome ou código, e tolera erro de
 * digitação ("karatte" acha "KARATÊ ZEON"). Vale para TODOS os setores e categorias.
 * Quem ainda não tem P.A. vem antes; depois, o nome que começa pelo termo.
 */
fun buscarProdutos(produtos: List<Produto>, digitado: String, max: Int = 30): List<Produto> {
    val tokens = normaliza(digitado).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    val nomes = produtos.map { normaliza(it.nome) }
    val vocab = nomes.flatMap { it.split(Regex("[^A-Z0-9]+")) }.filter { it.length >= 3 }.toSet()
    // cada token vira o conjunto de formas aceitas; sem acerto literal, entram as palavras parecidas
    val formas = tokens.map { t ->
        val literal = nomes.any { it.contains(t) } || produtos.any { normalizaCodigo(it.codigo).contains(t.uppercase()) }
        if (literal || t.length < 4) setOf(t)
        else setOf(t) + vocab.filter { Difflib.ratio(t, it) >= 0.75 }
    }
    // posição do termo no produto (nome vale mais que código); null = algum termo não casou
    fun posicao(i: Int): Int? {
        val cod = normalizaCodigo(produtos[i].codigo)
        var pior = 0
        for (fs in formas) {
            var melhor: Int? = null
            for (f in fs) {
                val no = nomes[i].indexOf(f)
                val co = cod.indexOf(f.uppercase())
                val achou = if (no >= 0) no else if (co >= 0) 1000 + co else null
                if (achou != null && (melhor == null || achou < melhor)) melhor = achou
            }
            if (melhor == null) return null
            if (melhor > pior) pior = melhor
        }
        return pior
    }
    return produtos.indices
        .mapNotNull { i -> posicao(i)?.let { Triple(i, produtos[i].pas.isNotEmpty(), it) } }
        .sortedWith(compareBy<Triple<Int, Boolean, Int>>({ it.second }, { it.third }, { nomes[it.first].length }))
        .take(max).map { produtos[it.first] }
}

/** Passo 1 do vínculo: achar o produto (qualquer setor) e escolher; o passo 2 é o [DialogoCadastroPa]. */
@Composable
fun DialogoBuscaProduto(
    produtos: List<Produto>,
    onEscolher: (Produto) -> Unit,
    onFechar: () -> Unit,
) {
    var digitado by rememberSaveable { mutableStateOf("") }
    val achados = remember(produtos, digitado) { buscarProdutos(produtos, digitado) }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = Cores.surface,
        title = { Text("Vincular P.A.", color = Cores.text, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = digitado, onValueChange = { digitado = it }, singleLine = true,
                    label = { Text("Produto do estoque (nome ou código)") },
                    placeholder = { Text("Ex.: karate") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cores.label) },
                    modifier = Modifier.fillMaxWidth(), shape = RaioLinha, colors = camposCores(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Spacer(Modifier.padding(4.dp))
                when {
                    digitado.isBlank() -> Text(
                        "Busca em todos os produtos do estoque, sem acento e mesmo com erro de digitação.",
                        color = Cores.label, fontSize = 12.sp,
                    )
                    achados.isEmpty() -> Text("Nenhum produto encontrado.", color = Cores.label, fontSize = 13.sp)
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(achados, key = { it.codigo.ifEmpty { it.nome } + "|" + it.nome }) { p ->
                            Column(
                                Modifier.fillMaxWidth().clickable { onEscolher(p) }.padding(vertical = 8.dp),
                            ) {
                                Text(p.nome, color = Cores.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    (if (p.pas.isEmpty()) "⚠️ sem P.A." else "✅ " + p.pas.joinToString(" + ")) +
                                        (if (p.categoria.isNotBlank()) " · ${p.categoria}" else ""),
                                    color = if (p.pas.isEmpty()) Cores.warn else Cores.ok, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onFechar) { Text("Fechar", color = Cores.text2) } },
    )
}

/**
 * Cadastro/edição do P.A. de um produto. Misturas: um ingrediente por vez
 * (vira "A + B" no banco, o mesmo formato da planilha).
 */
@Composable
fun DialogoCadastroPa(
    produto: Produto,
    conhecidos: List<String>,
    salvando: Boolean,
    erro: String?,
    onSalvar: (List<String>, String) -> Unit,
    onFechar: () -> Unit,
) {
    val escolhidos = remember(produto) { mutableStateListOf<String>().apply { addAll(produto.pas) } }
    var digitado by rememberSaveable(produto.codigo) { mutableStateOf("") }
    var categoria by rememberSaveable(produto.codigo) { mutableStateOf(produto.categoria) }
    val sugestoes = sugerirPa(digitado, conhecidos, escolhidos)

    fun adicionar(pa: String) {
        PaMatcher.separar(pa).forEach { novo ->
            // usa a grafia já conhecida quando existe ("glifosato" → "Glifosato")
            val nome = conhecidos.firstOrNull { normaliza(it) == normaliza(novo) } ?: novo
            if (escolhidos.none { normaliza(it) == normaliza(nome) }) escolhidos += nome
        }
        digitado = ""
    }

    AlertDialog(
        onDismissRequest = { if (!salvando) onFechar() },
        containerColor = Cores.surface,
        title = {
            Column {
                Text(if (produto.pas.isEmpty()) "Cadastrar P.A." else "Editar P.A.", color = Cores.text,
                    fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.padding(2.dp))
                Codigo(produto.codigo)
                Text(produto.nome, color = Cores.text2, fontSize = 14.sp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Rotulo("Princípio(s) ativo(s)")
                Spacer(Modifier.padding(4.dp))
                if (escolhidos.isEmpty()) {
                    Text("Nenhum ainda. Mistura? Adicione um de cada vez.", color = Cores.label, fontSize = 12.sp)
                } else {
                    LinhaEtiquetas {
                        escolhidos.toList().forEach { pa ->
                            Row(
                                Modifier.clip(RaioPilula).background(Cores.okSoft)
                                    .clickable(enabled = !salvando) { escolhidos.remove(pa) }
                                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(pa, color = Cores.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Filled.Close, "Remover", tint = Cores.ok, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = digitado, onValueChange = { digitado = it }, singleLine = true,
                    label = { Text("Adicionar P.A.") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (digitado.isNotBlank()) adicionar(digitado) }),
                    trailingIcon = {
                        if (digitado.isNotBlank()) IconButton(onClick = { adicionar(digitado) }) {
                            Icon(Icons.Filled.Add, "Adicionar", tint = Cores.text)
                        }
                    },
                    shape = RaioLinha,
                    colors = camposCores(),
                    enabled = !salvando,
                )
                if (sugestoes.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RaioLinha).background(Cores.surface2)
                            .heightIn(max = 240.dp),
                    ) {
                        sugestoes.forEach { s ->
                            Text(
                                s, color = Cores.text, fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().clickable { adicionar(s) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    value = categoria, onValueChange = { categoria = it }, singleLine = true,
                    label = { Text("Categoria (opcional)") }, modifier = Modifier.fillMaxWidth(),
                    shape = RaioLinha, colors = camposCores(), enabled = !salvando,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Grava em principios_ativos com o nome exato do produto, igual ao " +
                        "cadastro do dashboard. Vale também lá.",
                    color = Cores.label, fontSize = 11.sp, lineHeight = 15.sp,
                )
                if (erro != null) {
                    Spacer(Modifier.padding(4.dp))
                    Text(erro, color = Cores.crit, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // texto digitado e não adicionado também entra
                    if (digitado.isNotBlank()) adicionar(digitado)
                    onSalvar(escolhidos.toList(), categoria)
                },
                enabled = !salvando && (escolhidos.isNotEmpty() || digitado.isNotBlank()),
                shape = RaioPilula,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.accent, contentColor = Cores.onAccent),
            ) {
                if (salvando) CircularProgressIndicator(Modifier.size(18.dp), color = Cores.onAccent, strokeWidth = 2.dp)
                else Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onFechar, enabled = !salvando) { Text("Cancelar", color = Cores.text2) }
        },
    )
}

@Composable
private fun camposCores() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cores.text2, unfocusedBorderColor = Cores.line2,
    focusedLabelColor = Cores.text2, unfocusedLabelColor = Cores.label, cursorColor = Cores.text,
    focusedTextColor = Cores.text, unfocusedTextColor = Cores.text,
)

/** Faixa de confirmação/erro depois de salvar. */
@Composable
fun FaixaAviso(aviso: Pair<Boolean, String>, onFechar: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RaioLinha).background(if (aviso.first) Cores.okSoft else Cores.critSoft)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            (if (aviso.first) "✅ Salvo: " else "⚠️ ") + aviso.second,
            color = Cores.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        IconButton(onClick = onFechar) { Icon(Icons.Filled.Close, "Fechar", tint = Cores.text2) }
    }
}
