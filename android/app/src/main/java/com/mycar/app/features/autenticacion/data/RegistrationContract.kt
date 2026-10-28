package com.mycar.app.features.autenticacion.data

class PrivacyConsent(val aceptado: Boolean, val version: String)
class RegistrationRequest(
    val nombre: String, val apellido: String, val correo: String, val fechaNacimiento: String,
    val contrasena: String, val confirmacionContrasena: String,
    val consentimientoPrivacidad: PrivacyConsent
) {
    override fun toString() = "RegistrationRequest[REDACTED]"
}
class RegistrationResponse(val resultado: String? = null, val destino: String? = null)
internal class RegistrationError(
    val codigo: String? = null, val erroresPorCampo: Map<String, String>? = null
)
fun interface RegistrationService {
    suspend fun register(request: RegistrationRequest)
}

enum class RegistrationFailureKind {
    VALIDATION, EMAIL_UNAVAILABLE, UNAVAILABLE, NETWORK, UNCONFIRMED,
    PRIVACY_PENDING, VERSION_MISMATCH, IN_PROGRESS
}
class RegistrationFailure(
    val kind: RegistrationFailureKind,
    val fields: Map<String, String> = emptyMap()
) : Exception(when (kind) {
    RegistrationFailureKind.VALIDATION -> "Revisa los datos del registro."
    RegistrationFailureKind.EMAIL_UNAVAILABLE -> "Correo no disponible."
    RegistrationFailureKind.UNAVAILABLE -> "No se pudo completar el registro. Inténtalo más tarde."
    RegistrationFailureKind.NETWORK -> "No se pudo confirmar el registro. Comprueba tu conexión antes de volver a intentarlo."
    RegistrationFailureKind.UNCONFIRMED -> "No se pudo confirmar el registro. No se ha iniciado sesión."
    RegistrationFailureKind.PRIVACY_PENDING -> "Registro no disponible: falta configurar la Política de Privacidad aprobada."
    RegistrationFailureKind.VERSION_MISMATCH -> "La versión de privacidad debe actualizarse antes de continuar."
    RegistrationFailureKind.IN_PROGRESS -> "Ya hay un registro en curso."
})
