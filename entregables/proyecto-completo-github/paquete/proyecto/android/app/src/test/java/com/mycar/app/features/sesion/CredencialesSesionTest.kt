package com.mycar.app.features.sesion

import com.mycar.app.features.sesion.data.*
import com.mycar.app.features.sesion.domain.CredencialesSesion
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.util.concurrent.*

class CredencialesSesionTest {
    private val ahora=Instant.parse("2026-10-27T12:00:00Z")
    private fun tokens(access: String="token-inicial", exp: String="2026-10-27T12:05:00Z", id: Long=3) =
        TokensSesionDto("AUTENTICADO",id,access,exp,"A".repeat(43),"2026-10-28T12:00:00Z")
    @Test fun usaTokenVigenteSinRenovar() {
        val c=CredencialesSesion(tokens(),"instalacion",{ throw AssertionError("No debe renovar") },{ ahora })
        assertEquals("Bearer token-inicial",c.autorizacion())
        assertFalse(c.toString().contains("token-inicial"))
    }
    @Test fun renuevaUnaVezYConservaSesion() {
        var veces=0
        val c=CredencialesSesion(tokens(exp="2026-10-27T12:00:05Z"),"instalacion",{
            veces++; assertEquals("instalacion",it.idInstalacion); tokens("token-nuevo")
        },{ ahora })
        assertEquals("Bearer token-nuevo",c.autorizacion()); assertEquals("Bearer token-nuevo",c.autorizacion()); assertEquals(1,veces)
    }
    @Test fun cierreNoRenuevaYNoPermiteUsarCredencialDespues() {
        val c=CredencialesSesion(tokens(exp="2026-10-27T11:59:00Z"),"instalacion",{ throw AssertionError("RF06 no renueva") },{ ahora })
        assertEquals("Bearer token-inicial",c.retirarParaCierre()); assertNull(c.retirarParaCierre())
        assertThrows(IOException::class.java) { c.autorizacion() }
    }
    @Test fun respuestaTardiaNoResucitaSesionCerrada() {
        val entro=CountDownLatch(1); val continuar=CountDownLatch(1)
        val c=CredencialesSesion(tokens(exp="2026-10-27T12:00:05Z"),"instalacion",{
            entro.countDown(); assertTrue(continuar.await(3,TimeUnit.SECONDS)); tokens("token-tardio")
        },{ ahora })
        val pool=Executors.newSingleThreadExecutor()
        try {
            val futuro=pool.submit<Boolean> { try { c.autorizacion(); false } catch (_: IOException) { true } }
            assertTrue(entro.await(3,TimeUnit.SECONDS)); c.retirarParaCierre(); continuar.countDown()
            assertTrue(futuro.get(3,TimeUnit.SECONDS)); assertThrows(IOException::class.java) { c.autorizacion() }
        } finally { continuar.countDown(); pool.shutdownNow() }
    }
    @Test fun perdidaDeRespuestaDeRefreshNoReutilizaCredencialConsumida() {
        var veces=0
        val c=CredencialesSesion(tokens(exp="2026-10-27T12:00:05Z"),"instalacion",{ veces++; throw IOException("Sin respuesta") },{ ahora })
        repeat(2) { assertThrows(IOException::class.java) { c.autorizacion() } }; assertEquals(1,veces)
    }
    @Test fun respuestaDeOtraSesionNoSeAcepta() {
        val c=CredencialesSesion(tokens(exp="2026-10-27T12:00:05Z"),"instalacion",{ tokens(id=99) },{ ahora })
        assertThrows(IOException::class.java) { c.autorizacion() }
    }
    @Test fun noExponeSecretosEnToString() {
        assertFalse(tokens().toString().contains("token-inicial"))
        assertFalse(SolicitudLogin("correo","clave-secreta","instalacion").toString().contains("clave-secreta"))
        assertFalse(SolicitudRenovacion("instalacion","refresh-secreto").toString().contains("refresh-secreto"))
    }
}
