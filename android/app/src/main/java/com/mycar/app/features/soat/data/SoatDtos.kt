package com.mycar.app.features.soat.data

data class SoatDto(
    val idSoat: Long,
    val aseguradora: String,
    val numeroPoliza: String,
    val fechaInicio: String,
    val fechaVencimiento: String,
    val fechaEmision: String? = null,
    val nombreContratante: String? = null,
    val importePrima: Double? = null,
    val vigencia: String,
    val estadoCobertura: String,
    val recordatorioActivo: Boolean,
    val tieneDocumento: Boolean,
    val versionFila: Int
)

data class SoatVehiculoResumenDto(
    val idVehiculo: Long,
    val placa: String? = null,
    val marca: String? = null,
    val modelo: String? = null
)

data class SoatConsultaResponse(
    val actual: SoatDto? = null,
    val historial: List<SoatDto> = emptyList(),
    val vehiculo: SoatVehiculoResumenDto? = null
)

data class SoatDocumentoRefDto(
    val objectKey: String,
    val contentType: String? = null,
    val nombreArchivo: String? = null
)

data class SoatMutacionDto(
    val idOperacion: String,
    val aseguradora: String,
    val numeroPoliza: String,
    val fechaInicio: String,
    val fechaVencimiento: String,
    val fechaEmision: String? = null,
    val nombreContratante: String? = null,
    val importePrima: Double? = null,
    val documento: SoatDocumentoRefDto? = null,
    val eliminarDocumento: Boolean? = null,
    val versionFila: Int? = null
)

data class SoatRecordatorioDto(val activo: Boolean)

data class SoatPresignSolicitudDto(
    val nombreArchivo: String,
    val contentType: String,
    val tamanoBytes: Long
)

data class SoatPresignRespuestaDto(
    val uploadUrl: String,
    val objectKey: String,
    val headers: Map<String, String>? = null
)

data class SoatDocumentoDescargaDto(val downloadUrl: String)