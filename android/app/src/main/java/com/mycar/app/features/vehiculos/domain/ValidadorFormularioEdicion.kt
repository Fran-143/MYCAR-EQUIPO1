package com.mycar.app.features.vehiculos.domain

import com.mycar.app.features.vehiculos.data.CatalogosRegistroDto
import com.mycar.app.features.vehiculos.data.SolicitudActualizacionVehiculoDto
import java.time.Year
import java.time.ZoneId

object ValidadorFormularioEdicion {
    private val vin = Regex("[A-HJ-NPR-Z0-9]{17}")
    private val motor = Regex("[A-Z0-9 -]{5,15}")
    private val serie = Regex("[A-Z0-9]{6,80}")
    private val entero = Regex("[0-9]+")

    fun validar(
        f: FormularioRegistroVehiculo,
        c: CatalogosRegistroDto,
        tieneTive: Boolean,
        anioActual: Int = Year.now(ZoneId.of("America/Lima")).value
    ): Map<String, String> {
        val errores = linkedMapOf<String, String>()
        fun texto(campo: String, valor: String, maximo: Int) {
            if (valor.isBlank() || valor.codePointCount(0, valor.length) > maximo)
                errores[campo] = "Completa este campo (máximo $maximo caracteres)."
        }
        texto("marca", f.marca, 100)
        texto("modelo", f.modelo, 100)
        texto("color", f.color, 80)
        if (!entero.matches(f.anioFabricacion) || f.anioFabricacion.toIntOrNull()?.let { it > 0 && it <= anioActual } != true)
            errores["anioFabricacion"] = "Ingresa un año positivo que no sea futuro."
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
            if (!tieneTive && f.numeroMotor.isNotEmpty() && !motor.matches(f.numeroMotor))
                errores["numeroMotor"] = "Usa de 5 a 15 letras mayúsculas, números, guiones o espacios."
        }
        if (!tieneTive && f.numeroVin.isNotEmpty() && !vin.matches(f.numeroVin))
            errores["numeroVin"] = "Usa 17 letras mayúsculas o números, sin I, O ni Q."
        if (!tieneTive && f.numeroSerie.isNotEmpty() && !serie.matches(f.numeroSerie))
            errores["numeroSerie"] = "Usa de 6 a 80 letras mayúsculas o números, sin espacios."
        return errores
    }

    fun solicitud(f: FormularioRegistroVehiculo, c: CatalogosRegistroDto, tieneTive: Boolean): SolicitudActualizacionVehiculoDto {
        val tipo = c.tiposMotor.first { it.idTipoMotor.toString() == f.idTipoMotor }
        val combustible = c.combustibles.find { it.idCombustible.toString() == f.idCombustible }
        val aceite = c.tiposAceite.find { it.idTipoAceite.toString() == f.idTipoAceite }
        return SolicitudActualizacionVehiculoDto(
            marca = f.marca,
            modelo = f.modelo,
            usoVehiculoCodigo = f.usoVehiculoCodigo,
            anioFabricacion = f.anioFabricacion.toInt(),
            color = f.color,
            idTipoMotor = tipo.idTipoMotor,
            idCombustible = if (tipo.esElectrico) null else combustible?.idCombustible,
            numeroVin = f.numeroVin.takeIf { it.isNotEmpty() },
            numeroSerie = f.numeroSerie.takeIf { it.isNotEmpty() },
            numeroMotor = if (tipo.esElectrico) null else f.numeroMotor.takeIf { it.isNotEmpty() },
            idTipoAceite = if (tipo.esElectrico) null else aceite?.idTipoAceite,
            tipoMotorOtro = if (tipo.codigo == "OTRO") f.tipoMotorOtro else null,
            combustibleOtro = if (!tipo.esElectrico && combustible?.codigo == "OTRO") f.combustibleOtro else null,
            tipoAceiteOtro = if (!tipo.esElectrico && aceite?.codigo == "OTRO") f.tipoAceiteOtro else null
        )
    }

    fun desdeDetalle(
        d: com.mycar.app.features.vehiculos.data.DetalleVehiculoDto,
        catalogos: CatalogosRegistroDto? = null
    ): FormularioRegistroVehiculo {
        val uso = d.usoVehiculoCodigo
            ?: catalogos?.usos?.find { it.nombre.equals(d.usoVehiculo, ignoreCase = true) }?.codigo
            .orEmpty()
        val idMotor = d.idTipoMotor?.toString()
            ?: catalogos?.tiposMotor?.find {
                it.nombre.equals(d.tipoMotor, ignoreCase = true) ||
                    (it.codigo == "OTRO" && !d.tipoMotorOtro.isNullOrBlank())
            }?.idTipoMotor?.toString()
            .orEmpty()
        val idComb = d.idCombustible?.toString()
            ?: catalogos?.combustibles?.find { it.nombre.equals(d.combustible.orEmpty(), ignoreCase = true) }
                ?.idCombustible?.toString()
            .orEmpty()
        val idAceite = d.idTipoAceite?.toString()
            ?: catalogos?.tiposAceite?.find { it.nombre.equals(d.tipoAceite.orEmpty(), ignoreCase = true) }
                ?.idTipoAceite?.toString()
            .orEmpty()
        return FormularioRegistroVehiculo(
            marca = d.marca.uppercase(),
            modelo = d.modelo.uppercase(),
            usoVehiculoCodigo = uso,
            placa = d.placa.uppercase(),
            anioFabricacion = d.anioFabricacion.toString(),
            color = d.color.uppercase(),
            idTipoMotor = idMotor,
            idCombustible = idComb,
            kilometrajeInicial = d.kilometrajeActual?.toString().orEmpty(),
            numeroVin = d.numeroVin.orEmpty().uppercase(),
            numeroSerie = d.numeroSerie.orEmpty().uppercase(),
            numeroMotor = d.numeroMotor.orEmpty().uppercase(),
            idTipoAceite = idAceite,
            tipoMotorOtro = d.tipoMotorOtro.orEmpty().uppercase(),
            combustibleOtro = d.combustibleOtro.orEmpty().uppercase(),
            tipoAceiteOtro = d.tipoAceiteOtro.orEmpty().uppercase()
        )
    }
}
