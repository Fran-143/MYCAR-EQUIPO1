package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

class CoordinadorRegistroVehiculoTest {
    @Test fun consultaCupoAntesDeAbrirFormulario() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val s = PendientesPrueba(); val c = CoordinadorRegistroVehiculo(r, s)
        c.cargar(); assertEquals(FaseRegistro.FORMULARIO, c.estado.fase)
        assertEquals(1, r.consultasCupo); assertEquals(1, r.consultasCatalogos); assertTrue(r.envios.isEmpty())
    }
    @Test fun cupoLlenoNoAbreFormularioNiCargaCatalogos() = runBlocking<Unit> {
        val r = RepositoryPrueba().apply { cupoValor = CupoRegistroDto("FREE", 1, 1, false) }
        val c = CoordinadorRegistroVehiculo(r, PendientesPrueba()); c.cargar()
        assertEquals(FaseRegistro.SIN_CUPO, c.estado.fase); assertEquals(0, r.consultasCatalogos); assertTrue(r.envios.isEmpty())
    }
    @Test fun sinSesionNoSeInventaAcceso() = runBlocking<Unit> {
        val r = RepositoryPrueba().apply { errorCupo = FalloRegistroVehiculo("SESION_NO_VALIDA", 401) }
        val c = CoordinadorRegistroVehiculo(r, PendientesPrueba()); c.cargar()
        assertEquals(FaseRegistro.SESION_REQUERIDA, c.estado.fase); assertTrue(r.envios.isEmpty())
    }
    @Test fun datosInvalidosNoPersistenNiEnvian() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val s = PendientesPrueba(); val c = CoordinadorRegistroVehiculo(r, s)
        c.cargar(); c.guardar(); assertTrue(c.estado.errores.containsKey("marca")); assertNull(s.actual); assertTrue(r.envios.isEmpty())
    }
    @Test fun conservaSolicitudAntesDeEnviarYHastaNavegar() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val s = PendientesPrueba()
        r.alEnviar = { assertEquals(it, s.actual); DatosPruebaRf08.resultado(it) }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario)
        c.cargar(); c.guardar(); assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase)
        UUID.fromString(s.actual!!.idOperacion)
        c.guardar(); assertEquals(1, r.envios.size)
        assertTrue(c.finalizarConfirmacion()); assertNull(s.actual); assertEquals(FaseRegistro.CARGANDO, c.estado.fase)
    }
    @Test fun dobleToqueMientrasSeEnviaNoGeneraOtraSolicitud() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val entrada = CompletableDeferred<Unit>(); val salida = CompletableDeferred<Unit>()
        r.alEnviar = { entrada.complete(Unit); salida.await(); DatosPruebaRf08.resultado(it) }
        val c = CoordinadorRegistroVehiculo(r, PendientesPrueba(), DatosPruebaRf08.formulario); c.cargar()
        val envio = launch { c.guardar() }; entrada.await(); c.guardar(); salida.complete(Unit); envio.join()
        assertEquals(1, r.envios.size); assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase)
    }
    @Test fun respuestaPerdidaSeRecuperaSinOtroPost() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val s = PendientesPrueba()
        r.alEnviar = { throw IOException("Respuesta perdida") }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.INCIERTA, c.estado.fase)
        r.alConsultar = { EstadoOperacionRegistroDto(it, "CONFIRMADA", DatosPruebaRf08.resultado(s.actual!!)) }
        c.comprobar(); assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase); assertEquals(1, r.envios.size)
    }
    @Test fun tras404ReenviaExactamenteLaMismaClaveYContenido() = runBlocking<Unit> {
        val r = RepositoryPrueba(); val s = PendientesPrueba()
        r.alEnviar = { throw IOException("Timeout") }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        val original = s.actual!!; c.comprobar(); assertTrue(c.estado.permiteReenviar); assertEquals(original, s.actual)
        c.cambiar("color", "Rojo"); assertEquals(original.color, c.estado.formulario.color)
        r.alEnviar = { DatosPruebaRf08.resultado(it) }; c.reenviar()
        assertEquals(listOf(original, original), r.envios); assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase)
    }
    @Test fun recuperacionTienePrioridadSobreCupoAunqueYaEsteLleno() = runBlocking<Unit> {
        val solicitud = DatosPruebaRf08.solicitud(); val s = PendientesPrueba(solicitud)
        val r = RepositoryPrueba().apply { errorCupo = IllegalStateException("No consultar cupo"); alConsultar = { EstadoOperacionRegistroDto(it, "CONFIRMADA", DatosPruebaRf08.resultado(solicitud)) } }
        val c = CoordinadorRegistroVehiculo(r, s); c.cargar()
        assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase); assertEquals(0, r.consultasCupo); assertTrue(r.envios.isEmpty())
    }
    @Test fun nuevaInstanciaRecuperaOperacionPersistida() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw IOException("Timeout") } }
        val primera = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); primera.cargar(); primera.guardar()
        val solicitud = s.actual!!
        r.alConsultar = { EstadoOperacionRegistroDto(it, "CONFIRMADA", DatosPruebaRf08.resultado(solicitud)) }
        val segunda = CoordinadorRegistroVehiculo(r, s); segunda.cargar()
        assertEquals(solicitud.idOperacion, segunda.estado.resultado!!.idOperacion); assertEquals(1, r.envios.size)
    }
    @Test fun sesionVencidaNoDescartaSolicitudPendiente() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw FalloRegistroVehiculo("SESION_NO_VALIDA", 401) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.SESION_REQUERIDA, c.estado.fase); assertNotNull(s.actual)
    }
    @Test fun falloTecnicoConservaSolicitudYNoReenviaAutomaticamente() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw FalloRegistroVehiculo("ERROR_INTERNO", 500) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar(); c.reenviar()
        assertNotNull(s.actual); assertEquals(1, r.envios.size); assertFalse(c.estado.permiteReenviar)
    }
    @Test fun rechazoDeCamposPermiteCorregirConOperacionNueva() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw FalloRegistroVehiculo("DATOS_INVALIDOS", 400, setOf("marca")) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        val anterior = r.envios.first().idOperacion
        assertNull(s.actual); assertEquals(FaseRegistro.FORMULARIO, c.estado.fase); assertTrue(c.estado.errores.containsKey("marca"))
        c.cambiar("marca", "Otra marca"); r.alEnviar = { DatosPruebaRf08.resultado(it) }; c.guardar()
        assertNotEquals(anterior, r.envios.last().idOperacion)
    }
    @Test fun cupoAgotadoDuranteElEnvioCierraFormularioSinAltaFingida() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw FalloRegistroVehiculo("LIMITE_FREE", 409) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.SIN_CUPO, c.estado.fase); assertNull(s.actual); assertNull(c.estado.resultado)
    }
    @Test fun almacenamientoFallidoImpideElPost() = runBlocking<Unit> {
        val s = PendientesPrueba().apply { fallarGuardar = true }; val r = RepositoryPrueba()
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.ERROR, c.estado.fase); assertTrue(r.envios.isEmpty())
    }
    @Test fun operacionEnProcesoNoSeMuestraComoExito() = runBlocking<Unit> {
        val s = PendientesPrueba(DatosPruebaRf08.solicitud()); val r = RepositoryPrueba().apply { alConsultar = { EstadoOperacionRegistroDto(it, "EN_PROCESO", null) } }
        val c = CoordinadorRegistroVehiculo(r, s); c.cargar(); c.reenviar()
        assertEquals(FaseRegistro.INCIERTA, c.estado.fase); assertNull(c.estado.resultado); assertTrue(r.envios.isEmpty()); assertNotNull(s.actual)
    }
    @Test fun respuestaDeOtraOperacionNoSeAceptaComoConfirmacion() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { DatosPruebaRf08.resultado(it).copy(idOperacion = UUID.randomUUID().toString()) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.INCIERTA, c.estado.fase); assertNull(c.estado.resultado); assertNotNull(s.actual)
    }
    @Test fun conflictoDeIdempotenciaNoSeDescarta() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw FalloRegistroVehiculo("CONFLICTO_IDEMPOTENCIA", 409) } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar(); c.guardar()
        assertEquals(FaseRegistro.INCIERTA, c.estado.fase); assertNotNull(s.actual)
    }
    @Test fun falloAlCerrarConfirmacionNoNiegaElRegistroReal() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba(); val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario)
        c.cargar(); c.guardar(); s.fallarEliminar = true
        assertFalse(c.finalizarConfirmacion()); assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase); assertNotNull(s.actual)
    }
    @Test fun cuentaSuspendidaNoSeConfundeConSesionVencida() = runBlocking<Unit> {
        val r = RepositoryPrueba().apply { errorCupo = FalloRegistroVehiculo("CUENTA_NO_AUTORIZADA", 403) }
        val c = CoordinadorRegistroVehiculo(r, PendientesPrueba()); c.cargar()
        assertEquals(FaseRegistro.ERROR, c.estado.fase); assertTrue(r.envios.isEmpty())
    }
    @Test fun cuentaBloqueadaConSolicitudPendienteNoLaBorra() = runBlocking<Unit> {
        val s = PendientesPrueba(DatosPruebaRf08.solicitud())
        val r = RepositoryPrueba().apply { alConsultar = { throw FalloRegistroVehiculo("CUENTA_NO_AUTORIZADA", 403) } }
        val c = CoordinadorRegistroVehiculo(r, s); c.cargar()
        assertEquals(FaseRegistro.ERROR, c.estado.fase); assertNotNull(s.actual); assertTrue(r.envios.isEmpty())
    }
    @Test fun cancelacionDeCorrutinaNoBorraLaSolicitud() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply { alEnviar = { throw CancellationException("Cancelación de prueba") } }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario); c.cargar()
        try { c.guardar(); fail("Debe propagar la cancelación") } catch (_: CancellationException) { }
        assertNotNull(s.actual)
    }
    @Test fun codigoSesionDeElsaConservaOperacionParaRecuperarla() = runBlocking<Unit> {
        val s = PendientesPrueba(); val r = RepositoryPrueba().apply {
            alEnviar = { throw FalloRegistroVehiculo("SESION_INVALIDA", 401) }
        }
        val c = CoordinadorRegistroVehiculo(r, s, DatosPruebaRf08.formulario)
        c.cargar(); c.guardar()
        val original = s.actual!!
        assertEquals(FaseRegistro.SESION_REQUERIDA, c.estado.fase)
        r.alConsultar = { EstadoOperacionRegistroDto(it, "CONFIRMADA", DatosPruebaRf08.resultado(original)) }
        c.cargar()
        assertEquals(FaseRegistro.CONFIRMADA, c.estado.fase)
        assertEquals(1, r.envios.size)
        assertEquals(original.idOperacion, c.estado.resultado!!.idOperacion)
    }
    @Test fun cambiarMotorLimpiaErroresDeCamposEliminadosSinOcultarVin() = runBlocking<Unit> {
        val r = RepositoryPrueba().apply { alEnviar = {
            throw FalloRegistroVehiculo("DATOS_INVALIDOS", 400, setOf("numeroMotor", "idCombustible", "tipoAceiteOtro", "numeroVin"))
        } }
        val c = CoordinadorRegistroVehiculo(r, PendientesPrueba(), DatosPruebaRf08.formulario)
        c.cargar(); c.guardar()
        val electrico = DatosPruebaRf08.catalogos.tiposMotor.first { it.esElectrico }.idTipoMotor.toString()
        c.cambiar("idTipoMotor", electrico)
        assertEquals(setOf("numeroVin"), c.estado.errores.keys)
        assertEquals("", c.estado.formulario.idCombustible)
        assertEquals("", c.estado.formulario.numeroMotor)
    }
}
