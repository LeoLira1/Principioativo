package br.com.camda.principioativo.core

import java.text.Normalizer
import java.util.Locale

/** Remove diacríticos: Ó→O, Ã→A, Ç→C. */
fun semAcento(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Chave de comparação: maiúsculas, sem acento, espaços colapsados. */
fun normaliza(s: String): String =
    semAcento(s).uppercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

/**
 * Chave de agrupamento de P.A.: só letras e números, sem acento.
 * "2,4-D", "2,4D" e "2.4 D" viram o mesmo grupo ("24D").
 */
fun chavePa(s: String): String = normaliza(s).filter { it.isLetterOrDigit() }

/** Sufixos de tamanho/formulação (mesma regex `_RE_PA_SIZE` do camda-estoque). */
private val RE_TAMANHO = Regex(
    "\\b\\d+[.,]?\\d*\\s*(?:L|ML|KG|G|GR|SC|T|MG|WG|WP|SL|EC|CS|GD|OD|SE|FS|EW|ME|TG|WDG|ZC|DC|ULV)\\b",
    RegexOption.IGNORE_CASE,
)

/** Nome sem tamanho/formulação: "ROUNDUP WG 5KG" → "ROUNDUP". */
fun semTamanho(s: String): String = RE_TAMANHO.replace(s, "").replace(Regex("\\s+"), " ").trim()

/** Normaliza código de produto: "254185.0" → "254185", maiúsculas. */
fun normalizaCodigo(c: String?): String {
    if (c == null) return ""
    var s = c.trim()
    if (s.isEmpty() || s.equals("nan", ignoreCase = true)) return ""
    if (Regex("\\d+\\.0+").matches(s)) s = s.substringBefore('.')
    return s.uppercase(Locale.ROOT)
}

private val PT_BR = Locale("pt", "BR")

/** Inteiro com separador de milhar pt-BR: 12345 → "12.345". */
fun fmtInt(v: Double): String = String.format(PT_BR, "%,d", Math.round(v))

/** Número com até 1 casa decimal (sem ",0" quando é inteiro). */
fun fmtNum(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) fmtInt(r) else String.format(PT_BR, "%,.1f", r)
}
