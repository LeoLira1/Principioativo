package br.com.camda.principioativo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultaTest {

    private fun snap(estoque: List<LinhaEstoque>, pas: List<LinhaPa>, mapa: List<LinhaMapa> = emptyList()) =
        Snapshot(estoque, pas, mapa, emptyList())

    private val base = snap(
        estoque = listOf(
            LinhaEstoque("100", "ROUNDUP ORIGINAL MAIS 20L", "HERBICIDAS", 10.0),
            LinhaEstoque("101", "GLIFOSATO NORTOX 5L", "HERBICIDAS", 8.0),
            LinhaEstoque("200", "JUMP 5L", "HERBICIDAS", 4.0),
            LinhaEstoque("300", "HEXAZINONA 250SL 1L", "HERBICIDAS", 0.0),
            LinhaEstoque("400", "AMISTAR TOP 4X5L", "FUNGICIDAS", 12.0),
            LinhaEstoque("500", "PRODUTO DESCONHECIDO XYZ 10L", "OUTROS", 3.0),
            LinhaEstoque("600", "AÇÚCAR", "OUTROS", 50.0),
        ),
        pas = listOf(
            LinhaPa("ROUNDUP ORIGINAL MAIS", "Glifosato"),
            LinhaPa("GLIFOSATO NORTOX", "Glifosato"),
            LinhaPa("JUMP", "Diuron + Hexazinona"),
            LinhaPa("HEXAZINONA 250SL", "Hexazinona"),
            LinhaPa("AMISTAR TOP", "Azoxistrobina + Difenoconazol"),
        ),
        mapa = listOf(LinhaMapa("R1-A-C3-N2", 40.0, "L", "Roundup", listOf("100"))),
    )

    private val produtos = Consulta.montarProdutos(base)

    // ── busca parcial ────────────────────────────────────────────────────────

    @Test fun buscaParcialPorPaSemAcentoNemCaixa() {
        val f = Consulta.aplicaFiltros(produtos, Filtros(busca = "glifo"))
        assertEquals(setOf("100", "101"), f.map { it.codigo }.toSet())
        val g = Consulta.aplicaFiltros(produtos, Filtros(busca = "HEXAZÍNONA"))
        assertEquals(setOf("200", "300"), g.map { it.codigo }.toSet())
    }

    @Test fun buscaParcialPorNomeECodigo() {
        assertEquals(listOf("200"), Consulta.aplicaFiltros(produtos, Filtros(busca = "jum")).map { it.codigo })
        assertEquals(listOf("400"), Consulta.aplicaFiltros(produtos, Filtros(busca = "40")).map { it.codigo })
        assertEquals(listOf("400"), Consulta.aplicaFiltros(produtos, Filtros(busca = "amistar top")).map { it.codigo })
    }

    @Test fun buscaPelaMisturaInteira() {
        val f = Consulta.aplicaFiltros(produtos, Filtros(busca = "diuron + hexa"))
        assertEquals(listOf("200"), f.map { it.codigo })
    }

    @Test fun buscaVaziaTrazTudo() {
        assertEquals(produtos.size, Consulta.aplicaFiltros(produtos, Filtros(busca = "  ")).size)
    }

    // ── múltiplos P.A. ──────────────────────────────────────────────────────

    @Test fun misturaApareceEmCadaGrupoSemDuplicar() {
        // duplica a linha do JUMP para garantir que não repete dentro do grupo
        val dup = produtos + produtos.first { it.codigo == "200" }
        val r = Consulta.agrupar(dup)
        val diuron = r.grupos.first { it.chave == "DIURON" }
        val hexa = r.grupos.first { it.chave == "HEXAZINONA" }
        assertEquals(listOf("200"), diuron.produtos.map { it.codigo })
        assertEquals(setOf("200", "300"), hexa.produtos.map { it.codigo }.toSet())
        assertEquals(1, hexa.produtos.count { it.codigo == "200" })
        val jump = produtos.first { it.codigo == "200" }
        assertEquals(listOf("Diuron", "Hexazinona"), jump.pas)
    }

    @Test fun separarMantemVirgulaDe24D() {
        assertEquals(listOf("Picloram", "2,4-D"), PaMatcher.separar("Picloram + 2,4-D"))
        assertEquals(listOf("Glifosato"), PaMatcher.separar("Glifosato + glifosato"))
    }

    @Test fun matcherAchaPorNomeComTamanhoECaixa() {
        val m = PaMatcher(mapOf("ROUNDUP ORIGINAL MAIS" to "Glifosato"))
        assertEquals("Glifosato", m.lookup("roundup original mais 20L"))
        assertEquals("Glifosato", m.lookup("ROUNDUP ORIGINAL MAIZ"))  // fuzzy
        assertEquals("", m.lookup("FERTILIZANTE FOLIAR"))
    }

    // ── grupo sem P.A. ──────────────────────────────────────────────────────

    @Test fun produtoSemPaVaiParaGrupoSeparado() {
        val r = Consulta.agrupar(produtos)
        assertEquals(listOf("500"), r.semPa.map { it.codigo })
        assertTrue(r.grupos.none { g -> g.produtos.any { it.codigo == "500" } })
    }

    @Test fun produtosIgnoradosSaem() {
        assertFalse(produtos.any { it.codigo == "600" })
    }

    @Test fun semTabelaDePaTudoFicaSemPa() {
        val p = Consulta.montarProdutos(snap(base.estoque, emptyList()))
        val r = Consulta.agrupar(p)
        assertTrue(r.grupos.isEmpty())
        assertEquals(p.size, r.semPa.size)
    }

    @Test fun autoDuplicadoDeCodigoRealSai() {
        val s = snap(
            listOf(
                LinhaEstoque("123", "JUMP 5L", "HERB", 2.0),
                LinhaEstoque("AUTO_9", "US1 - JUMP 5L", "HERB", 2.0),
                LinhaEstoque("AUTO_8", "OUTRO 5L", "HERB", 1.0),
            ),
            base.pas,
        )
        assertEquals(listOf("123", "AUTO_8"), Consulta.montarProdutos(s).map { it.codigo })
    }

    // ── filtros combinados ──────────────────────────────────────────────────

    @Test fun filtrosCombinados() {
        val f = Filtros(
            busca = "hexa",
            estoque = FiltroEstoque.COM,
            embalagens = setOf(TipoEmbalagem.L5, TipoEmbalagem.L1),
        )
        assertEquals(listOf("200"), Consulta.aplicaFiltros(produtos, f).map { it.codigo })

        val sem = Consulta.aplicaFiltros(produtos, f.copy(estoque = FiltroEstoque.SEM))
        assertEquals(listOf("300"), sem.map { it.codigo })

        val cat = Consulta.aplicaFiltros(produtos, Filtros(categorias = setOf("FUNGICIDAS"), estoque = FiltroEstoque.COM))
        assertEquals(listOf("400"), cat.map { it.codigo })

        val nada = Consulta.aplicaFiltros(produtos, Filtros(busca = "glifo", categorias = setOf("FUNGICIDAS")))
        assertTrue(nada.isEmpty())
        assertEquals(3, Filtros(estoque = FiltroEstoque.SEM, embalagens = setOf(TipoEmbalagem.KG), categorias = setOf("X")).ativosNoPainel)
    }

    @Test fun filtrosAtualizamGraficos() {
        val todos = Consulta.fatias(Consulta.agrupar(produtos).grupos)
        val so5 = Consulta.fatias(
            Consulta.agrupar(Consulta.aplicaFiltros(produtos, Filtros(embalagens = setOf(TipoEmbalagem.L20)))).grupos,
        )
        assertTrue(todos.size > so5.size)
        assertEquals(listOf("Glifosato"), so5.map { it.nome })
        assertEquals(200.0, so5.single().valor, 0.001)
    }

    // ── gráficos / agrupamento ──────────────────────────────────────────────

    @Test fun roscaAgrupaRestoEmOutros() {
        val grupos = (1..12).map { i ->
            GrupoPa("P$i", "P$i", listOf(
                Produto("$i", "X$i 1L", "", Quantidade(i.toDouble(), Embalagem.deNome("1L")), listOf("P$i"), emptyList()),
            ))
        }.sortedByDescending { it.volume }
        val f = Consulta.fatias(grupos, 8)
        assertEquals(9, f.size)
        assertEquals("Outros", f.last().nome)
        assertEquals((1..4).sum().toDouble(), f.last().valor, 0.001)
    }

    @Test fun localizacaoPorCodigo() {
        val r = produtos.first { it.codigo == "100" }
        assertEquals(listOf("R1 A · C3 N2"), r.locais.map { it.rotulo })
        assertTrue(produtos.first { it.codigo == "101" }.locais.isEmpty())
    }

    @Test fun historicoMantemUltimoValorETerminaNoAtual() {
        val g = Consulta.agrupar(produtos).grupos.filter { it.chave == "GLIFOSATO" }
        val hist = listOf(
            LinhaHistorico("100", "2026-10-01", 10.0),
            LinhaHistorico("101", "2026-10-01", 4.0),
            LinhaHistorico("100", "2026-10-02", 8.0),
        )
        val s = Consulta.historico(g, hist, hoje = "2026-10-05").single()
        // 100 = 20L, 101 = 5L. Dia 1: 10×20 + 4×5 = 220; dia 2: 8×20 + (4 mantido)×5 = 180
        // hoje = estoque atual (100: 10, 101: 8) = 10×20 + 8×5 = 240, igual às barras
        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-05"), s.pontos.map { it.first })
        assertEquals(listOf(220.0, 180.0, 240.0), s.pontos.map { it.second })
        assertEquals(g.single().volume, s.pontos.last().second, 0.001)
    }

    @Test fun historicoTerminaNoAtualMesmoComUploadDeHoje() {
        val g = Consulta.agrupar(produtos).grupos.filter { it.chave == "GLIFOSATO" }
        val hist = listOf(
            LinhaHistorico("100", "2026-10-01", 10.0),
            LinhaHistorico("101", "2026-10-01", 4.0),
            LinhaHistorico("100", "2026-10-02", 3.0), // upload parcial de hoje
        )
        val s = Consulta.historico(g, hist, hoje = "2026-10-02").single()
        assertEquals(listOf("2026-10-01", "2026-10-02"), s.pontos.map { it.first })
        assertEquals(g.single().volume, s.pontos.last().second, 0.001)
    }

    @Test fun historicoNaoComecaDoZero() {
        val g = Consulta.agrupar(produtos).grupos.filter { it.chave == "GLIFOSATO" }
        // 101 só aparece no dia 2: no dia 1 vale o primeiro registro (6), não 0.
        // 100 nunca aparece (não vendeu): fica constante no estoque atual (10).
        val hist = listOf(
            LinhaHistorico("999", "2026-10-01", 1.0),
            LinhaHistorico("101", "2026-10-02", 6.0),
        )
        val s = Consulta.historico(g, hist, hoje = "2026-10-03").single()
        assertEquals(10 * 20.0 + 6 * 5.0, s.pontos[0].second, 0.001)
        assertEquals(10 * 20.0 + 6 * 5.0, s.pontos[1].second, 0.001)
    }

    @Test fun historicoComUmaDataSoNaoDesenha() {
        val g = Consulta.agrupar(produtos).grupos
        assertTrue(Consulta.historico(g, listOf(LinhaHistorico("100", "2026-10-01", 1.0))).isEmpty())
    }

    // ── grafias diferentes do mesmo P.A. ────────────────────────────────────

    private val grafias = Consulta.montarProdutos(
        snap(
            listOf(
                LinhaEstoque("1", "TORDON 20L", "HERB", 2.0),
                LinhaEstoque("2", "DMA 806 20L", "HERB", 3.0),
                LinhaEstoque("3", "AMINOL 20L", "HERB", 1.0),
            ),
            listOf(
                LinhaPa("TORDON", "Picloram + 2,4-D"),
                LinhaPa("DMA 806", "2,4D"),
                LinhaPa("AMINOL", "2,4-D"),
            ),
        ),
    )

    @Test fun grafiasDiferentesViramUmGrupo() {
        val r = Consulta.agrupar(grafias)
        val g = r.grupos.filter { it.chave == "24D" }
        assertEquals(1, g.size)
        assertEquals(setOf("1", "2", "3"), g.single().produtos.map { it.codigo }.toSet())
        assertEquals("2,4-D", g.single().nome) // grafia mais usada
        assertEquals(120.0, g.single().litros, 0.001)
    }

    @Test fun buscaIgnoraPontuacaoDoPa() {
        assertEquals(3, Consulta.aplicaFiltros(grafias, Filtros(busca = "24d")).size)
        assertEquals(3, Consulta.aplicaFiltros(grafias, Filtros(busca = "2,4 D")).size)
        assertEquals(1, Consulta.aplicaFiltros(grafias, Filtros(busca = "pic")).size)
    }
}
