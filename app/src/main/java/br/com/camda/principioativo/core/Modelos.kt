package br.com.camda.principioativo.core

/** Linha de `estoque_mestre`. */
data class LinhaEstoque(
    val codigo: String,
    val produto: String,
    val categoria: String,
    val qtdSistema: Double,
)

/** Linha de `principios_ativos`. */
data class LinhaPa(val produto: String, val principioAtivo: String)

/** Posição do Mapa de Estoque (`mapa_posicoes` + `mapa_produtos`). */
data class LinhaMapa(
    val posKey: String,
    val quantidade: Double?,
    val unidade: String,
    val nomeProduto: String,
    val codigos: List<String>,
)

/** Linha de `vendas_historico` (snapshot de estoque por dia de upload). */
data class LinhaHistorico(val codigo: String, val data: String, val qtdEstoque: Double)

/** Entrada do catálogo embarcado (produtos_CAMDA.xlsx) — só informativo. */
data class InfoCatalogo(
    val produto: String,
    val principioAtivo: String,
    val grupoQuimico: String,
    val mecanismo: String,
    val hrac: String,
)

/** Tudo que o app lê do banco numa única ida. */
data class Snapshot(
    val estoque: List<LinhaEstoque>,
    val pas: List<LinhaPa>,
    val mapa: List<LinhaMapa>,
    val historico: List<LinhaHistorico>,
    val avisos: List<String> = emptyList(),
)

/** Endereço legível: "R1-A-C3-N2" → "R1 A · C3 N2". */
data class Local(val posKey: String, val quantidade: Double?, val unidade: String) {
    val rotulo: String
        get() {
            val p = posKey.split('-')
            return if (p.size == 4) "${p[0]} ${p[1]} · ${p[2]} ${p[3]}" else posKey
        }
}

/** Produto do estoque já enriquecido com P.A., embalagem e localização. */
data class Produto(
    val codigo: String,
    val nome: String,
    val categoria: String,
    val quantidade: Quantidade,
    /** Ingredientes (vazio = sem P.A. cadastrado). */
    val pas: List<String>,
    val locais: List<Local>,
) {
    val temEstoque: Boolean get() = quantidade.qtd > 0
}

/** Um grupo de P.A. com seus produtos. */
data class GrupoPa(
    val chave: String,
    val nome: String,
    val produtos: List<Produto>,
) {
    val litros: Double = produtos.sumOf { it.quantidade.litros }
    val kg: Double = produtos.sumOf { it.quantidade.kg }
    val unidades: Double = produtos.sumOf { it.quantidade.qtd }

    /** Métrica de volume dos gráficos: L + kg (mesma ordenação da aba do dashboard). */
    val volume: Double get() = litros + kg

    fun volumeTexto(): String {
        val partes = buildList {
            if (litros > 0) add("${fmtInt(litros)} L")
            if (kg > 0) add("${fmtNum(kg)} kg")
        }
        return if (partes.isEmpty()) "${fmtInt(unidades)} un." else partes.joinToString(" + ")
    }
}
