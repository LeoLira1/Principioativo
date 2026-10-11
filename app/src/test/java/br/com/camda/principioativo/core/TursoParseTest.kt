package br.com.camda.principioativo.core

import br.com.camda.principioativo.data.Turso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TursoParseTest {

    private fun ok(vararg linhas: String) =
        """{"type":"ok","response":{"type":"execute","result":{"cols":[],"rows":[${linhas.joinToString(",")}]}}}"""

    @Test fun parseDoPipeline() {
        val corpo = """
        {"baton":null,"results":[
          ${ok("""[{"type":"text","value":"100"},{"type":"text","value":"JUMP 5L"},{"type":"text","value":"HERB"},{"type":"integer","value":"7"}]""")},
          ${ok("""[{"type":"text","value":"JUMP"},{"type":"text","value":"Diuron + Hexazinona"}]""")},
          {"type":"error","error":{"message":"no such table: mapa_posicoes"}},
          ${ok("""[{"type":"text","value":"100"},{"type":"text","value":"2026-10-01"},{"type":"float","value":3.5}]""", """[{"type":"text","value":"100"},{"type":"text","value":"2026-10-02"},{"type":"null"}]""")},
          ${ok("""[{"type":"text","value":"100"},{"type":"text","value":"Vermífugo"},{"type":"text","value":"Bovinos"},{"type":"null"}]""")},
          {"type":"ok","response":{"type":"close"}}
        ]}
        """.trimIndent()
        val s = Turso.parse(corpo)
        assertEquals(LinhaEstoque("100", "JUMP 5L", "HERB", 7.0), s.estoque.single())
        assertEquals("Diuron + Hexazinona", s.pas.single().principioAtivo)
        assertTrue(s.mapa.isEmpty())
        assertEquals(1, s.avisos.size)
        assertEquals(listOf(3.5, 0.0), s.historico.map { it.qtdEstoque })
        assertEquals(LinhaClasseVet("100", "Vermífugo", "Bovinos", ""), s.classes.single())
    }

    @Test fun urlDoPipeline() {
        assertEquals("https://camda-x.turso.io/v2/pipeline", Turso.urlPipeline("libsql://camda-x.turso.io"))
        assertEquals("https://camda-x.turso.io/v2/pipeline", Turso.urlPipeline("https://camda-x.turso.io/"))
        assertEquals("https://camda-x.turso.io/v2/pipeline", Turso.urlPipeline(" camda-x.turso.io "))
    }

    @Test fun difflibIgualAoPython() {
        // difflib.SequenceMatcher(None, "ROUNDUP ORIGINAL MAIS", "ROUNDUP ORIGINAL MAIZ").ratio()
        assertEquals(0.9523809523809523, Difflib.ratio("ROUNDUP ORIGINAL MAIS", "ROUNDUP ORIGINAL MAIZ"), 1e-12)
        // difflib.SequenceMatcher(None, "abcd", "bcde").ratio() == 0.75
        assertEquals(0.75, Difflib.ratio("abcd", "bcde"), 1e-12)
    }
}
