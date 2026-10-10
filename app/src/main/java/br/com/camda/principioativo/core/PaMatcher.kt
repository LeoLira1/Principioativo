package br.com.camda.principioativo.core

import java.util.Locale

/**
 * Acha o princípio ativo de um nome de produto — porte fiel de
 * `_build_pa_lookup` + `_lookup_pa_from_index` do camda-estoque, para o app
 * mostrar exatamente o mesmo P.A. que a aba 🧬 do dashboard.
 *
 * Etapas: nome exato → normalizado → contém chave do catálogo → sem tamanho →
 * contém chave sem tamanho → (≥5 chars) chave contém o nome → fuzzy 0,72.
 *
 * @param mapa produto (MAIÚSCULAS) → texto do P.A., na ordem de inserção.
 */
class PaMatcher(mapa: Map<String, String>) {
    private val exato = LinkedHashMap<String, String>()
    private val porNorm = LinkedHashMap<String, String>()
    private val porSemTam = LinkedHashMap<String, String>()
    private val chavesNorm: List<String>
    private val chavesSemTam: List<String>

    init {
        for ((k, v) in mapa) {
            val ku = k.trim().uppercase(Locale.ROOT)
            exato[ku] = v
            val n = normaliza(ku)
            porNorm[n] = v
            porSemTam[semTamanho(n)] = v
        }
        chavesNorm = porNorm.keys.sortedByDescending { it.length }
        chavesSemTam = porSemTam.keys.sortedByDescending { it.length }
    }

    val vazio: Boolean get() = exato.isEmpty()

    fun lookup(nome: String): String {
        val chave = nome.trim().uppercase(Locale.ROOT)
        exato[chave]?.let { return it }
        val n = normaliza(chave)
        porNorm[n]?.let { return it }
        chavesNorm.firstOrNull { it.isNotEmpty() && n.contains(it) }?.let { return porNorm.getValue(it) }
        val s = semTamanho(n)
        porSemTam[s]?.let { return it }
        chavesSemTam.firstOrNull { it.isNotEmpty() && s.contains(it) }?.let { return porSemTam.getValue(it) }
        if (s.length >= 5) {
            chavesNorm.firstOrNull { it.contains(n) }?.let { return porNorm.getValue(it) }
            chavesSemTam.firstOrNull { it.contains(s) }?.let { return porSemTam.getValue(it) }
        }
        Difflib.closestMatch(s, porSemTam.keys, 0.72)?.let { return porSemTam.getValue(it) }
        return ""
    }

    companion object {
        /**
         * Quebra o texto do P.A. em ingredientes: "Picloram + 2,4-D" →
         * ["Picloram", "2,4-D"]. Só o "+" separa (vírgula faz parte de nomes
         * como 2,4-D).
         */
        fun separar(pa: String): List<String> =
            pa.split('+').map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotEmpty() && it.uppercase(Locale.ROOT) !in setOf("NAN", "NONE", "-") }
                .distinctBy { normaliza(it) }

        /** Inverso de [separar]: ["Diuron", "Hexazinona"] → "Diuron + Hexazinona". */
        fun juntar(pas: List<String>): String =
            pas.flatMap { separar(it) }.distinctBy { normaliza(it) }.joinToString(" + ")
    }
}
