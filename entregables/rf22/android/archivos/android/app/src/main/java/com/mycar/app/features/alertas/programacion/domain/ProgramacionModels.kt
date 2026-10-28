package com.mycar.app.features.alertas.programacion.domain

enum class CriterioRf22 { POR_TIEMPO, POR_KILOMETRAJE, AMBOS }
enum class UnidadRf22 { SEMANAS, MESES }
data class VehiculoRf22(val idVehiculo: Long, val placa: String, val marca: String, val modelo: String,
    val principal: Boolean, val aceite: String?)
data class TipoRf22(val idTipoMantenimiento: Long, val codigo: String, val nombre: String)
data class ContextoRf22(val claveCuenta: String, val fechaServidor: String,
    val vehiculos: List<VehiculoRf22>, val tipos: List<TipoRf22>)
data class ReferenciaRf22(val idMantenimiento: Long, val fechaServicio: String, val kmServicio: Int, val servicioOtro: String?)
data class AntecedentesRf22(val fechaServidor: String, val idLectura: Long?, val kmActual: Int?,
    val sugerencias: List<String>, val referencia: ReferenciaRf22?)
data class ConfiguracionRf22(val idVehiculo: Long, val idTipoMantenimiento: Long, val servicioOtro: String?,
    val criterio: CriterioRf22, val unidadTiempo: UnidadRf22?, val cantidadTiempo: Int?, val intervaloKm: Int?,
    val idMantenimientoReferencia: Long?)
data class BasesRf22(val fechaBase: String, val idLectura: Long?, val kmBase: Int?)
data class ObjetivosRf22(val fechaObjetivo: String?, val kmObjetivo: Int?, val estado: String)
data class RevisionRf22(val configuracion: ConfiguracionRf22, val bases: BasesRf22,
    val objetivos: ObjetivosRf22, val referencia: ReferenciaRf22?)
data class SolicitudRf22(val idOperacion: String, val revision: RevisionRf22)
data class ResultadoRf22(val idOperacion: String, val idAlerta: Long, val idVehiculo: Long, val servicio: String,
    val criterio: CriterioRf22, val fechaObjetivo: String?, val kmObjetivo: Int?, val estadoInicial: String,
    val eventoCreacionRegistrado: Boolean)
data class OperacionRf22(val idOperacion: String, val estado: String, val resultado: ResultadoRf22?)

class ErrorRf22(val codigo: String, override val message: String, val revision: RevisionRf22? = null) : Exception(message)
interface ProgramacionRepository {
    suspend fun contexto(): ContextoRf22
    suspend fun antecedentes(vehiculo: Long, tipo: Long, servicioElegido: String? = null): AntecedentesRf22
    suspend fun revisar(configuracion: ConfiguracionRf22): RevisionRf22
    suspend fun confirmar(solicitud: SolicitudRf22): ResultadoRf22
    suspend fun consultar(idOperacion: String): OperacionRf22
}
interface PendientesProgramacion {
    fun leer(): SolicitudRf22?
    fun guardar(solicitud: SolicitudRf22)
    fun borrar()
    fun cerrar(borrar: Boolean)
}
