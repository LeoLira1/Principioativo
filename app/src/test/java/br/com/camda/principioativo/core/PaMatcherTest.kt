package br.com.camda.principioativo.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PaMatcherTest {

    private val matcher = PaMatcher(
        mapOf(
            "DECTOMAX INJETAVEL FRASCO 500ML" to "Doramectina",
            "ROUNDUP ORIGINAL MAIS" to "Glifosato",
            "INSEMAX POUR ON 1L" to "Cipermetrina + Clorpirifós",
        ),
    )

    @Test fun sufixoGenericoNaoCasaProdutosDiferentes() {
        // 78–82% iguais só pelo "INJETAVEL FRASCO": herdavam "Doramectina" por engano
        assertEquals("", matcher.lookup("AMOX JA INJETAVEL FRASCO 100ML"))
        assertEquals("", matcher.lookup("LACTOCINA INJETAVEL FRASCO 100ML"))
        assertEquals("", matcher.lookup("TREO ACE INJETAVEL FRASCO 500ML"))
        assertEquals("", matcher.lookup("TIGUVON SPOT ON 1L"))
        assertEquals("", matcher.lookup("INJETAVEL FRASCO 100ML")) // só palavras genéricas
    }

    @Test fun mesmoProdutoContinuaCasando() {
        assertEquals("Doramectina", matcher.lookup("DECTOMAX INJETAVEL FRASCO 500ML"))
        assertEquals("Doramectina", matcher.lookup("Dectomax injetável frasco 500 ml"))
        assertEquals("Cipermetrina + Clorpirifós", matcher.lookup("INSEMAX POUR ON 5L"))
        assertEquals("Glifosato", matcher.lookup("ROUNDUP ORIGINAL MAIS 20L"))
    }

    @Test fun variacaoDoMesmoNomeAindaCasaPorSemelhanca() {
        assertEquals("Doramectina", matcher.lookup("DECTOMAX INJ FRASCO 1000ML"))
        assertEquals("Glifosato", matcher.lookup("ROUNDUP ORIGINAL MAI 5L"))
    }

    @Test fun miolo() {
        assertEquals("AMOX JA", miolo("AMOX JA INJETAVEL FRASCO 100ML"))
        assertEquals("INSEMAX", miolo("INSEMAX POUR ON 1L"))
        assertEquals("", miolo("USO TOPICO FRASCO 5L"))
    }
}
