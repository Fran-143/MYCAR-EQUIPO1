package com.mycar.app.features.alertas.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.alertas.data.ProgramarAlertaDto
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.CriterioAlerta
import com.mycar.app.features.alertas.domain.ReferenciaMantenimientoAlerta
import com.mycar.app.features.alertas.domain.ResultadoOperacionIncierto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class BorradorAlerta(
    val codigoTipo: String = "",
    val criterio: String = "POR_KILOMETRAJE",
    val unidadTiempo: String = "MESES",
    val cantidadTiempo: String = "6",
    val intervaloKm: String = "5000",
    val servicioOtro: String = "",
    val idReferencia: Long? = null
)

data class ResumenAlerta(
    val fechaBase: String,
    val kmBase: Int?,
    val idLectura: Long?,
    val fechaObjetivo: String?,
    val kmObjetivo: Int?,
    val referencia: ReferenciaMantenimientoAlerta?
)

enum class PasoProgramar { FORMULARIO, REVISION, EXITO }

data class EstadoProgramarAlerta(
    val idVehiculo: Long,
    val contexto: com.mycar.app.features.alertas.domain.ContextoProgramarAlerta? = null,
    val borrador: BorradorAlerta = BorradorAlerta(),
    val paso: PasoProgramar = PasoProgramar.FORMULARIO,
    val resumen: ResumenAlerta? = null,
    val cargando: Boolean = true,
    val enviando: Boolean = false,
    val exito: Long? = null,
    val errores: Map<String, String> = emptyMap(),
    val mensaje: String? = null,
    val idOperacion: String? = null,
    val basesCambiaron: Boolean = false
)

class ProgramarAlertaViewModel(
    private val repository: AlertasRepository,
    private val idVehiculo: Long,
    borradorInicial: BorradorAlerta? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(
        EstadoProgramarAlerta(idVehiculo, borrador = borradorInicial ?: BorradorAlerta())
    )
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun prepararNuevaProgramacion() {
        mutable.value = EstadoProgramarAlerta(idVehiculo)
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            mutable.value = mutable.value.copy(
                cargando = true, mensaje = null, exito = null, enviando = false,
                paso = PasoProgramar.FORMULARIO, resumen = null, basesCambiaron = false
            )
            try {
                val ctx = repository.contexto(idVehiculo)
                mutable.value = mutable.value.copy(contexto = ctx, cargando = false)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    cargando = false,
                    mensaje = e.message ?: "No se pudo cargar el contexto de programación"
                )
            }
        }
    }

    fun actualizar(cambio: (BorradorAlerta) -> BorradorAlerta) {
        if (mutable.value.enviando || mutable.value.exito != null) return
        if (mutable.value.paso == PasoProgramar.EXITO) return
        mutable.value = mutable.value.copy(
            borrador = cambio(mutable.value.borrador),
            errores = emptyMap(),
            mensaje = null,
            idOperacion = null,
            paso = PasoProgramar.FORMULARIO,
            resumen = null,
            basesCambiaron = false
        )
    }

    fun volverFormulario() {
        if (mutable.value.enviando) return
        mutable.value = mutable.value.copy(
            paso = PasoProgramar.FORMULARIO, resumen = null, mensaje = null, basesCambiaron = false
        )
    }

    /** Calcula resumen local (mismas reglas G61) y pasa a revisión. */
    fun revisar() {
        val s = mutable.value
        if (s.enviando || s.exito != null) return
        val ctx = s.contexto
        if (ctx == null) {
            mutable.value = s.copy(mensaje = "Carga el contexto antes de revisar.")
            return
        }
        val errores = validarBorrador(s.borrador, ctx.kmActual)
        if (errores.isNotEmpty()) {
            mutable.value = s.copy(errores = errores, mensaje = errores.values.firstOrNull())
            return
        }
        val b = s.borrador
        val fechaBase = ctx.fechaServidor.take(10)
        val fechaObj = if (CriterioAlerta.usaTiempo(b.criterio)) {
            calcularFechaObjetivo(fechaBase, b.unidadTiempo, b.cantidadTiempo.toInt())
        } else null
        val kmObj = if (CriterioAlerta.usaKm(b.criterio)) {
            sumarKmSeguro(ctx.kmActual!!, b.intervaloKm.toInt())
        } else null
        if (CriterioAlerta.usaKm(b.criterio) && kmObj == null) {
            mutable.value = s.copy(
                errores = mapOf("intervaloKm" to "El kilometraje objetivo supera el máximo permitido."),
                mensaje = "El kilometraje objetivo supera el máximo permitido."
            )
            return
        }
        val ref = ctx.referencias.firstOrNull {
            it.codigoTipo == b.codigoTipo && (b.codigoTipo != "OTRO" || it.servicioOtro == b.servicioOtro)
        }
        // G68/G71: la referencia mostrada en el resumen se envía al guardar.
        mutable.value = s.copy(
            paso = PasoProgramar.REVISION,
            borrador = b.copy(idReferencia = ref?.id?.takeIf { it > 0 }),
            resumen = ResumenAlerta(fechaBase, ctx.kmActual, ctx.idLecturaActual, fechaObj, kmObj, ref),
            errores = emptyMap(),
            mensaje = null,
            basesCambiaron = false
        )
    }

    fun confirmar() {
        val s = mutable.value
        if (s.enviando || s.exito != null || s.paso != PasoProgramar.REVISION) return
        val resumen = s.resumen ?: return
        val idOperacion = s.idOperacion ?: UUID.randomUUID().toString()
        mutable.value = s.copy(enviando = true, mensaje = null, idOperacion = idOperacion)
        viewModelScope.launch {
            try {
                // Reconsulta local de bases (G89 cliente) antes del POST.
                val ctx = repository.contexto(idVehiculo)
                val b = mutable.value.borrador
                if (CriterioAlerta.usaTiempo(b.criterio) && ctx.fechaServidor.take(10) != resumen.fechaBase) {
                    mutable.value = mutable.value.copy(
                        enviando = false, contexto = ctx, basesCambiaron = true,
                        paso = PasoProgramar.REVISION,
                        resumen = recalcularResumen(b, ctx),
                        mensaje = "La fecha actual cambió. Revisa el resumen y confirma de nuevo.",
                        idOperacion = null
                    )
                    return@launch
                }
                if (CriterioAlerta.usaKm(b.criterio) &&
                    (ctx.kmActual != resumen.kmBase || ctx.idLecturaActual != resumen.idLectura)
                ) {
                    mutable.value = mutable.value.copy(
                        enviando = false, contexto = ctx, basesCambiaron = true,
                        paso = PasoProgramar.REVISION,
                        resumen = recalcularResumen(b, ctx),
                        mensaje = "El kilometraje vigente cambió. Revisa el resumen y confirma de nuevo.",
                        idOperacion = null
                    )
                    return@launch
                }
                val idRef = resumen.referencia?.id?.takeIf { it > 0 } ?: b.idReferencia
                val id = repository.programar(idVehiculo, ProgramarAlertaDto(
                    idOperacion, b.codigoTipo, b.criterio,
                    b.unidadTiempo.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    b.cantidadTiempo.toIntOrNull()?.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    b.intervaloKm.toIntOrNull()?.takeIf { CriterioAlerta.usaKm(b.criterio) },
                    b.servicioOtro.takeIf { b.codigoTipo == "OTRO" },
                    idRef,
                    resumen.fechaBase.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    resumen.kmBase,
                    resumen.idLectura
                ))
                mutable.value = mutable.value.copy(
                    enviando = false, exito = id, mensaje = null, idOperacion = null,
                    paso = PasoProgramar.EXITO, basesCambiaron = false
                )
            } catch (e: ResultadoOperacionIncierto) {
                recuperarOperacion(e.idOperacion)
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                if (msg.contains("BASES_CAMBIARON") || msg.contains("cambió") || msg.contains("cambio")) {
                    try {
                        val ctx = repository.contexto(idVehiculo)
                        val b = mutable.value.borrador
                        mutable.value = mutable.value.copy(
                            enviando = false, contexto = ctx, basesCambiaron = true,
                            paso = PasoProgramar.REVISION,
                            resumen = recalcularResumen(b, ctx),
                            mensaje = msg.ifBlank { "Las bases cambiaron. Revisa el resumen." },
                            idOperacion = null
                        )
                    } catch (_: Exception) {
                        mutable.value = mutable.value.copy(
                            enviando = false, mensaje = msg.ifBlank { "Las bases cambiaron." },
                            idOperacion = null
                        )
                    }
                } else if (msg.contains("OPERACION_EN_PROCESO") || msg.contains("sigue en proceso")) {
                    val op = mutable.value.idOperacion
                    if (op != null) recuperarOperacion(op)
                    else mutable.value = mutable.value.copy(
                        enviando = false,
                        mensaje = e.message ?: "No se pudo programar la alerta"
                    )
                } else {
                    mutable.value = mutable.value.copy(
                        enviando = false,
                        mensaje = e.message ?: "No se pudo programar la alerta"
                    )
                }
            }
        }
    }

    private suspend fun recuperarOperacion(idOperacion: String) {
        try {
            val estadoOp = repository.operacion(idOperacion)
            when (estadoOp.estado) {
                "CONFIRMADA" -> mutable.value = mutable.value.copy(
                    enviando = false,
                    exito = estadoOp.idAlerta,
                    mensaje = null,
                    idOperacion = null,
                    paso = PasoProgramar.EXITO,
                    basesCambiaron = false
                )
                "EN_PROCESO" -> mutable.value = mutable.value.copy(
                    enviando = false,
                    mensaje = "La operación sigue en proceso. Inténtalo de nuevo en unos segundos."
                )
                else -> mutable.value = mutable.value.copy(
                    enviando = false,
                    mensaje = "No se pudo confirmar el resultado (${estadoOp.estado}). Conserva la misma operación antes de reintentar."
                )
            }
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(
                enviando = false,
                mensaje = e.message ?: "No se pudo confirmar el resultado. Conserva la misma operación antes de reintentar."
            )
        }
    }

    private fun recalcularResumen(
        b: BorradorAlerta,
        ctx: com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
    ): ResumenAlerta {
        val fechaBase = ctx.fechaServidor.take(10)
        val ref = ctx.referencias.firstOrNull {
            it.codigoTipo == b.codigoTipo && (b.codigoTipo != "OTRO" || it.servicioOtro == b.servicioOtro)
        }
        return ResumenAlerta(
            fechaBase = fechaBase,
            kmBase = ctx.kmActual,
            idLectura = ctx.idLecturaActual,
            fechaObjetivo = if (CriterioAlerta.usaTiempo(b.criterio))
                calcularFechaObjetivo(fechaBase, b.unidadTiempo, b.cantidadTiempo.toIntOrNull() ?: 1)
            else null,
            kmObjetivo = if (CriterioAlerta.usaKm(b.criterio) && ctx.kmActual != null)
                sumarKmSeguro(ctx.kmActual, b.intervaloKm.toIntOrNull() ?: 0)
            else null,
            referencia = ref
        )
    }

    companion object {
        fun validarBorrador(b: BorradorAlerta, kmActual: Int? = null): Map<String, String> = buildMap {
            if (b.codigoTipo.isBlank()) put("codigoTipo", "Selecciona un tipo de mantenimiento")
            if (CriterioAlerta.usaTiempo(b.criterio) && b.cantidadTiempo.toIntOrNull()?.let { it > 0 } != true)
                put("cantidadTiempo", "Indica una cantidad de tiempo válida")
            if (CriterioAlerta.usaTiempo(b.criterio) && b.unidadTiempo !in setOf("SEMANAS", "MESES"))
                put("unidadTiempo", "Elige semanas o meses")
            if (CriterioAlerta.usaKm(b.criterio) && b.intervaloKm.toIntOrNull()?.let { it > 0 } != true)
                put("intervaloKm", "Indica un intervalo de kilómetros válido")
            if (CriterioAlerta.usaKm(b.criterio) && kmActual == null)
                put("intervaloKm", "Se necesita kilometraje vigente para este criterio")
            if (CriterioAlerta.usaKm(b.criterio) && kmActual != null) {
                val intervalo = b.intervaloKm.toIntOrNull()
                if (intervalo != null && intervalo > 0 && sumarKmSeguro(kmActual, intervalo) == null) {
                    put("intervaloKm", "El kilometraje objetivo supera el máximo permitido.")
                }
            }
            if (b.codigoTipo == "OTRO" && b.servicioOtro.isBlank())
                put("servicioOtro", "Describe el servicio OTRO")
        }

        /** Suma km_base + intervalo evitando desbordamiento de Int (rango INTEGER). */
        fun sumarKmSeguro(kmBase: Int, intervalo: Int): Int? {
            val suma = kmBase.toLong() + intervalo.toLong()
            return if (suma > Int.MAX_VALUE) null else suma.toInt()
        }

        fun calcularFechaObjetivo(fechaBase: String, unidad: String, cantidad: Int): String {
            val base = LocalDate.parse(fechaBase.take(10))
            val obj = when (unidad) {
                "SEMANAS" -> base.plusWeeks(cantidad.toLong())
                else -> base.plusMonths(cantidad.toLong())
            }
            return obj.toString()
        }
    }
}
