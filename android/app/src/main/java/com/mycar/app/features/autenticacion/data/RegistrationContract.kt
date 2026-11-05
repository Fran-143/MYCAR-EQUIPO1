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
    val codigo: String? = null,
    val mensaje: String? = null,
    val erroresPorCampo: Map<String, String>? = null
)

class VerificationOriginResponse(val idOrigen: String? = null, val secretoOrigen: String? = null)
class VerificationEmitRequest(
    val idOrigen: String,
    val secretoOrigen: String,
    val idOperacion: String? = null,
    val secretoOperacion: String? = null,
    val idSolicitud: String,
    val revision: Long = 0,
    val formulario: RegistrationRequest
) {
    override fun toString() = "VerificationEmitRequest[REDACTED]"
}
class VerificationEmitResponse(
    val resultado: String? = null,
    val idOperacion: String? = null,
    val secretoOperacion: String? = null,
    val idEmision: String? = null,
    val revision: Long = 0,
    val bloqueoHastaUtc: String? = null,
    val codigoDesarrollo: String? = null
)
class VerificationCompleteRequest(
    val idOperacion: String,
    val secretoOperacion: String,
    val idSolicitud: String,
    val revision: Long,
    val idEmision: String,
    val codigo: String,
    val formulario: RegistrationRequest
) {
    override fun toString() = "VerificationCompleteRequest[REDACTED]"
}

data class VerificationSession(
    val idOrigen: String,
    val secretoOrigen: String,
    val idOperacion: String,
    val secretoOperacion: String,
    val idEmision: String,
    val revision: Long,
    val codigoDesarrollo: String? = null
) {
    override fun toString() = "VerificationSession[REDACTED]"
}

interface RegistrationService {
    suspend fun register(request: RegistrationRequest)
    suspend fun startEmailVerification(request: RegistrationRequest): VerificationSession
    suspend fun resendEmailCode(session: VerificationSession, request: RegistrationRequest): VerificationSession
    suspend fun completeEmailVerification(session: VerificationSession, code: String, request: RegistrationRequest)
}

enum class RegistrationFailureKind {
    VALIDATION, EMAIL_UNAVAILABLE, UNAVAILABLE, NETWORK, UNCONFIRMED,
    PRIVACY_PENDING, VERSION_MISMATCH, IN_PROGRESS, RATE_LIMITED
}
class RegistrationFailure(
    val kind: RegistrationFailureKind,
    val fields: Map<String, String> = emptyMap(),
    detail: String? = null
) : Exception(detail ?: when (kind) {
    RegistrationFailureKind.VALIDATION -> "Revisa los datos del registro."
    RegistrationFailureKind.EMAIL_UNAVAILABLE -> "Correo no disponible."
    RegistrationFailureKind.UNAVAILABLE -> "No se pudo completar el registro. Inténtalo más tarde."
    RegistrationFailureKind.NETWORK -> "No se pudo confirmar el registro. Comprueba tu conexión antes de volver a intentarlo."
    RegistrationFailureKind.UNCONFIRMED -> "No se pudo confirmar el registro. No se ha iniciado sesión."
    RegistrationFailureKind.PRIVACY_PENDING -> "Registro no disponible: falta configurar la Política de Privacidad aprobada."
    RegistrationFailureKind.VERSION_MISMATCH -> "La versión de privacidad debe actualizarse antes de continuar."
    RegistrationFailureKind.IN_PROGRESS -> "Ya hay un registro en curso."
    RegistrationFailureKind.RATE_LIMITED ->
        "Ya enviamos un código a este correo. Espera 10 minutos antes de pedir otro."
})
