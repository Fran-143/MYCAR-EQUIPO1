package com.mycar.app.features.alertas.programacion

import com.mycar.app.features.alertas.programacion.data.*
import com.mycar.app.features.alertas.programacion.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.util.UUID

class RemoteProgramacionRepositoryTest {
    private val configuracion = ConfiguracionRf22(1, 2, null, CriterioRf22.POR_TIEMPO, UnidadRf22.MESES, 1, null, null)
    private val revision = RevisionRf22(configuracion, BasesRf22("2026-01-31", null, null), ObjetivosRf22("2026-02-28", null, "PROXIMA"), null)
    @Test fun validaFinDeMesYSinLecturaEnTiempo() { RemoteProgramacionRepository.validarRevision(revision) }
    @Test fun rechazaMesTratadoComoTreintaDias() {
        assertThrows(IllegalArgumentException::class.java) { RemoteProgramacionRepository.validarRevision(revision.copy(objetivos = revision.objetivos.copy(fechaObjetivo = "2026-03-02"))) }
    }
    @Test fun rechazaKmInventadoEnTiempo() {
        assertThrows(IllegalArgumentException::class.java) { RemoteProgramacionRepository.validarRevision(revision.copy(bases = revision.bases.copy(kmBase = 50000))) }
    }
    @Test fun rechazaConfirmacionSinHistorialYObjetivosDelCriterio() {
        val r = ResultadoRf22(UUID.randomUUID().toString(), 1, 1, "Servicio", CriterioRf22.POR_TIEMPO, "2026-02-28", null, "PROXIMA", false)
        assertThrows(IllegalArgumentException::class.java) { RemoteProgramacionRepository.validarResultado(r) }
        assertThrows(IllegalArgumentException::class.java) { RemoteProgramacionRepository.validarResultado(r.copy(eventoCreacionRegistrado = true, kmObjetivo = 100)) }
    }
    @Test fun consultaNoEncontradaNoEsConfirmacion() = runBlocking {
        val id = UUID.randomUUID().toString()
        val api = object : ProgramacionApi {
            override suspend fun contexto(bearer: String): Response<ContextoRf22> = error("No corresponde")
            override suspend fun antecedentes(bearer: String, vehiculo: Long, tipo: Long, servicio: String?): Response<AntecedentesRf22> = error("No corresponde")
            override suspend fun revisar(bearer: String, configuracion: ConfiguracionRf22): Response<RevisionRf22> = error("No corresponde")
            override suspend fun confirmar(bearer: String, solicitud: SolicitudRf22): Response<ResultadoRf22> = error("No corresponde")
            override suspend fun consultar(bearer: String, id: String) = Response.success(OperacionRf22(id, "NO_ENCONTRADA", null))
        }
        val peticiones = object : PeticionesProgramacion {
            override suspend fun <T> ejecutar(peticion: suspend (String) -> Response<T>): Response<T> = peticion("FIXTURE")
        }
        val r = RemoteProgramacionRepository(api, peticiones).consultar(id); assertNull(r.resultado); assertEquals("NO_ENCONTRADA", r.estado)
    }
}
