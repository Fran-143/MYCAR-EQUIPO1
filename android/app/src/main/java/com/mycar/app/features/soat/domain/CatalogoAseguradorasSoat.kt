package com.mycar.app.features.soat.domain

/**
 * Catálogo SOAT Perú (APESEG) + perfiles de validación de póliza.
 *
 * Fuentes verificadas (2025-11):
 * - APESEG: 9 aseguradoras con oferta SOAT
 * - SBS: el identificador lo determina cada aseguradora (sin longitud universal)
 * - DS 012-2017-MTC art. 12 / DS 024-2002-MTC: vigencia anual ordinaria
 *
 * No inventar longitudes/regex exclusivas por compañía sin evidencia.
 * Los ejemplos públicos son ilustrativos, no requisitos.
 */
data class PerfilValidacionPoliza(
    val idAseguradora: String,
    val nombreVisible: String,
    /** Ayuda bajo el campo; no afirma autenticidad. */
    val mensajeAyuda: String,
    /** Ejemplo ilustrativo si hay referencia pública verificable; null si no. */
    val ejemploIlustrativo: String?,
    val fuenteValidacion: String,
    val fechaVerificacion: String,
    /**
     * Solo si una fuente lo documenta como obligatorio.
     * null = no imponer (validación general).
     */
    val longitudMinDocumentada: Int? = null,
    val longitudMaxDocumentada: Int? = null,
)

enum class AseguradoraSoat(
    val id: String,
    /** Valor persistido (sin tildes) para comparación/guardar. */
    val codigo: String,
    val nombre: String,
    val alias: Set<String> = emptySet(),
    /** Oferta vigente en selector (APESEG). false = solo histórico en BD. */
    val activo: Boolean = true,
    val perfil: PerfilValidacionPoliza,
) {
    RIMAC(
        id = "RIMAC",
        codigo = "RIMAC SEGUROS",
        nombre = "RÍMAC SEGUROS",
        alias = setOf("RIMAC", "RIMAC SEGUROS Y REASEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "RIMAC",
            nombreVisible = "RÍMAC SEGUROS",
            mensajeAyuda = "Números y separadores según tu certificado. " +
                "MyCar valida el formato, no la autenticidad oficial.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; SBS (identificador definido por la aseguradora)",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    POSITIVA(
        id = "POSITIVA",
        codigo = "LA POSITIVA SEGUROS",
        nombre = "LA POSITIVA SEGUROS",
        alias = setOf("LA POSITIVA", "POSITIVA", "POSITIVA SEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "POSITIVA",
            nombreVisible = "LA POSITIVA SEGUROS",
            mensajeAyuda = "Se admiten números y guiones; conserva ceros iniciales. " +
                "Sin longitud fija obligatoria documentada.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; SBS (identificador definido por la aseguradora)",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    PACIFICO(
        id = "PACIFICO",
        codigo = "PACIFICO SEGUROS",
        nombre = "PACÍFICO SEGUROS",
        alias = setOf("PACIFICO", "PACIFICO SEGUROS Y REASEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "PACIFICO",
            nombreVisible = "PACÍFICO SEGUROS",
            mensajeAyuda = "Ingresa el identificador de tu certificado Pacífico. " +
                "Los ejemplos públicos no fijan una longitud obligatoria.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; Pacífico (documentación SOAT); SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    MAPFRE(
        id = "MAPFRE",
        codigo = "MAPFRE PERU",
        nombre = "MAPFRE PERÚ",
        alias = setOf("MAPFRE", "MAPFRE PERU"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "MAPFRE",
            nombreVisible = "MAPFRE PERÚ",
            mensajeAyuda = "Sin formato obligatorio confirmado en fuentes revisadas. " +
                "Se aplica validación general de MyCar.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    INTERSEGURO(
        id = "INTERSEGURO",
        codigo = "INTERSEGURO",
        nombre = "INTERSEGURO",
        alias = setOf("INTERSEGURO COMPANIA DE SEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "INTERSEGURO",
            nombreVisible = "INTERSEGURO",
            mensajeAyuda = "Su web muestra un ejemplo ilustrativo de 8 dígitos; " +
                "eso no es un requisito obligatorio en MyCar. Usa el número de tu certificado.",
            ejemploIlustrativo = "12345678",
            fuenteValidacion = "APESEG; Interseguro (estado de cobertura — ejemplo ilustrativo); SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    PROTECTA(
        id = "PROTECTA",
        codigo = "PROTECTA SECURITY",
        nombre = "PROTECTA SECURITY",
        alias = setOf("PROTECTA", "PROTECTA SEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "PROTECTA",
            nombreVisible = "PROTECTA SECURITY",
            mensajeAyuda = "Formato obligatorio no confirmado. Validación general de MyCar.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    CRECER(
        id = "CRECER",
        codigo = "CRECER SEGUROS",
        nombre = "CRECER SEGUROS",
        alias = setOf("CRECER"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "CRECER",
            nombreVisible = "CRECER SEGUROS",
            mensajeAyuda = "Figura en APESEG; formato de póliza no documentado como longitud fija. " +
                "Validación general.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG (oferta SOAT); SBS estadísticas 2025 (sin interpretar ausencia como prohibición)",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    VIVIR(
        id = "VIVIR",
        codigo = "VIVIR SEGUROS",
        nombre = "VIVIR SEGUROS",
        alias = setOf("VIVIR", "VIVIR SEGUROS S.A."),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "VIVIR",
            nombreVisible = "VIVIR SEGUROS",
            mensajeAyuda = "Formato obligatorio no confirmado. Validación general de MyCar.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    QUALITAS(
        id = "QUALITAS",
        codigo = "QUALITAS PERU",
        nombre = "QUÁLITAS PERÚ",
        alias = setOf("QUALITAS", "QUALITAS PERU", "QUALITAS COMPANIA DE SEGUROS"),
        perfil = PerfilValidacionPoliza(
            idAseguradora = "QUALITAS",
            nombreVisible = "QUÁLITAS PERÚ",
            mensajeAyuda = "El portal distingue póliza y certificado; ingresa el identificador de tu documento. " +
                "Sin longitud fija inferida.",
            ejemploIlustrativo = null,
            fuenteValidacion = "APESEG; Quálitas (consulta de estatus); SBS",
            fechaVerificacion = "2025-11-15",
        ),
    ),

    // Históricos: siguen resolviéndose para consulta/corrección; no salen en el selector.
    HDI(
        id = "HDI",
        codigo = "HDI SEGUROS",
        nombre = "HDI SEGUROS",
        alias = setOf("HDI"),
        activo = false,
        perfil = PerfilValidacionPoliza(
            idAseguradora = "HDI",
            nombreVisible = "HDI SEGUROS",
            mensajeAyuda = "Registro histórico. Validación general.",
            ejemploIlustrativo = null,
            fuenteValidacion = "Histórico MyCar (ya no en catálogo APESEG activo)",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    CHUBB(
        id = "CHUBB",
        codigo = "CHUBB PERU",
        nombre = "CHUBB PERÚ",
        alias = setOf("CHUBB"),
        activo = false,
        perfil = PerfilValidacionPoliza(
            idAseguradora = "CHUBB",
            nombreVisible = "CHUBB PERÚ",
            mensajeAyuda = "Registro histórico. Validación general.",
            ejemploIlustrativo = null,
            fuenteValidacion = "Histórico MyCar",
            fechaVerificacion = "2025-11-15",
        ),
    ),
    CARDIF(
        id = "CARDIF",
        codigo = "BNP PARIBAS CARDIF",
        nombre = "BNP PARIBAS CARDIF",
        alias = setOf("CARDIF", "BNP PARIBAS CARDIF"),
        activo = false,
        perfil = PerfilValidacionPoliza(
            idAseguradora = "CARDIF",
            nombreVisible = "BNP PARIBAS CARDIF",
            mensajeAyuda = "Registro histórico. Validación general.",
            ejemploIlustrativo = null,
            fuenteValidacion = "Histórico MyCar",
            fechaVerificacion = "2025-11-15",
        ),
    );

    companion object {
        fun activas(): List<AseguradoraSoat> = entries.filter { it.activo }

        fun porCodigo(codigo: String?): AseguradoraSoat? {
            val n = normalizar(codigo) ?: return null
            return entries.firstOrNull { a ->
                normalizar(a.codigo) == n ||
                    normalizar(a.nombre) == n ||
                    normalizar(a.id) == n ||
                    a.alias.any { normalizar(it) == n }
            }
        }

        /** Alta/renovación: solo oferta APESEG activa. */
        fun esValidaParaAlta(codigoONombre: String?): Boolean =
            porCodigo(codigoONombre)?.activo == true

        /** Consulta/corrección: reconoce también históricos. */
        fun esReconocida(codigoONombre: String?): Boolean = porCodigo(codigoONombre) != null

        fun esValida(codigoONombre: String?): Boolean = esValidaParaAlta(codigoONombre)

        fun normalizarParaGuardar(codigoONombre: String): String? =
            porCodigo(codigoONombre)?.codigo

        fun perfilDe(codigoONombre: String?): PerfilValidacionPoliza? =
            porCodigo(codigoONombre)?.perfil

        fun ayudaPoliza(codigoONombre: String?): String {
            val p = perfilDe(codigoONombre)
            return p?.mensajeAyuda
                ?: "Ingresa el número de póliza o certificado tal como aparece en el documento. " +
                "MyCar valida el formato, no la autenticidad oficial (SBS: cada aseguradora define su identificador)."
        }

        fun ejemploPoliza(codigoONombre: String?): String =
            perfilDe(codigoONombre)?.ejemploIlustrativo
                ?: "Según tu certificado"

        fun normalizar(texto: String?): String? {
            if (texto.isNullOrBlank()) return null
            return texto.trim().uppercase()
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
                .replace('Ó', 'O').replace('Ú', 'U').replace('Ü', 'U')
                .replace(Regex("\\s+"), " ")
        }
    }
}
