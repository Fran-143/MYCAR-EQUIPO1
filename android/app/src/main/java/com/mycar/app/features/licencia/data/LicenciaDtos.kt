package com.mycar.app.features.licencia.data

data class LicenciaFotoDto(
    val objectKey: String,
    val contentType: String? = null,
    val tamanoBytes: Int? = null,
    val nombreOriginal: String? = null,
    val cifrado: Boolean? = null
)

data class LicenciaDto(
    val idLicencia: Long = 0,
    val categoria: String = "",
    val numero: String = "",
    val fechaEmision: String = "",
    val fechaVencimiento: String = "",
    val vigencia: String = "",
    val estadoVigencia: String? = null,
    val recordatorioActivo: Boolean = false,
    val tieneFotoFrontal: Boolean = false,
    val tieneFotoPosterior: Boolean = false,
    val versionFila: Int = 0
)

data class LicenciaHistorialResumenDto(
    val idLicencia: Long = 0,
    val categoria: String = "",
    val numero: String = "",
    val fechaEmision: String = "",
    val fechaVencimiento: String = "",
    val etiqueta: String = ""
)

data class LicenciaRecordatorioDto(
    val activo: Boolean = false,
    val idAlerta: Long? = null,
    val estadoAlerta: String? = null
)

data class LicenciaConsultaResponse(
    val actual: LicenciaDto? = null,
    val tieneHistorial: Boolean = false,
    val historial: List<LicenciaHistorialResumenDto> = emptyList(),
    val recordatorio: LicenciaRecordatorioDto? = null
)

data class LicenciaRegistrarRequest(
    val idOperacion: String,
    val categoria: String,
    val numero: String,
    val fechaEmision: String,
    val fechaVencimiento: String,
    val fotoFrontal: LicenciaFotoDto,
    val fotoPosterior: LicenciaFotoDto
)

data class LicenciaEditarRequest(
    val categoria: String,
    val numero: String,
    val fechaEmision: String,
    val fechaVencimiento: String,
    val accionFotoFrontal: String = "MANTENER",
    val accionFotoPosterior: String = "MANTENER",
    val fotoFrontal: LicenciaFotoDto? = null,
    val fotoPosterior: LicenciaFotoDto? = null
)

data class LicenciaRenovarRequest(
    val idOperacion: String,
    val idLicenciaAnterior: Long,
    val categoria: String,
    val numero: String,
    val fechaEmision: String,
    val fechaVencimiento: String,
    val fotoFrontal: LicenciaFotoDto? = null,
    val fotoPosterior: LicenciaFotoDto? = null
)

data class LicenciaRegistrarResponse(val idOperacion: String? = null, val idLicencia: Long = 0, val estadoVigencia: String? = null)
data class LicenciaEditarResponse(val idLicencia: Long = 0, val versionFila: Int = 0, val estadoVigencia: String? = null)
data class LicenciaRenovarResponse(
    val idOperacion: String? = null,
    val idLicenciaAnterior: Long = 0,
    val idLicenciaNueva: Long = 0,
    val estadoVigencia: String? = null
)
data class LicenciaRecordatorioRequest(val activo: Boolean)
data class LicenciaRecordatorioResponse(
    val idLicencia: Long = 0,
    val recordatorioActivo: Boolean = false,
    val idAlerta: Long? = null,
    val estadoAlerta: String? = null
)
