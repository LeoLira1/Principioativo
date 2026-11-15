package br.com.camda.principioativo.core

import br.com.camda.principioativo.data.Turso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Produtos que não são veterinários e caíam na Loja Vet (OUTROS com P.A. cadastrado):
 * o MCP marca em `setor_override` (setor = "fora_vet") e o app tira da loja.
 */
class SetorOverrideTest {

    private fun snap(overrides: List<LinhaSetorOverride>) = Snapshot(
        estoque = listOf(
            LinhaEstoque("US33936", "REGULADOR CRESCIMENTO ETHREL 720 20L", "OUTROS", 76.0),
            LinhaEstoque("277973", "REGULADOR CRESCIMENTO CURAVIAL 300G", "OUTROS", 98.0),
            LinhaEstoque("10", "IVOMEC 1% 500ML", "VERMIFUGOS", 24.0),
            LinhaEstoque("100259993", "REGULADOR CRESCIMENTO PROGIBB 400 250GR", "MATURADORES", 57.0),
            LinhaEstoque("US100259993", "REGULADOR CRESCIMENTO PROGIBB 400 250GR", "MATURADORES", 8250.0),
        ),
        pas = listOf(
            LinhaPa("REGULADOR CRESCIMENTO ETHREL 720 20L", "Etefom", "MATURADORES"),
            LinhaPa("REGULADOR CRESCIMENTO CURAVIAL 300G", "Sulfometurom-metil", "OUTROS"),
            LinhaPa("IVOMEC 1%", "Ivermectina"),
            LinhaPa("REGULADOR CRESCIMENTO PROGIBB 400 250GR", "Ácido Giberélico", "MATURADORES"),
        ),
        mapa = emptyList(),
        historico = emptyList(),
        overrides = overrides,
    )

    private fun loja(s: Snapshot) = Consulta.produtosDaAba(Consulta.montarProdutos(s), Setor.LOJA_VET).map { it.codigo }.toSet()

    @Test fun semMarcacaoReguladoresCaemNaLoja() {
        assertEquals(setOf("US33936", "277973", "10"), loja(snap(emptyList())))
    }

    @Test fun foraVetTiraDaLojaSemMexerNaCategoria() {
        val s = snap(listOf(LinhaSetorOverride("us33936", "fora_vet"), LinhaSetorOverride("277973.0", "fora_vet")))
        assertEquals(setOf("10"), loja(s))
        val ethrel = Consulta.montarProdutos(s).first { it.codigo == "US33936" }
        assertTrue(ethrel.foraDaVet)
        assertEquals(Setor.OUTRO, ethrel.setor)
        assertEquals("OUTROS", ethrel.categoriaEstoque)
        assertFalse(Consulta.vetForaDaLoja(ethrel))
        // nem pela busca da loja
        assertFalse("US33936" in Consulta.produtosDaAba(Consulta.montarProdutos(s), Setor.LOJA_VET, "ethrel").map { it.codigo })
    }

    @Test fun foraVetTiraAteVeterinarioPelaCategoria() {
        val s = snap(listOf(LinhaSetorOverride("10", "fora_vet")))
        assertFalse("10" in loja(s))
        // outro valor de setor não tira
        assertTrue("10" in loja(snap(listOf(LinhaSetorOverride("10", "outra_coisa")))))
    }

    @Test fun tabelaAusenteNaoViraAviso() {
        fun ok(rows: String) = """{"type":"ok","response":{"type":"execute","result":{"cols":[],"rows":$rows}}}"""
        val corpo = """
        {"baton":null,"results":[
          ${ok("""[[{"type":"text","value":"US33936"},{"type":"text","value":"ETHREL 20L"},{"type":"text","value":"OUTROS"},{"type":"integer","value":"7"}]]""")},
          ${ok("[]")}, ${ok("[]")}, ${ok("[]")},
          {"type":"error","error":{"message":"SQLite error: no such table: setor_override"}},
          {"type":"ok","response":{"type":"close"}}
        ]}
        """.trimIndent()
        val s = Turso.parseBase(corpo)
        assertTrue(s.avisos.isEmpty())
        assertTrue(s.overrides.isEmpty())

        val comTabela = corpo.replace(
            """{"type":"error","error":{"message":"SQLite error: no such table: setor_override"}}""",
            ok("""[[{"type":"text","value":"US33936"},{"type":"text","value":"fora_vet"}]]"""),
        )
        assertEquals(listOf(LinhaSetorOverride("US33936", "fora_vet")), Turso.parseBase(comTabela).overrides)
    }

    /** PROGIBB: MATURADORES no Protheus, com P.A. Não é da loja, nem com busca (caía em "Sem classificação"). */
    @Test fun buscaNaLojaNaoTrazNaoVeterinario() {
        val ps = Consulta.montarProdutos(snap(emptyList()))
        for (q in listOf("progibb", "regulador", "giberelico", "100259993")) {
            val aba = Consulta.aplicaFiltros(Consulta.produtosDaAba(ps, Setor.LOJA_VET, q), Filtros(busca = q))
            val semClasse = Consulta.agruparPorClasse(aba).grupos.firstOrNull { it.nome == CLASSE_SEM }?.produtos.orEmpty()
            assertFalse(q, semClasse.any { it.nome.contains("PROGIBB") })
        }
        // no barracão a busca por P.A. ainda acha
        assertTrue("100259993" in Consulta.produtosDaAba(ps, Setor.BARRACAO, "progibb").map { it.codigo })
    }
}
