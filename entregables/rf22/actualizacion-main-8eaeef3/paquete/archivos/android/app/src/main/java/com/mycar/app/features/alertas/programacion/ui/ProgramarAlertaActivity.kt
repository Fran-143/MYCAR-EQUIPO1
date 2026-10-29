package com.mycar.app.features.alertas.programacion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.programacion.data.*
import com.mycar.app.features.alertas.programacion.viewmodel.ProgramacionViewModel
import com.mycar.app.features.autenticacion.data.AuthGraph
import com.mycar.app.features.autenticacion.data.SessionPhase
import com.mycar.app.features.autenticacion.ui.AuthGate
import com.mycar.app.ui.theme.MyCarTheme

/** Reutiliza el acceso, registro, almacén cifrado y cierre que Elsa integró en main. */
class ProgramarAlertaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        AuthGraph.repository(applicationContext).registerLocalCleanup("rf22") { ArchivoProgramacionPendiente.limpiarLocales(applicationContext) }
        setContent { MyCarTheme { Surface(Modifier.fillMaxSize()) {
            val sesiones = remember { AuthGraph.repository(applicationContext) }
            val sesion by sesiones.session.collectAsStateWithLifecycle()
            val vinculo = if (sesion.phase == SessionPhase.AUTHENTICATED) sesiones.authenticatedContext() else null
            var moduloActual by remember { mutableStateOf<ProgramacionViewModel?>(null) }
            AuthGate(onLocalSessionCleared = { moduloActual?.retirar(true) }) {
                if (vinculo != null) {
                    val contextoVersion = vinculo.version
                    val vm: ProgramacionViewModel = viewModel(key = "rf22-sesion-${vinculo.sessionId}",
                        factory = ProgramacionViewModel.Factory(sesiones,
                            RemoteProgramacionRepository(AuthGraph.service(applicationContext, ProgramacionApi::class.java), PeticionesSesionMyCar(sesiones)),
                            { cuenta -> PendientesSesionRf22(sesiones, ArchivoProgramacionPendiente(applicationContext, cuenta), contextoVersion) }))
                    SideEffect { moduloActual = vm }
                    val coordinador = vm.coordinador
                    val e by coordinador.estado.collectAsStateWithLifecycle()
                    ProgramarAlertaScreen(e, AccionesProgramacion(
                        volver = ::finish,
                        vehiculo = { vm.ejecutar { seleccionarVehiculo(it) } }, tipo = { vm.ejecutar { seleccionarTipo(it) } },
                        servicio = coordinador::editarServicio, sugerencia = { vm.ejecutar { elegirSugerencia(it) } },
                        referencia = coordinador::usarReferencia, criterio = coordinador::criterio,
                        cantidad = { coordinador.intervalos(cantidad = it) }, kilometros = { coordinador.intervalos(km = it) },
                        unidad = { coordinador.intervalos(unidad = it) }, revisar = { vm.ejecutar { revisar() } },
                        confirmar = { vm.ejecutar { confirmar() } }, editar = coordinador::volverEditar,
                        comprobar = { vm.ejecutar { comprobar() } }, reenviar = { vm.ejecutar { reenviarMismaOperacion() } },
                        recargar = { vm.ejecutar { if (e.contexto == null) iniciar() else recargarDatos() } },
                        cerrarSesion = null
                    ), Modifier.navigationBarsPadding().imePadding())
                } else CircularProgressIndicator()
            }
        } } }
    }
}
