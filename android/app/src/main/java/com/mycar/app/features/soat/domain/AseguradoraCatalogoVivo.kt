package com.mycar.app.features.soat.domain

import com.mycar.app.features.soat.data.AseguradoraSoatDto

/**
 * Catálogo SOAT vivo (API). Si aún no se cargó, cae al enum histórico embebido.
 */
data class AseguradoraCatalogoItem(
    val codigo: String,
    val nombre: String,
    val permiteNombreLibre: Boolean = false,
    val ayudaPoliza: String = "",
    val ejemploPoliza: String = "Según tu certificado"
)

object AseguradoraCatalogoVivo {
    @Volatile
    private var remoto: List<AseguradoraCatalogoItem> = emptyList()

    fun actualizar(items: List<AseguradoraSoatDto>) {
        remoto = items.mapNotNull { dto ->
            val codigo = dto.codigo?.trim().orEmpty()
            val nombre = dto.nombre?.trim().orEmpty()
            if (codigo.isEmpty() && nombre.isEmpty()) null
            else AseguradoraCatalogoItem(
                codigo = codigo.ifEmpty { nombre },
                nombre = nombre.ifEmpty { codigo },
                permiteNombreLibre = dto.permiteNombreLibre,
                ayudaPoliza = dto.ayudaPoliza.orEmpty(),
                ejemploPoliza = dto.ejemploPoliza?.takeIf { it.isNotBlank() } ?: "Según tu certificado"
            )
        }
    }

    fun activas(): List<AseguradoraCatalogoItem> {
        if (remoto.isNotEmpty()) return remoto
        return AseguradoraSoat.activas().map {
            AseguradoraCatalogoItem(
                codigo = it.codigo,
                nombre = it.nombre,
                permiteNombreLibre = false,
                ayudaPoliza = it.perfil.mensajeAyuda,
                ejemploPoliza = it.perfil.ejemploIlustrativo ?: "Según tu certificado"
            )
        }
    }

    fun resolver(codigoONombre: String?): AseguradoraCatalogoItem? {
        val n = AseguradoraSoat.normalizar(codigoONombre) ?: return null
        activas().firstOrNull {
            AseguradoraSoat.normalizar(it.codigo) == n || AseguradoraSoat.normalizar(it.nombre) == n
        }?.let { return it }
        return AseguradoraSoat.porCodigo(codigoONombre)?.let {
            AseguradoraCatalogoItem(it.codigo, it.nombre, false, it.perfil.mensajeAyuda,
                it.perfil.ejemploIlustrativo ?: "Según tu certificado")
        }
    }

    fun esOtra(codigoONombre: String?): Boolean =
        resolver(codigoONombre)?.permiteNombreLibre == true

    fun otraActiva(): Boolean = activas().any { it.permiteNombreLibre }

    /** Alta/renovar: catálogo activo (no OTRA) o nombre libre si OTRA está activa. */
    fun esValidaParaAlta(valor: String?): Boolean {
        if (valor.isNullOrBlank()) return false
        val match = resolver(valor)
        if (match != null) {
            return !match.permiteNombreLibre
        }
        if (otraActiva()) {
            // Nombre libre: no debe ser solo un histórico inactivo del enum.
            val hist = AseguradoraSoat.porCodigo(valor)
            return hist == null || hist.activo
        }
        return AseguradoraSoat.esValidaParaAlta(valor)
    }

    fun esReconocida(valor: String?): Boolean {
        if (valor.isNullOrBlank()) return false
        if (resolver(valor) != null && !esOtra(valor)) return true
        if (AseguradoraSoat.esReconocida(valor)) return true
        return otraActiva()
    }

    fun normalizarParaGuardar(valor: String): String? {
        val match = resolver(valor)
        if (match != null && !match.permiteNombreLibre) return match.codigo
        if (otraActiva() && valor.isNotBlank()) {
            val hist = AseguradoraSoat.porCodigo(valor)
            if (hist != null && !hist.activo) return hist.codigo
            return valor.trim()
        }
        return AseguradoraSoat.normalizarParaGuardar(valor)
    }

    fun ayudaPoliza(valor: String?): String {
        resolver(valor)?.ayudaPoliza?.takeIf { it.isNotBlank() }?.let { return it }
        return AseguradoraSoat.ayudaPoliza(valor)
    }

    fun ejemploPoliza(valor: String?): String {
        resolver(valor)?.ejemploPoliza?.takeIf { it.isNotBlank() }?.let { return it }
        return AseguradoraSoat.ejemploPoliza(valor)
    }
}
