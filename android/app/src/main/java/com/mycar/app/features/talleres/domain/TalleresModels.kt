package com.mycar.app.features.talleres.domain

data class Taller(
    val id: Long,
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val estado: String = "ACTIVO",
    val favorito: Boolean = false,
    val versionFila: Int = 0,
    val origen: String = "MANUAL",
    val tieneWhatsapp: Boolean = false,
    val idTallerGlobal: Long? = null,
    val editable: Boolean = true
) {
    val esAsociado: Boolean get() = origen.equals("ASOCIADO", ignoreCase = true)
}

data class TallerGlobal(
    val id: Long,
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val tieneWhatsapp: Boolean = false
)

data class DatosTallerFormulario(
    val nombre: String,
    val telefono: String? = null,
    val correo: String? = null,
    val direccion: String? = null,
    val notas: String? = null,
    val tieneWhatsapp: Boolean = false,
    val versionFila: Int? = null
)

enum class FiltroOrigenTaller(val api: String, val etiqueta: String) {
    TODOS("TODOS", "Todos"),
    ASOCIADOS("ASOCIADO", "Asociados"),
    MANUALES("MANUAL", "Manuales")
}

interface TalleresRepository {
    suspend fun listarActivos(filtro: FiltroOrigenTaller = FiltroOrigenTaller.TODOS): List<Taller>
    suspend fun detalle(id: Long): Taller
    suspend fun registrar(datos: DatosTallerFormulario): Taller
    suspend fun actualizar(id: Long, datos: DatosTallerFormulario): Taller
    suspend fun retirar(id: Long, versionFila: Int): Taller
    suspend fun marcarFavorito(id: Long): Taller
    suspend fun desmarcarFavorito(id: Long): Taller
    suspend fun catalogoGlobal(q: String? = null): List<TallerGlobal>
    suspend fun vincularAsociado(idTallerGlobal: Long): Taller
}

class ErrorTalleres(val codigo: String, message: String) : Exception(message)
