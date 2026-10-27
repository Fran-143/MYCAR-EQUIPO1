package com.mycar.app.features.vehiculos

import com.mycar.app.features.vehiculos.domain.*
import org.junit.Assert.*
import org.junit.Test

class ValidadorFormularioRegistroTest {
    private val c = DatosPruebaRf08.catalogos
    private val f = DatosPruebaRf08.formulario
    private fun errores(form: FormularioRegistroVehiculo) = ValidadorFormularioRegistro.validar(form, c, 2026)

    @Test fun datosValidosNoTienenErrores() { assertTrue(errores(f).isEmpty()) }
    @Test fun obligatoriosVaciosSeSenalan() {
        val campos = errores(FormularioRegistroVehiculo()).keys
        assertTrue(campos.containsAll(listOf("marca", "modelo", "usoVehiculoCodigo", "placa", "anioFabricacion", "color", "idTipoMotor", "kilometrajeInicial")))
    }
    @Test fun placaEsEstricta() {
        listOf("a1B234", "A1B-234", " A1B234", "A1B234 ", "ABC123").forEach { assertTrue(errores(f.copy(placa = it)).containsKey("placa")) }
    }
    @Test fun anioPositivoSinLimiteInferiorInventado() { assertFalse(errores(f.copy(anioFabricacion = "1")).containsKey("anioFabricacion")) }
    @Test fun anioNoPositivoFuturoOTextoSeRechaza() {
        listOf("0", "-1", "2027", "dos mil", "2.5", "2147483648").forEach { assertTrue(errores(f.copy(anioFabricacion = it)).containsKey("anioFabricacion")) }
    }
    @Test fun kilometrajeDebeSerEnteroPositivoConCapacidadInt() {
        listOf("", "0", "-1", "1.5", "2147483648", " 10").forEach { assertTrue(errores(f.copy(kilometrajeInicial = it)).containsKey("kilometrajeInicial")) }
        assertFalse(errores(f.copy(kilometrajeInicial = "2147483647")).containsKey("kilometrajeInicial"))
    }
    @Test fun opcionalesVaciosViajanComoNull() {
        val s = ValidadorFormularioRegistro.solicitud(f, c, "operacion")
        assertNull(s.numeroVin); assertNull(s.numeroSerie); assertNull(s.numeroMotor); assertNull(s.idTipoAceite)
    }
    @Test fun vinValidoTiene17Caracteres() { assertFalse(errores(f.copy(numeroVin = "1HGCM82633A004352")).containsKey("numeroVin")) }
    @Test fun vinRechazaIoyQMinusculasEspaciosYLongitudIncorrecta() {
        listOf("1HGCM82633A00435I", "1HGCM82633A00435O", "1HGCM82633A00435Q", "1hgcm82633a004352", "VIN", "                 ").forEach {
            assertTrue(errores(f.copy(numeroVin = it)).containsKey("numeroVin"))
        }
    }
    @Test fun motorAdmiteGuionYEspacioNormal() { assertFalse(errores(f.copy(numeroMotor = "AB-12 34")).containsKey("numeroMotor")) }
    @Test fun motorRechazaLimitesCaracteresYSoloEspacios() {
        listOf("AB12", "A".repeat(16), "motor123", "AB/123", "     ", "AB\t123").forEach { assertTrue(errores(f.copy(numeroMotor = it)).containsKey("numeroMotor")) }
    }
    @Test fun serieEsAlfanumericaEntre6Y80() {
        assertFalse(errores(f.copy(numeroSerie = "A".repeat(80))).containsKey("numeroSerie"))
        listOf("ABCDE", "A".repeat(81), "ABC-123", "abc123", "ABC 123").forEach { assertTrue(errores(f.copy(numeroSerie = it)).containsKey("numeroSerie")) }
    }
    @Test fun motorOtroExigeTexto() {
        assertTrue(errores(f.copy(idTipoMotor = "3")).containsKey("tipoMotorOtro"))
        assertFalse(errores(f.copy(idTipoMotor = "3", tipoMotorOtro = "Motor informado")).containsKey("tipoMotorOtro"))
    }
    @Test fun combustibleYAceiteOtroExigenTexto() {
        val e = errores(f.copy(idCombustible = "2", idTipoAceite = "2"))
        assertTrue(e.containsKey("combustibleOtro")); assertTrue(e.containsKey("tipoAceiteOtro"))
    }
    @Test fun electricoLimpiaCamposNoAplicables() {
        val electrico = f.copy(numeroMotor = "MOTOR123", idTipoAceite = "2", tipoAceiteOtro = "Aceite", combustibleOtro = "Combustible")
            .cambiar("idTipoMotor", "2", c)
        assertEquals("", electrico.idCombustible); assertEquals("", electrico.idTipoAceite); assertEquals("", electrico.numeroMotor)
        assertEquals("", electrico.combustibleOtro); assertEquals("", electrico.tipoAceiteOtro)
        assertTrue(errores(electrico).isEmpty())
        val s = ValidadorFormularioRegistro.solicitud(electrico, c, "op")
        assertNull(s.idCombustible); assertNull(s.idTipoAceite); assertNull(s.numeroMotor)
    }
    @Test fun cambiarDesdeOtroEliminaSuTextoAnterior() {
        assertEquals("", f.copy(idCombustible = "2", combustibleOtro = "Especial").cambiar("idCombustible", "1", c).combustibleOtro)
        assertEquals("", f.copy(idTipoAceite = "2", tipoAceiteOtro = "Especial").cambiar("idTipoAceite", "", c).tipoAceiteOtro)
    }
    @Test fun catalogoNoAdmiteIdentificadoresInventados() {
        val e = errores(f.copy(idTipoMotor = "999", usoVehiculoCodigo = "INVENTADO"))
        assertTrue(e.containsKey("idTipoMotor")); assertTrue(e.containsKey("usoVehiculoCodigo"))
    }
    @Test fun textoCuentaCaracteresUnicodeComoPostgres() {
        assertFalse(errores(f.copy(marca = "🚗".repeat(100))).containsKey("marca"))
        assertTrue(errores(f.copy(marca = "🚗".repeat(101))).containsKey("marca"))
    }
}
