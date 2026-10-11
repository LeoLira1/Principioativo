package br.com.camda.principioativo.ui

import br.com.camda.principioativo.core.Embalagem
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.PaMatcher
import br.com.camda.principioativo.core.Produto
import br.com.camda.principioativo.core.Quantidade
import br.com.camda.principioativo.data.Turso
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CadastroPaTest {

    @Test fun juntarMisturaSemRepetir() {
        assertEquals("Diuron + Hexazinona", PaMatcher.juntar(listOf("Diuron", " Hexazinona ", "DIURON")))
        assertEquals("Picloram + 2,4-D", PaMatcher.juntar(listOf("Picloram + 2,4-D")))
        assertEquals("", PaMatcher.juntar(emptyList()))
    }

    @Test fun sugestoesSemAcentoEPrefixoPrimeiro() {
        val conhecidos = paConhecidos(
            listOf(Produto("1", "X 1L", "", Quantidade(1.0, Embalagem.deNome("1L")), listOf("Tiametoxam"), emptyList())),
            listOf(
                InfoCatalogo("A", "Glufosinato sal de amônio", "", "", ""),
                InfoCatalogo("B", "Lambda-cialotrina + Tiametoxam", "", "", ""),
                InfoCatalogo("C", "Atrazina", "", "", ""),
            ),
        )
        assertEquals(4, conhecidos.size) // Tiametoxam não duplica
        // todos contêm "a", mas quem começa com "a" vem primeiro
        val a = sugerirPa("a", conhecidos, emptyList())
        assertEquals("Atrazina", a.first())
        assertEquals(4, a.size)
        assertEquals(listOf("Glufosinato sal de amônio"), sugerirPa("AMONIO", conhecidos, emptyList()))
        assertTrue(sugerirPa("tiam", conhecidos, listOf("tiametoxam")).isEmpty())
        assertTrue(sugerirPa("  ", conhecidos, emptyList()).isEmpty())
    }

    @Test fun corpoDaGravacaoIgualAoDashboard() {
        val corpo = Json.parseToJsonElement(Turso.corpoSalvarPa(" Jump 20l ", "Diuron + Hexazinona", "HERBICIDAS"))
        val reqs = corpo.jsonObject["requests"]!!.jsonArray
        val sqls = reqs.mapNotNull { it.jsonObject["stmt"]?.jsonObject?.get("sql")?.jsonPrimitive?.content }
        assertEquals("BEGIN", sqls.first())
        assertEquals("COMMIT", sqls.last())
        assertTrue(sqls[1].startsWith("DELETE FROM principios_ativos WHERE produto = ?"))
        val args = reqs[2].jsonObject["stmt"]!!.jsonObject["args"]!!.jsonArray
            .map { it.jsonObject["value"]!!.jsonPrimitive.content }
        assertEquals(listOf("JUMP 20L", "Diuron + Hexazinona", "HERBICIDAS"), args)
        assertEquals("close", reqs.last().jsonObject["type"]!!.jsonPrimitive.content)
    }

    private fun prod(cod: String, nome: String, pas: List<String> = emptyList(), cat: String = "") =
        Produto(cod, nome, cat, Quantidade(1.0, Embalagem.deNome(nome)), pas, emptyList())

    @Test fun buscaProdutoSemAcentoForaDeOrdemEComErroDeDigitacao() {
        val lista = listOf(
            prod("10", "KARATÊ ZEON 250 CS 1L", cat = "OUTROS"),
            prod("11", "KARATE ZEON 50 CS 5L", listOf("Lambda-cialotrina")),
            prod("12", "GLIFOSATO 20L"),
        )
        // sem P.A. vem antes; acha por qualquer setor/categoria
        assertEquals(listOf("10", "11"), buscarProdutos(lista, "karate").map { it.codigo })
        assertEquals(listOf("10", "11"), buscarProdutos(lista, "zeon karate").map { it.codigo })
        assertEquals(listOf("10", "11"), buscarProdutos(lista, "karatte").map { it.codigo })
        assertEquals(listOf("11"), buscarProdutos(lista, "karate 5l").map { it.codigo })
        assertEquals(listOf("12"), buscarProdutos(lista, "12").map { it.codigo })
        assertTrue(buscarProdutos(lista, "  ").isEmpty())
    }

    @Test fun corpoDaClassificacaoIgualAoDashboard() {
        val corpo = Json.parseToJsonElement(
            Turso.corpoSalvarClassificacao(listOf(" 123 ", "ab9", "123"), " Vermífugo ", "Bovinos", "", "2026-10-11 11:30:00"),
        )
        val reqs = corpo.jsonObject["requests"]!!.jsonArray
        val sqls = reqs.mapNotNull { it.jsonObject["stmt"]?.jsonObject?.get("sql")?.jsonPrimitive?.content }
        assertEquals("BEGIN", sqls.first())
        assertEquals("COMMIT", sqls.last())
        val inserts = reqs.filter {
            it.jsonObject["stmt"]?.jsonObject?.get("sql")?.jsonPrimitive?.content?.startsWith("INSERT OR REPLACE INTO classificacao_vet") == true
        }
        assertEquals(2, inserts.size) // código repetido não duplica
        val args = inserts[0].jsonObject["stmt"]!!.jsonObject["args"]!!.jsonArray
            .map { it.jsonObject["value"]!!.jsonPrimitive.content }
        assertEquals(listOf("123", "Vermífugo", "Bovinos", "", "app", "", "", "2026-10-11 11:30:00"), args)
        assertEquals("AB9", inserts[1].jsonObject["stmt"]!!.jsonObject["args"]!!.jsonArray[0].jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test fun classesConhecidasUsaGrafiaDoEstoqueSemRepetir() {
        val lista = classesConhecidas(
            listOf(
                prod("1", "A").copy(classe = "vermifugo"),
                prod("2", "B").copy(classe = "Pomada/Unguento"),
                prod("3", "C").copy(classe = "Probiótico"),
            ),
        )
        assertEquals("vermifugo", lista.first()) // a grafia usada no estoque vence
        assertEquals(1, lista.count { it.lowercase().startsWith("vermifugo") || it == "Vermífugo" })
        assertEquals(1, lista.count { it == "Pomada/Unguento" })
        assertTrue("Probiótico" in lista)
    }
}
