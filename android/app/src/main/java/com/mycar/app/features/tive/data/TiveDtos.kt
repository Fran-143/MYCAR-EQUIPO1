package com.mycar.app.features.tive.data

data class TiveDocumentoRefDto(
    val objectKey: String,
    val contentType: String? = null,
    val tamanoBytes: Int? = null,
    val nombreOriginal: String? = null
)

data class TiveDto(
    val idTive: Long,
    val numeroTitulo: String,
    val codigoVerificacion: String,
    val fechaTitulo: String,
    val zonaRegistral: String? = null,
    val sedeRegistral: String? = null,
    val numeroVin: String,
    val placa: String,
    val numeroMotor: String,
    val numeroSerie: String,
    val tieneDocumento: Boolean = false,
    val versionFila: Int = 0,
    val documento: TiveDocumentoRefDto? = null
) {
    val conDocumento: Boolean get() = tieneDocumento || documento?.objectKey?.isNotBlank() == true
}

data class TiveConsultaResponse(
    val tive: TiveDto? = null,
    val mensajeDocumento: String? = null,
    val marca: String? = null,
    val modelo: String? = null,
    val placa: String? = null,
    val numeroVin: String? = null,
    val anioFabricacion: Int? = null,
    val color: String? = null
)

data class TivePrecheckResponse(
    val puedeRegistrar: Boolean = false,
    val tieneVin: Boolean = false,
    val tieneTive: Boolean = false,
    val placa: String? = null,
    val numeroVin: String? = null,
    val numeroMotor: String? = null,
    val numeroSerie: String? = null,
    val codigoBloqueo: String? = null,
    val mensaje: String? = null
)

data class TiveRegistrarRequest(
    val idOperacion: String,
    val numeroTitulo: String,
    val codigoVerificacion: String,
    val fechaTitulo: String,
    val zonaRegistral: String? = null,
    val sedeRegistral: String? = null,
    val numeroMotor: String,
    val numeroSerie: String,
    val documento: TiveDocumentoRefDto? = null
)

data class TiveRegistrarResponse(
    val idOperacion: String? = null,
    val idTive: Long = 0
)

data class TiveActualizarRequest(
    val versionFila: Int,
    val numeroTitulo: String,
    val codigoVerificacion: String,
    val fechaTitulo: String,
    val zonaRegistral: String? = null,
    val sedeRegistral: String? = null,
    val numeroMotor: String,
    val numeroSerie: String,
    val accionDocumento: String,
    val documento: TiveDocumentoRefDto? = null
)

data class TiveActualizarResponse(
    val idTive: Long = 0,
    val versionFila: Int = 0
)