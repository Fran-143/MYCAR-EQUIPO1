package com.mycar.app.features.historial.data

import com.mycar.app.features.historial.domain.*
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class RemoteHistorialRepository(private val api: HistorialApi) : HistorialRepository {
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
        val costo = m.costoTotal?.takeUnless { it.isJsonNull }?.let {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isNumber) invalido()
            BigDecimal(it.asString).also { n ->
                if (n.signum() < 0 || n > BigDecimal("9999999999.99") || n.stripTrailingZeros().scale() > 2) invalido()
            }
        }
        return MantenimientoHistorial(id, codigo, tipo, m.servicioOtro,
            LocalDate.parse(texto(m.fechaServicio)), km, estado, costo, m.taller?.takeIf { it.isNotBlank() })
    }

    private fun texto(valor: String?): String = valor?.takeIf { it.isNotBlank() } ?: invalido()
    private fun invalido(): Nothing = throw ErrorHistorial(CodigoErrorHistorial.DATOS, "Respuesta de historial inválida")

    private fun <T> cuerpo(r: Response<T>): T {
        if (r.isSuccessful) return r.body() ?: invalido()
        throw when (r.code()) {
            401 -> ErrorHistorial(CodigoErrorHistorial.SESION, "Se requiere una sesión válida")
            403, 404 -> ErrorHistorial(CodigoErrorHistorial.VEHICULO_NO_DISPONIBLE, "Vehículo no disponible")
            503 -> ErrorHistorial(CodigoErrorHistorial.CONEXION, "Historial no disponible")
            else -> ErrorHistorial(CodigoErrorHistorial.CONEXION, "No se pudo consultar el historial")
        }
    }

    private suspend fun <T> llamada(bloque: suspend () -> T): T = try { bloque()
    } catch (ex: CancellationException) { throw ex
    } catch (ex: ErrorHistorial) { throw ex
    } catch (ex: IOException) { throw ErrorHistorial(CodigoErrorHistorial.CONEXION, "No se pudo conectar con el servidor")
    } catch (ex: Exception) { invalido() }
}

object HistorialApiAdapter {
    fun desde(retrofitCompartidoAutenticado: Retrofit): HistorialRepository =
        RemoteHistorialRepository(retrofitCompartidoAutenticado.create(HistorialApi::class.java))
}
