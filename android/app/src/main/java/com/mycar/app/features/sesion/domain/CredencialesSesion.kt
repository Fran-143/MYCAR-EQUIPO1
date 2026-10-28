package com.mycar.app.features.sesion.domain

import com.mycar.app.features.sesion.data.*
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Solo memoria. Un cierre invalida también una renovación que aún esté esperando respuesta. */
class CredencialesSesion(iniciales: TokensSesionDto, private val instalacion: String,
    private val renovar: (SolicitudRenovacion) -> TokensSesionDto,
    private val ahora: () -> Instant = { Instant.now() }) {
    private val vigentes = AtomicBoolean(true)
    private val tokens = AtomicReference<TokensSesionDto?>(validar(iniciales))
    private val bloqueoRenovacion = Any()
    val idSesion: Long = iniciales.idSesion

    fun autorizacion(): String = synchronized(bloqueoRenovacion) {
        val anterior = tokens.get() ?: throw IOException("SESION_NO_VALIDA")
        if (!vigentes.get()) throw IOException("SESION_NO_VALIDA")
        if (!ahora().isBefore(Instant.parse(anterior.accessTokenExpiraUtc).minusSeconds(10))) {
            try {
                if (!ahora().isBefore(Instant.parse(anterior.sesionExpiraUtc))) throw IOException("SESION_NO_VALIDA")
                val nuevos = validar(renovar(SolicitudRenovacion(instalacion, anterior.refreshToken!!)))
                if (nuevos.idSesion != idSesion || nuevos.sesionExpiraUtc != anterior.sesionExpiraUtc)
                    throw IOException("RESPUESTA_INVALIDA")
                if (!vigentes.get() || !tokens.compareAndSet(anterior, nuevos)) throw IOException("SESION_NO_VALIDA")
            } catch (error: Exception) {
                // La rotación podría haberse confirmado sin respuesta. No reutilizar el refresh anterior.
                tokens.set(null); vigentes.set(false)
                throw IOException("SESION_NO_VALIDA", error)
            }
        }
        val actuales = tokens.get() ?: throw IOException("SESION_NO_VALIDA")
        if (!vigentes.get()) throw IOException("SESION_NO_VALIDA")
        "Bearer ${actuales.accessToken}"
    }
    /** No renueva para RF06. Solo la prueba efímera viaja al intento de cierre remoto. */
    fun retirarParaCierre(): String? {
        vigentes.set(false)
        return tokens.getAndSet(null)?.accessToken?.let { "Bearer $it" }
    }
    override fun toString() = "CredencialesSesion[REDACTED]"
    companion object {
        fun validar(t: TokensSesionDto): TokensSesionDto {
            try {
                require(t.estado == "AUTENTICADO" && t.idSesion > 0)
                require(!t.accessToken.isNullOrBlank() && t.refreshToken?.matches(Regex("[A-Za-z0-9_-]{43}")) == true)
                require(t.accessToken!!.none { it.isWhitespace() })
                require(!Instant.parse(t.accessTokenExpiraUtc).isAfter(Instant.parse(t.sesionExpiraUtc)))
            } catch (_: Exception) { throw IOException("RESPUESTA_INVALIDA") }
            return t
        }
    }
}
