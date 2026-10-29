package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.*
import kotlinx.coroutines.CancellationException
import java.util.UUID

enum class FaseRegistro { CARGANDO, FORMULARIO, SIN_CUPO, SESION_REQUERIDA, INCIERTA, CONFIRMADA, ERROR }
data class EstadoRegistroVehiculo(
    val fase: FaseRegistro = FaseRegistro.CARGANDO,
    val formulario: FormularioRegistroVehiculo = FormularioRegistroVehiculo(),
    val catalogos: CatalogosRegistroDto? = null,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val pendiente: SolicitudRegistroVehiculoDto? = null,
    val resultado: ResultadoRegistroVehiculoDto? = null,
    val permiteReenviar: Boolean = false,
    val ocupado: Boolean = false
)

/** Estado y decisiones sin Android, para probar recuperación e idempotencia en JVM. */
class CoordinadorRegistroVehiculo(
    private val repository: RegistroVehiculoRepository,
    private val pendientes: SolicitudRegistroPendienteStore,
    formularioInicial: FormularioRegistroVehiculo = FormularioRegistroVehiculo()
) {
    var estado = EstadoRegistroVehiculo(formulario = formularioInicial)
        private set

    fun cambiar(campo: String, valor: String) {
        if (estado.fase != FaseRegistro.FORMULARIO || estado.pendiente != null) return
        estado = estado.copy(formulario = estado.formulario.cambiar(campo, valor, estado.catalogos),
            errores = estado.errores - estado.formulario.camposAfectados(campo), mensaje = null)
    }

    suspend fun cargar() {
        try {
            val pendiente = pendientes.cargar()
            if (pendiente != null) {
                estado = estado.copy(pendiente = pendiente, formulario = FormularioRegistroVehiculo.desde(pendiente))
                comprobar()
                return
            }
            val cupo = repository.cupo()
            if (!cupo.puedeRegistrar) {
                estado = estado.copy(fase = FaseRegistro.SIN_CUPO, mensaje = "Tu plan Free ya tiene un vehículo registrado.")
                return
            }
            estado = estado.copy(fase = FaseRegistro.FORMULARIO,
                catalogos = repository.catalogos(), mensaje = null, errores = emptyMap())
        } catch (cancelada: CancellationException) { throw cancelada }
        catch (error: Exception) {
            estado = estado.copy(fase = if (esSesion(error)) FaseRegistro.SESION_REQUERIDA else FaseRegistro.ERROR,
                mensaje = if (esCuenta(error)) "Tu cuenta no tiene acceso a este registro." else if (esSesion(error)) "Inicia sesión para continuar." else "No se pudo cargar el registro. Inténtalo nuevamente.")
        }
    }

    suspend fun guardar() {
        if (estado.fase != FaseRegistro.FORMULARIO || estado.pendiente != null) return
        val catalogos = estado.catalogos ?: return
        val errores = ValidadorFormularioRegistro.validar(estado.formulario, catalogos)
        if (errores.isNotEmpty()) { estado = estado.copy(errores = errores); return }
        val solicitud = ValidadorFormularioRegistro.solicitud(estado.formulario, catalogos, UUID.randomUUID().toString())
        try {
            // Persistir ANTES de enviar. No se publica una petición sin conservar su UUID/contenido.
            pendientes.guardar(solicitud)
        } catch (_: Exception) {
            estado = estado.copy(fase = FaseRegistro.ERROR,
                mensaje = "No se pudo preparar el registro. No se enviaron los datos.")
            return
        }
        estado = estado.copy(pendiente = solicitud, errores = emptyMap())
        enviar(solicitud)
    }

    suspend fun comprobar() {
        val pendiente = estado.pendiente ?: return
        try {
            val consulta = repository.operacion(pendiente.idOperacion)
            if (consulta.idOperacion != pendiente.idOperacion) throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
            when (consulta.estado) {
                "CONFIRMADA" -> confirmar(consulta.resultado ?: throw FalloRegistroVehiculo("RESPUESTA_INVALIDA"), pendiente)
                "EN_PROCESO" -> incierta("El registro todavía no está confirmado. Vuelve a comprobarlo.", false)
                else -> throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
            }
        } catch (cancelada: CancellationException) { throw cancelada }
        catch (error: Exception) {
            if (esCuenta(error)) {
                estado = estado.copy(fase = FaseRegistro.ERROR, mensaje = "Tu cuenta no tiene acceso a este registro.")
            } else if (esSesion(error)) {
                estado = estado.copy(fase = FaseRegistro.SESION_REQUERIDA, mensaje = "Inicia sesión para comprobar tu registro.")
            } else {
                // 404 no demuestra rollback. Un reenvío conservará exactamente la misma clave/carga.
                val noVisible = error is FalloRegistroVehiculo && error.estadoHttp == 404 && error.codigo == "OPERACION_NO_ENCONTRADA"
                incierta(if (noVisible) "Aún no se encontró el resultado. Puedes volver a comprobar o reenviar la misma solicitud."
                    else "No se pudo comprobar el resultado. Inténtalo nuevamente.", noVisible)
            }
        }
    }

    suspend fun reenviar() {
        if (estado.fase != FaseRegistro.INCIERTA || !estado.permiteReenviar) return
        val solicitud = estado.pendiente ?: return
        enviar(solicitud)
    }

    private suspend fun enviar(solicitud: SolicitudRegistroVehiculoDto) {
        try { confirmar(repository.registrar(solicitud), solicitud) }
        catch (cancelada: CancellationException) { throw cancelada }
        catch (error: Exception) {
            if (esCuenta(error)) {
                estado = estado.copy(fase = FaseRegistro.ERROR, mensaje = "Tu cuenta no tiene acceso a este registro.")
                return
            }
            if (esSesion(error)) {
                estado = estado.copy(fase = FaseRegistro.SESION_REQUERIDA, mensaje = "Inicia sesión para comprobar tu registro.")
                return
            }
            val rechazo = error as? FalloRegistroVehiculo
            val definitivo = (rechazo?.estadoHttp == 400 && rechazo.codigo in setOf("DATOS_INVALIDOS", "SOLICITUD_INVALIDA")) ||
                (rechazo?.estadoHttp == 409 && rechazo.codigo in setOf("LIMITE_FREE", "PLAN_FREE_NO_DISPONIBLE", "PLACA_DUPLICADA", "VIN_DUPLICADO", "CONFLICTO_REGISTRO"))
            if (definitivo) {
                try { pendientes.eliminar(solicitud.idOperacion) }
                catch (_: Exception) { incierta("No se pudo recuperar el estado local. Vuelve a comprobar el registro.", false); return }
                val sinCupo = rechazo?.codigo in setOf("LIMITE_FREE", "PLAN_FREE_NO_DISPONIBLE")
                estado = estado.copy(fase = if (sinCupo) FaseRegistro.SIN_CUPO else FaseRegistro.FORMULARIO,
                    pendiente = null, permiteReenviar = false,
                    errores = rechazo?.campos.orEmpty().associateWith { "Revisa este campo." },
                    mensaje = when (rechazo?.codigo) {
                        "PLACA_DUPLICADA" -> "La placa ya está registrada."
                        "VIN_DUPLICADO" -> "El VIN ya está registrado."
                        "LIMITE_FREE" -> "Tu plan Free ya tiene un vehículo registrado."
                        "PLAN_FREE_NO_DISPONIBLE" -> "Tu cuenta no tiene un plan disponible para registrar."
                        else -> "Revisa los datos antes de volver a guardar."
                    })
            } else {
                incierta("No se pudo confirmar el registro. Comprueba su estado antes de volver a enviarlo.", false)
            }
        }
    }

    private fun confirmar(r: ResultadoRegistroVehiculoDto, solicitud: SolicitudRegistroVehiculoDto) {
        if (r.idOperacion != solicitud.idOperacion || r.idVehiculo <= 0 || r.idLecturaKm <= 0 ||
            r.placa != solicitud.placa || r.kilometrajeActual != solicitud.kilometrajeInicial || r.fechaLectura.isNullOrBlank())
            throw FalloRegistroVehiculo("RESPUESTA_INVALIDA")
        estado = estado.copy(fase = FaseRegistro.CONFIRMADA, resultado = r, mensaje = null, permiteReenviar = false)
        // Mantener la solicitud hasta que la persona navegue desde la confirmación.
    }

    fun finalizarConfirmacion(): Boolean {
        if (estado.fase != FaseRegistro.CONFIRMADA) return false
        val pendiente = estado.pendiente ?: return false
        return try { pendientes.eliminar(pendiente.idOperacion); estado = EstadoRegistroVehiculo(); true }
        catch (_: Exception) { estado = estado.copy(mensaje = "El vehículo está registrado, pero no se pudo cerrar esta confirmación. Inténtalo nuevamente."); false }
    }

    private fun incierta(mensaje: String, permiteReenviar: Boolean) {
        estado = estado.copy(fase = FaseRegistro.INCIERTA, mensaje = mensaje, permiteReenviar = permiteReenviar)
    }
    private fun esCuenta(error: Exception) = error is FalloRegistroVehiculo && error.codigo == "CUENTA_NO_AUTORIZADA"
    private fun esSesion(error: Exception) = error is FalloRegistroVehiculo &&
        (error.estadoHttp == 401 || error.codigo == "SESION_NO_VALIDA")
}
