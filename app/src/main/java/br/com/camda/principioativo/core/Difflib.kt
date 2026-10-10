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
        val b2j = HashMap<Char, MutableList<Int>>()
        b.forEachIndexed { j, c -> b2j.getOrPut(c) { mutableListOf() }.add(j) }
        var soma = 0
        val fila = ArrayDeque<IntArray>()
        fila.add(intArrayOf(0, a.length, 0, b.length))
        while (fila.isNotEmpty()) {
            val (alo, ahi, blo, bhi) = fila.removeLast()
            val (i, j, k) = longestMatch(a, b2j, alo, ahi, blo, bhi)
            if (k > 0) {
                soma += k
                if (alo < i && blo < j) fila.add(intArrayOf(alo, i, blo, j))
                if (i + k < ahi && j + k < bhi) fila.add(intArrayOf(i + k, ahi, j + k, bhi))
            }
        }
        return soma
    }

    private fun longestMatch(
        a: String, b2j: Map<Char, List<Int>>, alo: Int, ahi: Int, blo: Int, bhi: Int,
    ): Triple<Int, Int, Int> {
        var bi = alo
        var bj = blo
        var bs = 0
        var j2len = HashMap<Int, Int>()
        for (i in alo until ahi) {
            val novo = HashMap<Int, Int>()
            for (j in b2j[a[i]] ?: emptyList()) {
                if (j < blo) continue
                if (j >= bhi) break
                val k = (j2len[j - 1] ?: 0) + 1
                novo[j] = k
                if (k > bs) {
                    bi = i - k + 1; bj = j - k + 1; bs = k
                }
            }
            j2len = novo
        }
        return Triple(bi, bj, bs)
    }

    /** Melhor candidato com ratio ≥ cutoff (empate: maior string, como o heapq do Python). */
    fun closestMatch(word: String, possibilidades: Collection<String>, cutoff: Double): String? {
        var melhor: String? = null
        var melhorScore = -1.0
        for (x in possibilidades) {
            val s = ratio(x, word)
            if (s < cutoff) continue
            if (s > melhorScore || (s == melhorScore && x > melhor!!)) {
                melhor = x; melhorScore = s
            }
        }
        return melhor
    }
}
