package com.mycar.app.features.soporte.data

data class VehiculoElegibleDto(
    val idVehiculo: Long,
    val placa: String,
    val marca: String,
    val modelo: String,
    val numeroVin: String?,
    val tieneTive: Boolean
)

data class SolicitudResumenDto(
    val codigo: String,
    val tipo: String,
    val estado: String,
    val idVehiculo: Long,
    val placa: String,
    val creadoUtc: String? = null,
    val actualizadoUtc: String? = null
)

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
    val idVehiculo: Long,
    val placa: String,
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
    val eventos: List<EventoSoporteDto> = emptyList()
) {
    val vehiculoEtiqueta: String
        get() = listOfNotNull(
            placa.takeIf { it.isNotBlank() },
            listOfNotNull(marca, modelo).joinToString(" ").takeIf { it.isNotBlank() }
        ).joinToString(" – ")
}

data class CrearSolicitudResponse(
    val idOperacion: String? = null,
    val codigo: String,
    val estado: String
)