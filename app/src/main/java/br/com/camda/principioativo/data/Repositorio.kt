package br.com.camda.principioativo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.LinhaHistorico
import br.com.camda.principioativo.core.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class Credenciais(val url: String, val token: String)

private val jsonCatalogo = Json { ignoreUnknownKeys = true }

/**
 * Credenciais (cifradas no aparelho), cache offline da última leitura e o
 * catálogo embarcado. Nada de credencial no código nem no APK.
 */
class Repositorio(private val ctx: Context) {

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "pa_credenciais",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            ctx,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val arquivoBase get() = File(ctx.filesDir, "ultima_base.json")
    private val arquivoHistorico get() = File(ctx.filesDir, "ultimo_historico.json")
    private val arquivoData get() = File(ctx.filesDir, "ultima_leitura.txt")
    /** Cache de antes da divisão base/histórico; só lido, até a primeira leitura nova. */
    private val arquivoLegado get() = File(ctx.filesDir, "ultima_leitura.json")

    fun credenciais(): Credenciais? {
        val url = prefs.getString("url", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Credenciais(url, token)
    }

    fun salvarCredenciais(c: Credenciais) {
        prefs.edit().putString("url", c.url.trim()).putString("token", c.token.trim()).apply()
    }

    fun apagarCredenciais() {
        prefs.edit().clear().apply()
        listOf(arquivoBase, arquivoHistorico, arquivoData, arquivoLegado).forEach { it.delete() }
    }

    /** Lê a base do Turso (sem histórico), valida e guarda o JSON cru como cache offline. */
    fun atualizar(c: Credenciais): Pair<Snapshot, Long> {
        val corpo = Turso.buscarBase(c.url, c.token)
        val snap = Turso.parseBase(corpo)
        val agora = System.currentTimeMillis()
        arquivoBase.writeText(corpo)
        arquivoData.writeText(agora.toString())
        arquivoLegado.delete()
        return snap to agora
    }

    /** Lê o histórico do gráfico de linhas e guarda no cache. */
    fun atualizarHistorico(c: Credenciais): List<LinhaHistorico> {
        val corpo = Turso.buscarHistorico(c.url, c.token)
        val hist = Turso.parseHistorico(corpo)
        arquivoHistorico.writeText(corpo)
        return hist
    }

    /** Grava o P.A. de um produto no banco (precisa de token com escrita). */
    fun salvarPa(c: Credenciais, produto: String, principioAtivo: String, categoria: String) {
        Turso.escrever(c.url, c.token, Turso.corpoSalvarPa(produto, principioAtivo, categoria))
    }

    /** Grava classe/espécie/segmento dos produtos (precisa de token com escrita). */
    fun salvarClassificacao(c: Credenciais, codigos: List<String>, classe: String, especie: String, segmento: String) {
        val agora = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Sao_Paulo"))
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        Turso.escrever(c.url, c.token, Turso.corpoSalvarClassificacao(codigos, classe, especie, segmento, agora))
    }

    /** Última leitura salva, com o histórico junto (vazio se ainda não veio). */
    fun cache(): Pair<Snapshot, Long>? = runCatching {
        val novo = arquivoBase.exists()
        if (!novo && !arquivoLegado.exists()) return null
        // data antes do JSON (o inverso da gravação em [atualizar]): se a leitura
        // do Turso gravar no meio, o JSON pode sair novo, mas a data nunca sai
        // mais nova que o JSON — o cache não passa por cima da leitura fresca
        val quando = arquivoData.readText().trim().toLongOrNull() ?: 0L
        val snap = if (novo) {
            val hist = runCatching { Turso.parseHistorico(arquivoHistorico.readText()) }.getOrDefault(emptyList())
            Turso.parseBase(arquivoBase.readText()).copy(historico = hist)
        } else {
            Turso.parse(arquivoLegado.readText())
        }
        snap to quando
    }.getOrNull()

    @Serializable
    private data class ItemCatalogo(
        val produto: String,
        val principio_ativo: String,
        val grupo_quimico: String = "",
        val mecanismo: String = "",
        val hrac: String = "",
    )

    /** produtos_CAMDA.xlsx embarcado: grupo químico, mecanismo e HRAC. */
    fun catalogo(): List<InfoCatalogo> = runCatching {
        val txt = ctx.assets.open("catalogo_pa.json").bufferedReader().readText()
        jsonCatalogo.decodeFromString<List<ItemCatalogo>>(txt).map {
            InfoCatalogo(it.produto, it.principio_ativo, it.grupo_quimico, it.mecanismo, it.hrac)
        }
    }.getOrDefault(emptyList())
}
