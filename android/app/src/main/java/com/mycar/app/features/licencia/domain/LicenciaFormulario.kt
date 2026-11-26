package com.mycar.app.features.licencia.domain

import com.mycar.app.features.soat.domain.SoatFormulario
import java.time.LocalDate
import java.time.ZoneId

object LicenciaFormulario {
    val ZONA_LIMA: ZoneId = ZoneId.of("America/Lima")
    fun hoyLima(): LocalDate = LocalDate.now(ZONA_LIMA)

    const val MSG_NUMERO =
        "El número de licencia debe contener una letra seguida de 8 números, sin guiones. Ejemplo: Q12345678."
    const val PLACEHOLDER_NUMERO = "Ej. Q12345678"
    const val MSG_VENCIMIENTO_RANGO =
        "La fecha de vencimiento debe estar entre 6 meses y 10 años después de la emisión."
    const val MSG_EMISION_FUTURA = "La fecha de emisión no puede ser posterior a hoy"
    const val MSG_EMISION_ANTIGUA =
        "La fecha de emisión no puede ser anterior al año actual menos 10 años."
    const val MSG_CATEGORIA = "Selecciona categoría A-I o A-IIa."

    val CATEGORIAS = listOf("A-I", "A-IIa")

    fun normalizarNumero(raw: String): String = raw.trim().uppercase()

    fun validarNumero(raw: String): String? {
        val n = normalizarNumero(raw)
        if (n.isEmpty()) return "Indica el número de licencia."
        if (!n.matches(Regex("^[A-Z][0-9]{8}$"))) return MSG_NUMERO
        return null
    }

    fun limitarNumero(raw: String): String {
        val up = raw.uppercase()
        val out = StringBuilder()
        for (ch in up) {
            if (out.isEmpty()) {
                if (ch in 'A'..'Z') out.append(ch)
            } else if (out.length < 9 && ch.isDigit()) {
                out.append(ch)
            }
        }
        return out.toString()
    }

    fun enmascararNumero(numero: String?): String {
        val n = numero?.trim().orEmpty()
        if (n.isEmpty()) return ""
        if (n.length <= 4) return "****"
        return "****" + n.takeLast(4)
    }

    fun validarCampos(
        categoria: String,
        numero: String,
        fechaEmision: String,
        fechaVencimiento: String,
        hoy: LocalDate = hoyLima()
    ): Map<String, String> {
        val err = mutableMapOf<String, String>()
        if (categoria !in CATEGORIAS) err["categoria"] = MSG_CATEGORIA
        validarNumero(numero)?.let { err["numero"] = it }
        val em = SoatFormulario.parsearFecha(fechaEmision)
        val ve = SoatFormulario.parsearFecha(fechaVencimiento)
        if (fechaEmision.isBlank()) err["fechaEmision"] = "Indica la fecha de emisión."
        else if (em == null) err["fechaEmision"] = SoatFormulario.MSG_FECHA_INVALIDA
        if (fechaVencimiento.isBlank()) err["fechaVencimiento"] = "Indica la fecha de vencimiento."
        else if (ve == null) err["fechaVencimiento"] = SoatFormulario.MSG_FECHA_INVALIDA
        if (em != null && em.isAfter(hoy)) err["fechaEmision"] = MSG_EMISION_FUTURA
        if (em != null) {
            val emisionMinima = LocalDate.of(hoy.year - 10, 1, 1)
            if (em.isBefore(emisionMinima)) err["fechaEmision"] = MSG_EMISION_ANTIGUA
        }
        if (em != null && ve != null) {
            if (!ve.isAfter(em)) {
                err["fechaVencimiento"] = "La fecha de vencimiento debe ser posterior a la emisión."
            } else {
                val min = em.plusMonths(6)
                val max = em.plusYears(10)
                if (ve.isBefore(min) || ve.isAfter(max)) err["fechaVencimiento"] = MSG_VENCIMIENTO_RANGO
            }
        }
        return err
    }

    fun validarRenovacionVencimiento(
        fechaVencimientoNueva: String,
        fechaVencimientoAnterior: String
    ): String? {
        val n = SoatFormulario.parsearFecha(fechaVencimientoNueva) ?: return null
        val a = SoatFormulario.parsearFecha(fechaVencimientoAnterior) ?: return null
        if (!n.isAfter(a)) return "La nueva fecha de vencimiento debe ser posterior a la anterior."
        return null
    }

    /** Renovar solo desde el día de vencimiento inclusive. */
    fun puedeRenovar(fechaVencimiento: String?, hoy: LocalDate = hoyLima()): Boolean {
        val v = SoatFormulario.parsearFecha(fechaVencimiento ?: return false) ?: return false
        return !hoy.isBefore(v)
    }

    fun hayCambiosEdicion(
        categoriaIni: String,
        numeroIni: String,
        emisionIni: String,
        vencimientoIni: String,
        accionFrontal: String,
        accionPosterior: String,
        categoria: String,
        numero: String,
        emision: String,
        vencimiento: String
    ): Boolean {
        if (categoria != categoriaIni) return true
        if (normalizarNumero(numero) != normalizarNumero(numeroIni)) return true
        if (emision != emisionIni) return true
        if (vencimiento != vencimientoIni) return true
        if (accionFrontal != "MANTENER") return true
        if (accionPosterior != "MANTENER") return true
        return false
    }

    fun estadoVigencia(fechaVencimiento: String, hoy: LocalDate = hoyLima()): String {
        val v = SoatFormulario.parsearFecha(fechaVencimiento) ?: return ""
        return if (hoy.isAfter(v)) "VENCIDA" else "VIGENTE"
    }
}
