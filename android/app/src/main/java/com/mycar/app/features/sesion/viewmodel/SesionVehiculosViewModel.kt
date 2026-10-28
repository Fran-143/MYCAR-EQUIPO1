package com.mycar.app.features.sesion.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.mycar.app.BuildConfig
import com.mycar.app.features.sesion.data.*
import com.mycar.app.features.sesion.domain.CredencialesSesion
import com.mycar.app.features.vehiculos.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID

class CuentaVehiculos(val usuario: Long, val ciclo: String, val credenciales: CredencialesSesion,
    val cliente: ClienteVehiculosAutenticado, val pendientes: PendientesSesionVehiculos,
    val modelos: ViewModelStore = ViewModelStore(),
    val archivoMantenimiento: com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente? = null) : ViewModelStoreOwner {
    override val viewModelStore get() = modelos
    val registro = RemoteRegistroVehiculoRepository(cliente.retrofit.create(RegistroVehiculoApi::class.java))
    val listado = RemoteListadoVehiculosRepository(cliente.retrofit.create(ListadoVehiculosApi::class.java))
    val mantenimiento = com.mycar.app.features.mantenimiento.data.MantenimientoApiAdapter.desde(cliente.retrofit)
    val historial = com.mycar.app.features.historial.data.HistorialApiAdapter.desde(cliente.retrofit)
    val resumen = com.mycar.app.features.inicio.data.RemoteResumenInicioRepository(listado,historial,mantenimiento)
    override fun toString() = "CuentaVehiculos[REDACTED]"
}

data class EstadoAcceso(val ocupada: Boolean = false, val cuenta: CuentaVehiculos? = null,
    val mensaje: String? = null)

/** Host de integración RF02–vehículos. No sustituye la entrega visual definitiva de Elsa. */
class SesionVehiculosViewModel(application: Application) : AndroidViewModel(application) {
    private val api = ClientesSesion.publico(BuildConfig.API_BASE_URL).create(SesionApi::class.java)
    private val _estado = MutableStateFlow(EstadoAcceso())
    val estado = _estado.asStateFlow()
    private var revision = 0L
    private var cuentaConLimpiezaPendiente: CuentaVehiculos? = null

    fun entrar(correo: String, contrasena: String) {
        if (_estado.value.ocupada || _estado.value.cuenta != null) return
        if (correo.isBlank() || contrasena.isEmpty()) { _estado.value = EstadoAcceso(mensaje="Completa correo y contraseña."); return }
        if (cuentaConLimpiezaPendiente != null) {
            _estado.value = EstadoAcceso(mensaje="Completa la limpieza local antes de entrar en otra cuenta."); return
        }
        val intento = ++revision
        _estado.value = EstadoAcceso(ocupada=true)
        viewModelScope.launch {
            var credenciales: CredencialesSesion? = null
            try {
                val instalacion = withContext(Dispatchers.IO) { InstalacionMyCar.obtener(getApplication()) }
                val r = api.login(SolicitudLogin(correo.trim(), contrasena, instalacion))
                if (!r.isSuccessful) {
                    val mensaje = when(r.code()) {
                        401 -> "Correo o contraseña incorrectos, o acceso temporalmente restringido."
                        403 -> "La cuenta no está habilitada para iniciar sesión."
                        503 -> "La autenticación no está disponible. Revisa la configuración del servidor."
                        else -> "No se pudo iniciar sesión. Inténtalo nuevamente."
                    }
                    if (revision == intento) _estado.value = EstadoAcceso(mensaje=mensaje)
                    return@launch
                }
                val t = CredencialesSesion.validar(r.body() ?: throw IOException("RESPUESTA_INVALIDA"))
                val prueba = "Bearer ${t.accessToken}"
                val identidad = api.identidad(prueba)
                val usuario = identidad.body()?.idUsuario
                if (!identidad.isSuccessful || usuario == null || usuario <= 0)
                    throw IOException("No se pudo verificar la cuenta para vehículos")
                credenciales = CredencialesSesion(t, instalacion, renovar = { solicitud ->
                    val respuesta = api.renovar(solicitud).execute()
                    respuesta.body()?.takeIf { respuesta.isSuccessful } ?: throw IOException("SESION_NO_VALIDA")
                })
                val archivo = withContext(Dispatchers.IO) {
                    ArchivoSolicitudRegistroPendiente(getApplication(), BuildConfig.API_BASE_URL, usuario)
                }
                val archivoMantenimiento = withContext(Dispatchers.IO) {
                    com.mycar.app.features.mantenimiento.data.ArchivoRegistroPendiente(getApplication(), "${BuildConfig.API_BASE_URL}|$usuario")
                }
                val ciclo = UUID.randomUUID().toString()
                val cuenta = CuentaVehiculos(usuario, ciclo, credenciales,
                    ClienteVehiculosAutenticado(BuildConfig.API_BASE_URL, credenciales,
                        alCaducar={ viewModelScope.launch { sesionRequerida(ciclo) } }),
                    PendientesSesionVehiculos(archivo, archivo::borrarAlCerrarSesion), archivoMantenimiento=archivoMantenimiento)
                if (revision == intento) _estado.value = EstadoAcceso(cuenta=cuenta)
                else { cuenta.credenciales.retirarParaCierre(); cuenta.cliente.cancelar(); cuenta.pendientes.cerrar(false); cuenta.archivoMantenimiento?.cerrar(false); cuenta.modelos.clear() }
            } catch (e: CancellationException) { credenciales?.retirarParaCierre(); throw e }
            catch (_: Exception) {
                credenciales?.retirarParaCierre()
                if (revision == intento) _estado.value = EstadoAcceso(mensaje="No se pudo conectar o verificar la cuenta. Comprueba el servidor y vuelve a entrar.")
            }
        }
    }

    fun sesionRequerida(cicloEsperado: String) {
        val cuenta = _estado.value.cuenta?.takeIf { it.ciclo == cicloEsperado } ?: return
        ++revision
        cuenta.credenciales.retirarParaCierre(); cuenta.cliente.cancelar()
        cuenta.archivoMantenimiento?.cerrar(false)
        cuenta.pendientes.cerrar(false) // Recuperable exclusivamente después de autenticar la misma cuenta.
        cuenta.modelos.clear()
        _estado.value = EstadoAcceso(mensaje="Inicia sesión nuevamente. Tu solicitud pendiente se conserva para esta cuenta.")
    }

    /** Confirmación ya recibida: preparar prueba, limpiar, Bienvenida y luego intento remoto sin renovación. */
    fun cerrarConfirmado() {
        val cuenta = _estado.value.cuenta ?: return
        val prueba = cuenta.credenciales.retirarParaCierre()
        val cierre = ++revision
        cuenta.cliente.cancelar(); cuenta.modelos.clear()
        cuentaConLimpiezaPendiente = cuenta
        val limpia = limpiarArchivos(cuenta)
        if (limpia) cuentaConLimpiezaPendiente = null
        _estado.value = EstadoAcceso(mensaje=if (limpia) "Sesión cerrada en este dispositivo. Comprobando el cierre remoto…"
            else "Acceso cerrado. No se pudo completar la limpieza local; pulsa Reintentar limpieza.")
        // El token existe solo en esta llamada efímera, nunca en una cola ni en disco.
        viewModelScope.launch {
            val confirmada = prueba != null && runCatching { api.cerrar(prueba).code() == 204 }.getOrDefault(false)
            if (revision == cierre && cuentaConLimpiezaPendiente == null)
                _estado.value = EstadoAcceso(mensaje=if (confirmada) "Sesión cerrada en este dispositivo y en el servidor."
                    else "Sesión cerrada en este dispositivo. No se pudo confirmar el cierre en el servidor.")
        }
    }
    fun reintentarLimpieza() {
        val cuenta = cuentaConLimpiezaPendiente ?: return
        if (limpiarArchivos(cuenta)) {
            cuentaConLimpiezaPendiente = null
            _estado.value = EstadoAcceso(mensaje="Datos locales limpiados. El cierre remoto no está confirmado.")
        }
    }
    private fun limpiarArchivos(cuenta: CuentaVehiculos): Boolean {
        val vehiculos=runCatching { cuenta.pendientes.cerrar(true) }.isSuccess
        val mantenimiento=runCatching { cuenta.archivoMantenimiento?.cerrar(true) }.isSuccess
        return vehiculos && mantenimiento
    }
    override fun onCleared() {
        _estado.value.cuenta?.let { it.credenciales.retirarParaCierre(); it.cliente.cancelar(); it.modelos.clear(); it.pendientes.cerrar(false); it.archivoMantenimiento?.cerrar(false) }
        super.onCleared()
    }
}
