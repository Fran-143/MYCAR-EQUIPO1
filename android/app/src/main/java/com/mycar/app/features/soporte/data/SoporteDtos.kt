package com.mycar.app.features.soporte.data

import com.google.gson.annotations.SerializedName

data class VehiculoElegibleDto(
    val idVehiculo: Long,
    val placa: String,
    val marca: String,
    val modelo: String,
    @SerializedName(value = "numeroVin", alternate = ["numero_vin"])
    val numeroVin: String?,
    @SerializedName(value = "tieneTive", alternate = ["tiene_tive"])
    val tieneTive: Boolean
)

data class SolicitudResumenDto(
    val codigo: String,
    val tipo: String,
    val estado: String,
    val idVehiculo: Long? = null,
    val placa: String? = null,
    val asunto: String? = null,
    val categoria: String? = null,
    val creadoUtc: String? = null,
    val actualizadoUtc: String? = null
) {
    val esIncidenciaGeneral: Boolean get() = tipo.equals("INCIDENCIA_GENERAL", ignoreCase = true)
    val esCorreccionVin: Boolean get() = tipo.equals("CORRECCION_VIN", ignoreCase = true)
}

data class EventoSoporteDto(
    val estadoAnterior: String? = null,
    val estadoNuevo: String? = null,
    val observacion: String? = null,
    val ocurridoUtc: String? = null
)

data class SolicitudDetalleDto(
    val codigo: String,
    val tipo: String,
    val estado: String,
    val idVehiculo: Long? = null,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val nombreUsuario: String? = null,
    val correoUsuario: String? = null,
    val vinRegistrado: String? = null,
    val vinPropuesto: String? = null,
    val motivo: String? = null,
    val motivoRechazo: String? = null,
    val tieneEvidencia: Boolean = false,
    val contentTypeEvidencia: String? = null,
    val nombreEvidencia: String? = null,
    val tamanoEvidenciaBytes: Int? = null,
    val versionFila: Int = 0,
    val creadoUtc: String? = null,
    val actualizadoUtc: String? = null,
    val eventos: List<EventoSoporteDto> = emptyList(),
    val categoria: String? = null,
    val temaOtro: String? = null,
    val asunto: String? = null,
    val descripcion: String? = null,
    val respuestaAdmin: String? = null,
    val cerradoUtc: String? = null
) {
    val esIncidenciaGeneral: Boolean get() = tipo.equals("INCIDENCIA_GENERAL", ignoreCase = true)
    val vehiculoEtiqueta: String
        get() = listOfNotNull(
            placa?.takeIf { it.isNotBlank() },
            listOfNotNull(marca, modelo).joinToString(" ").takeIf { it.isNotBlank() }
        ).joinToString(" – ")
}

data class CrearSolicitudResponse(
    val idOperacion: String? = null,
    val codigo: String,
    val estado: String
)

data class ResumenSoporteDto(
    val tieneSolicitudes: Boolean = false,
    val total: Int = 0,
    val pendientes: Int = 0
)

data class ContadoresIncidenciaDto(
    val pendientes: Int = 0,
    val aceptadas: Int = 0,
    val enProceso: Int = 0,
    val resueltas: Int = 0,
    val rechazadas: Int = 0
)
