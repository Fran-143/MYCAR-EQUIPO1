package com.mycar.app.features.talleres.data

import com.google.gson.Gson
import com.mycar.app.features.sesion.data.ClienteVehiculosAutenticado
import com.mycar.app.features.talleres.domain.DatosTallerFormulario
import com.mycar.app.features.talleres.domain.ErrorTalleres
import com.mycar.app.features.talleres.domain.FiltroOrigenTaller
import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.TallerGlobal
import com.mycar.app.features.talleres.domain.TalleresRepository
import retrofit2.HttpException

class RemoteTalleresRepository(private val api: TalleresApi) : TalleresRepository {
    override suspend fun listarActivos(filtro: FiltroOrigenTaller): List<Taller> =
        api.listar(filtro.api).talleres.orEmpty().mapNotNull { it?.toDomain() }

    override suspend fun detalle(id: Long): Taller = ejecutar { api.detalle(id).toDomain() }

    override suspend fun registrar(datos: DatosTallerFormulario): Taller = ejecutar {
        api.registrar(
            RegistrarTallerDto(
                datos.nombre, datos.telefono, datos.correo, datos.direccion, datos.notas,
                datos.tieneWhatsapp
            )
        ).toDomain()
    }

    override suspend fun actualizar(id: Long, datos: DatosTallerFormulario): Taller = ejecutar {
        api.actualizar(
            id,
            ActualizarTallerDto(
                datos.nombre, datos.telefono, datos.correo, datos.direccion, datos.notas,
                datos.tieneWhatsapp,
                datos.versionFila ?: error("versionFila requerida")
            )
        ).toDomain()
    }

    override suspend fun retirar(id: Long, versionFila: Int): Taller = ejecutar {
        api.retirar(id, RetirarTallerDto(versionFila)).toDomain()
    }

    override suspend fun marcarFavorito(id: Long): Taller = ejecutar { api.marcarFavorito(id).toDomain() }

    override suspend fun desmarcarFavorito(id: Long): Taller =
        ejecutar { api.desmarcarFavorito(id).toDomain() }

    override suspend fun catalogoGlobal(q: String?): List<TallerGlobal> = ejecutar {
        api.catalogoGlobal(q).talleres.orEmpty().mapNotNull { dto ->
            val id = dto?.idTallerGlobal?.takeIf { it > 0 } ?: return@mapNotNull null
            TallerGlobal(
                id = id,
                nombre = dto.nombre?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                telefono = dto.telefono?.takeIf { it.isNotBlank() },
                correo = dto.correo?.takeIf { it.isNotBlank() },
                direccion = dto.direccion?.takeIf { it.isNotBlank() },
                tieneWhatsapp = dto.tieneWhatsapp == true,
                contactoAutorizado = dto.contactoAutorizado == true
            )
        }
    }

    override suspend fun vincularAsociado(idTallerGlobal: Long): Taller = ejecutar {
        api.vincularAsociado(VincularAsociadoDto(idTallerGlobal)).toDomain()
    }

    private fun TallerDto.toDomain(): Taller {
        val id = idTaller?.takeIf { it > 0 } ?: error("id inválido")
        val origen = origen?.takeIf { it.isNotBlank() } ?: "MANUAL"
        return Taller(
            id = id,
            nombre = nombre?.takeIf { it.isNotBlank() } ?: error("nombre inválido"),
            telefono = telefono?.takeIf { it.isNotBlank() },
            correo = correo?.takeIf { it.isNotBlank() },
            direccion = direccion?.takeIf { it.isNotBlank() },
            notas = notas?.takeIf { it.isNotBlank() },
            estado = estado ?: "ACTIVO",
            favorito = favorito == true,
            versionFila = versionFila ?: 0,
            origen = origen,
            tieneWhatsapp = tieneWhatsapp == true,
            contactoAutorizado = contactoAutorizado == true,
            idTallerGlobal = idTallerGlobal,
            editable = editable ?: origen.equals("MANUAL", ignoreCase = true)
        )
    }

    private suspend fun <T> ejecutar(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val cuerpo = e.response()?.errorBody()?.string()
        val err = runCatching { Gson().fromJson(cuerpo, ErrorTallerDto::class.java) }.getOrNull()
        throw ErrorTalleres(err?.codigo ?: "ERROR", err?.mensaje ?: "No se pudo completar la operación")
    }
}

object TalleresApiAdapter {
    fun desde(cliente: ClienteVehiculosAutenticado): TalleresRepository =
        RemoteTalleresRepository(cliente.retrofit.create(TalleresApi::class.java))
}
