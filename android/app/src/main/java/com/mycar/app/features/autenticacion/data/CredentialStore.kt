package com.mycar.app.features.autenticacion.data

import java.time.Instant
import java.util.UUID

class StoredTokens(
    val idSesion: Long,
    val accessToken: String,
    val accessTokenExpiraUtc: String,
    val refreshToken: String,
    val sesionExpiraUtc: String
) {
    fun validate(): StoredTokens {
        require(idSesion > 0 && accessToken.isNotBlank() && refreshToken.isNotBlank())
        require(!Instant.parse(accessTokenExpiraUtc).isAfter(Instant.parse(sesionExpiraUtc)))
        return this
    }
}
class CredentialVault(val installation: String, val tokens: StoredTokens?) {
    fun validate(): CredentialVault {
        UUID.fromString(installation)
        tokens?.validate()
        return this
    }
}
interface CredentialStore {
    // Se invoca únicamente en Dispatchers.IO; debe guardar UUID y tokens atómicamente.
    fun load(): CredentialVault?
    fun save(vault: CredentialVault)
    // Sustituye el sobre cifrado sin tokens, conservando el UUID de instalación.
    fun clearSession(installation: String) { save(CredentialVault(installation, null)) }
}
