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
}
