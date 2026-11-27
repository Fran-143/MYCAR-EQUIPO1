package com.mycar.app.features.soat.data

data class SoatDocumentoRefDto(
    val objectKey: String,
    val contentType: String? = null,
    val tamanoBytes: Int? = null,
    val nombreOriginal: String? = null,
    /** Backend Jackson: "cifrado". Multipart siempre cifra el archivo. */
    val cifrado: Boolean? = null
)

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
    val estadoCobertura: String? = null,
    val recordatorioActivo: Boolean = false,
    val tieneDocumento: Boolean = false,
    val versionFila: Int = 0,
    val documento: SoatDocumentoRefDto? = null
) {
    val conDocumento: Boolean get() = tieneDocumento || documento?.objectKey?.isNotBlank() == true
}

data class SoatRecordatorioEstadoDto(
    val activo: Boolean = false,
    val idAlerta: Long? = null,
    val estadoAlerta: String? = null
)

data class AseguradoraSoatDto(
    val id: Long = 0,
    val codigo: String? = null,
    val nombre: String? = null,
    val permiteNombreLibre: Boolean = false,
    val ayudaPoliza: String? = null,
    val ejemploPoliza: String? = null
)

data class AseguradorasSoatListaDto(
    val items: List<AseguradoraSoatDto> = emptyList(),
    val total: Int = 0
)

data class SoatConsultaResponse(
    val actual: SoatDto? = null,
    val historial: List<SoatDto> = emptyList(),
    val estadoCobertura: String? = null,
    val recordatorio: SoatRecordatorioEstadoDto? = null
)

data class SoatMutacionDto(
    val idOperacion: String? = null,
    val idSoatAnterior: Long? = null,
    val aseguradora: String,
    val numeroPoliza: String,
    val fechaInicio: String,
    val fechaVencimiento: String,
    val fechaEmision: String? = null,
    val nombreContratante: String? = null,
    val importePrima: Double? = null,
    val documento: SoatDocumentoRefDto? = null,
    val accionDocumento: String? = null
)

data class SoatRegistrarRespuestaDto(
    val idOperacion: String? = null,
    val idSoat: Long = 0,
    val estadoCobertura: String? = null
)

data class SoatCorregirRespuestaDto(
    val idSoat: Long = 0,
    val versionFila: Int = 0,
    val estadoCobertura: String? = null
)

data class SoatRenovarRespuestaDto(
    val idOperacion: String? = null,
    val idSoatAnterior: Long = 0,
    val idSoatNuevo: Long = 0,
    val estadoCobertura: String? = null
)

data class SoatRecordatorioDto(val activo: Boolean)

data class SoatRecordatorioRespuestaDto(
    val idSoat: Long = 0,
    val recordatorioActivo: Boolean = false,
    val idAlerta: Long? = null,
    val estadoAlerta: String? = null
)

data class SoatPresignSolicitudDto(
    val contentType: String,
    val tamanoBytes: Int,
    val nombreOriginal: String
)

data class SoatPresignRespuestaDto(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: String? = null,
    val headers: Map<String, String>? = null
)

data class SoatDocumentoDescargaDto(
    val downloadUrl: String,
    val expiresAt: String? = null,
    val nombreOriginal: String? = null
)
