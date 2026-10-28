package com.mycar.app.features.alertas.programacion

import com.mycar.app.features.alertas.programacion.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CoordinadorProgramacionTest {
    private class Archivo : PendientesProgramacion {
        var solicitud: SolicitudRf22? = null; var cerrado = false; var falla = false
        override fun leer() = solicitud
        override fun guardar(solicitud: SolicitudRf22) { check(!cerrado && !falla); this.solicitud = solicitud }
        override fun borrar() { check(!cerrado); solicitud = null }
        override fun cerrar(borrar: Boolean) { cerrado = true; if (borrar) solicitud = null }
    }
    private class Repo : ProgramacionRepository {
        var km: Int? = 50000; var cuenta = "A"; var vehiculos = listOf(VehiculoRf22(1, "ABC-123", "Toyota", "Corolla", true, null))
        var errorConfirmar: Exception? = null; var errorAntecedentes: Exception? = null
        var revisionCambiada: RevisionRf22? = null
        var antesConfirmar: (suspend () -> Unit)? = null
        val envios = mutableListOf<SolicitudRf22>(); var consultas = 0; var revisiones = 0; var consultasAntecedentes = 0
        var operacion: OperacionRf22? = null
        override suspend fun contexto() = ContextoRf22(cuenta, "2026-10-28", vehiculos,
            listOf(TipoRf22(1, "MANTENIMIENTO_GENERAL", "Mantenimiento general"), TipoRf22(2, "OTRO", "Otro")))
        override suspend fun antecedentes(vehiculo: Long, tipo: Long, servicioElegido: String?): AntecedentesRf22 {
            consultasAntecedentes++; errorAntecedentes?.let { throw it }
            return AntecedentesRf22("2026-10-28", km?.let { 1L }, km, if (tipo == 2L) listOf("Cambio de correa") else emptyList(),
                if (tipo == 1L || servicioElegido != null) ReferenciaRf22(10, "2026-01-01", 20000, servicioElegido) else null)
        }
        override suspend fun revisar(configuracion: ConfiguracionRf22): RevisionRf22 {
            revisiones++; val tiempo = configuracion.criterio != CriterioRf22.POR_KILOMETRAJE
            val kilometraje = configuracion.criterio != CriterioRf22.POR_TIEMPO
            return RevisionRf22(configuracion, BasesRf22("2026-10-28", if (kilometraje) 1L else null, if (kilometraje) km else null),
                ObjetivosRf22(if (tiempo) LocalDate.of(2026,10,28).plusMonths(configuracion.cantidadTiempo!!.toLong()).toString() else null,
                    if (kilometraje) km!! + configuracion.intervaloKm!! else null, "PROGRAMADA"),
                configuracion.idMantenimientoReferencia?.let { ReferenciaRf22(it, "2026-01-01", 20000, configuracion.servicioOtro) })
        }
        override suspend fun confirmar(solicitud: SolicitudRf22): ResultadoRf22 {
            envios += solicitud; antesConfirmar?.invoke(); errorConfirmar?.let { throw it }
            revisionCambiada?.let { throw ErrorRf22("REVISION_CAMBIO", "Revisa los nuevos datos", it) }
            return resultado(solicitud)
        }
        override suspend fun consultar(idOperacion: String): OperacionRf22 { consultas++; return operacion ?: OperacionRf22(idOperacion, "NO_ENCONTRADA", null) }
        fun resultado(s: SolicitudRf22) = ResultadoRf22(s.idOperacion, 20, s.revision.configuracion.idVehiculo, "Mantenimiento general",
            s.revision.configuracion.criterio, s.revision.objetivos.fechaObjetivo, s.revision.objetivos.kmObjetivo, "PROGRAMADA", true)
    }
    private suspend fun preparado(repo: Repo, archivo: Archivo): CoordinadorProgramacion {
        val c = CoordinadorProgramacion(repo, { archivo }); c.iniciar(); c.seleccionarTipo(1); c.intervalos(cantidad = "2", km = "5000"); return c
    }
    @Test fun seleccionaPrincipalYSinPrincipalEsperaEleccion() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a)
        assertEquals(1L, c.estado.value.vehiculo)
        r.vehiculos = r.vehiculos.map { it.copy(principal = false) }
        val otro = CoordinadorProgramacion(r, { Archivo() }); otro.iniciar(); assertNull(otro.estado.value.vehiculo)
    }
    @Test fun sinVehiculosMuestraVacioSinEnviarGuardado() = runBlocking {
        val r = Repo(); r.vehiculos = emptyList(); val c = CoordinadorProgramacion(r, { Archivo() }); c.iniciar()
        assertEquals(FaseProgramacion.SIN_VEHICULOS, c.estado.value.fase); assertTrue(r.envios.isEmpty())
    }
    @Test fun sinKmPermiteTiempoYBloqueaAmbos() = runBlocking {
        val r = Repo(); r.km = null; val c = preparado(r, Archivo())
        c.criterio(CriterioRf22.AMBOS); assertEquals(CriterioRf22.POR_TIEMPO, c.estado.value.criterio)
        c.revisar(); assertEquals(FaseProgramacion.REVISION, c.estado.value.fase)
        assertNull(c.estado.value.revision!!.objetivos.kmObjetivo)
    }
    @Test fun errorTecnicoNoSeConvierteEnLecturaAusenteValida() = runBlocking {
        val r = Repo(); r.errorAntecedentes = ErrorRf22("ERROR", "No disponible"); val c = preparado(r, Archivo())
        assertNull(c.estado.value.antecedentes); assertFalse(c.estado.value.puedeRevisar); c.revisar(); assertEquals(0, r.revisiones)
    }
    @Test fun intervalosInvalidosYOtroVacioNoLleganAlServidor() = runBlocking {
        val r = Repo(); val c = preparado(r, Archivo()); c.intervalos(cantidad = "0"); c.revisar(); assertEquals(0, r.revisiones)
        c.seleccionarTipo(2); c.intervalos(cantidad = "2"); c.revisar(); assertEquals(0, r.revisiones)
    }
    @Test fun sugerenciasSeFiltranLocalmenteYSoloSeleccionExplicitaVincula() = runBlocking {
        val r = Repo(); val c = preparado(r, Archivo()); c.seleccionarTipo(2)
        val consultas = r.consultasAntecedentes
        c.editarServicio("Cambio de correa"); assertNull(c.estado.value.referencia); assertEquals(consultas, r.consultasAntecedentes)
        c.elegirSugerencia("Cambio de correa"); assertNotNull(c.estado.value.referencia)
        c.editarServicio("Cambio de otra correa"); assertNull(c.estado.value.referencia); assertNull(c.estado.value.antecedentes!!.referencia)
    }
    @Test fun revisarYCancelarNoEscribenPendienteNiEnvianAlta() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar(); c.volverEditar(); c.cerrar(false)
        assertTrue(r.envios.isEmpty()); assertNull(a.solicitud)
    }
    @Test fun noEnviaSiNoPuedeConservarLaOperacion() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar(); a.falla = true; c.confirmar()
        assertTrue(r.envios.isEmpty()); assertEquals(FaseProgramacion.REVISION, c.estado.value.fase)
    }
    @Test fun respuestaPerdidaSeConsultaYSeReenviaMismoIdYCuerpo() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar(); r.errorConfirmar = ErrorRf22("RESULTADO_POR_COMPROBAR", "Sin respuesta"); c.confirmar()
        assertEquals(FaseProgramacion.POR_COMPROBAR, c.estado.value.fase); val original = a.solicitud!!
        c.confirmar(); assertEquals(1, r.envios.size)
        c.comprobar(); assertTrue(c.estado.value.permiteReenvio); r.errorConfirmar = null; c.reenviarMismaOperacion()
        assertEquals(original, r.envios[1]); assertEquals(FaseProgramacion.CONFIRMADO, c.estado.value.fase); assertNull(a.solicitud)
    }
    @Test fun resultadoConfirmadoSeRecuperaSinSegundoPost() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar(); r.errorConfirmar = ErrorRf22("RESULTADO_POR_COMPROBAR", "Sin respuesta"); c.confirmar()
        val original = a.solicitud!!; r.operacion = OperacionRf22(original.idOperacion, "CONFIRMADA", r.resultado(original)); c.comprobar()
        assertEquals(1, r.envios.size); assertNull(a.solicitud); assertEquals(FaseProgramacion.CONFIRMADO, c.estado.value.fase)
    }
    @Test fun cambioDeBasesExigeConfirmarOtraVezNoConfirmaAutomaticamente() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar()
        r.revisionCambiada = c.estado.value.revision!!.copy(bases = BasesRf22("2026-10-29", null, null), objetivos = ObjetivosRf22("2026-12-29", null, "PROGRAMADA"))
        c.confirmar(); assertEquals(FaseProgramacion.REVISION, c.estado.value.fase); assertEquals(1, r.envios.size); assertNull(a.solicitud)
        assertEquals("2026-10-29", c.estado.value.antecedentes!!.fechaServidor)
        assertEquals(c.estado.value.revision!!.referencia, c.estado.value.referencia)
        r.revisionCambiada = null; c.confirmar(); assertEquals(2, r.envios.size); assertNotEquals(r.envios[0].idOperacion, r.envios[1].idOperacion)
    }
    @Test fun operacionPersistidaBloqueaFormularioHastaConciliar() = runBlocking {
        val r = Repo(); val a = Archivo(); val primero = preparado(r, a); primero.revisar(); r.errorConfirmar = ErrorRf22("RESULTADO_POR_COMPROBAR", "Sin respuesta"); primero.confirmar()
        val segundo = CoordinadorProgramacion(r, { a }); segundo.iniciar()
        assertEquals(FaseProgramacion.POR_COMPROBAR, segundo.estado.value.fase); segundo.revisar(); assertEquals(1, r.envios.size)
    }
    @Test fun referenciaRetiradaPorServidorNoReapareceAlVolverEditar() = runBlocking {
        val r = Repo(); val c = preparado(r, Archivo()); c.revisar()
        val anterior = c.estado.value.revision!!
        r.revisionCambiada = anterior.copy(configuracion = anterior.configuracion.copy(idMantenimientoReferencia = null), referencia = null)
        c.confirmar(); c.volverEditar()
        assertNull(c.estado.value.referencia); assertNull(c.estado.value.antecedentes!!.referencia)
        assertEquals("2", c.estado.value.cantidad)
    }
    @Test fun cuentaDistintaNoRecibePendienteDeOtra() = runBlocking {
        val r = Repo(); val a = Archivo(); val b = Archivo(); val c = preparado(r, a); c.revisar(); r.errorConfirmar = ErrorRf22("RESULTADO_POR_COMPROBAR", "Sin respuesta"); c.confirmar()
        r.cuenta = "B"; val otra = CoordinadorProgramacion(r, { if (it == "A") a else b }); otra.iniciar()
        assertNull(otra.estado.value.pendiente); assertNotNull(a.solicitud)
    }
    @Test fun cierreLocalIgnoraRespuestaTardiaYNoRecreaArchivo() = runBlocking {
        val r = Repo(); val a = Archivo(); val c = preparado(r, a); c.revisar()
        val entro = CompletableDeferred<Unit>(); val salir = CompletableDeferred<Unit>()
        r.antesConfirmar = { entro.complete(Unit); salir.await() }
        val trabajo = launch { c.confirmar() }; entro.await(); c.cerrar(true); salir.complete(Unit); trabajo.join()
        assertNull(a.solicitud); assertTrue(a.cerrado); assertNotEquals(FaseProgramacion.CONFIRMADO, c.estado.value.fase)
    }
}
