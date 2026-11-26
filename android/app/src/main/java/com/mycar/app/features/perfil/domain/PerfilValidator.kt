package com.mycar.app.features.perfil.domain

import com.mycar.app.features.autenticacion.domain.RegistrationValidator
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object PerfilValidator {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    private val display = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    private val name = Regex("\\p{L}[\\p{L}\\p{M}]*(?:[ '\u2019-]\\p{L}[\\p{L}\\p{M}]*)*")

    fun isoToDisplay(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            LocalDate.parse(isoDate, iso).format(display)
        } catch (_: DateTimeParseException) {
            isoDate
        }
    }

    fun displayToIso(displayDate: String): String? {
        val birth = RegistrationValidator.birthDate(displayDate) ?: return null
        return birth.format(iso)
    }

    fun validateEdition(form: FormularioEdicionPerfil, clock: Clock = Clock.systemUTC()): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        for ((field, value) in listOf("nombre" to form.nombre, "apellido" to form.apellido)) {
            val normalized = RegistrationValidator.normalizedName(value)
            if (normalized.codePointCount(0, normalized.length) !in 2..100 || !name.matches(normalized)) {
                errors[field] = RegistrationValidator.messages.getValue(field)
            }
        }
        val today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")))
        val birth = RegistrationValidator.birthDate(form.fechaNacimiento)
        if (birth == null || birth.isAfter(today) || Period.between(birth, today).years !in 18..85) {
            errors["fechaNacimiento"] = RegistrationValidator.messages.getValue("fechaNacimiento")
        }
        return errors
    }

    fun validatePasswordChange(actual: String, nueva: String, confirmacion: String): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (actual.isEmpty()) errors["contrasenaActual"] = "Ingresa tu contraseña actual."
        if (RegistrationValidator.passwordRequirements(nueva).any { !it.second }) {
            errors["nuevaContrasena"] = RegistrationValidator.messages.getValue("contrasena")
        }
        if (nueva != confirmacion) {
            errors["confirmacionContrasena"] = RegistrationValidator.messages.getValue("confirmacionContrasena")
        }
        if (actual.isNotEmpty() && nueva == actual) {
            errors["nuevaContrasena"] = "La nueva contraseña debe ser distinta de la actual."
        }
        return errors
    }
}
