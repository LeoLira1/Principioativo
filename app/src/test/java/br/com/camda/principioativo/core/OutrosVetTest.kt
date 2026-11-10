package br.com.camda.principioativo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Veterinários que o estoque deixa em "OUTROS" (Panteq, Cydectin, Bertac…). */
class OutrosVetTest {

    private val snap = Snapshot(
        estoque = listOf(
            LinhaEstoque("106160", "PANTEQ ORAL SERINGA 40G", "OUTROS", 36.0),
            LinhaEstoque("140449", "CYDECTIN INJETAVEL FRASCO 500ML", "OUTROS", 30.0),
            LinhaEstoque("73768", "BERTAC 1L", "OUTROS", 80.0),
            LinhaEstoque("236195", "ROFLIN INJETAVEL FRASCO 100ML", "OUTROS", 24.0),
            LinhaEstoque("500", "MATA BICHEIRA SEM CADASTRO 500ML", "MATA BICHEIRA", 5.0),
            LinhaEstoque("501", "GRAXA AZUL 1KG", "OUTROS", 12.0),
            LinhaEstoque("502", "CHAVE DE FENDA", "FERRAMENTAS", 3.0),
            LinhaEstoque("503", "SEMENTE MILHO 20KG", "SEMENTES", 40.0),
            LinhaEstoque("1", "ROUNDUP ORIGINAL MAIS 20L", "HERBICIDAS", 10.0),
            LinhaEstoque("10", "IVOMEC 1% 500ML", "VERMIFUGOS", 24.0),
        ),
        pas = listOf(
            LinhaPa("PANTEQ ORAL SERINGA 40G", "Oxfendazol + Triclorfon"),
            LinhaPa("CYDECTIN INJETAVEL FRASCO 500ML", "Moxidectina", "VERMIFUGOS"),
            LinhaPa("BERTAC 1L", "Triclorfon + Diclorvós (DDVP) + Violeta de genciana"),
            LinhaPa("ROFLIN INJETAVEL FRASCO 100ML", "Florfenicol"),
            LinhaPa("ROUNDUP ORIGINAL MAIS", "Glifosato"),
            LinhaPa("IVOMEC 1%", "Ivermectina"),
        ),
        mapa = emptyList(),
        historico = emptyList(),
        classes = listOf(LinhaClasseVet("236195", "Antibiótico", "Bovinos", "")),
    )
    private val produtos = Consulta.montarProdutos(snap)
    private fun p(cod: String) = produtos.first { it.codigo == cod }
    private fun busca(setor: Setor, q: String) =
        Consulta.aplicaFiltros(Consulta.produtosDaAba(produtos, setor, q), Filtros(busca = q)).map { it.codigo }

    @Test fun buscaAchaPanteqCydectinRoflin() {
        assertTrue("106160" in busca(Setor.LOJA_VET, "Panteq"))
        assertTrue("140449" in busca(Setor.LOJA_VET, "Cydectin"))
        assertTrue("236195" in busca(Setor.LOJA_VET, "Roflin"))
        // "Outros" com P.A. também aparece na busca do barracão
        assertTrue("106160" in busca(Setor.BARRACAO, "Panteq"))
    }

    @Test fun buscaPorPaECodigo() {
        assertTrue("140449" in busca(Setor.LOJA_VET, "moxidectina"))
        assertEquals(listOf("106160", "73768").sorted(), busca(Setor.LOJA_VET, "triclorfon").sorted())
        assertTrue("73768" in busca(Setor.BARRACAO, "73768"))
        // graxa sem P.A. não aparece na busca
        assertTrue(busca(Setor.BARRACAO, "graxa").isEmpty())
    }

    @Test fun lojaListaPrincipalLevaVetDeOutros() {
        val loja = Consulta.agrupar(Consulta.produtosDaAba(produtos, Setor.LOJA_VET), porUnidades = true) { it.pas }
        val comPa = loja.grupos.flatMap { it.produtos }.map { it.codigo }.toSet()
        assertTrue(comPa.containsAll(listOf("106160", "140449", "73768", "236195", "10")))
        // barracão continua só com defensivos
        assertEquals(listOf("1"), Consulta.produtosDaAba(produtos, Setor.BARRACAO).map { it.codigo })
    }

    @Test fun semPaSoOsVeterinariosDeOutros() {
        val loja = Consulta.agrupar(Consulta.produtosDaAba(produtos, Setor.LOJA_VET), porUnidades = true) { it.pas }
        assertEquals(listOf("500"), loja.semPa.map { it.codigo }) // mata bicheira entra; graxa, ferramenta e semente não
    }

    @Test fun categoriaGravadaNoCadastroVence() {
        assertEquals("VERMIFUGOS", p("140449").categoria)
        assertEquals("OUTROS", p("140449").categoriaEstoque)
        assertEquals(Setor.LOJA_VET, p("140449").setor) // a categoria gravada já põe na loja
        assertEquals("OUTROS", p("106160").categoria) // sem categoria gravada: a do estoque
        assertFalse(Consulta.vetForaDaLoja(p("501")))
    }
}
