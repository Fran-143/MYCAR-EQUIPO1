package com.mycar.app.features.vehiculos.data

data class UsoRegistroDto(val codigo: String, val nombre: String)
data class MotorRegistroDto(val idTipoMotor: Long, val codigo: String, val nombre: String, val esElectrico: Boolean)
data class CombustibleRegistroDto(val idCombustible: Long, val codigo: String, val nombre: String)
data class AceiteRegistroDto(val idTipoAceite: Long, val codigo: String, val nombre: String)
data class CatalogosRegistroDto(
    val usos: List<UsoRegistroDto>,
    val tiposMotor: List<MotorRegistroDto>,
    val combustibles: List<CombustibleRegistroDto>,
    val tiposAceite: List<AceiteRegistroDto>
)
data class CupoRegistroDto(val plan: String, val limite: Int, val registrados: Long, val puedeRegistrar: Boolean)
data class SolicitudRegistroVehiculoDto(
    val idOperacion: String,
    val marca: String,
    val modelo: String,
    val usoVehiculoCodigo: String,
    val placa: String,
    val anioFabricacion: Int,
    val color: String,
    val idTipoMotor: Long,
    val idCombustible: Long?,
    val kilometrajeInicial: Int,
    val numeroVin: String?,
    val numeroSerie: String?,
    val numeroMotor: String?,
    val idTipoAceite: Long?,
    val tipoMotorOtro: String?,
    val combustibleOtro: String?,
    val tipoAceiteOtro: String?
)
data class ResultadoRegistroVehiculoDto(
    val idOperacion: String,
    val idVehiculo: Long,
    val placa: String,
    val principal: Boolean,
    val kilometrajeActual: Int,
    val idLecturaKm: Long,
    val fechaLectura: String,
    val versionKm: Int
)
data class EstadoOperacionRegistroDto(
    val idOperacion: String,
    val estado: String,
    val resultado: ResultadoRegistroVehiculoDto?
)
