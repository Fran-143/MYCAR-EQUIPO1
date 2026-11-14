package com.mycar.app.features.talleres.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mycar.app.features.talleres.domain.DatosTallerFormulario
import com.mycar.app.features.talleres.domain.ErrorTalleres
import com.mycar.app.features.talleres.domain.FiltroOrigenTaller
import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.TallerGlobal
import com.mycar.app.features.talleres.domain.TalleresRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseTalleres { CARGANDO, LISTO, FORMULARIO, CATALOGO_ASOCIADO, DETALLE, ERROR }

data class EstadoTalleres(
    val fase: FaseTalleres = FaseTalleres.CARGANDO,
    val talleres: List<Taller> = emptyList(),
    val filtro: FiltroOrigenTaller = FiltroOrigenTaller.TODOS,
    val busqueda: String = "",
    val catalogo: List<TallerGlobal> = emptyList(),
    val catalogoBusqueda: String = "",
    val editando: Taller? = null,
    val detalle: Taller? = null,
    val mensaje: String? = null,
    val ocupado: Boolean = false
) {
    val talleresFiltrados: List<Taller>
        get() {
            val q = busqueda.trim()
            if (q.isEmpty()) return talleres
            return talleres.filter {
                it.nombre.contains(q, ignoreCase = true) ||
                    (it.telefono?.contains(q) == true) ||
                    (it.direccion?.contains(q, ignoreCase = true) == true)
            }
        }
}

class TalleresViewModel(private val repository: TalleresRepository) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoTalleres())
    val estado = mutable.asStateFlow()
    /** Evita reintentos en bucle del vínculo automático al taller de sistema. */
    private var autoVinculoIntentado = false

    init { cargar() }

    fun cargar() {
        val prev = mutable.value
        mutable.value = prev.copy(fase = FaseTalleres.CARGANDO, mensaje = null)
        viewModelScope.launch {
            try {
                // Todos = asociados + manuales; el filtro de chips se aplica en el API.
                val lista = repository.listarActivos(prev.filtro)
                mutable.value = prev.copy(
                    fase = FaseTalleres.LISTO,
                    talleres = lista,
                    mensaje = null,
                    ocupado = false,
                    editando = null,
                    detalle = null
                )
                asegurarTallerSistema(lista)
            } catch (e: Exception) {
                mutable.value = prev.copy(
                    fase = FaseTalleres.ERROR,
                    mensaje = e.message ?: "No se pudieron cargar los talleres"
                )
            }
        }
    }

    /**
     * Vincula por defecto el taller oficial autorizado (Prueba MyCar) si falta,
     * y lo marca favorito solo cuando no hay un favorito MANUAL.
     */
    private suspend fun asegurarTallerSistema(listaActual: List<Taller>) {
        if (autoVinculoIntentado) return
        val tieneFavorito = listaActual.any { it.favorito }
        val prueba = listaActual.firstOrNull { esTallerPrueba(it) }
        if (prueba != null) {
            autoVinculoIntentado = true
            // Solo marcar favorito el de sistema si el usuario aún no eligió ninguno.
            if (!tieneFavorito) {
                runCatching { repository.marcarFavorito(prueba.id) }
                val refresco = repository.listarActivos(mutable.value.filtro)
                mutable.value = mutable.value.copy(talleres = refresco)
            }
            return
        }
        try {
            var cat = repository.catalogoGlobal("PRUEBA")
            if (cat.isEmpty()) cat = repository.catalogoGlobal(null)
            val objetivo = cat.firstOrNull { it.id == 3L }
                ?: cat.firstOrNull { it.nombre.contains("PRUEBA MYCAR", ignoreCase = true) }
                ?: cat.firstOrNull { it.nombre.contains("PRUEBA", ignoreCase = true) }
            if (objetivo == null) {
                autoVinculoIntentado = true
                return
            }
            val vinculado = repository.vincularAsociado(objetivo.id)
            if (!tieneFavorito) {
                runCatching { repository.marcarFavorito(vinculado.id) }
            }
            val lista = repository.listarActivos(FiltroOrigenTaller.TODOS)
            autoVinculoIntentado = true
            mutable.value = mutable.value.copy(
                fase = FaseTalleres.LISTO,
                talleres = lista,
                filtro = FiltroOrigenTaller.TODOS,
                ocupado = false,
                mensaje = null
            )
        } catch (_: Exception) {
            // Reintentará en la próxima carga si el catálogo/API no respondió.
        }
    }

    private fun esTallerPrueba(t: Taller): Boolean =
        t.esAsociado && (
            t.nombre.contains("PRUEBA MYCAR", ignoreCase = true) ||
                t.nombre.contains("Taller Prueba", ignoreCase = true) ||
                t.nombre.contains("PRUEBA", ignoreCase = true)
            )

    fun cambiarFiltro(f: FiltroOrigenTaller) {
        if (mutable.value.filtro == f) return
        mutable.value = mutable.value.copy(filtro = f)
        cargar()
    }

    fun cambiarBusqueda(q: String) {
        mutable.value = mutable.value.copy(busqueda = q)
    }

    fun abrirAlta() {
        mutable.value = mutable.value.copy(fase = FaseTalleres.FORMULARIO, editando = null, mensaje = null)
    }

    fun abrirDetalle(t: Taller) {
        mutable.value = mutable.value.copy(fase = FaseTalleres.DETALLE, detalle = t, mensaje = null)
    }

    fun abrirCatalogoAsociado(q: String? = "PRUEBA") {
        val consulta = q?.trim().orEmpty()
        mutable.value = mutable.value.copy(
            fase = FaseTalleres.CATALOGO_ASOCIADO,
            ocupado = true,
            mensaje = null,
            catalogoBusqueda = consulta,
            catalogo = emptyList()
        )
        viewModelScope.launch {
            try {
                // Primero con filtro; si vacío, listar todo el catálogo oficial.
                var cat = repository.catalogoGlobal(consulta.takeIf { it.isNotBlank() })
                if (cat.isEmpty() && consulta.isNotBlank()) {
                    cat = repository.catalogoGlobal(null)
                }
                mutable.value = mutable.value.copy(
                    fase = FaseTalleres.CATALOGO_ASOCIADO,
                    catalogo = cat,
                    ocupado = false,
                    mensaje = if (cat.isEmpty()) {
                        "No hay talleres oficiales en el catálogo. Revisa tu conexión o sesión."
                    } else null
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    fase = FaseTalleres.CATALOGO_ASOCIADO,
                    ocupado = false,
                    mensaje = e.message ?: "No se pudo cargar el catálogo asociado"
                )
            }
        }
    }

    fun buscarEnCatalogo(q: String) {
        mutable.value = mutable.value.copy(catalogoBusqueda = q, ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                val cat = repository.catalogoGlobal(q.takeIf { it.isNotBlank() })
                mutable.value = mutable.value.copy(
                    catalogo = cat,
                    ocupado = false,
                    mensaje = if (cat.isEmpty()) "Sin resultados para \"$q\"." else null
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    mensaje = e.message ?: "No se pudo buscar en el catálogo"
                )
            }
        }
    }

    fun vincularAsociado(idGlobal: Long) {
        if (mutable.value.ocupado) return
        mutable.value = mutable.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch { ejecutarVinculo(idGlobal) }
    }

    /** Atajo de prueba: busca y vincula Taller Prueba MyCar (id global 3 si está disponible). */
    fun vincularTallerPruebaMyCar() {
        if (mutable.value.ocupado) return
        mutable.value = mutable.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                var cat = repository.catalogoGlobal("PRUEBA")
                if (cat.isEmpty()) cat = repository.catalogoGlobal(null)
                val objetivo = cat.firstOrNull { it.id == 3L }
                    ?: cat.firstOrNull { it.nombre.contains("PRUEBA MYCAR", ignoreCase = true) }
                    ?: cat.firstOrNull { it.nombre.contains("PRUEBA", ignoreCase = true) }
                if (objetivo == null) {
                    mutable.value = mutable.value.copy(
                        ocupado = false,
                        fase = FaseTalleres.CATALOGO_ASOCIADO,
                        catalogo = cat,
                        mensaje = "No se encontró Taller Prueba MyCar en el catálogo."
                    )
                    return@launch
                }
                ejecutarVinculo(objetivo.id)
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    mensaje = e.message ?: "No se pudo vincular el taller de prueba"
                )
            }
        }
    }

    private suspend fun ejecutarVinculo(idGlobal: Long) {
        try {
            val vinculado = repository.vincularAsociado(idGlobal)
            val listaPre = repository.listarActivos(FiltroOrigenTaller.TODOS)
            if (listaPre.none { it.favorito }) {
                runCatching { repository.marcarFavorito(vinculado.id) }
            }
            val lista = repository.listarActivos(FiltroOrigenTaller.TODOS)
            mutable.value = EstadoTalleres(
                fase = FaseTalleres.LISTO,
                talleres = lista,
                filtro = FiltroOrigenTaller.TODOS,
                busqueda = "",
                mensaje = "Taller asociado vinculado."
            )
        } catch (e: ErrorTalleres) {
            mutable.value = mutable.value.copy(ocupado = false, mensaje = mensajeApi(e))
        } catch (e: Exception) {
            mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo vincular")
        }
    }

    fun abrirEdicion(t: Taller) {
        if (!t.editable) {
            mutable.value = mutable.value.copy(mensaje = "Los talleres asociados son de solo lectura")
            return
        }
        mutable.value = mutable.value.copy(fase = FaseTalleres.FORMULARIO, editando = t, mensaje = null)
    }

    fun cancelarFormulario() {
        mutable.value = mutable.value.copy(fase = FaseTalleres.LISTO, editando = null, detalle = null, mensaje = null)
    }

    fun guardar(datos: DatosTallerFormulario) {
        if (mutable.value.ocupado) return
        mutable.value = mutable.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                val edit = mutable.value.editando
                if (edit == null) repository.registrar(datos)
                else repository.actualizar(edit.id, datos.copy(versionFila = edit.versionFila))
                val lista = repository.listarActivos(mutable.value.filtro)
                mutable.value = EstadoTalleres(
                    fase = FaseTalleres.LISTO,
                    talleres = lista,
                    filtro = mutable.value.filtro,
                    busqueda = mutable.value.busqueda,
                    mensaje = "Taller guardado"
                )
            } catch (e: ErrorTalleres) {
                if (e.codigo == "TALLER_NO_DISPONIBLE") {
                    val lista = try { repository.listarActivos(mutable.value.filtro) } catch (_: Exception) { emptyList() }
                    mutable.value = EstadoTalleres(
                        fase = FaseTalleres.LISTO,
                        talleres = lista,
                        filtro = mutable.value.filtro,
                        busqueda = mutable.value.busqueda,
                        mensaje = mensajeApi(e)
                    )
                } else {
                    mutable.value = mutable.value.copy(ocupado = false, mensaje = mensajeApi(e))
                }
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo guardar")
            }
        }
    }

    fun retirar(t: Taller) {
        if (mutable.value.ocupado) return
        val eraFavorito = t.favorito
        mutable.value = mutable.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                repository.retirar(t.id, t.versionFila)
                val lista = repository.listarActivos(mutable.value.filtro)
                val msg = when {
                    t.esAsociado -> "Taller retirado de Mis talleres"
                    eraFavorito && lista.none { it.favorito } ->
                        "Ya no tienes un taller favorito seleccionado"
                    else -> "Taller eliminado"
                }
                mutable.value = EstadoTalleres(
                    fase = FaseTalleres.LISTO,
                    talleres = lista,
                    filtro = mutable.value.filtro,
                    busqueda = mutable.value.busqueda,
                    mensaje = msg
                )
            } catch (e: ErrorTalleres) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = mensajeApi(e))
                if (e.codigo == "TALLER_NO_DISPONIBLE") cargar()
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo eliminar")
                cargar()
            }
        }
    }

    fun alternarFavorito(t: Taller) {
        if (mutable.value.ocupado) return
        mutable.value = mutable.value.copy(ocupado = true, mensaje = null)
        viewModelScope.launch {
            try {
                if (t.favorito) repository.desmarcarFavorito(t.id) else repository.marcarFavorito(t.id)
                val lista = repository.listarActivos(mutable.value.filtro)
                val actualizado = lista.find { it.id == t.id }
                mutable.value = mutable.value.copy(
                    fase = if (mutable.value.fase == FaseTalleres.DETALLE) FaseTalleres.DETALLE else FaseTalleres.LISTO,
                    talleres = lista,
                    detalle = actualizado ?: mutable.value.detalle,
                    ocupado = false,
                    mensaje = if (!t.favorito) null else if (lista.none { it.favorito })
                        "Ya no tienes un taller favorito seleccionado" else null
                )
            } catch (e: ErrorTalleres) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = mensajeApi(e))
                if (e.codigo == "TALLER_NO_DISPONIBLE" || e.codigo == "FAVORITO_NO_ACTUALIZADO") cargar()
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo actualizar favorito")
                cargar()
            }
        }
    }

    private fun mensajeApi(e: ErrorTalleres): String = when (e.codigo) {
        "NOMBRE_DUPLICADO" -> "Ya tienes un taller registrado con este nombre"
        "TELEFONO_DUPLICADO" -> "Ya tienes un taller manual con ese teléfono"
        "TALLER_YA_VINCULADO" -> "Este taller ya está en Mis talleres"
        "TALLER_ASOCIADO_SOLO_LECTURA" -> "Los talleres asociados a MyCar no se pueden modificar"
        "TALLER_NO_DISPONIBLE" -> e.message?.takeIf { it.isNotBlank() } ?: "Este taller ya no está disponible"
        "FAVORITO_NO_ACTUALIZADO" -> "No se pudo actualizar el taller favorito, intenta nuevamente"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación"
    }
}
