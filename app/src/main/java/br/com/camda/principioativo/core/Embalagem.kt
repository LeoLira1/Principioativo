package br.com.camda.principioativo.core

import java.util.Locale

/** Grupo de embalagem usado no filtro. */
enum class TipoEmbalagem(val rotulo: String) {
    L1("1L"),
    L5("5L"),
    L10("10L"),
    L20("20L"),
    CAIXA_NXM("Caixa nXm"),
    OUTROS_L("Outros (L)"),
    KG("kg"),
    SEM("Sem embalagem"),
}

/**
 * Embalagem lida do nome do produto (o banco não tem coluna de embalagem).
 *
 * @param porUnidade volume de UMA unidade do estoque (frasco, balde, saco…)
 * @param unidadeMedida "L" ou "kg"
 * @param porCaixa unidades por caixa no formato nXm (n), ou null
 */
data class Embalagem(
    val porUnidade: Double?,
    val unidadeMedida: String?,
    val porCaixa: Int? = null,
) {
    val tipo: TipoEmbalagem
        get() = when {
            porUnidade == null -> TipoEmbalagem.SEM
            unidadeMedida == "kg" -> TipoEmbalagem.KG
            porCaixa != null -> TipoEmbalagem.CAIXA_NXM
            porUnidade == 1.0 -> TipoEmbalagem.L1
            porUnidade == 5.0 -> TipoEmbalagem.L5
            porUnidade == 10.0 -> TipoEmbalagem.L10
            porUnidade == 20.0 -> TipoEmbalagem.L20
            else -> TipoEmbalagem.OUTROS_L
        }

    /** Rótulo curto: "4X5L", "20L", "500mL", "15kg", "—". */
    val rotulo: String
        get() {
            val v = porUnidade ?: return "—"
            val base = if (unidadeMedida == "L" && v < 1.0) "${fmtNum(v * 1000)}mL"
            else "${fmtNum(v)}${unidadeMedida}"
            return if (porCaixa != null) "${porCaixa}X$base" else base
        }

    companion object {
        private val RE_NXM = Regex(
            "\\b(\\d+)\\s*X\\s*(\\d+(?:[.,]\\d+)?)\\s*(ML|L|KG|G)\\b",
        )
        private val RE_ML = Regex("(\\d+(?:[.,]\\d+)?)\\s*ML\\b")
        private val RE_L = Regex("(\\d+(?:[.,]\\d+)?)\\s*L\\b")
        private val RE_KG = Regex("(\\d+(?:[.,]\\d+)?)\\s*KG\\b")
        private val RE_G = Regex("(\\d+(?:[.,]\\d+)?)\\s*G\\b")

        private fun num(s: String) = s.replace(',', '.').toDouble()

        /** Mesmas regras de `extrair_litros`/`extrair_kg` do dashboard, mais o formato nXm. */
        fun deNome(nome: String): Embalagem {
            val n = nome.uppercase(Locale.ROOT)
            RE_NXM.find(n)?.let { m ->
                val qtd = m.groupValues[1].toInt()
                val v = num(m.groupValues[2])
                val (por, un) = when (m.groupValues[3]) {
                    "ML" -> v / 1000 to "L"
                    "L" -> v to "L"
                    "KG" -> v to "kg"
                    else -> v / 1000 to "kg"
                }
                if (qtd > 0 && por > 0) return Embalagem(por, un, qtd)
            }
            RE_ML.find(n)?.let { return Embalagem(num(it.groupValues[1]) / 1000, "L") }
            RE_L.find(n)?.let { return Embalagem(num(it.groupValues[1]), "L") }
            RE_KG.find(n)?.let { return Embalagem(num(it.groupValues[1]), "kg") }
            RE_G.find(n)?.let { return Embalagem(num(it.groupValues[1]) / 1000, "kg") }
            return Embalagem(null, null)
        }
    }
}

/** Uma parte da quantidade física: "120 frascos", "30 caixas (5L x 4)". */
data class ParteQtd(val valor: Double, val unidade: String, val emoji: String = "")

/**
 * Estoque de um produto convertido para litros/kg e unidade física.
 * `qtd` é a quantidade de unidades do estoque (qtd_sistema): frascos,
 * baldes, galões, sacos.
 */
data class Quantidade(val qtd: Double, val emb: Embalagem) {
    val volume: Double? get() = emb.porUnidade?.let { it * qtd }
    val litros: Double get() = if (emb.unidadeMedida == "L") volume ?: 0.0 else 0.0
    val kg: Double get() = if (emb.unidadeMedida == "kg") volume ?: 0.0 else 0.0

    /** Unidade física conforme a embalagem (regras da CAMDA). */
    fun fisico(): List<ParteQtd> {
        val v = emb.porUnidade
        return when (emb.tipo) {
            TipoEmbalagem.L1 -> listOf(ParteQtd(qtd, "unidades", "🧴"))
            TipoEmbalagem.L5 -> listOf(
                ParteQtd(qtd, "frascos", "🧴"),
                ParteQtd(qtd / 4.0, "caixas (5L x 4)", "📦"),
            )
            TipoEmbalagem.L10 -> listOf(ParteQtd(qtd, "galões", ""))
            TipoEmbalagem.L20 -> listOf(ParteQtd(qtd, "baldes", "🪣"))
            TipoEmbalagem.CAIXA_NXM -> {
                val n = emb.porCaixa!!
                // caixas = litros ÷ (n × m)
                val caixas = (volume ?: 0.0) / (n * v!!)
                listOf(
                    ParteQtd(qtd, "frascos", "🧴"),
                    ParteQtd(caixas, "caixas (${emb.rotulo.substringAfter('X')} x $n)", "📦"),
                )
            }
            TipoEmbalagem.OUTROS_L -> listOf(ParteQtd(qtd, "un. de ${emb.rotulo}", ""))
            TipoEmbalagem.KG -> listOf(ParteQtd(qtd, "un. de ${emb.rotulo}", ""))
            TipoEmbalagem.SEM -> listOf(ParteQtd(qtd, "unidades", ""))
        }
    }

    /** Texto do volume: "600 L", "15 kg" ou "" (sem embalagem no nome). */
    fun volumeTexto(): String = when {
        emb.porUnidade == null -> ""
        emb.unidadeMedida == "kg" -> "${fmtNum(kg)} kg"
        else -> "${fmtNum(litros)} L"
    }

    fun fisicoTexto(): String = fisico().joinToString(" · ") {
        "${fmtNum(it.valor)} ${it.unidade}" + if (it.emoji.isNotEmpty()) " ${it.emoji}" else ""
    }
}
