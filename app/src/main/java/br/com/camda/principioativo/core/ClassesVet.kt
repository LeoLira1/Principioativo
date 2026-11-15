package br.com.camda.principioativo.core

/**
 * Lista oficial das classes da Loja Vet. Só estas são gravadas em `classificacao_vet.classe`,
 * por qualquer caminho (botão Classificar do app e classificar_produtos do MCP: CLASSES_VET em
 * camda-mcp/src/cadastro_vet.ts — mude as duas juntas).
 */
val CLASSES_VET = listOf(
    "Vermífugo", "Antibiótico", "Anti-inflamatório", "Antiparasitário externo",
    "Carrapaticida/Mosquicida", "Vacina", "Soro", "Hormônio/Reprodução",
    "Vitamina/Suplemento", "Anestésico/Analgésico", "Antitóxico", "Antidiarreico",
    "Homeopático", "Pomada/Unguento", "Identificação animal", "Material", "Outros",
)

/** Grafia oficial da classe (ignora acento, maiúsculas e pontuação), ou null se não é da lista. */
fun classeOficial(txt: String): String? {
    val k = chavePa(txt)
    if (k.isEmpty()) return null
    return CLASSES_VET.firstOrNull { chavePa(it) == k }
}

/** A classe gravada está exatamente como na lista oficial? ("Antidiarrerico" não está). */
fun ehClasseOficial(txt: String): Boolean = classeOficial(txt) == txt.trim()
