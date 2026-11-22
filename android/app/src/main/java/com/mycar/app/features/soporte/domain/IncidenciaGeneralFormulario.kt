package com.mycar.app.features.soporte.domain

import java.util.Locale

object IncidenciaGeneralFormulario {
    const val MAX_ASUNTO = 120
    const val MIN_ASUNTO = 5
    const val MAX_DESCRIPCION = 3000
    const val MIN_DESCRIPCION = 15
    const val MAX_TEMA_OTRO = 120
    const val MIN_TEMA_OTRO = 3

    val CATEGORIAS = listOf(
        "FALLAS_APP" to "Fallas de la aplicación",
        "CUENTA_ACCESO" to "Cuenta y acceso",
        "VEHICULOS_DOCUMENTOS" to "Vehículos y documentos",
        "ALERTAS_NOTIFICACIONES" to "Alertas y notificaciones",
        "SERVICIOS_EXTERNOS" to "Servicios externos",
        "OTROS" to "Otros"
    )

    fun etiquetaCategoria(codigo: String?): String {
        if (codigo.isNullOrBlank()) return "Incidencia"
        return CATEGORIAS.firstOrNull { it.first.equals(codigo, true) }?.second ?: codigo
    }

    fun validar(
        categoria: String?,
        temaOtro: String,
        asunto: String,
        descripcion: String,
        tocados: Set<String> = emptySet(),
        exigirTodos: Boolean = true
    ): Map<String, String> {
        val e = mutableMapOf<String, String>()
        fun incluir(campo: String, mensaje: String?) {
            if (mensaje == null) return
            if (exigirTodos || campo in tocados) e[campo] = mensaje
        }
        incluir("categoria", errorCategoria(categoria, exigirTodos || "categoria" in tocados))
        if (categoria.equals("OTROS", true)) {
            incluir("temaOtro", errorTemaOtro(temaOtro, exigirTodos || "temaOtro" in tocados))
        }
        incluir("asunto", errorAsunto(asunto, exigirTodos || "asunto" in tocados))
        incluir("descripcion", errorDescripcion(descripcion, exigirTodos || "descripcion" in tocados))
        return e
    }

    fun errorCategoria(raw: String?, exigir: Boolean): String? {
        if (raw.isNullOrBlank()) return if (exigir) "Selecciona el tema de la incidencia" else null
        if (CATEGORIAS.none { it.first.equals(raw, true) }) return "Tema no válido"
        return null
    }

    fun errorTemaOtro(raw: String, exigir: Boolean): String? {
        val t = normalizarEspacios(raw)
        if (t.isEmpty()) return if (exigir) "Especifica el tema" else null
        if (t.length < MIN_TEMA_OTRO || t.length > MAX_TEMA_OTRO) {
            return "El tema debe tener entre $MIN_TEMA_OTRO y $MAX_TEMA_OTRO caracteres"
        }
        return null
    }

    fun errorAsunto(raw: String, exigir: Boolean): String? {
        val a = normalizarEspacios(raw)
        if (a.isEmpty()) return if (exigir) "Escribe un asunto" else null
        if (a.length < MIN_ASUNTO || a.length > MAX_ASUNTO) {
            return "El asunto debe tener entre $MIN_ASUNTO y $MAX_ASUNTO caracteres"
        }
        return null
    }

    fun errorDescripcion(raw: String, exigir: Boolean): String? {
        val d = raw.trim()
        if (d.isEmpty()) return if (exigir) "Describe lo ocurrido" else null
        val utiles = d.replace(Regex("\\s+"), "")
        if (utiles.length < MIN_DESCRIPCION) {
            return "La descripción debe tener al menos $MIN_DESCRIPCION caracteres útiles"
        }
        if (d.length > MAX_DESCRIPCION) {
            return "La descripción no puede superar los $MAX_DESCRIPCION caracteres"
        }
        return null
    }

    fun normalizarEspacios(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    fun sugiereCorreccionVin(asunto: String, descripcion: String): Boolean {
        val texto = "$asunto $descripcion".lowercase(Locale.ROOT)
        return Regex("(corregir|cambiar|actualizar|modificar)\\s+(el\\s+)?vin|\\bvin\\s+(incorrecto|errado|mal)")
            .containsMatchIn(texto)
    }

    fun contadorAsunto(raw: String): String = "${raw.length.coerceAtMost(MAX_ASUNTO + 20)}/$MAX_ASUNTO"
    fun contadorDescripcion(raw: String): String =
        "${raw.length.coerceAtMost(MAX_DESCRIPCION + 50)}/$MAX_DESCRIPCION"
}
