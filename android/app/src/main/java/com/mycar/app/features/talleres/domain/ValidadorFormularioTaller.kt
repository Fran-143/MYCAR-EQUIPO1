package com.mycar.app.features.talleres.domain

object ValidadorFormularioTaller {
    fun digitosTelefono(raw: String): String = raw.filter { it.isDigit() }.take(9)

    fun errores(nombre: String, telefono: String, correo: String, notas: String): Map<String, String> = buildMap {
        if (nombre.isBlank()) put("nombre", "El nombre es obligatorio")
        else if (nombre.codePointCount(0, nombre.length) > 140) put("nombre", "Hasta 140 caracteres")
        if (telefono.isNotBlank() && telefono.length != 9) {
            put("telefono", "El teléfono ingresado no es válido")
        }
        if (correo.isNotBlank()) {
            val c = correo.trim()
            if (c.length < 5 || c.length > 254 || !c.contains('@') || !c.contains('.')) {
                put("correo", "El correo ingresado no es válido")
            }
        }
        if (notas.codePointCount(0, notas.length) > 500) {
            put("notas", "Las notas no pueden superar los 500 caracteres")
        }
    }

    fun puedeGuardar(
        nombre: String,
        telefono: String,
        correo: String,
        notas: String,
        editando: Taller?,
        nombreActual: String,
        telefonoActual: String,
        correoActual: String,
        direccionActual: String,
        notasActual: String,
        direccion: String
    ): Boolean {
        if (errores(nombre, telefono, correo, notas).isNotEmpty()) return false
        if (nombre.isBlank()) return false
        if (editando == null) return true
        return nombre != nombreActual
            || telefono != telefonoActual
            || correo != correoActual
            || direccion != direccionActual
            || notas != notasActual
    }
}
