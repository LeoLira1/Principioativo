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

/** Linha de `classificacao_vet` (classe terapêutica dos medicamentos). */
data class LinhaClasseVet(val codigo: String, val classe: String, val especie: String, val segmento: String)

/** Onde o produto fica: barracão (defensivos) ou loja veterinária. */
enum class Setor(val rotulo: String) {
    BARRACAO("🌾 Barracão"),
    LOJA_VET("💊 Loja Vet"),
    OUTRO("Outros"),
}

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
    val classes: List<LinhaClasseVet> = emptyList(),
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
    val setor: Setor = Setor.BARRACAO,
    /** Classe terapêutica (só medicamentos); vazio = sem classificação. */
    val classe: String = "",
    val especie: String = "",
    val segmento: String = "",
) {
    val temEstoque: Boolean get() = quantidade.qtd > 0

    /** Brinco conta 1 un. por peça: sai dos gráficos da loja para não engolir a rosca. */
    val ehBrinco: Boolean get() = RE_BRINCO.containsMatchIn(nome)
}

private val RE_BRINCO = Regex("\\bBRINCOS?\\b", RegexOption.IGNORE_CASE)

/** Um grupo de P.A. com seus produtos. */
data class GrupoPa(
    val chave: String,
    val nome: String,
    val produtos: List<Produto>,
    /** Loja Vet: mede em unidades, não em L + kg. */
    val porUnidades: Boolean = false,
) {
    val litros: Double = produtos.sumOf { it.quantidade.litros }
    val kg: Double = produtos.sumOf { it.quantidade.kg }
    val unidades: Double = produtos.sumOf { it.quantidade.qtd }

    /** Métrica dos gráficos: L + kg no barracão (ordenação da aba do dashboard), unidades na loja. */
    val volume: Double get() = if (porUnidades) unidades else litros + kg

    fun volumeTexto(): String {
        if (porUnidades) return "${fmtInt(unidades)} un."
        val partes = buildList {
            if (litros > 0) add("${fmtInt(litros)} L")
            if (kg > 0) add("${fmtNum(kg)} kg")
        }
        return if (partes.isEmpty()) "${fmtInt(unidades)} un." else partes.joinToString(" + ")
    }
}
