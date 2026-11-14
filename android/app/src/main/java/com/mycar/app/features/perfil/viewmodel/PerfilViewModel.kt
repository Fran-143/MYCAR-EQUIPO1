package com.mycar.app.features.perfil.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.perfil.domain.DatosPerfil
import com.mycar.app.features.perfil.domain.ErrorPerfil
import com.mycar.app.features.perfil.domain.FormularioEdicionPerfil
import com.mycar.app.features.perfil.domain.PerfilRepository
import com.mycar.app.features.perfil.domain.PerfilValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PantallaPerfil {
    HUB, DATOS, EDITAR, CAMBIAR_CONTRASENA, CONTRASENA_OK, NOTIFICACIONES_EXTERNAS
}

data class EstadoPerfil(
    val pantalla: PantallaPerfil = PantallaPerfil.HUB,
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val perfil: DatosPerfil? = null,
    val formulario: FormularioEdicionPerfil = FormularioEdicionPerfil(),
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val exitoDatos: Boolean = false,
    val confirmarCierre: Boolean = false,
    val contrasenaActual: String = "",
    val nuevaContrasena: String = "",
    val confirmacionContrasena: String = "",
    /** RF05: solo tras verificar la actual se liberan nueva/confirmación. */
    val contrasenaActualVerificada: Boolean = false,
    val sesionInvalida: Boolean = false
)

class PerfilViewModel(
    private val repository: PerfilRepository,
    private val onNombreActualizado: (String) -> Unit = {}
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPerfil(cargando = true))
    val estado: StateFlow<EstadoPerfil> = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(cargando = true, mensaje = null, sesionInvalida = false)
            try {
                val datos = repository.consultar()
                onNombreActualizado(datos.nombre)
                _estado.value = _estado.value.copy(cargando = false, perfil = datos, mensaje = null)
            } catch (e: ErrorPerfil.Sesion) {
                _estado.value = _estado.value.copy(cargando = false, sesionInvalida = true, mensaje = e.message)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(cargando = false,
                    mensaje = (e as? ErrorPerfil)?.message ?: "No se pudo cargar tu perfil.")
            }
        }
    }

    fun abrirDatos() {
        _estado.value = _estado.value.copy(pantalla = PantallaPerfil.DATOS, exitoDatos = false, mensaje = null)
    }

    fun abrirEditar() {
        val p = _estado.value.perfil ?: return
        _estado.value = _estado.value.copy(
            pantalla = PantallaPerfil.EDITAR,
            formulario = FormularioEdicionPerfil(
                nombre = p.nombre,
                apellido = p.apellido,
                fechaNacimiento = PerfilValidator.isoToDisplay(p.fechaNacimientoIso),
                correo = p.correo
            ),
            errores = emptyMap(),
            mensaje = null,
            exitoDatos = false
        )
    }

    fun editarCampo(transform: (FormularioEdicionPerfil) -> FormularioEdicionPerfil) {
        _estado.value = _estado.value.copy(formulario = transform(_estado.value.formulario), errores = emptyMap())
    }

    fun cancelarEdicion() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaPerfil.DATOS,
            errores = emptyMap(),
            mensaje = null,
            formulario = FormularioEdicionPerfil()
        )
    }

    fun guardar() {
        if (_estado.value.guardando) return
        val form = _estado.value.formulario
        val locales = PerfilValidator.validateEdition(form)
        if (locales.isNotEmpty()) {
            _estado.value = _estado.value.copy(errores = locales)
            return
        }
        val iso = PerfilValidator.displayToIso(form.fechaNacimiento) ?: run {
            _estado.value = _estado.value.copy(errores = mapOf("fechaNacimiento" to
                "La edad debe estar entre 18 y 85 años cumplidos."))
            return
        }
        viewModelScope.launch {
            _estado.value = _estado.value.copy(guardando = true, errores = emptyMap(), mensaje = null)
            try {
                val nombre = com.mycar.app.features.autenticacion.domain.RegistrationValidator.normalizedName(form.nombre)
                val apellido = com.mycar.app.features.autenticacion.domain.RegistrationValidator.normalizedName(form.apellido)
                val actualizado = repository.actualizar(nombre, apellido, iso)
                onNombreActualizado(actualizado.nombre)
                _estado.value = _estado.value.copy(
                    guardando = false,
                    perfil = actualizado,
                    pantalla = PantallaPerfil.DATOS,
                    exitoDatos = true,
                    formulario = FormularioEdicionPerfil()
                )
            } catch (e: ErrorPerfil.Validacion) {
                _estado.value = _estado.value.copy(guardando = false, errores = e.campos, mensaje = e.message)
            } catch (e: ErrorPerfil.Sesion) {
                _estado.value = _estado.value.copy(guardando = false, sesionInvalida = true, mensaje = e.message)
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(guardando = false,
                    mensaje = (e as? ErrorPerfil)?.message ?: "No se pudieron guardar los cambios.")
            }
        }
    }

    fun abrirCambiarContrasena() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaPerfil.CAMBIAR_CONTRASENA,
            contrasenaActual = "", nuevaContrasena = "", confirmacionContrasena = "",
            contrasenaActualVerificada = false,
            errores = emptyMap(), mensaje = null
        )
    }

    fun abrirNotificacionesExternas() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaPerfil.NOTIFICACIONES_EXTERNAS,
            mensaje = null
        )
    }

    fun editarContrasena(actual: String? = null, nueva: String? = null, confirmacion: String? = null) {
        val max = com.mycar.app.features.autenticacion.domain.RegistrationValidator.MAX_CONTRASENA
        val limit = com.mycar.app.features.autenticacion.domain.RegistrationValidator::limitCodePoints
        val prev = _estado.value
        val nextActual = actual?.let { limit(it, max) } ?: prev.contrasenaActual
        val actualCambio = actual != null && nextActual != prev.contrasenaActual
        _estado.value = prev.copy(
            contrasenaActual = nextActual,
            nuevaContrasena = when {
                actualCambio -> ""
                nueva != null -> limit(nueva, max)
                else -> prev.nuevaContrasena
            },
            confirmacionContrasena = when {
                actualCambio -> ""
                confirmacion != null -> limit(confirmacion, max)
                else -> prev.confirmacionContrasena
            },
            contrasenaActualVerificada = if (actualCambio) false else prev.contrasenaActualVerificada,
            errores = emptyMap(),
            mensaje = null
        )
    }

    fun verificarContrasenaActual() {
        if (_estado.value.guardando) return
        val actual = _estado.value.contrasenaActual
        if (actual.isEmpty()) {
            _estado.value = _estado.value.copy(errores = mapOf("contrasenaActual" to "Ingresa tu contraseña actual."))
            return
        }
        viewModelScope.launch {
            _estado.value = _estado.value.copy(guardando = true, errores = emptyMap(), mensaje = null)
            try {
                repository.verificarContrasenaActual(actual)
                _estado.value = _estado.value.copy(
                    guardando = false,
                    contrasenaActualVerificada = true,
                    nuevaContrasena = "",
                    confirmacionContrasena = "",
                    mensaje = null
                )
            } catch (err: ErrorPerfil.Validacion) {
                _estado.value = _estado.value.copy(
                    guardando = false,
                    contrasenaActualVerificada = false,
                    errores = err.campos,
                    mensaje = err.campos.values.firstOrNull() ?: err.message
                )
            } catch (err: ErrorPerfil.Sesion) {
                _estado.value = _estado.value.copy(guardando = false, sesionInvalida = true, mensaje = err.message)
            } catch (err: Exception) {
                _estado.value = _estado.value.copy(guardando = false,
                    mensaje = (err as? ErrorPerfil)?.message ?: "No se pudo verificar la contraseña.")
            }
        }
    }

    fun cancelarCambioContrasena() {
        _estado.value = _estado.value.copy(
            pantalla = PantallaPerfil.HUB,
            contrasenaActual = "", nuevaContrasena = "", confirmacionContrasena = "",
            contrasenaActualVerificada = false,
            errores = emptyMap(), mensaje = null
        )
    }

    fun confirmarCambioContrasena() {
        if (_estado.value.guardando) return
        val e = _estado.value
        if (!e.contrasenaActualVerificada) {
            _estado.value = e.copy(errores = mapOf("contrasenaActual" to "Primero confirma tu contraseña actual."))
            return
        }
        val locales = PerfilValidator.validatePasswordChange(e.contrasenaActual, e.nuevaContrasena, e.confirmacionContrasena)
        if (locales.isNotEmpty()) {
            _estado.value = e.copy(errores = locales)
            return
        }
        viewModelScope.launch {
            _estado.value = _estado.value.copy(guardando = true, errores = emptyMap(), mensaje = null)
            try {
                repository.cambiarContrasena(e.contrasenaActual, e.nuevaContrasena, e.confirmacionContrasena)
                _estado.value = _estado.value.copy(
                    guardando = false,
                    pantalla = PantallaPerfil.CONTRASENA_OK,
                    contrasenaActual = "", nuevaContrasena = "", confirmacionContrasena = "",
                    contrasenaActualVerificada = false
                )
            } catch (err: ErrorPerfil.Validacion) {
                _estado.value = _estado.value.copy(guardando = false, errores = err.campos,
                    mensaje = err.campos.values.firstOrNull() ?: err.message)
            } catch (err: ErrorPerfil.Sesion) {
                _estado.value = _estado.value.copy(guardando = false, sesionInvalida = true, mensaje = err.message)
            } catch (err: Exception) {
                _estado.value = _estado.value.copy(guardando = false,
                    mensaje = (err as? ErrorPerfil)?.message ?: "No se pudo cambiar la contraseña.")
            }
        }
    }

    fun volverAlPerfil() {
        _estado.value = _estado.value.copy(pantalla = PantallaPerfil.HUB, exitoDatos = false, mensaje = null)
    }

    fun volverAtras() {
        when (_estado.value.pantalla) {
            PantallaPerfil.HUB -> Unit
            PantallaPerfil.DATOS, PantallaPerfil.CAMBIAR_CONTRASENA, PantallaPerfil.CONTRASENA_OK,
            PantallaPerfil.NOTIFICACIONES_EXTERNAS ->
                _estado.value = _estado.value.copy(pantalla = PantallaPerfil.HUB, exitoDatos = false)
            PantallaPerfil.EDITAR -> cancelarEdicion()
        }
    }

    fun pedirCerrarSesion() { _estado.value = _estado.value.copy(confirmarCierre = true) }
    fun cancelarCierre() { _estado.value = _estado.value.copy(confirmarCierre = false) }
}
