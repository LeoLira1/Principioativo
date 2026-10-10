package br.com.camda.principioativo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbalagemTest {

    private fun q(nome: String, qtd: Double) = Quantidade(qtd, Embalagem.deNome(nome))

    @Test fun umLitroMostraUnidades() {
        val x = q("HEXAZINONA 250SL 1L", 36.0)
        assertEquals(TipoEmbalagem.L1, x.emb.tipo)
        assertEquals(36.0, x.litros, 0.0)
        assertEquals("36 unidades 🧴", x.fisicoTexto())
    }

    @Test fun cincoLitrosFrascosECaixas() {
        val x = q("GLIFOSATO NORTOX 5L", 120.0)
        assertEquals(TipoEmbalagem.L5, x.emb.tipo)
        assertEquals(600.0, x.litros, 0.0)
        val f = x.fisico()
        assertEquals(120.0, f[0].valor, 0.0); assertEquals("frascos", f[0].unidade)
        assertEquals(30.0, f[1].valor, 0.0); assertEquals("caixas (5L x 4)", f[1].unidade)
    }

    @Test fun dezLitrosGaloesSoltos() {
        val x = q("ATRAZINA 10L", 7.0)
        assertEquals(TipoEmbalagem.L10, x.emb.tipo)
        assertEquals("7 galões", x.fisicoTexto())
        assertEquals(70.0, x.litros, 0.0)
    }

    @Test fun vinteLitrosCadaUnidadeEUmBalde() {
        val x = q("REGULADOR CRESCIMENTO ETHREL 720 20L", 90.0)
        assertEquals(TipoEmbalagem.L20, x.emb.tipo)
        assertEquals("90 baldes 🪣", x.fisicoTexto())
        assertEquals(1800.0, x.litros, 0.0)
    }

    @Test fun formatoNxM() {
        val x = q("AMISTAR TOP 4X5L", 12.0)
        assertEquals(TipoEmbalagem.CAIXA_NXM, x.emb.tipo)
        assertEquals("4X5L", x.emb.rotulo)
        assertEquals(60.0, x.litros, 0.0)
        // caixas = litros ÷ (n × m) = 60 ÷ 20
        assertEquals(3.0, x.fisico()[1].valor, 0.0)

        val y = q("PRODUTO 12 X 1L", 24.0)
        assertEquals(2.0, y.fisico()[1].valor, 0.0)
        val z = q("PRODUTO 2X500ML", 8.0)
        assertEquals(4.0, z.litros, 0.0)
        assertEquals(4.0, z.fisico()[1].valor, 0.0)
        assertEquals("2X500mL", z.emb.rotulo)
    }

    @Test fun mililitrosEKg() {
        val ml = Embalagem.deNome("CURATHANE 250ML")
        assertEquals(0.25, ml.porUnidade!!, 1e-9)
        assertEquals(TipoEmbalagem.OUTROS_L, ml.tipo)
        val kg = q("ROUNDUP WG 5KG", 3.0)
        assertEquals(TipoEmbalagem.KG, kg.emb.tipo)
        assertEquals(15.0, kg.kg, 0.0)
        assertEquals(0.0, kg.litros, 0.0)
        assertEquals(0.5, Embalagem.deNome("PRODUTO 500G").porUnidade!!, 1e-9)
        assertEquals(1.5, Embalagem.deNome("PRODUTO 1,5L").porUnidade!!, 1e-9)
    }

    @Test fun semEmbalagemNoNome() {
        val e = Embalagem.deNome("HEXAZINONA 250SL")
        assertNull(e.porUnidade)
        assertEquals(TipoEmbalagem.SEM, e.tipo)
        assertEquals("", Quantidade(5.0, e).volumeTexto())
    }

    @Test fun formatacaoPtBr() {
        assertEquals("12.345", fmtInt(12345.0))
        assertEquals("7,5", fmtNum(7.5))
        assertEquals("30", fmtNum(30.0))
    }
}
