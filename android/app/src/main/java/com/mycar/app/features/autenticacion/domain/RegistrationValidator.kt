package com.mycar.app.features.autenticacion.domain

import java.net.URI
import java.net.IDN
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Catálogo cerrado de recursos incluidos. La versión sólo pertenece a este documento. */
enum class LocalPrivacyDocument(val version: String) { MYCAR_ACADEMIC_V1("1.0") }

class PrivacyPolicyConfiguration(
    val version: String = "", val documentUrl: String = "",
    val localDocument: LocalPrivacyDocument? = null
) {
    val available: Boolean get() = if (localDocument != null) {
        version == localDocument.version && documentUrl.isEmpty()
    } else try {
        val uri = URI(documentUrl)
        version.isNotBlank() && version.codePointCount(0, version.length) <= 40 &&
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    companion object {
        fun academicLocal() = PrivacyPolicyConfiguration(
            LocalPrivacyDocument.MYCAR_ACADEMIC_V1.version,
            localDocument = LocalPrivacyDocument.MYCAR_ACADEMIC_V1)
        fun configured(source: String, externalVersion: String, externalUrl: String) = when (source) {
            "local-academic" -> academicLocal()
            "external" -> PrivacyPolicyConfiguration(externalVersion, externalUrl)
            else -> PrivacyPolicyConfiguration()
        }
    }
}

data class RegistrationForm(
    val nombre: String = "", val apellido: String = "", val correo: String = "",
    val fechaNacimiento: String = "", val contrasena: String = "",
    val confirmacionContrasena: String = "", val privacidadAceptada: Boolean = false
) {
    override fun toString() = "RegistrationForm[REDACTED]"
}

object RegistrationValidator {
    private val spaces = Regex("[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+")
    private val name = Regex("\\p{L}[\\p{L}\\p{M}]*(?:[ '\u2019-]\\p{L}[\\p{L}\\p{M}]*)*")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val specialTypes = setOf(
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
        Character.OTHER_PUNCTUATION.toInt(), Character.MATH_SYMBOL.toInt(),
        Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(), Character.OTHER_SYMBOL.toInt()
    )
    fun normalizedName(value: String) = spaces.replace(value, " ").trim { Character.isWhitespace(it) }
    fun normalizedEmail(value: String) = value.trim { Character.isWhitespace(it) }.lowercase(Locale.ROOT)
    fun birthDate(value: String): LocalDate? = try { LocalDate.parse(value, dateFormat) } catch (_: Exception) { null }
    fun passwordRequirements(value: String): List<Pair<String, Boolean>> {
        val points = value.codePoints().toArray()
        return listOf(
            "Entre 8 y 64 caracteres" to (points.size in 8..64),
            "Una letra mayúscula" to points.any(Character::isUpperCase),
            "Una letra minúscula" to points.any(Character::isLowerCase),
            "Un dígito" to points.any(Character::isDigit),
            "Un símbolo o carácter especial" to points.any { Character.getType(it) in specialTypes },
            "Sin espacios" to points.none { Character.isWhitespace(it) || Character.isSpaceChar(it) }
        )
    }
    // Prevalidación sintáctica sin consultas de disponibilidad. El backend revalida @Email.
    private fun emailValid(value: String): Boolean {
        if (value.codePointCount(0, value.length) !in 5..254) return false
        val at = value.lastIndexOf('@')
        if (at <= 0 || at == value.lastIndex) return false
        val local = value.substring(0, at)
        if (local.length > 64 || local.startsWith('.') || local.endsWith('.')) return false
        // No se restringen dominios comerciales ni formatos avanzados admitidos por @Email.
        // Las comillas permiten espacios en el componente local; el servidor decide el formato final.
        val quoted = local.startsWith('"') && local.endsWith('"')
        if (!quoted && (".." in local || local.any { Character.isWhitespace(it) || Character.isSpaceChar(it) } ||
            '@' in local || '"' in local)) return false
        val domain = value.substring(at + 1)
        if (domain.endsWith('.') || domain.any { Character.isWhitespace(it) || Character.isSpaceChar(it) }) return false
        if (domain.startsWith('[') && domain.endsWith(']')) return domain.length <= 255
        return try { IDN.toASCII(domain).length in 1..255 } catch (_: Exception) { false }
    }
    val messages = mapOf(
        "nombre" to "Usa 2–100 caracteres: letras, espacios y guion o apóstrofo entre letras.",
        "apellido" to "Usa 2–100 caracteres: letras, espacios y guion o apóstrofo entre letras.",
        "correo" to "Ingresa un correo válido de hasta 254 caracteres.",
        "fechaNacimiento" to "La edad debe estar entre 18 y 85 años cumplidos.",
        "contrasena" to "Usa 8–64 caracteres con mayúscula, minúscula, dígito y especial, sin espacios.",
        "confirmacionContrasena" to "Las contraseñas deben coincidir exactamente.",
        "privacidadAceptada" to "Debes aceptar expresamente la política de privacidad.",
        "versionPrivacidad" to "Debes aceptar la versión vigente de la política de privacidad."
    )
    fun validate(form: RegistrationForm, policy: PrivacyPolicyConfiguration, clock: Clock): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        fun error(field: String) { errors[field] = messages.getValue(field) }
        for ((field, value) in listOf("nombre" to form.nombre, "apellido" to form.apellido)) {
            val normalized = normalizedName(value)
            if (normalized.codePointCount(0, normalized.length) !in 2..100 || !name.matches(normalized)) error(field)
        }
        if (!emailValid(normalizedEmail(form.correo))) error("correo")
        val today = LocalDate.now(clock.withZone(ZoneId.of("America/Lima")))
        val birth = birthDate(form.fechaNacimiento)
        if (birth == null || birth.isAfter(today) || Period.between(birth, today).years !in 18..85) error("fechaNacimiento")
        if (passwordRequirements(form.contrasena).any { !it.second }) error("contrasena")
        if (form.confirmacionContrasena.isEmpty() || form.contrasena != form.confirmacionContrasena) error("confirmacionContrasena")
        if (!form.privacidadAceptada) error("privacidadAceptada")
        if (!policy.available) error("versionPrivacidad")
        return errors
    }
}
