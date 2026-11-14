package com.mycar.app.features.soat.domain

/**
 * Catálogo controlado de aseguradoras con oferta SOAT en Perú.
 * Códigos normalizados (sin tildes) para persistencia/comparación;
 * [nombre] es la etiqueta legible en UI.
 */
enum class AseguradoraSoat(val codigo: String, val nombre: String) {
    RIMAC("RIMAC SEGUROS", "RÍMAC SEGUROS"),
    PACIFICO("PACIFICO SEGUROS", "PACÍFICO SEGUROS"),
    MAPFRE("MAPFRE PERU", "MAPFRE PERÚ"),
    LA_POSITIVA("LA POSITIVA SEGUROS", "LA POSITIVA SEGUROS"),
    INTERSEGURO("INTERSEGURO", "INTERSEGURO"),
    PROTECTA("PROTECTA SECURITY", "PROTECTA SECURITY"),
    QUALITAS("QUALITAS", "QUALITAS"),
    HDI("HDI SEGUROS", "HDI SEGUROS"),
    CRECER("CRECER SEGUROS", "CRECER SEGUROS"),
    CHUBB("CHUBB PERU", "CHUBB PERÚ"),
    CARDIF("BNP PARIBAS CARDIF", "BNP PARIBAS CARDIF");

    companion object {
        fun porCodigo(codigo: String?): AseguradoraSoat? {
            val n = normalizar(codigo) ?: return null
            return entries.firstOrNull { normalizar(it.codigo) == n || normalizar(it.nombre) == n }
        }

        fun esValida(codigoONombre: String?): Boolean = porCodigo(codigoONombre) != null

        fun normalizarParaGuardar(codigoONombre: String): String? =
            porCodigo(codigoONombre)?.codigo

        fun normalizar(texto: String?): String? {
            if (texto.isNullOrBlank()) return null
            val base = texto.trim().uppercase()
                .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
                .replace('Ó', 'O').replace('Ú', 'U').replace('Ü', 'U')
                .replace(Regex("\\s+"), " ")
            return base
        }
    }
}
