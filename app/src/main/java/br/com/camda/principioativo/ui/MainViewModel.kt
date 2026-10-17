package br.com.camda.principioativo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.camda.principioativo.core.Consulta
import br.com.camda.principioativo.core.Filtros
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.LinhaHistorico
import br.com.camda.principioativo.core.PaMatcher
import br.com.camda.principioativo.core.Produto
import br.com.camda.principioativo.core.Setor
import br.com.camda.principioativo.core.Snapshot
import br.com.camda.principioativo.data.Credenciais
import br.com.camda.principioativo.data.Repositorio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Chave especial do grupo "Sem P.A. cadastrado". */
const val CHAVE_SEM_PA = "__sem_pa__"

data class Estado(
    val conectado: Boolean = false,
    val carregando: Boolean = false,
    val erro: String? = null,
    val snapshot: Snapshot? = null,
    /** Histórico do gráfico de linhas: chega depois da lista, numa leitura à parte. */
    val historico: List<LinhaHistorico> = emptyList(),
    val produtos: List<Produto> = emptyList(),
    val atualizadoEm: Long = 0L,
    val filtros: Filtros = Filtros(),
    val paAberto: String? = null,
    val catalogo: List<InfoCatalogo> = emptyList(),
    val salvando: Boolean = false,
    /** Resultado do último cadastro: (ok, mensagem). */
    val aviso: Pair<Boolean, String>? = null,
    val setor: Setor = Setor.BARRACAO,
    /** Loja Vet: lista/gráficos por classe terapêutica (true) ou por P.A. (false). */
    val porClasse: Boolean = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repositorio(app)
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    init {
        viewModelScope.launch {
            // a leitura do Turso já sai enquanto o cache e o catálogo carregam
            val cred = withContext(Dispatchers.IO) { runCatching { repo.credenciais() }.getOrNull() }
            _estado.update { it.copy(conectado = cred != null) }
            if (cred != null) atualizar()
            withContext(Dispatchers.IO) { repo.cache() }?.let { (snap, quando) -> aplicar(snap, quando) }
            val catalogo = withContext(Dispatchers.IO) { repo.catalogo() }
            _estado.update { it.copy(catalogo = catalogo) }
        }
    }

    private suspend fun aplicar(snap: Snapshot, quando: Long) {
        val produtos = withContext(Dispatchers.Default) { Consulta.montarProdutos(snap) }
        _estado.update {
            // histórico do cache só enquanto o do Turso não chegou
            val hist = it.historico.ifEmpty { snap.historico }
            // cache e leitura do Turso correm juntos na abertura: o mais novo vence
            if (quando < it.atualizadoEm) it.copy(historico = hist)
            else it.copy(snapshot = snap, produtos = produtos, atualizadoEm = quando, historico = hist)
        }
    }

    /** Depois da lista na tela: falha aqui só deixa o gráfico com o histórico do cache. */
    private suspend fun atualizarHistorico(cred: Credenciais) {
        val hist = runCatching { withContext(Dispatchers.IO) { repo.atualizarHistorico(cred) } }.getOrNull() ?: return
        _estado.update { it.copy(historico = hist) }
    }

    fun atualizar() {
        val cred = runCatching { repo.credenciais() }.getOrNull() ?: return
        if (_estado.value.carregando) return
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            try {
                val (snap, quando) = withContext(Dispatchers.IO) { repo.atualizar(cred) }
                aplicar(snap, quando)
                _estado.update { it.copy(carregando = false) }
            } catch (e: Exception) {
                _estado.update { it.copy(carregando = false, erro = e.message ?: e.javaClass.simpleName) }
                return@launch
            }
            atualizarHistorico(cred)
        }
    }

    /** Testa a conexão antes de salvar: só guarda credencial que funciona. */
    fun conectar(url: String, token: String) {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            try {
                val cred = Credenciais(url, token)
                val (snap, quando) = withContext(Dispatchers.IO) {
                    val r = repo.atualizar(cred)
                    repo.salvarCredenciais(cred)
                    r
                }
                aplicar(snap, quando)
                _estado.update { it.copy(conectado = true, carregando = false) }
                atualizarHistorico(cred)
            } catch (e: Exception) {
                _estado.update { it.copy(carregando = false, erro = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun desconectar() {
        viewModelScope.launch(Dispatchers.IO) { repo.apagarCredenciais() }
        _estado.update { Estado(catalogo = it.catalogo) }
    }

    /** Cadastra/troca o P.A. do produto e relê o banco para refletir na hora. */
    fun salvarPa(produto: Produto, pas: List<String>, categoria: String, aoTerminar: (Boolean) -> Unit) {
        val cred = runCatching { repo.credenciais() }.getOrNull() ?: return
        val texto = PaMatcher.juntar(pas)
        if (texto.isBlank()) return
        _estado.update { it.copy(salvando = true, aviso = null) }
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) { repo.salvarPa(cred, produto.nome, texto, categoria) }
                val (snap, quando) = withContext(Dispatchers.IO) { repo.atualizar(cred) }
                aplicar(snap, quando)
                _estado.update { it.copy(salvando = false, aviso = true to "${produto.nome} → $texto") }
                true
            } catch (e: Exception) {
                _estado.update { it.copy(salvando = false, aviso = false to (e.message ?: e.javaClass.simpleName)) }
                false
            }
            aoTerminar(ok)
        }
    }

    /** Classifica vários produtos de uma vez e relê o banco. */
    fun salvarClassificacao(
        produtos: List<Produto>, classe: String, especie: String, segmento: String, aoTerminar: (Boolean) -> Unit,
    ) {
        val cred = runCatching { repo.credenciais() }.getOrNull() ?: return
        if (classe.isBlank() || produtos.isEmpty()) return
        _estado.update { it.copy(salvando = true, aviso = null) }
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    repo.salvarClassificacao(cred, produtos.map { it.codigo }, classe, especie, segmento)
                }
                val (snap, quando) = withContext(Dispatchers.IO) { repo.atualizar(cred) }
                aplicar(snap, quando)
                val n = produtos.size
                _estado.update { it.copy(salvando = false, aviso = true to "$n produto${if (n != 1) "s" else ""} → ${classe.trim()}") }
                true
            } catch (e: Exception) {
                _estado.update { it.copy(salvando = false, aviso = false to (e.message ?: e.javaClass.simpleName)) }
                false
            }
            aoTerminar(ok)
        }
    }

    fun limparAviso() = _estado.update { it.copy(aviso = null) }

    fun filtros(f: Filtros) = _estado.update { it.copy(filtros = f) }

    /** Troca barracão ↔ loja: filtros do painel não fazem sentido entre setores. */
    fun setor(s: Setor) = _estado.update {
        if (it.setor == s) it else it.copy(setor = s, filtros = Filtros(busca = it.filtros.busca), paAberto = null)
    }

    fun porClasse(v: Boolean) = _estado.update { it.copy(porClasse = v) }
    fun abrir(chave: String?) = _estado.update { it.copy(paAberto = chave) }
    fun limparErro() = _estado.update { it.copy(erro = null) }
}
