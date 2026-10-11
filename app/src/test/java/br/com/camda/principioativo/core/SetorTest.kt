package br.com.camda.principioativo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetorTest {

    private val snap = Snapshot(
        estoque = listOf(
            LinhaEstoque("1", "ROUNDUP ORIGINAL MAIS 20L", "HERBICIDAS", 10.0),
            LinhaEstoque("2", "PRIORI XTRA 5L", "FUNGICIDAS", 4.0),
            LinhaEstoque("3", "ENGEO PLENO S 1L", "INSETICIDAS", 6.0),
            LinhaEstoque("4", "RUGBY 200 CS 20L", "NEMATICIDAS", 2.0),
            LinhaEstoque("10", "IVOMEC 1% 500ML", "VERMIFUGOS", 24.0),
            LinhaEstoque("11", "TERRAMICINA LA 50ML", "MEDICAMENTOS VETERINÁRIOS", 40.0),
            LinhaEstoque("12", "PRODUTO VET NOVO", "MEDICAMENTOS", 5.0),
            LinhaEstoque("13", "ESPECIAL COM CLASSE", "DIVERSOS", 3.0),
            LinhaEstoque("20", "ADUBO FOLIAR ZN 1L", "ADUBOS FOLIARES", 100.0),
            LinhaEstoque("21", "SAL MINERAL 30KG", "SUPLEMENTO MINERAL", 500.0),
        ),
        pas = listOf(
            LinhaPa("ROUNDUP ORIGINAL MAIS", "Glifosato"),
            LinhaPa("IVOMEC 1%", "Ivermectina"),
            LinhaPa("TERRAMICINA LA", "Oxitetraciclina"),
        ),
        mapa = emptyList(),
        historico = emptyList(),
        classes = listOf(
            LinhaClasseVet("10", "Vermífugo", "Bovinos", "Corte"),
            LinhaClasseVet("11", "Antibiótico", "Multiespécie", ""),
            LinhaClasseVet("13", "Vitamina/Suplemento", "Equinos", ""),
        ),
    )
    private val produtos = Consulta.montarProdutos(snap)
    private fun setor(cod: String) = produtos.first { it.codigo == cod }.setor

    @Test fun barracaoSoDefensivos() {
        listOf("1", "2", "3", "4").forEach { assertEquals(it, Setor.BARRACAO, setor(it)) }
        assertEquals(Setor.OUTRO, setor("20")) // adubo fica fora dos dois
        assertEquals(Setor.OUTRO, setor("21"))
    }

    @Test fun defensivoComCategoriaDiferenteVaiParaBarracaoPeloNome() {
        assertEquals(Setor.BARRACAO, Consulta.setorDe("", false, "INSETICIDA KARATE ZEON 250 CS 1L"))
        assertEquals(Setor.BARRACAO, Consulta.setorDe("DEFENSIVOS", false, "Herbicida Glifosato 20L"))
        assertEquals(Setor.OUTRO, Consulta.setorDe("", false, "ADUBO FOLIAR ZN 1L"))
        // categoria vet continua mandando na loja
        assertEquals(Setor.LOJA_VET, Consulta.setorDe("VERMIFUGOS", false, "INSETICIDA X"))
    }

    @Test fun lojaPorCategoriaOuClasse() {
        listOf("10", "11", "12").forEach { assertEquals(it, Setor.LOJA_VET, setor(it)) }
        assertEquals(Setor.LOJA_VET, setor("13")) // categoria genérica, mas tem classe vet
        assertEquals(Setor.LOJA_VET, Consulta.setorDe("Medicamentos Veterinarios", false))
    }

    @Test fun lojaAgrupaPorClasseEmUnidades() {
        val vet = produtos.filter { it.setor == Setor.LOJA_VET }
        val r = Consulta.agruparPorClasse(vet)
        val nomes = r.grupos.associate { it.nome to it.unidades }
        assertEquals(40.0, nomes["Antibiótico"]!!, 0.0)
        assertEquals(24.0, nomes["Vermífugo"]!!, 0.0)
        assertEquals(5.0, nomes[CLASSE_SEM]!!, 0.0) // sem linha em classificacao_vet
        assertTrue(r.semPa.isEmpty())
        assertTrue(r.grupos.all { it.porUnidades && it.chave.startsWith(PREFIXO_CLASSE) })
        assertEquals("40 un.", r.grupos.first().volumeTexto()) // ordenado por unidades
    }

    @Test fun lojaPorPaEmUnidadesComSemPa() {
        val vet = produtos.filter { it.setor == Setor.LOJA_VET }
        val r = Consulta.agrupar(vet, porUnidades = true) { it.pas }
        assertEquals(listOf("Oxitetraciclina", "Ivermectina"), r.grupos.map { it.nome })
        assertEquals(setOf("12", "13"), r.semPa.map { it.codigo }.toSet())
        // rosca/barras em unidades, mesmo com embalagem em mL
        assertEquals(40.0, Consulta.fatias(r.grupos).first().valor, 0.0)
    }

    @Test fun buscaEFiltroDaLoja() {
        val vet = produtos.filter { it.setor == Setor.LOJA_VET }
        assertEquals(listOf("10"), Consulta.aplicaFiltros(vet, Filtros(busca = "vermifugo")).map { it.codigo })
        assertEquals(listOf("10"), Consulta.aplicaFiltros(vet, Filtros(busca = "bovinos")).map { it.codigo })
        assertEquals(listOf("13"), Consulta.aplicaFiltros(vet, Filtros(especies = setOf("Equinos"))).map { it.codigo })
        // "—" = sem espécie
        assertEquals(listOf("12"), Consulta.aplicaFiltros(vet, Filtros(especies = setOf("—"))).map { it.codigo })
        assertEquals(1, Filtros(especies = setOf("Equinos")).ativosNoPainel)
    }

    @Test fun historicoDaLojaEmUnidades() {
        val vet = produtos.filter { it.setor == Setor.LOJA_VET }
        val g = Consulta.agruparPorClasse(vet).grupos.filter { it.nome == "Vermífugo" }
        val hist = listOf(LinhaHistorico("10", "2026-10-01", 30.0), LinhaHistorico("10", "2026-10-02", 26.0))
        val s = Consulta.historico(g, hist, hoje = "2026-10-03").single()
        // 1 unidade = 1 (não 0,5 L)
        assertEquals(listOf(30.0, 26.0, 24.0), s.pontos.map { it.second })
    }
}
