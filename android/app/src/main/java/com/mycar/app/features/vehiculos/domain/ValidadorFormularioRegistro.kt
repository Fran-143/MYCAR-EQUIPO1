package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.*
import java.time.Year
import java.time.ZoneId

object ValidadorFormularioRegistro {
    private val placa = Regex("[A-Z][0-9][A-Z][0-9]{3}")
    private val vin = Regex("[A-HJ-NPR-Z0-9]{17}")
    private val motor = Regex("[A-Z0-9 -]{5,15}")
    private val serie = Regex("[A-Z0-9]{6,80}")
    private val entero = Regex("[0-9]+")

    fun validar(f: FormularioRegistroVehiculo, c: CatalogosRegistroDto,
                anioActual: Int = Year.now(ZoneId.of("America/Lima")).value): Map<String, String> {
        val errores = linkedMapOf<String, String>()
        fun texto(campo: String, valor: String, maximo: Int) {
            if (valor.isBlank() || valor.codePointCount(0, valor.length) > maximo)
                errores[campo] = "Completa este campo (máximo $maximo caracteres)."
        }
        texto("marca", f.marca, 100); texto("modelo", f.modelo, 100); texto("color", f.color, 80)
        if (!placa.matches(f.placa)) errores["placa"] = "Usa el formato A1B234, sin espacios ni guiones."
        if (!entero.matches(f.anioFabricacion) || f.anioFabricacion.toIntOrNull()?.let { it > 0 && it <= anioActual } != true)
            errores["anioFabricacion"] = "Ingresa un año positivo que no sea futuro."
        if (!entero.matches(f.kilometrajeInicial) || f.kilometrajeInicial.toIntOrNull()?.let { it > 0 } != true)
            errores["kilometrajeInicial"] = "Ingresa un kilometraje entero mayor que cero."
        if (c.usos.none { it.codigo == f.usoVehiculoCodigo }) errores["usoVehiculoCodigo"] = "Selecciona el uso."
        val tipo = c.tiposMotor.find { it.idTipoMotor.toString() == f.idTipoMotor }
        if (tipo == null) errores["idTipoMotor"] = "Selecciona el tipo de motor."
        if (tipo?.codigo == "OTRO") texto("tipoMotorOtro", f.tipoMotorOtro, 100)
        if (tipo != null && !tipo.esElectrico) {
            val combustible = c.combustibles.find { it.idCombustible.toString() == f.idCombustible }
            if (combustible == null) errores["idCombustible"] = "Selecciona el combustible."
            if (combustible?.codigo == "OTRO") texto("combustibleOtro", f.combustibleOtro, 100)
            if (f.idTipoAceite.isNotEmpty()) {
                val aceite = c.tiposAceite.find { it.idTipoAceite.toString() == f.idTipoAceite }
                if (aceite == null) errores["idTipoAceite"] = "Selecciona un aceite válido."
                if (aceite?.codigo == "OTRO") texto("tipoAceiteOtro", f.tipoAceiteOtro, 100)
            }
            if (f.numeroMotor.isNotEmpty() && (f.numeroMotor.isBlank() || !motor.matches(f.numeroMotor)))
                errores["numeroMotor"] = "Usa de 5 a 15 letras mayúsculas, números, guiones o espacios."
        }
        if (f.numeroVin.isNotEmpty() && !vin.matches(f.numeroVin))
            errores["numeroVin"] = "Usa 17 letras mayúsculas o números, sin I, O ni Q."
        if (f.numeroSerie.isNotEmpty() && !serie.matches(f.numeroSerie))
            errores["numeroSerie"] = "Usa de 6 a 80 letras mayúsculas o números, sin espacios."
        return errores
    }

    /** Invocar solamente después de validar. No cambia mayúsculas ni recorta datos. */
    fun solicitud(f: FormularioRegistroVehiculo, c: CatalogosRegistroDto, idOperacion: String): SolicitudRegistroVehiculoDto {
        val tipo = c.tiposMotor.first { it.idTipoMotor.toString() == f.idTipoMotor }
        val combustible = c.combustibles.find { it.idCombustible.toString() == f.idCombustible }
        val aceite = c.tiposAceite.find { it.idTipoAceite.toString() == f.idTipoAceite }
        return SolicitudRegistroVehiculoDto(
            idOperacion, f.marca, f.modelo, f.usoVehiculoCodigo, f.placa, f.anioFabricacion.toInt(),
            f.color, tipo.idTipoMotor, if (tipo.esElectrico) null else combustible?.idCombustible,
            f.kilometrajeInicial.toInt(), f.numeroVin.takeIf { it.isNotEmpty() },
            f.numeroSerie.takeIf { it.isNotEmpty() },
            if (tipo.esElectrico) null else f.numeroMotor.takeIf { it.isNotEmpty() },
            if (tipo.esElectrico) null else aceite?.idTipoAceite,
            if (tipo.codigo == "OTRO") f.tipoMotorOtro else null,
            if (!tipo.esElectrico && combustible?.codigo == "OTRO") f.combustibleOtro else null,
            if (!tipo.esElectrico && aceite?.codigo == "OTRO") f.tipoAceiteOtro else null
        )
    }
}
