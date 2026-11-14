package com.mycar.app.features.historial.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mycar.app.features.historial.domain.*
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class RemoteHistorialRepository(private val api: HistorialApi) : HistorialRepository {
    private val gson = Gson()

    override suspend fun vehiculos(): List<VehiculoHistorial> = llamada {
        val lista = cuerpo(api.contexto()).vehiculos ?: invalido()
        lista.map { vehiculo(it ?: invalido()) }.also { vehiculos ->
            if (vehiculos.distinctBy { it.id }.size != vehiculos.size || vehiculos.count { it.principal } > 1) invalido()
        }
    }

    override suspend fun historial(idVehiculo: Long): ResultadoHistorial = llamada {
        val respuesta = cuerpo(api.historial(idVehiculo))
        val vehiculo = vehiculo(respuesta.vehiculo ?: invalido())
        if (vehiculo.id != idVehiculo) invalido()
        val lista = (respuesta.mantenimientos ?: invalido()).map { mantenimiento(it ?: invalido()) }
        if (respuesta.total != lista.size || lista.distinctBy { it.id }.size != lista.size) invalido()
        if (lista != lista.sortedWith(compareByDescending<MantenimientoHistorial> { it.fecha }.thenByDescending { it.id })) invalido()
        ResultadoHistorial(vehiculo, lista)
    }

    override suspend fun detalle(idVehiculo: Long, idMantenimiento: Long): DetalleMantenimientoHistorial = llamada {
        detalleDe(cuerpo(api.detalle(idVehiculo, idMantenimiento)))
    }

    override suspend fun modificar(
        idVehiculo: Long,
        idMantenimiento: Long,
        datos: ModificarMantenimientoDatos
    ): DetalleMantenimientoHistorial = llamada {
        detalleDe(cuerpo(api.modificar(idVehiculo, idMantenimiento, ModificarMantenimientoDto(
            costoTotal = datos.costoTotal?.toPlainString(),
            observaciones = datos.observaciones,
            idTaller = datos.idTaller,
            sinTaller = if (datos.sinTaller) true else null
        ))))
    }

    override suspend fun anular(idVehiculo: Long, idMantenimiento: Long) = llamada {
        cuerpo(api.anular(idVehiculo, idMantenimiento))
        Unit
    }

    private fun vehiculo(v: VehiculoHistorialDto): VehiculoHistorial {
        val id = v.idVehiculo?.takeIf { it > 0 } ?: invalido()
        val estado = v.estado?.takeIf { it in setOf("ACTIVO", "INACTIVO") } ?: invalido()
        return VehiculoHistorial(id, texto(v.placa), texto(v.marca), texto(v.modelo), estado, v.principal ?: invalido())
    }

    private fun mantenimiento(m: MantenimientoHistorialDto): MantenimientoHistorial {
        val id = m.idMantenimiento?.takeIf { it > 0 } ?: invalido()
        val codigo = texto(m.codigoTipo)
        val tipo = texto(m.tipo)
        if (codigo == "OTRO" && m.servicioOtro.isNullOrBlank()) invalido()
        val km = m.kmServicio?.takeIf { it > 0 } ?: invalido()
        val estado = m.estado?.takeIf { it in setOf("REALIZADO", "ANULADO") } ?: invalido()
        return MantenimientoHistorial(id, codigo, tipo, m.servicioOtro,
            LocalDate.parse(texto(m.fechaServicio)), km, estado, costo(m.costoTotal),
            m.taller?.takeIf { it.isNotBlank() })
    }

    private fun detalleDe(d: DetalleMantenimientoDto): DetalleMantenimientoHistorial {
        val id = d.idMantenimiento?.takeIf { it > 0 } ?: invalido()
        val idVehiculo = d.idVehiculo?.takeIf { it > 0 } ?: invalido()
        val codigo = texto(d.codigoTipo)
        if (codigo == "OTRO" && d.servicioOtro.isNullOrBlank()) invalido()
        val estado = d.estado?.takeIf { it in setOf("REALIZADO", "ANULADO") } ?: invalido()
        return DetalleMantenimientoHistorial(
            id = id,
            idVehiculo = idVehiculo,
            placa = d.placa?.takeIf { it.isNotBlank() },
            marca = d.marca?.takeIf { it.isNotBlank() },
            modelo = d.modelo?.takeIf { it.isNotBlank() },
            estadoVehiculo = d.estadoVehiculo?.takeIf { it.isNotBlank() },
            codigoTipo = codigo,
            tipo = texto(d.tipo),
            servicioOtro = d.servicioOtro,
            fecha = LocalDate.parse(texto(d.fechaServicio)),
            kmServicio = d.kmServicio?.takeIf { it > 0 } ?: invalido(),
            estado = estado,
            costo = costo(d.costoTotal),
            observaciones = d.observaciones?.takeIf { it.isNotBlank() },
            taller = d.taller?.takeIf { it.isNotBlank() },
            idTaller = d.idTaller?.takeIf { it > 0 },
            proximaFechaSugerida = d.proximaFechaSugerida?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            proximoKmSugerido = d.proximoKmSugerido?.takeIf { it > 0 },
            anuladoUtc = d.anuladoUtc?.takeIf { it.isNotBlank() },
            editable = d.editable == true,
            anulable = d.anulable == true,
            bloqueadoPorAlertaActiva = d.bloqueadoPorAlertaActiva == true,
            repuestos = d.repuestos.orEmpty().mapNotNull { r ->
                val item = r ?: return@mapNotNull null
                RepuestoHistorial(
                    texto(item.nombre),
                    item.marca?.takeIf { it.isNotBlank() },
                    item.cantidad?.takeIf { it > 0 } ?: invalido(),
                    costo(item.precioUnitario)
                )
            }
        )
    }

    private fun costo(valor: JsonElement?): BigDecimal? = valor?.takeUnless { it.isJsonNull }?.let {
        if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) invalido()
        BigDecimal(it.asString).also { n ->
            if (n.signum() < 0 || n > BigDecimal("9999999999.99") || n.stripTrailingZeros().scale() > 2) invalido()
        }
    }

    private fun texto(valor: String?): String = valor?.takeIf { it.isNotBlank() } ?: invalido()
    private fun invalido(): Nothing = throw ErrorHistorial(CodigoErrorHistorial.DATOS, "Respuesta de historial inválida")

    private fun <T> cuerpo(r: Response<T>): T {
        if (r.isSuccessful) return r.body() ?: invalido()
        val err = runCatching {
            r.errorBody()?.use { gson.fromJson(it.string(), ErrorHistorialDto::class.java) }
        }.getOrNull()
        throw when (r.code()) {
            401 -> ErrorHistorial(CodigoErrorHistorial.SESION, err?.mensaje ?: "Se requiere una sesión válida")
            404 -> when (err?.codigo) {
                "MANTENIMIENTO_NO_DISPONIBLE" -> ErrorHistorial(
                    CodigoErrorHistorial.MANTENIMIENTO_NO_DISPONIBLE,
                    err.mensaje ?: "Mantenimiento no disponible"
                )
                else -> ErrorHistorial(CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE, err?.mensaje ?: "Vehículo no disponible")
            }
            409 -> ErrorHistorial(
                CodigoErrorHistorial.ALERTA_ACTIVA,
                err?.mensaje ?: "Hay una alerta activa que referencia este mantenimiento",
                err?.idAlerta
            )
            403 -> ErrorHistorial(
                CodigoErrorHistorial.CONEXION,
                err?.mensaje ?: "Acceso denegado al historial (revisa RF18 en el backend)."
            )
            503 -> ErrorHistorial(CodigoErrorHistorial.CONEXION, err?.mensaje ?: "Historial no disponible")
            else -> ErrorHistorial(
                CodigoErrorHistorial.CONEXION,
                err?.mensaje ?: "No se pudo consultar el historial (HTTP ${r.code()})"
            )
        }
    }

    private suspend fun <T> llamada(bloque: suspend () -> T): T = try {
        bloque()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ErrorHistorial) {
        throw ex
    } catch (ex: IOException) {
        throw ErrorHistorial(CodigoErrorHistorial.CONEXION, "No se pudo conectar con el servidor")
    } catch (ex: Exception) {
        invalido()
    }
}

object HistorialApiAdapter {
    fun desde(retrofitCompartidoAutenticado: Retrofit): HistorialRepository =
        RemoteHistorialRepository(retrofitCompartidoAutenticado.create(HistorialApi::class.java))
}
