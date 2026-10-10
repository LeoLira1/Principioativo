package br.com.camda.principioativo.core

import java.util.Locale

enum class FiltroEstoque(val rotulo: String) { TODOS("Todos"), COM("Com estoque"), SEM("Sem estoque") }

data class Filtros(
    val busca: String = "",
    val estoque: FiltroEstoque = FiltroEstoque.TODOS,
    val embalagens: Set<TipoEmbalagem> = emptySet(),
    val categorias: Set<String> = emptySet(),
) {
    /** Quantos filtros do painel recolhível estão ativos (busca não conta). */
    val ativosNoPainel: Int
        get() = (if (estoque != FiltroEstoque.TODOS) 1 else 0) +
            (if (embalagens.isNotEmpty()) 1 else 0) +
            (if (categorias.isNotEmpty()) 1 else 0)
}

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

        val cachePa = HashMap<String, List<String>>()
        return limpo.map { l ->
            val pas = cachePa.getOrPut(l.produto) {
                if (matcher.vazio) emptyList() else PaMatcher.separar(matcher.lookup(l.produto))
            }
            val locais = porCodigo[normalizaCodigo(l.codigo)]
                ?: porNome[l.produto.trim().lowercase(Locale.ROOT)]
                ?: emptyList()
            Produto(
                codigo = l.codigo,
                nome = l.produto.trim(),
                categoria = l.categoria.trim(),
                quantidade = Quantidade(maxOf(l.qtdSistema, 0.0), Embalagem.deNome(l.produto)),
                pas = pas,
                locais = locais.sortedBy { it.posKey },
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
        // "diuron + hexa" também acha a mistura inteira
        return normaliza(p.pas.joinToString(" + ")).contains(q)
    }

    fun aplicaFiltros(produtos: List<Produto>, f: Filtros): List<Produto> = produtos.filter { p ->
        casaBusca(p, f.busca) &&
            when (f.estoque) {
                FiltroEstoque.TODOS -> true
                FiltroEstoque.COM -> p.temEstoque
                FiltroEstoque.SEM -> !p.temEstoque
            } &&
            (f.embalagens.isEmpty() || p.quantidade.emb.tipo in f.embalagens) &&
            (f.categorias.isEmpty() || p.categoria in f.categorias)
    }

    /**
     * Agrupa por P.A. Uma mistura entra no grupo de cada ingrediente, uma vez
     * só por grupo. Ordena pelo volume (L + kg), depois unidades, depois nome.
     */
    fun agrupar(produtos: List<Produto>): Resultado {
        val grupos = LinkedHashMap<String, Pair<String, LinkedHashMap<String, Produto>>>()
        val semPa = mutableListOf<Produto>()
        for (p in produtos) {
            if (p.pas.isEmpty()) { semPa += p; continue }
            for (pa in p.pas) {
                val chave = normaliza(pa)
                val g = grupos.getOrPut(chave) { pa to LinkedHashMap() }
                // chave do produto = código (ou nome) → nunca duplica dentro do grupo
                g.second.putIfAbsent(p.codigo.ifEmpty { p.nome }, p)
            }
        }
        val lista = grupos.map { (chave, v) ->
            GrupoPa(chave, v.first, v.second.values.sortedWith(ordemProdutos))
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
     * `vendas_historico`. Cada código mantém o último valor conhecido até o
     * próximo upload (os uploads parciais só trazem os produtos do dia).
     */
    fun historico(grupos: List<GrupoPa>, hist: List<LinhaHistorico>, maxSeries: Int = 4): List<Serie> {
        if (hist.isEmpty()) return emptyList()
        val datas = hist.map { it.data.take(10) }.distinct().sorted()
        if (datas.size < 2) return emptyList()
        val porCodigo = hist.groupBy { normalizaCodigo(it.codigo) }
            .mapValues { (_, v) -> v.associate { it.data.take(10) to it.qtdEstoque } }

        return grupos.filter { it.volume > 0 }.take(maxSeries).mapNotNull { g ->
            val fatores = g.produtos.mapNotNull { p ->
                p.quantidade.emb.porUnidade?.let { normalizaCodigo(p.codigo) to it }
            }
            val ultimo = HashMap<String, Double>()
            var algum = false
            val pontos = datas.map { d ->
                var soma = 0.0
                for ((cod, fator) in fatores) {
                    porCodigo[cod]?.get(d)?.let { ultimo[cod] = maxOf(it, 0.0); algum = true }
                    soma += (ultimo[cod] ?: 0.0) * fator
                }
                d to soma
            }
            if (algum) Serie(g.nome, pontos) else null
        }
    }
}
