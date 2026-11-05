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
    val criterio: String = "POR_TIEMPO",
    val origenCalculo: String = "DESDE_HOY",
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
    val preview: ResumenAlerta? = null,
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
    idVehiculoInicial: Long,
    borradorInicial: BorradorAlerta? = null
) : ViewModel() {
    private val mutable = MutableStateFlow(
        EstadoProgramarAlerta(idVehiculoInicial, borrador = borradorInicial ?: BorradorAlerta())
    )
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun cambiarVehiculo(idVehiculo: Long) {
        if (mutable.value.enviando || mutable.value.exito != null) return
        if (idVehiculo == mutable.value.idVehiculo) return
        mutable.value = EstadoProgramarAlerta(
            idVehiculo,
            borrador = mutable.value.borrador.copy(idReferencia = null)
        )
        cargar()
    }

    fun prepararNuevaProgramacion() {
        mutable.value = EstadoProgramarAlerta(mutable.value.idVehiculo)
        cargar()
    }

    fun cargar() {
        viewModelScope.launch {
            val id = mutable.value.idVehiculo
            mutable.value = mutable.value.copy(
                cargando = true, mensaje = null, exito = null, enviando = false,
                paso = PasoProgramar.FORMULARIO, resumen = null, preview = null, basesCambiaron = false
            )
            try {
                val ctx = repository.contexto(id)
                val b = mutable.value.borrador
                mutable.value = mutable.value.copy(
                    contexto = ctx,
                    cargando = false,
                    preview = calcularPreview(b, ctx)
                )
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
        val b = cambio(mutable.value.borrador)
        val ctx = mutable.value.contexto
        mutable.value = mutable.value.copy(
            borrador = b,
            errores = emptyMap(),
            mensaje = null,
            idOperacion = null,
            paso = PasoProgramar.FORMULARIO,
            resumen = null,
            preview = if (ctx != null) calcularPreview(b, ctx) else null,
            basesCambiaron = false
        )
    }

    fun volverFormulario() {
        if (mutable.value.enviando) return
        mutable.value = mutable.value.copy(
            paso = PasoProgramar.FORMULARIO, resumen = null, mensaje = null, basesCambiaron = false
        )
    }

    fun revisar() {
        val s = mutable.value
        if (s.enviando || s.exito != null) return
        val ctx = s.contexto
        if (ctx == null) {
            mutable.value = s.copy(mensaje = "Carga el contexto antes de revisar.")
            return
        }
        val refs = referenciasElegibles(s.borrador, ctx)
        val errores = validarBorrador(s.borrador, ctx.kmActual, refs)
        if (errores.isNotEmpty()) {
            mutable.value = s.copy(errores = errores, mensaje = errores.values.firstOrNull())
            return
        }
        val resumen = calcularPreview(s.borrador, ctx)!!
        if (CriterioAlerta.usaTiempo(s.borrador.criterio) && resumen.fechaObjetivo != null) {
            val hoy = ctx.fechaServidor.take(10)
            if (!LocalDate.parse(resumen.fechaObjetivo).isAfter(LocalDate.parse(hoy))) {
                mutable.value = s.copy(
                    errores = mapOf("cantidadTiempo" to "La fecha objetivo debe ser posterior a hoy."),
                    mensaje = "La fecha objetivo debe ser posterior a hoy."
                )
                return
            }
        }
        if (CriterioAlerta.usaKm(s.borrador.criterio)) {
            val kmObj = resumen.kmObjetivo
            val vigente = ctx.kmActual
            if (kmObj == null || vigente == null || kmObj <= vigente) {
                mutable.value = s.copy(
                    errores = mapOf("intervaloKm" to "El kilometraje objetivo ya fue alcanzado."),
                    mensaje = "El kilometraje objetivo ya fue alcanzado."
                )
                return
            }
        }
        mutable.value = s.copy(
            paso = PasoProgramar.REVISION,
            borrador = s.borrador.copy(idReferencia = resumen.referencia?.id?.takeIf { it > 0 }),
            resumen = resumen,
            preview = resumen,
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
        val idVehiculo = s.idVehiculo
        mutable.value = s.copy(enviando = true, mensaje = null, idOperacion = idOperacion)
        viewModelScope.launch {
            try {
                val ctx = repository.contexto(idVehiculo)
                val b = mutable.value.borrador
                val esperado = calcularPreview(b, ctx)
                if (esperado == null || esperado.fechaBase != resumen.fechaBase
                    || esperado.kmBase != resumen.kmBase
                    || esperado.referencia?.id != resumen.referencia?.id
                ) {
                    mutable.value = mutable.value.copy(
                        enviando = false, contexto = ctx, basesCambiaron = true,
                        paso = PasoProgramar.REVISION,
                        resumen = esperado,
                        preview = esperado,
                        mensaje = "Las bases cambiaron. Revisa el resumen y confirma de nuevo.",
                        idOperacion = null
                    )
                    return@launch
                }
                val idRef = if (b.origenCalculo == "DESDE_MANTENIMIENTO") {
                    resumen.referencia?.id?.takeIf { it > 0 } ?: b.idReferencia
                } else null
                val id = repository.programar(idVehiculo, ProgramarAlertaDto(
                    idOperacion = idOperacion,
                    codigoTipoMantenimiento = b.codigoTipo,
                    criterio = b.criterio,
                    origenCalculo = b.origenCalculo,
                    unidadTiempo = b.unidadTiempo.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    cantidadTiempo = b.cantidadTiempo.toIntOrNull()?.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    intervaloKm = b.intervaloKm.toIntOrNull()?.takeIf { CriterioAlerta.usaKm(b.criterio) },
                    servicioOtro = b.servicioOtro.takeIf { b.codigoTipo == "OTRO" },
                    idMantenimientoReferencia = idRef,
                    fechaBaseConfirmada = resumen.fechaBase.takeIf { CriterioAlerta.usaTiempo(b.criterio) },
                    kmBaseConfirmado = resumen.kmBase,
                    idLecturaConfirmada = resumen.idLectura.takeIf { b.origenCalculo == "DESDE_HOY" }
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
                            resumen = calcularPreview(b, ctx),
                            preview = calcularPreview(b, ctx),
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
                    enviando = false, exito = estadoOp.idAlerta, mensaje = null, idOperacion = null,
                    paso = PasoProgramar.EXITO, basesCambiaron = false
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

    companion object {
        fun referenciasElegibles(
            b: BorradorAlerta,
            ctx: com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
        ): List<ReferenciaMantenimientoAlerta> =
            ctx.referencias.filter {
                it.codigoTipo == b.codigoTipo &&
                    (b.codigoTipo != "OTRO" || it.servicioOtro == b.servicioOtro)
            }

        fun calcularPreview(
            b: BorradorAlerta,
            ctx: com.mycar.app.features.alertas.domain.ContextoProgramarAlerta
        ): ResumenAlerta? {
            if (b.codigoTipo.isBlank()) return null
            val refs = referenciasElegibles(b, ctx)
            val ref = if (b.origenCalculo == "DESDE_MANTENIMIENTO") {
                refs.firstOrNull { it.id == b.idReferencia }
            } else null
            if (b.origenCalculo == "DESDE_MANTENIMIENTO" && ref == null) {
                return ResumenAlerta(
                    fechaBase = "—",
                    kmBase = null,
                    idLectura = null,
                    fechaObjetivo = null,
                    kmObjetivo = null,
                    referencia = null
                )
            }
            val fechaBase = when (b.origenCalculo) {
                "DESDE_MANTENIMIENTO" -> ref!!.fecha.take(10)
                else -> ctx.fechaServidor.take(10)
            }
            val kmBase = when {
                !CriterioAlerta.usaKm(b.criterio) -> null
                b.origenCalculo == "DESDE_MANTENIMIENTO" -> ref?.kmServicio
                else -> ctx.kmActual
            }
            val cantidad = b.cantidadTiempo.toIntOrNull()
            val intervalo = b.intervaloKm.toIntOrNull()
            val fechaObj = if (CriterioAlerta.usaTiempo(b.criterio) && cantidad != null && cantidad > 0) {
                calcularFechaObjetivo(fechaBase, b.unidadTiempo, cantidad)
            } else null
            val kmObj = if (CriterioAlerta.usaKm(b.criterio) && kmBase != null && intervalo != null && intervalo > 0) {
                sumarKmSeguro(kmBase, intervalo)
            } else null
            return ResumenAlerta(
                fechaBase = fechaBase,
                kmBase = kmBase,
                idLectura = ctx.idLecturaActual.takeIf { b.origenCalculo == "DESDE_HOY" },
                fechaObjetivo = fechaObj,
                kmObjetivo = kmObj,
                referencia = ref
            )
        }

        fun validarBorrador(
            b: BorradorAlerta,
            kmActual: Int? = null,
            refs: List<ReferenciaMantenimientoAlerta> = emptyList()
        ): Map<String, String> = buildMap {
            if (b.codigoTipo.isBlank()) put("codigoTipo", "Selecciona un tipo de servicio")
            if (b.origenCalculo !in setOf("DESDE_HOY", "DESDE_MANTENIMIENTO")) {
                put("origenCalculo", "Elige desde cuándo calcular")
            }
            if (b.origenCalculo == "DESDE_MANTENIMIENTO") {
                if (refs.isEmpty()) {
                    put("idReferencia", "No hay mantenimientos registrados para este vehículo y servicio")
                } else if (b.idReferencia == null || refs.none { it.id == b.idReferencia }) {
                    put("idReferencia", "Selecciona el mantenimiento registrado")
                }
            }
            if (CriterioAlerta.usaTiempo(b.criterio) && b.cantidadTiempo.toIntOrNull()?.let { it > 0 } != true)
                put("cantidadTiempo", "Indica una cantidad de tiempo válida")
            if (CriterioAlerta.usaTiempo(b.criterio) && b.unidadTiempo !in setOf("SEMANAS", "MESES"))
                put("unidadTiempo", "Elige semanas o meses")
            if (CriterioAlerta.usaKm(b.criterio) && b.intervaloKm.toIntOrNull()?.let { it > 0 } != true)
                put("intervaloKm", "Indica un intervalo de kilómetros válido")
            if (CriterioAlerta.usaKm(b.criterio) && b.origenCalculo == "DESDE_HOY" && kmActual == null)
                put("intervaloKm", "Se necesita kilometraje vigente para este criterio")
            if (b.codigoTipo == "OTRO" && b.servicioOtro.isBlank())
                put("servicioOtro", "Describe el servicio OTRO")
        }

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
