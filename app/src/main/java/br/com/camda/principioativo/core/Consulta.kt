package br.com.camda.principioativo.core

import java.util.Locale

enum class FiltroEstoque(val rotulo: String) { TODOS("Todos"), COM("Com estoque"), SEM("Sem estoque") }

data class Filtros(
    val busca: String = "",
    val estoque: FiltroEstoque = FiltroEstoque.TODOS,
    val embalagens: Set<TipoEmbalagem> = emptySet(),
    val categorias: Set<String> = emptySet(),
    val especies: Set<String> = emptySet(),
) {
    /** Quantos filtros do painel recolhível estão ativos (busca não conta). */
    val ativosNoPainel: Int
        get() = (if (estoque != FiltroEstoque.TODOS) 1 else 0) +
            (if (embalagens.isNotEmpty()) 1 else 0) +
            (if (categorias.isNotEmpty()) 1 else 0) +
            (if (especies.isNotEmpty()) 1 else 0)
}

/** Nome do grupo dos medicamentos sem linha em `classificacao_vet` (igual ao dashboard). */
const val CLASSE_SEM = "Sem classificação"

/** Prefixo da chave dos grupos por classe (não colide com a chave de P.A.). */
const val PREFIXO_CLASSE = "CL:"

/** Fatia/barra dos gráficos. */
data class Fatia(val nome: String, val valor: Double, val chave: String?)

/** Série do gráfico de linhas: valores (L+kg) por data. */
data class Serie(val nome: String, val pontos: List<Pair<String, Double>>)

data class Resultado(
    val grupos: List<GrupoPa>,
    val semPa: List<Produto>,
    val totalProdutos: Int,
)

object Consulta {

    /** Nomes que o dashboard ignora em todo lugar (`_PRODUTOS_IGNORADOS`). */
    private val IGNORADOS = setOf("AÇÚCAR", "ACUCAR", "ARQUIVO MORTO")

    /** Defensivos do barracão: herbicidas, fungicidas, inseticidas e nematicidas. */
    private val PREFIXOS_BARRACAO = listOf("HERBICIDA", "FUNGICIDA", "INSETICIDA", "NEMATICIDA")

    /** Grupos veterinários do BI (mesma lista do dashboard, sem identificação/dieta/raticida). */
    private val CATEGORIAS_VET = listOf(
        "MEDICAMENTOS", "MEDICAMENTOS VETERINÁRIOS", "VACINA AFTOSA", "VACINAS DIVERSAS/SOROS",
        "ANTIBIOTICOS/ANTI-INFLAMATORIO", "ANESTESICO/ANALGESICO/DIURETIC", "ANTITOXICOS",
        "VERMIFUGOS", "MOSQUICIDA/CARRAPATICIDA/BERNI", "UNGUENTOS/POMADAS", "HOMEOPATICO",
        "HORMONIOS LEITEIROS", "TONICO MINERAL/VITAMINAS", "REPRODUCAO ANIMAL",
    ).map(::chavePa).toSet()

    /**
     * Setor pela categoria do BI. Produto com classe em `classificacao_vet`
     * é da loja mesmo que a categoria venha genérica.
     */
    fun setorDe(categoria: String, temClasseVet: Boolean): Setor {
        val c = chavePa(categoria)
        return when {
            PREFIXOS_BARRACAO.any { c.startsWith(it) } -> Setor.BARRACAO
            c in CATEGORIAS_VET || c.startsWith("MEDICAMENTO") || c.startsWith("VACINA") || temClasseVet -> Setor.LOJA_VET
            else -> Setor.OUTRO
        }
    }
    private val RE_COD_PREFIXO = Regex("^\\s*[A-Z0-9\\-]{3,20}\\s*[-–]\\s*(.+)$", RegexOption.IGNORE_CASE)

    /**
     * Monta a lista de produtos enriquecida. Repete as limpezas da aba 🧬 do
     * dashboard: ignora produtos da lista negra e descarta linhas AUTO_ cujo
     * nome já existe com código real.
     */
    fun montarProdutos(snap: Snapshot): List<Produto> {
        val estoque = snap.estoque.filter {
            it.produto.isNotBlank() && it.produto.trim().uppercase(Locale.ROOT) !in IGNORADOS
        }
        val nomesComCodigoReal = estoque
            .filterNot { it.codigo.startsWith("AUTO_") }
            .map { it.produto.trim().uppercase(Locale.ROOT) }.toSet()
        val limpo = estoque.filterNot { l ->
            if (!l.codigo.startsWith("AUTO_")) return@filterNot false
            val nome = l.produto.trim().uppercase(Locale.ROOT)
            val semCod = RE_COD_PREFIXO.find(l.produto.trim())?.groupValues?.get(1)?.trim()?.uppercase(Locale.ROOT)
            nome in nomesComCodigoReal || (semCod != null && semCod in nomesComCodigoReal)
        }

        // Banco tem prioridade; a última linha de um mesmo produto vence (dict do Python).
        val mapa = LinkedHashMap<String, String>()
        for (p in snap.pas) {
            val k = p.produto.trim().uppercase(Locale.ROOT)
            val v = p.principioAtivo.trim()
            if (k.isNotEmpty() && v.isNotEmpty()) mapa[k] = v
        }
        val matcher = PaMatcher(mapa)

        // Localização: por código (preferido) e por nome (fallback), como o Mapa faz.
        val porCodigo = HashMap<String, MutableList<Local>>()
        val porNome = HashMap<String, MutableList<Local>>()
        for (m in snap.mapa) {
            val loc = Local(m.posKey, m.quantidade, m.unidade)
            val cods = m.codigos.map(::normalizaCodigo).filter { it.isNotEmpty() }.toSet()
            if (cods.isNotEmpty()) cods.forEach { porCodigo.getOrPut(it) { mutableListOf() }.add(loc) }
            else porNome.getOrPut(m.nomeProduto.trim().lowercase(Locale.ROOT)) { mutableListOf() }.add(loc)
        }

        val classes = snap.classes.associateBy { normalizaCodigo(it.codigo) }

        val cachePa = HashMap<String, List<String>>()
        return limpo.map { l ->
            val pas = cachePa.getOrPut(l.produto) {
                if (matcher.vazio) emptyList() else PaMatcher.separar(matcher.lookup(l.produto))
            }
            val locais = porCodigo[normalizaCodigo(l.codigo)]
                ?: porNome[l.produto.trim().lowercase(Locale.ROOT)]
                ?: emptyList()
            val cv = classes[normalizaCodigo(l.codigo)]
            Produto(
                codigo = l.codigo,
                nome = l.produto.trim(),
                categoria = l.categoria.trim(),
                quantidade = Quantidade(maxOf(l.qtdSistema, 0.0), Embalagem.deNome(l.produto)),
                pas = pas,
                locais = locais.sortedBy { it.posKey },
                setor = setorDe(l.categoria, cv != null && cv.classe.isNotBlank()),
                classe = cv?.classe?.trim().orEmpty(),
                especie = cv?.especie?.trim().orEmpty(),
                segmento = cv?.segmento?.trim().orEmpty(),
            )
        }
    }

    /** Busca parcial, sem diferenciar maiúsculas nem acentos, em P.A., nome e código. */
    fun casaBusca(p: Produto, busca: String): Boolean {
        val q = normaliza(busca)
        if (q.isEmpty()) return true
        if (normaliza(p.nome).contains(q)) return true
        if (normaliza(p.codigo).contains(q)) return true
        if (p.pas.any { normaliza(it).contains(q) }) return true
        // "24d" acha "2,4-D"; "lambdacialotrina" acha "Lambda-cialotrina"
        val qc = chavePa(q)
        if (qc.length >= 2 && p.pas.any { chavePa(it).contains(qc) }) return true
        // "diuron + hexa" também acha a mistura inteira
        if (normaliza(p.pas.joinToString(" + ")).contains(q)) return true
        // loja: classe terapêutica e espécie ("vermifugo", "bovinos")
        return normaliza(p.classe).contains(q) || normaliza(p.especie).contains(q)
    }

    fun aplicaFiltros(produtos: List<Produto>, f: Filtros): List<Produto> = produtos.filter { p ->
        casaBusca(p, f.busca) &&
            when (f.estoque) {
                FiltroEstoque.TODOS -> true
                FiltroEstoque.COM -> p.temEstoque
                FiltroEstoque.SEM -> !p.temEstoque
            } &&
            (f.embalagens.isEmpty() || p.quantidade.emb.tipo in f.embalagens) &&
            (f.categorias.isEmpty() || p.categoria in f.categorias) &&
            (f.especies.isEmpty() || p.especie.ifBlank { "—" } in f.especies)
    }

    /**
     * Agrupa por P.A. Uma mistura entra no grupo de cada ingrediente, uma vez
     * só por grupo. Ordena pelo volume (L + kg), depois unidades, depois nome.
     */
    fun agrupar(produtos: List<Produto>): Resultado = agrupar(produtos, porUnidades = false) { it.pas }

    /** Loja Vet por classe terapêutica; sem classe vai para [CLASSE_SEM]. */
    fun agruparPorClasse(produtos: List<Produto>): Resultado =
        agrupar(produtos, porUnidades = true, prefixo = PREFIXO_CLASSE) { listOf(it.classe.ifBlank { CLASSE_SEM }) }

    /**
     * Agrupamento genérico: [chaves] diz em que grupos o produto entra
     * (vazio = "sem"). [porUnidades] troca a métrica de L + kg para unidades.
     */
    fun agrupar(
        produtos: List<Produto>,
        porUnidades: Boolean,
        prefixo: String = "",
        chaves: (Produto) -> List<String>,
    ): Resultado {
        val grupos = LinkedHashMap<String, LinkedHashMap<String, Produto>>()
        // grafias de cada grupo ("2,4-D" ×5, "2,4D" ×2) → mostra a mais usada
        val grafias = HashMap<String, LinkedHashMap<String, Int>>()
        val semPa = mutableListOf<Produto>()
        for (p in produtos) {
            val nomes = chaves(p)
            if (nomes.isEmpty()) { semPa += p; continue }
            for (pa in nomes.distinctBy(::chavePa)) {
                val chave = prefixo + chavePa(pa)
                val g = grupos.getOrPut(chave) { LinkedHashMap() }
                // chave do produto = código (ou nome) → nunca duplica dentro do grupo
                g.putIfAbsent(p.codigo.ifEmpty { p.nome }, p)
                grafias.getOrPut(chave) { LinkedHashMap() }.merge(pa, 1, Int::plus)
            }
        }
        val lista = grupos.map { (chave, v) ->
            val nome = grafias.getValue(chave).maxByOrNull { it.value }!!.key
            GrupoPa(chave, nome, v.values.sortedWith(ordemProdutos), porUnidades)
        }.sortedWith(
            compareByDescending<GrupoPa> { it.volume }
                .thenByDescending { it.unidades }
                .thenBy { it.nome },
        )
        return Resultado(lista, semPa.sortedWith(ordemProdutos), produtos.size)
    }

    private val ordemProdutos = compareByDescending<Produto> { it.quantidade.volume ?: 0.0 }
        .thenByDescending { it.quantidade.qtd }
        .thenBy { it.nome }

    /** Rosca: top N por volume + "Outros". */
    fun fatias(grupos: List<GrupoPa>, topN: Int = 8): List<Fatia> {
        val comVolume = grupos.filter { it.volume > 0 }
        val top = comVolume.take(topN).map { Fatia(it.nome, it.volume, it.chave) }
        val resto = comVolume.drop(topN).sumOf { it.volume }
        return if (resto > 0) top + Fatia("Outros", resto, null) else top
    }

    /**
     * Série histórica do volume (L + kg) por P.A., a partir dos snapshots de
     * `vendas_historico`. Os uploads parciais só trazem os produtos vendidos
     * no dia, então, para cada código:
     *  - entre dois registros vale o último conhecido;
     *  - antes do primeiro registro vale o primeiro (não começa do zero);
     *  - código sem nenhum registro (não vendeu no período) fica constante
     *    no estoque atual.
     * O último ponto é o estoque atual (`hoje`), o mesmo das barras.
     */
    fun historico(
        grupos: List<GrupoPa>,
        hist: List<LinhaHistorico>,
        maxSeries: Int = 4,
        hoje: String = java.time.LocalDate.now().toString(),
    ): List<Serie> {
        // porUnidades: cada unidade vale 1 (loja); senão, o volume da embalagem
        if (hist.isEmpty()) return emptyList()
        val datasHist = hist.map { it.data.take(10) }.distinct().sorted()
        if (datasHist.size < 2) return emptyList()
        val datas = if (hoje > datasHist.last()) datasHist + hoje else datasHist
        val porCodigo = hist.groupBy { normalizaCodigo(it.codigo) }
            .mapValues { (_, v) -> v.associate { it.data.take(10) to maxOf(it.qtdEstoque, 0.0) }.toSortedMap() }

        return grupos.filter { it.volume > 0 }.take(maxSeries).map { g ->
            val itens = g.produtos.mapNotNull { p ->
                val fator = if (g.porUnidades) 1.0 else p.quantidade.emb.porUnidade
                fator?.let { Triple(normalizaCodigo(p.codigo), it, p.quantidade.qtd) }
            }
            val pontos = datas.map { d ->
                d to itens.sumOf { (cod, fator, atual) ->
                    val regs = porCodigo[cod]
                    val qtd = when {
                        // hoje é sempre o estoque atual, mesmo com upload já feito hoje
                        d == hoje -> atual
                        regs == null -> atual
                        else -> regs.headMap(d + "\uFFFF").values.lastOrNull() ?: regs.values.first()
                    }
                    qtd * fator
                }
            }
            Serie(g.nome, pontos)
        }
    }
}
