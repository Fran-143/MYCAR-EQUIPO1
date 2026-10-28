package com.mycar.app.features.alertas.programacion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycar.app.features.alertas.programacion.domain.*
import com.mycar.app.ui.theme.MyCarTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Exclusiva de DEBUG. Recorre las tres vistas con datos marcados; no HTTP, archivos ni BD. */
class ProgramacionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { MyCarTheme {
            val scope = rememberCoroutineScope()
            val coordinador = remember { CoordinadorProgramacion(EjemplosRf22(), { MemoriaEjemplo() }) }
            val estado by coordinador.estado.collectAsStateWithLifecycle()
            LaunchedEffect(coordinador) {
                coordinador.iniciar(); coordinador.seleccionarTipo(1)
                coordinador.intervalos(cantidad = "2", km = "5000")
            }
            ProgramarAlertaScreen(estado, AccionesProgramacion(
                volver = ::finish, vehiculo = { scope.launch { coordinador.seleccionarVehiculo(it) } },
                tipo = { scope.launch { coordinador.seleccionarTipo(it) } }, servicio = coordinador::editarServicio,
                sugerencia = { scope.launch { coordinador.elegirSugerencia(it) } }, referencia = coordinador::usarReferencia,
                criterio = coordinador::criterio, cantidad = { coordinador.intervalos(cantidad = it) },
                kilometros = { coordinador.intervalos(km = it) }, unidad = { coordinador.intervalos(unidad = it) },
                revisar = { scope.launch { coordinador.revisar() } }, confirmar = { scope.launch { coordinador.confirmar() } },
                editar = coordinador::volverEditar, comprobar = { scope.launch { coordinador.comprobar() } },
                reenviar = { scope.launch { coordinador.reenviarMismaOperacion() } },
                recargar = { scope.launch { coordinador.recargarDatos() } }, cerrarSesion = ::finish
            ), Modifier.safeDrawingPadding(), demostracion = true)
        } }
    }
}
private class MemoriaEjemplo : PendientesProgramacion {
    var pendiente: SolicitudRf22? = null
    override fun leer() = pendiente
    override fun guardar(solicitud: SolicitudRf22) { pendiente = solicitud }
    override fun borrar() { pendiente = null }
    override fun cerrar(borrar: Boolean) { if (borrar) pendiente = null }
}
private class EjemplosRf22 : ProgramacionRepository {
    private val hoy = LocalDate.of(2026, 10, 28)
    override suspend fun contexto() = ContextoRf22("EJEMPLO", hoy.toString(), listOf(
        VehiculoRf22(1, "ABC-123", "Toyota", "Corolla", true, "Sintético"),
        VehiculoRf22(2, "DEF-456", "Kia", "Rio", false, null)),
        listOf(TipoRf22(1, "CAMBIO_ACEITE", "Cambio de aceite"), TipoRf22(2, "OTRO", "Otro")))
    override suspend fun antecedentes(vehiculo: Long, tipo: Long, servicioElegido: String?) =
        AntecedentesRf22(hoy.toString(), if (vehiculo == 1L) 1 else null, if (vehiculo == 1L) 50000 else null,
            if (tipo == 2L) listOf("Cambio de correa") else emptyList(),
            if (tipo == 1L || servicioElegido != null) ReferenciaRf22(1, "2026-09-10", 45000, servicioElegido) else null)
    override suspend fun revisar(configuracion: ConfiguracionRf22): RevisionRf22 {
        val tiempo = configuracion.criterio != CriterioRf22.POR_KILOMETRAJE
        val km = configuracion.criterio != CriterioRf22.POR_TIEMPO
        val fecha = if (!tiempo) null else if (configuracion.unidadTiempo == UnidadRf22.SEMANAS)
            hoy.plusWeeks(configuracion.cantidadTiempo!!.toLong()) else hoy.plusMonths(configuracion.cantidadTiempo!!.toLong())
        return RevisionRf22(configuracion, BasesRf22(hoy.toString(), if (km) 1 else null, if (km) 50000 else null),
            ObjetivosRf22(fecha?.toString(), if (km) 50000 + configuracion.intervaloKm!! else null,
                if (fecha == null || fecha.isAfter(hoy.plusDays(30))) "PROGRAMADA" else if (fecha.isAfter(hoy.plusDays(7))) "PROXIMA" else "PENDIENTE"),
            configuracion.idMantenimientoReferencia?.let { ReferenciaRf22(it, "2026-09-10", 45000, configuracion.servicioOtro) })
    }
    override suspend fun confirmar(solicitud: SolicitudRf22) = ResultadoRf22(solicitud.idOperacion, 1,
        solicitud.revision.configuracion.idVehiculo, solicitud.revision.configuracion.servicioOtro ?: "Cambio de aceite",
        solicitud.revision.configuracion.criterio, solicitud.revision.objetivos.fechaObjetivo,
        solicitud.revision.objetivos.kmObjetivo, solicitud.revision.objetivos.estado, true)
    override suspend fun consultar(idOperacion: String) = OperacionRf22(idOperacion, "NO_ENCONTRADA", null)
}
