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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.PaMatcher
import br.com.camda.principioativo.core.Produto
import br.com.camda.principioativo.core.normaliza

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
