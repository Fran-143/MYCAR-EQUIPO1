package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.*
import java.io.IOException

internal object DatosPruebaRf08 {
    val catalogos = CatalogosRegistroDto(
        listOf(UsoRegistroDto("PARTICULAR", "Particular")),
        listOf(MotorRegistroDto(1, "COMBUSTION", "Combustión", false), MotorRegistroDto(2, "ELECTRICO", "Eléctrico", true), MotorRegistroDto(3, "OTRO", "Otro", false)),
        listOf(CombustibleRegistroDto(1, "GASOLINA", "Gasolina"), CombustibleRegistroDto(2, "OTRO", "Otro")),
        listOf(AceiteRegistroDto(1, "MINERAL", "Mineral"), AceiteRegistroDto(2, "OTRO", "Otro")))
    val formulario = FormularioRegistroVehiculo(marca = "Toyota", modelo = "Corolla", usoVehiculoCodigo = "PARTICULAR",
        placa = "A1B234", anioFabricacion = "2020", color = "Azul", idTipoMotor = "1", idCombustible = "1", kilometrajeInicial = "50000")
    fun solicitud(id: String = "1c6d2c42-2ddf-4f44-b540-3b83a8519571") = ValidadorFormularioRegistro.solicitud(formulario, catalogos, id)
    fun resultado(s: SolicitudRegistroVehiculoDto) = ResultadoRegistroVehiculoDto(s.idOperacion, 81, s.placa, true,
        s.kilometrajeInicial, 300, "2026-10-27T12:00:00Z", 0)
}

internal class PendientesPrueba(var actual: SolicitudRegistroVehiculoDto? = null) : SolicitudRegistroPendienteStore {
    var fallarGuardar = false
    var fallarEliminar = false
    override fun cargar() = actual
    override fun guardar(solicitud: SolicitudRegistroVehiculoDto) {
        if (fallarGuardar) throw IOException("Fallo de prueba")
        check(actual == null || actual == solicitud)
        actual = solicitud
    }
    override fun eliminar(idOperacion: String) {
        if (fallarEliminar) throw IOException("Fallo de prueba")
        check(actual == null || actual?.idOperacion == idOperacion)
        actual = null
    }
}

internal class RepositoryPrueba : RegistroVehiculoRepository {
    var consultasCupo = 0
    var consultasCatalogos = 0
    val envios = mutableListOf<SolicitudRegistroVehiculoDto>()
    var cupoValor = CupoRegistroDto("FREE", 1, 0, true)
    var errorCupo: Exception? = null
    var alEnviar: suspend (SolicitudRegistroVehiculoDto) -> ResultadoRegistroVehiculoDto = { DatosPruebaRf08.resultado(it) }
    var alConsultar: suspend (String) -> EstadoOperacionRegistroDto = { throw FalloRegistroVehiculo("OPERACION_NO_ENCONTRADA", 404) }
    override suspend fun catalogos(): CatalogosRegistroDto { consultasCatalogos++; return DatosPruebaRf08.catalogos }
    override suspend fun cupo(): CupoRegistroDto { consultasCupo++; errorCupo?.let { throw it }; return cupoValor }
    override suspend fun registrar(solicitud: SolicitudRegistroVehiculoDto): ResultadoRegistroVehiculoDto { envios += solicitud; return alEnviar(solicitud) }
    override suspend fun operacion(idOperacion: String) = alConsultar(idOperacion)
}
