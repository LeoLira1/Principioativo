package br.com.camda.principioativo.data

import br.com.camda.principioativo.core.LinhaClasseVet
import br.com.camda.principioativo.core.LinhaEstoque
import br.com.camda.principioativo.core.LinhaHistorico
import br.com.camda.principioativo.core.LinhaMapa
import br.com.camda.principioativo.core.LinhaPa
import br.com.camda.principioativo.core.Snapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Leitura do Turso pela API HTTP do libSQL (`/v2/pipeline`). Duas requisições:
 * a base (estoque, P.A., mapa, classes), que monta a lista, e o histórico,
 * que só alimenta o gráfico de linhas e vem depois, sem segurar a abertura.
 * Tabela que não existir vira lista vazia + aviso.
 */
object Turso {

    private const val SQL_ESTOQUE = "SELECT codigo, produto, categoria, qtd_sistema FROM estoque_mestre"
    private const val SQL_PAS = "SELECT produto, principio_ativo FROM principios_ativos"
    private val SQL_MAPA = """
        SELECT p.pos_key, p.quantidade, p.unidade, mp.nome,
               COALESCE(mp.codigo, '') || '|' ||
               COALESCE((SELECT group_concat(c.codigo, '|')
                           FROM mapa_produtos_codigos c
                          WHERE c.produto_id = mp.produto_id), '')
          FROM mapa_posicoes p
          JOIN mapa_produtos mp ON mp.produto_id = p.produto_id
         WHERE p.produto_id IS NOT NULL
        """.trimIndent()
    private const val SQL_CLASSES = "SELECT codigo, classe, especie, segmento FROM classificacao_vet"

    /**
     * Só o último upload de cada código em cada dia — o único que o gráfico
     * usa ([br.com.camda.principioativo.core.Consulta.historico] fica com o
     * último do dia). No SQLite, com `max()`, as outras colunas vêm da mesma
     * linha do máximo.
     */
    val SQL_HISTORICO = """
        SELECT codigo, max(data_upload) AS d, qtd_estoque
          FROM vendas_historico
         WHERE data_upload >= date('now', '-180 days')
         GROUP BY codigo, substr(data_upload, 1, 10)
         ORDER BY d
        """.trimIndent()

    /** Ordem das consultas = ordem dos resultados no pipeline. */
    val CONSULTAS_BASE = listOf(SQL_ESTOQUE, SQL_PAS, SQL_MAPA, SQL_CLASSES)

    /** libsql://banco.turso.io → https://banco.turso.io/v2/pipeline */
    fun urlPipeline(url: String): String {
        var u = url.trim().removeSuffix("/")
        u = when {
            u.startsWith("libsql://") -> "https://" + u.removePrefix("libsql://")
            u.startsWith("wss://") -> "https://" + u.removePrefix("wss://")
            u.startsWith("ws://") -> "http://" + u.removePrefix("ws://")
            u.startsWith("http://") || u.startsWith("https://") -> u
            else -> "https://$u"
        }
        return "$u/v2/pipeline"
    }

    fun corpoRequisicao(consultas: List<String>): String = buildJsonObject {
        put("requests", buildJsonArray {
            consultas.forEach { sql ->
                add(buildJsonObject {
                    put("type", "execute")
                    put("stmt", buildJsonObject { put("sql", sql) })
                })
            }
            add(buildJsonObject { put("type", "close") })
        })
    }.toString()

    /**
     * Cadastro/troca do P.A. de um produto — mesmo efeito do
     * `upsert_principio_ativo` do dashboard: apaga a linha do nome (em
     * MAIÚSCULAS) e insere a nova, numa transação.
     */
    fun corpoSalvarPa(produto: String, principioAtivo: String, categoria: String): String {
        val chave = produto.trim().uppercase(java.util.Locale.ROOT)
        fun exec(sql: String, vararg args: String) = buildJsonObject {
            put("type", "execute")
            put("stmt", buildJsonObject {
                put("sql", sql)
                put("args", buildJsonArray {
                    args.forEach { a -> add(buildJsonObject { put("type", "text"); put("value", a) }) }
                })
            })
        }
        return buildJsonObject {
            put("requests", buildJsonArray {
                add(exec("BEGIN"))
                add(exec("DELETE FROM principios_ativos WHERE produto = ?", chave))
                add(exec(
                    "INSERT INTO principios_ativos (produto, principio_ativo, categoria) VALUES (?, ?, ?)",
                    chave, principioAtivo.trim(), categoria.trim(),
                ))
                add(exec("COMMIT"))
                add(buildJsonObject { put("type", "close") })
            })
        }.toString()
    }

    /**
     * Classifica produtos da loja (classe, espécie, segmento) — mesmo efeito do
     * `salvar_classificacao_vet` do dashboard: `INSERT OR REPLACE` por código
     * (em MAIÚSCULAS), numa transação. [agora] = "yyyy-MM-dd HH:mm:ss" (Brasília).
     */
    fun corpoSalvarClassificacao(
        codigos: List<String>, classe: String, especie: String, segmento: String, agora: String,
    ): String {
        fun exec(sql: String, vararg args: String) = buildJsonObject {
            put("type", "execute")
            put("stmt", buildJsonObject {
                put("sql", sql)
                if (args.isNotEmpty()) put("args", buildJsonArray {
                    args.forEach { a -> add(buildJsonObject { put("type", "text"); put("value", a) }) }
                })
            })
        }
        val chaves = codigos.map { it.trim().uppercase(java.util.Locale.ROOT) }.filter { it.isNotEmpty() }.distinct()
        return buildJsonObject {
            put("requests", buildJsonArray {
                add(exec("BEGIN"))
                // o dashboard cria a tabela; aqui só garante, com o mesmo formato
                add(exec(
                    "CREATE TABLE IF NOT EXISTS classificacao_vet (codigo TEXT PRIMARY KEY, classe TEXT NOT NULL, " +
                        "especie TEXT DEFAULT '', segmento TEXT DEFAULT '', origem TEXT DEFAULT 'manual', " +
                        "confianca TEXT DEFAULT '', obs TEXT DEFAULT '', atualizado_em TEXT)",
                ))
                chaves.forEach { cod ->
                    add(exec(
                        "INSERT OR REPLACE INTO classificacao_vet " +
                            "(codigo, classe, especie, segmento, origem, confianca, obs, atualizado_em) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        cod, classe.trim(), especie.trim(), segmento.trim(), "app", "", "", agora,
                    ))
                }
                add(exec("COMMIT"))
                add(buildJsonObject { put("type", "close") })
            })
        }.toString()
    }

    /** Envia uma escrita; qualquer passo com erro vira exceção com mensagem legível. */
    fun escrever(url: String, token: String, corpo: String) {
        val resp = post(url, token, corpo)
        val resultados = json.parseToJsonElement(resp).jsonObject["results"]?.jsonArray
            ?: throw IOException("Resposta do Turso sem 'results'.")
        resultados.forEach { r ->
            if (r.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "error") {
                val msg = r.jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (msg.contains("readonly", true) || msg.contains("read-only", true) ||
                    msg.contains("permission", true) || msg.contains("not allowed", true)
                ) throw IOException(MSG_SO_LEITURA)
                throw IOException("Não salvou: $msg")
            }
        }
    }

    const val MSG_SO_LEITURA =
        "Este token é só de leitura. Para cadastrar, conecte com um token com escrita (⚙️ → Desconectar)."

    /** Faz as requisições e devolvem o JSON cru (que também vai pro cache offline). */
    fun buscarBase(url: String, token: String): String = post(url, token, corpoRequisicao(CONSULTAS_BASE))

    fun buscarHistorico(url: String, token: String): String = post(url, token, corpoRequisicao(listOf(SQL_HISTORICO)))

    private fun post(url: String, token: String, corpo: String): String {
        val con = URL(urlPipeline(url)).openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 15_000
            con.readTimeout = 60_000
            con.doOutput = true
            con.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(corpo.toByteArray()) }
            val code = con.responseCode
            if (code == 401 || code == 403) throw IOException("Token recusado pelo Turso ($code). Confira o token.")
            if (code !in 200..299) {
                val msg = con.errorStream?.bufferedReader()?.readText()?.take(300).orEmpty()
                throw IOException("Turso respondeu $code. $msg")
            }
            return con.inputStream.bufferedReader().readText()
        } finally {
            con.disconnect()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun resultados(corpo: String): JsonArray =
        json.parseToJsonElement(corpo).jsonObject["results"]?.jsonArray
            ?: throw IOException("Resposta do Turso sem 'results'.")

    /** Linhas do resultado [i]; erro na consulta obrigatória vira exceção, nas outras aviso. */
    private fun linhas(
        resultados: JsonArray, i: Int, nome: String, avisos: MutableList<String>, obrigatoria: Boolean = false,
    ): List<List<JsonElement>> {
        val r = resultados.getOrNull(i)?.jsonObject ?: return emptyList()
        if (r["type"]?.jsonPrimitive?.contentOrNull != "ok") {
            val msg = r["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "erro"
            if (obrigatoria) throw IOException("Não consegui ler $nome: $msg")
            avisos += "Sem $nome: $msg"
            return emptyList()
        }
        val rows = r["response"]?.jsonObject?.get("result")?.jsonObject?.get("rows") as? JsonArray
            ?: return emptyList()
        return rows.map { it.jsonArray.toList() }
    }

    private fun estoque(l: List<List<JsonElement>>) =
        l.map { LinhaEstoque(texto(it[0]), texto(it[1]), texto(it[2]), numero(it[3]) ?: 0.0) }

    private fun pas(l: List<List<JsonElement>>) = l.map { LinhaPa(texto(it[0]), texto(it[1])) }

    private fun mapa(l: List<List<JsonElement>>) = l.map {
        LinhaMapa(
            posKey = texto(it[0]),
            quantidade = numero(it[1]),
            unidade = texto(it[2]),
            nomeProduto = texto(it[3]),
            codigos = texto(it[4]).split('|').filter { c -> c.isNotBlank() },
        )
    }

    private fun historico(l: List<List<JsonElement>>) =
        l.map { LinhaHistorico(texto(it[0]), texto(it[1]), numero(it[2]) ?: 0.0) }

    private fun classes(l: List<List<JsonElement>>) =
        l.map { LinhaClasseVet(texto(it[0]), texto(it[1]), texto(it[2]), texto(it[3])) }

    /** Resposta de [CONSULTAS_BASE] → [Snapshot] sem histórico. */
    fun parseBase(corpo: String): Snapshot {
        val r = resultados(corpo)
        val avisos = mutableListOf<String>()
        return Snapshot(
            estoque = estoque(linhas(r, 0, "estoque_mestre", avisos, obrigatoria = true)),
            pas = pas(linhas(r, 1, "principios_ativos", avisos)),
            mapa = mapa(linhas(r, 2, "mapa de estoque", avisos)),
            historico = emptyList(),
            avisos = avisos,
            classes = classes(linhas(r, 3, "classificação vet", avisos)),
        )
    }

    /** Resposta de [SQL_HISTORICO]; tabela ausente vira lista vazia (o gráfico some). */
    fun parseHistorico(corpo: String): List<LinhaHistorico> =
        historico(linhas(resultados(corpo), 0, "histórico (vendas_historico)", mutableListOf()))

    /**
     * Cache gravado antes da divisão base/histórico: as cinco consultas numa
     * resposta só (estoque, P.A., mapa, histórico, classes).
     */
    fun parse(corpo: String): Snapshot {
        val r = resultados(corpo)
        val avisos = mutableListOf<String>()
        return Snapshot(
            estoque = estoque(linhas(r, 0, "estoque_mestre", avisos, obrigatoria = true)),
            pas = pas(linhas(r, 1, "principios_ativos", avisos)),
            mapa = mapa(linhas(r, 2, "mapa de estoque", avisos)),
            historico = historico(linhas(r, 3, "histórico (vendas_historico)", avisos)),
            avisos = avisos,
            // cache salvo por versão ainda mais antiga não tem este resultado → lista vazia
            classes = classes(linhas(r, 4, "classificação vet", avisos)),
        )
    }

    /** Valor do Hrana: {"type":"text","value":"x"} / integer (string) / float / null. */
    private fun valor(e: JsonElement): JsonPrimitive? = when (e) {
        is JsonObject -> (e["value"] as? JsonPrimitive)?.takeUnless { e["type"]?.jsonPrimitive?.contentOrNull == "null" }
        is JsonPrimitive -> e
        else -> null
    }

    private fun texto(e: JsonElement): String = valor(e)?.contentOrNull ?: ""

    private fun numero(e: JsonElement): Double? = valor(e)?.contentOrNull?.replace(',', '.')?.toDoubleOrNull()
}
