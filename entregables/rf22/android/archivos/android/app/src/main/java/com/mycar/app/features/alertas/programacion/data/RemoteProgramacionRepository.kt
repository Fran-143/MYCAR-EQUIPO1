package com.mycar.app.features.alertas.programacion.data

import com.google.gson.Gson
import com.mycar.app.features.alertas.programacion.domain.*
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import com.mycar.app.features.autenticacion.data.AuthProblem
import com.mycar.app.features.autenticacion.data.AuthRepository

interface PeticionesProgramacion {
    suspend fun <T> ejecutar(peticion: suspend (String) -> Response<T>): Response<T>
}
class PeticionesSesionMyCar(private val sesiones: AuthRepository) : PeticionesProgramacion {
    override suspend fun <T> ejecutar(peticion: suspend (String) -> Response<T>): Response<T> = sesiones.authenticatedRequest(peticion)
}

class RemoteProgramacionRepository(private val api: ProgramacionApi, private val peticiones: PeticionesProgramacion) : ProgramacionRepository {
    override suspend fun contexto(): ContextoRf22 = llamada {
        cuerpo(peticiones.ejecutar { api.contexto(it) }).also { c ->
            require(c.claveCuenta.isNotBlank()); LocalDate.parse(c.fechaServidor)
            require(c.vehiculos.all { it.idVehiculo > 0 && it.placa.isNotBlank() && it.marca.isNotBlank() && it.modelo.isNotBlank() })
            require(c.vehiculos.distinctBy { it.idVehiculo }.size == c.vehiculos.size && c.vehiculos.count { it.principal } <= 1)
            require(c.tipos.all { it.idTipoMantenimiento > 0 && it.codigo.isNotBlank() && it.nombre.isNotBlank() })
            require(c.tipos.distinctBy { it.idTipoMantenimiento }.size == c.tipos.size)
        }
    }
    override suspend fun antecedentes(vehiculo: Long, tipo: Long, servicioElegido: String?): AntecedentesRf22 = llamada {
        cuerpo(peticiones.ejecutar { api.antecedentes(it, vehiculo, tipo, servicioElegido) }).also {
            LocalDate.parse(it.fechaServidor); require((it.kmActual == null) == (it.idLectura == null))
            require(it.kmActual == null || it.kmActual > 0 && it.idLectura!! > 0)
            require(it.sugerencias.all(String::isNotBlank)); it.referencia?.let(::validarReferencia)
        }
    }
    override suspend fun revisar(configuracion: ConfiguracionRf22): RevisionRf22 = llamada {
        cuerpo(peticiones.ejecutar { api.revisar(it, configuracion) }).also { validarRevision(it)
            require(it.configuracion.copy(idMantenimientoReferencia = configuracion.idMantenimientoReferencia) == configuracion)
        }
    }
    override suspend fun confirmar(solicitud: SolicitudRf22): ResultadoRf22 = llamada {
        cuerpo(peticiones.ejecutar { api.confirmar(it, solicitud) }).also { validarResultado(it)
            require(it.idOperacion == solicitud.idOperacion && it.idVehiculo == solicitud.revision.configuracion.idVehiculo)
        }
    }
    override suspend fun consultar(idOperacion: String): OperacionRf22 = llamada {
        cuerpo(peticiones.ejecutar { api.consultar(it, idOperacion) }).also {
            require(it.idOperacion == idOperacion && it.estado in setOf("CONFIRMADA", "EN_PROCESO", "NO_ENCONTRADA"))
            require((it.estado == "CONFIRMADA") == (it.resultado != null))
            it.resultado?.let { r -> validarResultado(r); require(r.idOperacion == idOperacion) }
        }
    }
    private data class ErrorDto(val codigo: String?, val mensaje: String?, val revision: RevisionRf22?)
    private fun <T> cuerpo(r: Response<T>): T {
        if (r.isSuccessful) return r.body() ?: throw IOException("Respuesta vacía")
        val error = try { Gson().fromJson(r.errorBody()?.string(), ErrorDto::class.java) } catch (_: Exception) { null }
        val codigo = if (r.code() == 401) "SESION_REQUERIDA" else error?.codigo ?: "RESULTADO_POR_COMPROBAR"
        error?.revision?.let(::validarRevision)
        throw ErrorRf22(codigo, error?.mensaje ?: if (r.code() == 401) "Inicia sesión nuevamente para comprobar tu operación."
            else "No se pudo completar la consulta. Comprueba tu conexión.", error?.revision)
    }
    private suspend fun <T> llamada(bloque: suspend () -> T): T = try { bloque() }
    catch (e: CancellationException) { throw e }
    catch (e: ErrorRf22) { throw e }
    catch (_: AuthProblem) { throw ErrorRf22("SESION_REQUERIDA", "No se pudo validar la sesión. Vuelve a intentarlo.") }
    catch (_: IOException) { throw ErrorRf22("RESULTADO_POR_COMPROBAR", "No se pudo confirmar la respuesta. Comprueba tu conexión y la misma operación.") }
    catch (_: Exception) { throw ErrorRf22("RESPUESTA_INVALIDA", "No se pudo verificar la respuesta del servidor.") }

    companion object {
        fun validarRevision(r: RevisionRf22) {
            val c = r.configuracion; val b = r.bases; val o = r.objetivos
            require(c.idVehiculo > 0 && c.idTipoMantenimiento > 0 && CriterioRf22.entries.contains(c.criterio))
            val base = LocalDate.parse(b.fechaBase); require(base.year in 1..9999)
            val tiempo = c.criterio != CriterioRf22.POR_KILOMETRAJE
            val km = c.criterio != CriterioRf22.POR_TIEMPO
            require(o.estado in setOf("PROGRAMADA", "PROXIMA", "PENDIENTE"))
            if (tiempo) {
                require(c.cantidadTiempo != null && c.cantidadTiempo > 0 && c.unidadTiempo != null)
                val objetivo = if (c.unidadTiempo == UnidadRf22.SEMANAS) base.plusWeeks(c.cantidadTiempo.toLong()) else base.plusMonths(c.cantidadTiempo.toLong())
                require(objetivo.isAfter(base) && LocalDate.parse(o.fechaObjetivo) == objetivo)
            } else require(c.cantidadTiempo == null && c.unidadTiempo == null && o.fechaObjetivo == null)
            if (km) {
                require(b.idLectura != null && b.idLectura > 0 && b.kmBase != null && b.kmBase > 0 && c.intervaloKm != null && c.intervaloKm > 0)
                require(o.kmObjetivo == Math.addExact(b.kmBase, c.intervaloKm))
            } else require(b.idLectura == null && b.kmBase == null && c.intervaloKm == null && o.kmObjetivo == null)
            r.referencia?.let { validarReferencia(it); require(c.idMantenimientoReferencia == it.idMantenimiento) }
            require((r.referencia == null) == (c.idMantenimientoReferencia == null))
        }
        private fun validarReferencia(r: ReferenciaRf22) {
            require(r.idMantenimiento > 0 && r.kmServicio > 0); LocalDate.parse(r.fechaServicio)
        }
        fun validarResultado(r: ResultadoRf22) {
            UUID.fromString(r.idOperacion); require(r.idAlerta > 0 && r.idVehiculo > 0 && r.servicio.isNotBlank() && r.eventoCreacionRegistrado && CriterioRf22.entries.contains(r.criterio))
            require(r.estadoInicial in setOf("PROGRAMADA", "PROXIMA", "PENDIENTE"))
            require((r.fechaObjetivo != null) == (r.criterio != CriterioRf22.POR_KILOMETRAJE))
            require((r.kmObjetivo != null) == (r.criterio != CriterioRf22.POR_TIEMPO))
            r.fechaObjetivo?.let { LocalDate.parse(it) }; r.kmObjetivo?.let { require(it > 0) }
        }
    }
}
