package br.com.camda.principioativo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.camda.principioativo.ui.App
import br.com.camda.principioativo.ui.MainViewModel
import br.com.camda.principioativo.ui.TemaPa

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaPa {
                val estado = vm.estado.collectAsStateWithLifecycle().value
                App(vm, estado)
            }
        }
    }
}
