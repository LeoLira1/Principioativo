package br.com.camda.principioativo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.camda.principioativo.core.Consulta
import br.com.camda.principioativo.core.Filtros
import br.com.camda.principioativo.core.InfoCatalogo
import br.com.camda.principioativo.core.Produto
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
    val produtos: List<Produto> = emptyList(),
    val atualizadoEm: Long = 0L,
    val filtros: Filtros = Filtros(),
    val paAberto: String? = null,
    val catalogo: List<InfoCatalogo> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repositorio(app)
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    init {
        viewModelScope.launch {
            val (cred, cache, catalogo) = withContext(Dispatchers.IO) {
                Triple(runCatching { repo.credenciais() }.getOrNull(), repo.cache(), repo.catalogo())
            }
            _estado.update { it.copy(conectado = cred != null, catalogo = catalogo) }
            if (cache != null) aplicar(cache.first, cache.second)
            if (cred != null) atualizar()
        }
    }

    private suspend fun aplicar(snap: Snapshot, quando: Long) {
        val produtos = withContext(Dispatchers.Default) { Consulta.montarProdutos(snap) }
        _estado.update { it.copy(snapshot = snap, produtos = produtos, atualizadoEm = quando) }
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
            }
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
            } catch (e: Exception) {
                _estado.update { it.copy(carregando = false, erro = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun desconectar() {
        viewModelScope.launch(Dispatchers.IO) { repo.apagarCredenciais() }
        _estado.update { Estado(catalogo = it.catalogo) }
    }

    fun filtros(f: Filtros) = _estado.update { it.copy(filtros = f) }
    fun abrir(chave: String?) = _estado.update { it.copy(paAberto = chave) }
    fun limparErro() = _estado.update { it.copy(erro = null) }
}
