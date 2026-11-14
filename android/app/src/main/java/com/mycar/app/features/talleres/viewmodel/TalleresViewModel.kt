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

    init { cargar() }

    fun cargar() {
        val prev = mutable.value
        mutable.value = prev.copy(fase = FaseTalleres.CARGANDO, mensaje = null)
        viewModelScope.launch {
            try {
                val lista = repository.listarActivos(prev.filtro)
                mutable.value = prev.copy(
                    fase = FaseTalleres.LISTO,
                    talleres = lista,
                    mensaje = null,
                    ocupado = false,
                    editando = null,
                    detalle = null
                )
            } catch (e: Exception) {
                mutable.value = prev.copy(
                    fase = FaseTalleres.ERROR,
                    mensaje = e.message ?: "No se pudieron cargar los talleres"
                )
            }
        }
    }

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

    fun abrirCatalogoAsociado(q: String? = null) {
        mutable.value = mutable.value.copy(
            fase = FaseTalleres.CATALOGO_ASOCIADO,
            ocupado = true,
            mensaje = null,
            catalogoBusqueda = q.orEmpty()
        )
        viewModelScope.launch {
            try {
                val cat = repository.catalogoGlobal(q?.takeIf { it.isNotBlank() })
                mutable.value = mutable.value.copy(
                    fase = FaseTalleres.CATALOGO_ASOCIADO,
                    catalogo = cat,
                    ocupado = false
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    fase = FaseTalleres.LISTO,
                    ocupado = false,
                    mensaje = e.message ?: "No se pudo cargar el catálogo asociado"
                )
            }
        }
    }

    fun buscarEnCatalogo(q: String) {
        mutable.value = mutable.value.copy(catalogoBusqueda = q, ocupado = true)
        viewModelScope.launch {
            try {
                val cat = repository.catalogoGlobal(q.takeIf { it.isNotBlank() })
                mutable.value = mutable.value.copy(catalogo = cat, ocupado = false)
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
        viewModelScope.launch {
            try {
                repository.vincularAsociado(idGlobal)
                val lista = repository.listarActivos(mutable.value.filtro)
                mutable.value = EstadoTalleres(
                    fase = FaseTalleres.LISTO,
                    talleres = lista,
                    filtro = mutable.value.filtro,
                    busqueda = mutable.value.busqueda,
                    mensaje = "Taller asociado agregado"
                )
            } catch (e: ErrorTalleres) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = mensajeApi(e))
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(ocupado = false, mensaje = e.message ?: "No se pudo vincular")
            }
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
