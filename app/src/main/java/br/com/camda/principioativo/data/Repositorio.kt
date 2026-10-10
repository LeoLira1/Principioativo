package br.com.camda.principioativo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import br.com.camda.principioativo.core.InfoCatalogo
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

    private val arquivoCache get() = File(ctx.filesDir, "ultima_leitura.json")
    private val arquivoData get() = File(ctx.filesDir, "ultima_leitura.txt")

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
        arquivoCache.delete()
        arquivoData.delete()
    }

    /** Lê do Turso, valida e guarda o JSON cru como cache offline. */
    fun atualizar(c: Credenciais): Pair<Snapshot, Long> {
        val corpo = Turso.buscar(c.url, c.token)
        val snap = Turso.parse(corpo)
        val agora = System.currentTimeMillis()
        arquivoCache.writeText(corpo)
        arquivoData.writeText(agora.toString())
        return snap to agora
    }

    /** Grava o P.A. de um produto no banco (precisa de token com escrita). */
    fun salvarPa(c: Credenciais, produto: String, principioAtivo: String, categoria: String) {
        Turso.escrever(c.url, c.token, Turso.corpoSalvarPa(produto, principioAtivo, categoria))
    }

    fun cache(): Pair<Snapshot, Long>? = runCatching {
        if (!arquivoCache.exists()) return null
        Turso.parse(arquivoCache.readText()) to (arquivoData.readText().trim().toLongOrNull() ?: 0L)
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
