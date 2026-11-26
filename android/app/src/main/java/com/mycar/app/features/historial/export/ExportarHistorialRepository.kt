package com.mycar.app.features.historial.export

import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.IOException

class FalloExportarHistorial(val codigo: String, override val message: String) : Exception(message)

interface ExportarHistorialRepository {
    suspend fun contexto(idVehiculo: Long): ContextoExportDto
    suspend fun generarPdf(
        idVehiculo: Long,
        fechaDesde: String,
        fechaHasta: String,
        tipoMantenimientoId: Long?,
        incluirAnulados: Boolean
    ): Pair<ByteArray, String>
}

class RemoteExportarHistorialRepository(private val api: ExportarHistorialApi) : ExportarHistorialRepository {
    override suspend fun contexto(idVehiculo: Long): ContextoExportDto {
        val r = api.contexto(idVehiculo)
        if (r.isSuccessful) {
            return r.body() ?: throw FalloExportarHistorial("VACIO", "Sin datos de exportación.")
        }
        throw errorHttp(r.code(), r.errorBody())
    }

    override suspend fun generarPdf(
        idVehiculo: Long,
        fechaDesde: String,
        fechaHasta: String,
        tipoMantenimientoId: Long?,
        incluirAnulados: Boolean
    ): Pair<ByteArray, String> {
        val r = api.exportarPdf(
            idVehiculo,
            ExportarHistorialBody(fechaDesde, fechaHasta, tipoMantenimientoId, incluirAnulados)
        )
        if (!r.isSuccessful) {
            throw errorHttp(r.code(), r.errorBody())
        }
        val body = r.body() ?: throw FalloExportarHistorial("VACIO", "PDF vacío.")
        val bytes = body.bytes()
        if (bytes.isEmpty()) {
            throw FalloExportarHistorial("PDF_VACIO", "PDF vacío.")
        }
        val nombre = r.headers()["X-MyCar-Nombre-Archivo"]
            ?: nombreDesdeDisposition(r.headers()["Content-Disposition"])
            ?: "Historial_MyCar.pdf"
        return bytes to nombre
    }

    private fun errorHttp(code: Int, errorBody: ResponseBody?): FalloExportarHistorial {
        val raw = try {
            errorBody?.string().orEmpty()
        } catch (_: IOException) {
            ""
        }
        val codigo = Regex("\"codigo\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
            ?: when (code) {
                401 -> "SESION_REQUERIDA"
                403 -> "PREMIUM_REQUERIDO"
                404 -> "VEHICULO_NO_DISPONIBLE"
                else -> "ERROR_HTTP"
            }
        val mensaje = Regex("\"mensaje\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
            ?: when (codigo) {
                "PREMIUM_REQUERIDO" ->
                    "La exportación de historial está disponible solo para usuarios con plan Premium."
                "SESION_REQUERIDA" -> "Inicia sesión para exportar el historial."
                else -> "No se pudo exportar el historial."
            }
        return FalloExportarHistorial(codigo, mensaje)
    }

    private fun nombreDesdeDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val m = Regex("filename=\"?([^\";]+)\"?").find(header) ?: return null
        return m.groupValues[1]
    }

    companion object {
        fun desde(retrofit: Retrofit): ExportarHistorialRepository =
            RemoteExportarHistorialRepository(retrofit.create(ExportarHistorialApi::class.java))
    }
}
