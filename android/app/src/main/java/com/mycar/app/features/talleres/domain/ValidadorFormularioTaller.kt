package com.mycar.app.features.talleres.domain

import android.util.Patterns
import java.util.Locale

object ValidadorFormularioTaller {
    /** Celular Perú: exactamente 9 dígitos y empieza con 9. */
    private val telefonoMovilPe = Regex("^9[0-9]{8}$")

    fun normalizarNombre(raw: String): String =
        raw.trim().uppercase(Locale.getDefault())

    fun normalizarDireccion(raw: String): String =
        raw.trim().uppercase(Locale.getDefault())

    fun normalizarCorreo(raw: String): String = raw.trim()

    fun normalizarNotas(raw: String): String = raw.trim()

    /**
     * Solo dígitos del número nacional (sin +51).
     * Si viene con prefijo 51 / 0, lo reduce a los 9 dígitos locales.
     */
    fun digitosTelefono(raw: String): String {
        val d = raw.filter { it.isDigit() }
        val nacional = when {
            d.length >= 11 && d.startsWith("51") -> d.drop(2)
            d.length == 10 && d.startsWith("0") -> d.drop(1)
            else -> d
        }
        return nacional
    }

    /** Presentación fija Perú: +51 9XXXXXXXX */
    fun formatearTelefonoUi(raw: String?): String? {
        val tel = digitosTelefono(raw.orEmpty())
        if (tel.isBlank()) return null
        return if (telefonoMovilPe.matches(tel)) "+51 $tel" else tel
    }

    fun errores(
        nombre: String,
        telefono: String,
        correo: String,
        direccion: String,
        notas: String
    ): Map<String, String> = buildMap {
        val nom = normalizarNombre(nombre)
        if (nom.isBlank()) put("nombre", "El nombre es obligatorio")
        else if (nom.codePointCount(0, nom.length) > 140) put("nombre", "Hasta 140 caracteres")

        val tel = digitosTelefono(telefono)
        if (tel.isNotBlank() && !telefonoMovilPe.matches(tel)) {
            put(
                "telefono",
                when {
                    tel.length != 9 -> "Ingresa 9 dígitos (celular Perú)"
                    !tel.startsWith("9") -> "El celular debe empezar con 9"
                    else -> "El teléfono ingresado no es válido"
                }
            )
        }

        val mail = normalizarCorreo(correo)
        if (mail.isNotBlank() && !correoValido(mail)) {
            put("correo", "El correo ingresado no es válido")
        }

        val dir = normalizarDireccion(direccion)
        if (dir.isNotBlank()) {
            val len = dir.codePointCount(0, dir.length)
            if (len < 9) put("direccion", "La dirección debe contener al menos 9 caracteres.")
            else if (len > 200) put("direccion", "La dirección no puede superar 200 caracteres")
        }

        val n = normalizarNotas(notas)
        if (n.codePointCount(0, n.length) > 500) {
            put("notas", "Las notas no pueden superar los 500 caracteres")
        }
    }

    fun correoValido(correo: String): Boolean {
        val c = correo.trim()
        if (c.isEmpty() || c.length < 5 || c.length > 254) return false
        if (c.any { it.isWhitespace() }) return false
        if (c.count { it == '@' } != 1) return false
        val at = c.indexOf('@')
        if (at <= 0 || at >= c.length - 1) return false
        val local = c.substring(0, at)
        val dominio = c.substring(at + 1)
        if (local.isBlank() || dominio.isBlank() || !dominio.contains('.')) return false
        // Patterns.EMAIL_ADDRESS es null o no mockeado en unit tests JVM.
        return runCatching {
            val p = Patterns.EMAIL_ADDRESS ?: return@runCatching true
            p.matcher(c).matches()
        }.getOrDefault(true)
    }

    /**
     * Alta: nombre no vacío + sin errores.
     * Edición: además debe haber al menos un cambio real (valores normalizados).
     */
    fun puedeGuardar(
        nombre: String,
        telefono: String,
        correo: String,
        direccion: String,
        notas: String,
        tieneWhatsapp: Boolean,
        editando: Taller?
    ): Boolean {
        if (errores(nombre, telefono, correo, direccion, notas).isNotEmpty()) return false
        if (normalizarNombre(nombre).isBlank()) return false
        if (editando == null) return true
        return hayCambiosReales(nombre, telefono, correo, direccion, notas, tieneWhatsapp, editando)
    }

    fun hayCambiosReales(
        nombre: String,
        telefono: String,
        correo: String,
        direccion: String,
        notas: String,
        tieneWhatsapp: Boolean,
        editando: Taller
    ): Boolean {
        val nom = normalizarNombre(nombre)
        val tel = digitosTelefono(telefono)
        val mail = normalizarCorreo(correo).lowercase(Locale.ROOT)
        val dir = normalizarDireccion(direccion)
        val n = normalizarNotas(notas)
        val wa = tieneWhatsapp && tel.isNotBlank()

        val nom0 = normalizarNombre(editando.nombre)
        val tel0 = digitosTelefono(editando.telefono.orEmpty())
        val mail0 = normalizarCorreo(editando.correo.orEmpty()).lowercase(Locale.ROOT)
        val dir0 = normalizarDireccion(editando.direccion.orEmpty())
        val n0 = normalizarNotas(editando.notas.orEmpty())
        val wa0 = editando.tieneWhatsapp == true && tel0.isNotBlank()

        return nom != nom0 || tel != tel0 || mail != mail0 || dir != dir0 || n != n0 || wa != wa0
    }
}
