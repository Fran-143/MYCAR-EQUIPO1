package com.mycar.app.features.autenticacion

import com.mycar.app.features.autenticacion.data.*
import retrofit2.Response

internal fun fakeRegistrationService(
    onStart: suspend (RegistrationRequest) -> VerificationSession = {
        VerificationSession("o", "os", "op", "ops", "em", 1, "123456")
    },
    onComplete: suspend (VerificationSession, String, RegistrationRequest) -> Unit = { _, _, _ -> },
    onResend: suspend (VerificationSession, RegistrationRequest) -> VerificationSession = { s, _ -> s },
    onRegister: suspend (RegistrationRequest) -> Unit = {
        throw RegistrationFailure(RegistrationFailureKind.UNAVAILABLE)
    }
) = object : RegistrationService {
    override suspend fun register(request: RegistrationRequest) = onRegister(request)
    override suspend fun startEmailVerification(request: RegistrationRequest) = onStart(request)
    override suspend fun resendEmailCode(session: VerificationSession, request: RegistrationRequest) =
        onResend(session, request)
    override suspend fun completeEmailVerification(
        session: VerificationSession, code: String, request: RegistrationRequest
    ) = onComplete(session, code, request)
}

internal fun AuthApi.unsupportedVerification(): Nothing = error("Not part of this regression")

internal suspend fun AuthApi.stubVerificationDefaults() {
    // marker for documentation only
}
