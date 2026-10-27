package com.mycar.app.features.mantenimiento.domain

import com.mycar.app.features.mantenimiento.data.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class RepuestoBorrador(
    val idLocal: String = UUID.randomUUID().toString(),
    val nombre: String = "", val marca: String = "", val cantidad: String = "1",
    val precio: String = ""
)
data class BorradorMantenimiento(
    val idOperacion: String = UUID.randomUUID().toString(),
    val idVehiculo: Long? = null, val codigoTipo: String = "", val fecha: String = "",
    val fechaModificada: Boolean = false,
    val origen: String = "LECTURA", val lectura: Long? = null, val km: String = "",
    val servicioOtro: String = "", val observaciones: String = "", val costo: String = "",
    val proximaFecha: String = "", val proximoKm: String = "",
    val repuestos: List<RepuestoBorrador> = emptyList(), val envioPendiente: Boolean = false
) {
    fun tieneContenido() = fechaModificada || codigoTipo.isNotBlank() || km.isNotBlank()
            || observaciones.isNotBlank() || servicioOtro.isNotBlank() || costo.isNotBlank()
            || proximaFecha.isNotBlank() || proximoKm.isNotBlank() || repuestos.isNotEmpty()
}

object ValidacionFormularioMantenimiento {
    private val maxImporte = BigDecimal("9999999999.99")
    private val formatoImporte = Regex("[0-9]+([.,][0-9]{1,2})?")
    fun importe(texto: String): BigDecimal? = texto.trim().takeIf { it.isNotEmpty() }
        ?.replace(',', '.')?.toBigDecimalOrNull()
    private fun importeValido(texto: String): Boolean = texto.isBlank() ||
        (formatoImporte.matches(texto.trim()) && importe(texto)?.let { it <= maxImporte } == true)
    private fun longitud(texto: String) = texto.codePointCount(0, texto.length)
    fun repuesto(p: RepuestoBorrador): Map<String,String> = buildMap {
        if (p.nombre.isBlank() || longitud(p.nombre) > 140) put("nombre", "Nombre obligatorio, hasta 140 caracteres")
        if (longitud(p.marca) > 120) put("marca", "Hasta 120 caracteres")
        if (p.cantidad.toIntOrNull()?.let { it > 0 } != true) put("cantidad", "Cantidad entera mayor que cero")
        if (!importeValido(p.precio)) put("precio", "Usa un importe no negativo con hasta 2 decimales, sin miles")
    }
    fun formulario(b: BorradorMantenimiento, c: ContextoRf17Dto?): Map<String,String> = buildMap {
        if (c == null) { put("contexto", "Carga los datos antes de guardar"); return@buildMap }
        if (c.vehiculos.none { it.idVehiculo == b.idVehiculo }) put("idVehiculo", "Selecciona un vehículo disponible")
        if (c.tipos.none { it.codigo == b.codigoTipo }) put("codigoTipoMantenimiento", "Selecciona un tipo disponible")
        val fecha = runCatching { LocalDate.parse(b.fecha) }.getOrNull()
        val hoy = runCatching { LocalDate.parse(c.fechaServidor) }.getOrNull()
        if (fecha == null || hoy == null || fecha > hoy) put("fechaServicio", "Selecciona una fecha válida no futura")
        val km = b.km.toIntOrNull()
        if (km == null || km <= 0) put("kmServicio", "El kilometraje debe ser un entero mayor que cero")
        if (b.origen == "LECTURA" && b.lectura == null) put("idLecturaKmReferencia", "Selecciona una lectura del vehículo")
        if (b.origen !in listOf("LECTURA", "HISTORICO_MANUAL")) put("origenKmServicio", "Selecciona el origen")
        if (b.codigoTipo == "OTRO" && (b.servicioOtro.isBlank() || longitud(b.servicioOtro) > 160))
            put("servicioOtro", "Describe el servicio, hasta 160 caracteres")
        if (!importeValido(b.costo)) put("costoTotal", "Importe no negativo, hasta 2 decimales, sin separador de miles")
        if (b.proximaFecha.isNotBlank()) {
            val proxima = runCatching { LocalDate.parse(b.proximaFecha) }.getOrNull()
            if (proxima == null || fecha == null || proxima <= fecha) put("proximaFechaSugerida", "Debe ser posterior al servicio")
        }
        if (b.proximoKm.isNotBlank() && (b.proximoKm.toIntOrNull()?.let { km != null && it > km } != true))
            put("proximoKmSugerido", "Debe ser mayor que el kilometraje del servicio")
        b.repuestos.forEachIndexed { i, p -> repuesto(p).forEach { (k,v) -> put("repuestos[$i].$k",v) } }
    }
    fun solicitud(b: BorradorMantenimiento) = SolicitudRf17Dto(
        b.idOperacion, b.codigoTipo, b.fecha, requireNotNull(b.km.toIntOrNull()), b.origen,
        if (b.origen == "LECTURA") b.lectura else null, importe(b.costo),
        b.servicioOtro.takeIf { b.codigoTipo == "OTRO" }, b.observaciones.takeIf { it.isNotBlank() },
        b.proximaFecha.takeIf { it.isNotBlank() }, b.proximoKm.toIntOrNull(),
        b.repuestos.map { RepuestoRf17Dto(it.nombre, it.marca.takeIf(String::isNotBlank),
            requireNotNull(it.cantidad.toIntOrNull()), importe(it.precio)) }
    )
}
