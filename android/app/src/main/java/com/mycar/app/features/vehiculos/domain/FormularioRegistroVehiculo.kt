package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.*

data class FormularioRegistroVehiculo(
    val marca: String = "",
    val modelo: String = "",
    val usoVehiculoCodigo: String = "",
    val placa: String = "",
    val anioFabricacion: String = "",
    val color: String = "",
    val idTipoMotor: String = "",
    val idCombustible: String = "",
    val kilometrajeInicial: String = "",
    val numeroVin: String = "",
    val numeroSerie: String = "",
    val numeroMotor: String = "",
    val idTipoAceite: String = "",
    val tipoMotorOtro: String = "",
    val combustibleOtro: String = "",
    val tipoAceiteOtro: String = ""
) {
    fun cambiar(campo: String, valor: String, catalogos: CatalogosRegistroDto?): FormularioRegistroVehiculo = when (campo) {
        "marca" -> copy(marca = valor)
        "modelo" -> copy(modelo = valor)
        "usoVehiculoCodigo" -> copy(usoVehiculoCodigo = valor)
        "placa" -> copy(placa = valor)
        "anioFabricacion" -> copy(anioFabricacion = valor)
        "color" -> copy(color = valor)
        "kilometrajeInicial" -> copy(kilometrajeInicial = valor)
        "numeroVin" -> copy(numeroVin = valor)
        "numeroSerie" -> copy(numeroSerie = valor)
        "numeroMotor" -> copy(numeroMotor = valor)
        "tipoMotorOtro" -> copy(tipoMotorOtro = valor)
        "combustibleOtro" -> copy(combustibleOtro = valor)
        "tipoAceiteOtro" -> copy(tipoAceiteOtro = valor)
        "idTipoMotor" -> {
            val motor = catalogos?.tiposMotor?.find { it.idTipoMotor.toString() == valor }
            copy(
                idTipoMotor = valor,
                tipoMotorOtro = if (motor?.codigo == "OTRO") tipoMotorOtro else "",
                idCombustible = if (motor?.esElectrico == true) "" else idCombustible,
                combustibleOtro = if (motor?.esElectrico == true) "" else combustibleOtro,
                idTipoAceite = if (motor?.esElectrico == true) "" else idTipoAceite,
                tipoAceiteOtro = if (motor?.esElectrico == true) "" else tipoAceiteOtro,
                numeroMotor = if (motor?.esElectrico == true) "" else numeroMotor
            )
        }
        "idCombustible" -> copy(idCombustible = valor, combustibleOtro =
            if (catalogos?.combustibles?.find { it.idCombustible.toString() == valor }?.codigo == "OTRO") combustibleOtro else "")
        "idTipoAceite" -> copy(idTipoAceite = valor, tipoAceiteOtro =
            if (catalogos?.tiposAceite?.find { it.idTipoAceite.toString() == valor }?.codigo == "OTRO") tipoAceiteOtro else "")
        else -> this
    }

    fun camposAfectados(campo: String): Set<String> = when (campo) {
        "idTipoMotor" -> setOf(campo, "tipoMotorOtro", "idCombustible", "combustibleOtro", "idTipoAceite", "tipoAceiteOtro", "numeroMotor")
        "idCombustible" -> setOf(campo, "combustibleOtro")
        "idTipoAceite" -> setOf(campo, "tipoAceiteOtro")
        else -> setOf(campo)
    }

    companion object {
        fun desde(s: SolicitudRegistroVehiculoDto) = FormularioRegistroVehiculo(
            s.marca, s.modelo, s.usoVehiculoCodigo, s.placa, s.anioFabricacion.toString(), s.color,
            s.idTipoMotor.toString(), s.idCombustible?.toString().orEmpty(), s.kilometrajeInicial.toString(),
            s.numeroVin.orEmpty(), s.numeroSerie.orEmpty(), s.numeroMotor.orEmpty(),
            s.idTipoAceite?.toString().orEmpty(), s.tipoMotorOtro.orEmpty(),
            s.combustibleOtro.orEmpty(), s.tipoAceiteOtro.orEmpty()
        )
    }
}
