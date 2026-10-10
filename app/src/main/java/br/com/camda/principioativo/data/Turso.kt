package br.com.camda.principioativo.data

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
 * Leitura do Turso pela API HTTP do libSQL (`/v2/pipeline`). Só SELECT:
 * o app nunca escreve no banco. As quatro consultas vão numa única
 * requisição; tabela que não existir vira lista vazia + aviso.
 */
object Turso {

    /** Ordem das consultas = ordem dos resultados no pipeline. */
    val CONSULTAS = listOf(
        "SELECT codigo, produto, categoria, qtd_sistema FROM estoque_mestre",
        "SELECT produto, principio_ativo FROM principios_ativos",
        """
        SELECT p.pos_key, p.quantidade, p.unidade, mp.nome,
               COALESCE(mp.codigo, '') || '|' ||
               COALESCE((SELECT group_concat(c.codigo, '|')
                           FROM mapa_produtos_codigos c
                          WHERE c.produto_id = mp.produto_id), '')
          FROM mapa_posicoes p
          JOIN mapa_produtos mp ON mp.produto_id = p.produto_id
         WHERE p.produto_id IS NOT NULL
        """.trimIndent(),
        """
        SELECT codigo, data_upload, qtd_estoque
          FROM vendas_historico
         WHERE data_upload >= date('now', '-180 days')
         ORDER BY data_upload
        """.trimIndent(),
    )

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

    fun corpoRequisicao(): String = buildJsonObject {
        put("requests", buildJsonArray {
            CONSULTAS.forEach { sql ->
                add(buildJsonObject {
                    put("type", "execute")
                    put("stmt", buildJsonObject { put("sql", sql) })
                })
            }
            add(buildJsonObject { put("type", "close") })
        })
    }.toString()

    /** Faz a requisição e devolve o JSON cru (que também vai pro cache offline). */
    fun buscar(url: String, token: String): String {
        val con = URL(urlPipeline(url)).openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 15_000
            con.readTimeout = 60_000
            con.doOutput = true
            con.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            con.setRequestProperty("Content-Type", "application/json")
            con.outputStream.use { it.write(corpoRequisicao().toByteArray()) }
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

    /** Converte a resposta do pipeline em [Snapshot]. */
    fun parse(corpo: String): Snapshot {
        val raiz = json.parseToJsonElement(corpo).jsonObject
        val resultados = raiz["results"]?.jsonArray ?: throw IOException("Resposta do Turso sem 'results'.")
        val avisos = mutableListOf<String>()
        val nomes = listOf("estoque_mestre", "principios_ativos", "mapa de estoque", "histórico (vendas_historico)")

        fun linhas(i: Int): List<List<JsonElement>> {
            val r = resultados.getOrNull(i)?.jsonObject ?: return emptyList()
            if (r["type"]?.jsonPrimitive?.contentOrNull != "ok") {
                val msg = r["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "erro"
                if (i == 0) throw IOException("Não consegui ler estoque_mestre: $msg")
                avisos += "Sem ${nomes[i]}: $msg"
                return emptyList()
            }
            val rows = r["response"]?.jsonObject?.get("result")?.jsonObject?.get("rows") as? JsonArray
                ?: return emptyList()
            return rows.map { it.jsonArray.toList() }
        }

        val estoque = linhas(0).map {
            LinhaEstoque(texto(it[0]), texto(it[1]), texto(it[2]), numero(it[3]) ?: 0.0)
        }
        val pas = linhas(1).map { LinhaPa(texto(it[0]), texto(it[1])) }
        val mapa = linhas(2).map {
            LinhaMapa(
                posKey = texto(it[0]),
                quantidade = numero(it[1]),
                unidade = texto(it[2]),
                nomeProduto = texto(it[3]),
                codigos = texto(it[4]).split('|').filter { c -> c.isNotBlank() },
            )
        }
        val hist = linhas(3).map { LinhaHistorico(texto(it[0]), texto(it[1]), numero(it[2]) ?: 0.0) }
        return Snapshot(estoque, pas, mapa, hist, avisos)
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
