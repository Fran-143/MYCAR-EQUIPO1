package com.mycar.app.features.sesion.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.mycar.app.BuildConfig
import com.mycar.app.features.autenticacion.data.*
import com.mycar.app.features.sesion.data.*
import com.mycar.app.features.vehiculos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

class CuentaVehiculos(val usuario: Long, val ciclo: String,
    val cliente: ClienteVehiculosAutenticado, val pendientes: PendientesSesionVehiculos,
    val modelos: ViewModelStore = ViewModelStore(),
    val archivoMantenimiento: com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente? = null) : ViewModelStoreOwner {
    override val viewModelStore get() = modelos
    val registro = RemoteRegistroVehiculoRepository(cliente.retrofit.create(RegistroVehiculoApi::class.java))
    val listado = RemoteListadoVehiculosRepository(cliente.retrofit.create(ListadoVehiculosApi::class.java))
    val detalle = RemoteDetalleVehiculosRepository(cliente.retrofit.create(DetalleVehiculoApi::class.java))
    val mantenimiento = com.mycar.app.features.mantenimiento.data.MantenimientoApiAdapter.desde(cliente.retrofit)
    val historial = com.mycar.app.features.historial.data.HistorialApiAdapter.desde(cliente.retrofit)
    val resumen = com.mycar.app.features.inicio.data.RemoteResumenInicioRepository(listado,historial,mantenimiento)
    override fun toString() = "CuentaVehiculos[REDACTED]"
}

data class EstadoAcceso(val ocupada: Boolean = false, val cuenta: CuentaVehiculos? = null,
    val mensaje: String? = null)

/** Adapta la sesión de Elsa a repositorios RF08/RF09/RF17/RF18/RF50; no hace login ni guarda tokens. */
class SesionVehiculosViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = AuthGraph.repository(application)
    private val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(SesionApi::class.java)
    private val _estado = MutableStateFlow(EstadoAcceso(ocupada = true))
    val estado = _estado.asStateFlow()
    private var revision = 0L
    private var contexto: AuthenticatedContext? = null
    private var carga: Job? = null

    init {
        viewModelScope.launch {
            auth.session.collect {
                val actual = auth.authenticatedContext()
                if (actual == null) {
                    ++revision; carga?.cancel(); contexto = null; retirarCuenta()
                    _estado.value = EstadoAcceso(ocupada = it.phase == SessionPhase.CHECKING)
                } else if (actual != contexto) cargar(actual)
            }
        }
    }
    fun reintentar() { auth.authenticatedContext()?.let { cargar(it) } }
    private fun cargar(actual: AuthenticatedContext) {
        ++revision; val intento = revision
        carga?.cancel(); retirarCuenta(); contexto = actual
        _estado.value = EstadoAcceso(ocupada = true)
        carga = viewModelScope.launch {
            var construida: CuentaVehiculos? = null
            try {
                val identidad = api.identidad(auth.authorization(actual))
                val usuario = identidad.body()?.idUsuario
                if (!identidad.isSuccessful || usuario == null || usuario <= 0) {
                    identidad.errorBody()?.close()
                    if (identidad.code() == 401) auth.validateSession()
                    throw IOException("No se pudo verificar la cuenta")
                }
                val ciclo = UUID.randomUUID().toString()
                val cuenta = withContext(Dispatchers.IO) {
                    val archivo = ArchivoSolicitudRegistroPendiente(getApplication(), BuildConfig.API_BASE_URL, usuario)
                    val archivoMantenimiento = com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente(
                        getApplication(), "${BuildConfig.API_BASE_URL}|$usuario")
                    val creada = CuentaVehiculos(usuario, ciclo,
                        ClienteVehiculosAutenticado(BuildConfig.API_BASE_URL,
                            autorizacion = { runBlocking { auth.authorization(actual) } },
                            alCaducar = { viewModelScope.launch { sesionRequerida(ciclo) } }),
                        PendientesSesionVehiculos(archivo, archivo::borrarAlCerrarSesion),
                        archivoMantenimiento = archivoMantenimiento)
                    // Migrar solo con el cierre ya registrado y sin soltar el bloqueo de la limpieza.
                    DatosPrivadosSesion.registrar(ciclo, { borrar ->
                        creada.cliente.cancelar()
                        val vehiculos = runCatching { creada.pendientes.cerrar(borrar) }
                        val mantenimiento = runCatching { creada.archivoMantenimiento?.cerrar(borrar) }
                        vehiculos.getOrThrow(); mantenimiento.getOrThrow()
                    }, alRegistrar = { archivoMantenimiento.preparar() })
                    creada
                }
                construida = cuenta
                if (revision == intento && auth.authenticatedContext() == actual) {
                    _estado.value = EstadoAcceso(cuenta = cuenta)
                    construida = null
                }
            } catch (cancelada: CancellationException) { throw cancelada
            } catch (_: Exception) {
                if (revision == intento) _estado.value = EstadoAcceso(mensaje =
                    "Tu sesión está iniciada, pero no se pudo cargar tu cuenta. Comprueba el servidor y vuelve a intentar.")
            } finally {
                construida?.let { DatosPrivadosSesion.retirar(it.ciclo); it.modelos.clear() }
            }
        }
    }
    fun sesionRequerida(cicloEsperado: String) {
        if (_estado.value.cuenta?.ciclo != cicloEsperado) return
        ++revision; carga?.cancel(); contexto = null; retirarCuenta()
        _estado.value = EstadoAcceso(ocupada = true)
        viewModelScope.launch {
            auth.validateSession()
            auth.authenticatedContext()?.let { cargar(it) }
        }
    }
    private fun retirarCuenta() {
        _estado.value.cuenta?.let {
            DatosPrivadosSesion.retirar(it.ciclo)
            it.modelos.clear()
        }
    }
    override fun onCleared() {
        carga?.cancel(); retirarCuenta()
        super.onCleared()
    }
}
