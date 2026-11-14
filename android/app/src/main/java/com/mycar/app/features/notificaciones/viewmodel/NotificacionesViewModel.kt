package com.mycar.app.features.notificaciones.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.notificaciones.domain.NotificacionItem
import com.mycar.app.features.notificaciones.domain.NotificacionesRepository
import com.mycar.app.features.notificaciones.domain.ResultadoLectura
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PestanaNotificaciones(val api: String, val etiqueta: String) {
    PENDIENTES("PENDIENTES", "Pendientes"),
    HISTORIAL("HISTORIAL", "Historial")
}

enum class CategoriaNotificaciones(val api: String, val etiqueta: String) {
    TODAS("TODAS", "Todas"),
    MANTENIMIENTO("MANTENIMIENTO", "Mantenimiento"),
    SOAT("SOAT", "SOAT"),
    KILOMETRAJE("KILOMETRAJE", "Kilometraje"),
    LICENCIA("LICENCIA", "Licencia")
}

/** RF46-EA11: filtro de lectura independiente de categoría y pestaña. */
enum class FiltroLecturaNotificaciones(val api: String, val etiqueta: String) {
    TODAS("TODAS", "Todas"),
    NO_LEIDAS("NO_LEIDAS", "No leídas"),
    LEIDAS("LEIDAS", "Leídas")
}

enum class FaseNotificaciones { CARGANDO, LISTO, VACIO, ERROR }

data class EstadoNotificaciones(
    val fase: FaseNotificaciones = FaseNotificaciones.CARGANDO,
    val pestana: PestanaNotificaciones = PestanaNotificaciones.PENDIENTES,
    val categoria: CategoriaNotificaciones = CategoriaNotificaciones.TODAS,
    val filtroLectura: FiltroLecturaNotificaciones = FiltroLecturaNotificaciones.TODAS,
    val contadorPendientesGlobal: Int = 0,
    val contadorNoLeidasGlobal: Int = 0,
    val items: List<NotificacionItem> = emptyList(),
    val mensaje: String? = null,
    val abriendoId: Long? = null
)

class NotificacionesViewModel(
    private val repository: NotificacionesRepository
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoNotificaciones())
    val estado = _estado.asStateFlow()

    init { cargar() }

    fun seleccionarPestana(pestana: PestanaNotificaciones) {
        if (_estado.value.pestana == pestana) return
        _estado.update { it.copy(pestana = pestana) }
        cargar()
    }

    fun seleccionarCategoria(categoria: CategoriaNotificaciones) {
        if (_estado.value.categoria == categoria) return
        // EA10: volver a Todas no elimina el filtro de lectura.
        _estado.update { it.copy(categoria = categoria) }
        cargar()
    }

    fun seleccionarFiltroLectura(filtro: FiltroLecturaNotificaciones) {
        if (_estado.value.filtroLectura == filtro) return
        _estado.update { it.copy(filtroLectura = filtro) }
        cargar()
    }

    fun recargar() = cargar()

    fun abrir(
        item: NotificacionItem,
        onDestino: (ResultadoLectura) -> Unit,
        onDestinoInvalido: (String) -> Unit
    ) {
        if (_estado.value.abriendoId != null) return
        viewModelScope.launch {
            _estado.update { it.copy(abriendoId = item.id, mensaje = null) }
            try {
                val lectura = repository.marcarLeida(item.id)
                _estado.update { actual ->
                    actual.copy(
                        abriendoId = null,
                        items = actual.items.map { n ->
                            if (n.id == lectura.id) n.copy(leida = lectura.leida, estadoGestion = lectura.estadoGestion)
                            else n
                        }
                    )
                }
                when {
                    item.categoria == "SOAT" || item.categoria == "LICENCIA" ->
                        onDestinoInvalido(mensajeDestinoNoDisponible(item.categoria))
                    lectura.destino == "DETALLE_ALERTA"
                        && item.categoria == "MANTENIMIENTO"
                        && (lectura.idAlerta ?: 0) > 0
                        && (lectura.idVehiculo ?: 0) > 0 ->
                        onDestino(lectura)
                    else ->
                        onDestinoInvalido(
                            "Este aviso ya no tiene un detalle disponible. Revisa el historial de alertas."
                        )
                }
                cargar(silencioso = true)
            } catch (e: Exception) {
                _estado.update {
                    it.copy(
                        abriendoId = null,
                        mensaje = (e as? IOException)?.message ?: "No se pudo abrir la notificación."
                    )
                }
            }
        }
    }

    private fun cargar(silencioso: Boolean = false) {
        val pestana = _estado.value.pestana
        val categoria = _estado.value.categoria
        val filtroLectura = _estado.value.filtroLectura
        viewModelScope.launch {
            if (!silencioso) {
                _estado.update { it.copy(fase = FaseNotificaciones.CARGANDO, mensaje = null) }
            }
            try {
                val bandeja = repository.consultarBandeja(pestana.api, categoria.api, filtroLectura.api)
                _estado.update {
                    it.copy(
                        fase = if (bandeja.items.isEmpty()) FaseNotificaciones.VACIO else FaseNotificaciones.LISTO,
                        pestana = pestana,
                        categoria = categoria,
                        filtroLectura = filtroLectura,
                        contadorPendientesGlobal = bandeja.contadorPendientesGlobal,
                        contadorNoLeidasGlobal = bandeja.contadorNoLeidasGlobal,
                        items = bandeja.items,
                        mensaje = if (bandeja.items.isEmpty()) {
                            mensajeVacio(pestana, categoria, filtroLectura)
                        } else null
                    )
                }
            } catch (e: Exception) {
                if (silencioso) return@launch
                _estado.update {
                    it.copy(
                        fase = FaseNotificaciones.ERROR,
                        mensaje = (e as? IOException)?.message ?: "No se pudieron cargar las notificaciones."
                    )
                }
            }
        }
    }

    private fun mensajeVacio(
        pestana: PestanaNotificaciones,
        categoria: CategoriaNotificaciones,
        lectura: FiltroLecturaNotificaciones
    ): String {
        val lecturaTxt = when (lectura) {
            FiltroLecturaNotificaciones.TODAS -> null
            FiltroLecturaNotificaciones.NO_LEIDAS -> "no leídas"
            FiltroLecturaNotificaciones.LEIDAS -> "leídas"
        }
        return when {
            categoria != CategoriaNotificaciones.TODAS && lecturaTxt != null ->
                "Sin avisos «${categoria.etiqueta.lowercase()}» $lecturaTxt en ${pestana.etiqueta.lowercase()}."
            categoria != CategoriaNotificaciones.TODAS ->
                "Sin avisos de «${categoria.etiqueta.lowercase()}» en ${pestana.etiqueta.lowercase()}."
            lecturaTxt != null ->
                "Sin notificaciones $lecturaTxt en ${pestana.etiqueta.lowercase()}."
            pestana == PestanaNotificaciones.PENDIENTES ->
                "No tienes notificaciones pendientes."
            else ->
                "Aún no hay notificaciones en el historial."
        }
    }

    private fun mensajeDestinoNoDisponible(categoria: String): String = when (categoria) {
        "SOAT" -> "El detalle de SOAT aún no está disponible en esta versión."
        "LICENCIA" -> "El detalle de licencia aún no está disponible en esta versión."
        else -> "Este aviso ya no tiene un detalle disponible. Revisa el historial de alertas."
    }
}
