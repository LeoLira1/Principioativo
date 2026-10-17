package br.com.camda.principioativo.core

/**
 * Porte mínimo do `difflib.SequenceMatcher.ratio` / `get_close_matches` do
 * Python — o mesmo critério fuzzy (cutoff 0,72) que o camda-estoque usa para
 * achar o P.A. de um nome de produto. Sem "junk" (as strings têm < 200 chars,
 * onde o autojunk do Python não age).
 */
object Difflib {

    fun ratio(a: String, b: String): Double {
        val total = a.length + b.length
        if (total == 0) return 1.0
        return 2.0 * matches(a, b) / total
    }

    private fun matches(a: String, b: String): Int {
        var soma = 0
        // j2len do Python em arrays (deslocado em 1), reaproveitados em toda a recursão
        val ant = IntArray(b.length + 1)
        val novo = IntArray(b.length + 1)
        val fila = ArrayDeque<IntArray>()
        fila.add(intArrayOf(0, a.length, 0, b.length))
        while (fila.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = fila.removeLast()
            val (i, j, k) = longestMatch(a, b, alo, ahi, blo, bhi, ant, novo)
            if (k > 0) {
                soma += k
                if (alo < i && blo < j) fila.add(intArrayOf(alo, i, blo, j))
                if (i + k < ahi && j + k < bhi) fila.add(intArrayOf(i + k, ahi, j + k, bhi))
            }
        }
        return soma
    }

    /** Maior bloco igual em a[alo:ahi] × b[blo:bhi]; empate: menor i, depois menor j (como o Python). */
    private fun longestMatch(
        a: String, b: String, alo: Int, ahi: Int, blo: Int, bhi: Int, ant0: IntArray, novo0: IntArray,
    ): Triple<Int, Int, Int> {
        var bi = alo
        var bj = blo
        var bs = 0
        var ant = ant0
        var novo = novo0
        ant.fill(0, blo, bhi + 1)
        for (i in alo until ahi) {
            val c = a[i]
            novo[blo] = 0
            for (j in blo until bhi) {
                val k = if (b[j] == c) ant[j] + 1 else 0
                novo[j + 1] = k
                if (k > bs) {
                    bi = i - k + 1; bj = j - k + 1; bs = k
                }
            }
            val t = ant; ant = novo; novo = t
        }
        return Triple(bi, bj, bs)
    }

    /**
     * Melhor candidato com ratio ≥ cutoff (empate: maior string, como o heapq do Python).
     * Como o `get_close_matches`, descarta antes pelos limites baratos
     * (`real_quick_ratio` pelo tamanho, `quick_ratio` pelas letras em comum):
     * são tetos do ratio, então o resultado não muda — só evita o cálculo
     * caro para quase todos os candidatos.
     */
    fun closestMatch(word: String, possibilidades: Collection<String>, cutoff: Double): String? {
        val letrasWord = HashMap<Char, Int>()
        word.forEach { letrasWord.merge(it, 1, Int::plus) }
        var melhor: String? = null
        var melhorScore = -1.0
        for (x in possibilidades) {
            val total = x.length + word.length
            if (total > 0) {
                if (2.0 * minOf(x.length, word.length) / total < cutoff) continue
                if (2.0 * comuns(letrasWord, x) / total < cutoff) continue
            }
            val s = ratio(x, word)
            if (s < cutoff) continue
            if (s > melhorScore || (s == melhorScore && x > melhor!!)) {
                melhor = x; melhorScore = s
            }
        }
        return melhor
    }

    /** Letras em comum (com repetição), independente da ordem. */
    private fun comuns(letras: Map<Char, Int>, x: String): Int {
        val disp = HashMap(letras)
        var n = 0
        for (c in x) {
            val d = disp[c] ?: 0
            if (d > 0) { disp[c] = d - 1; n++ }
        }
        return n
    }
}
