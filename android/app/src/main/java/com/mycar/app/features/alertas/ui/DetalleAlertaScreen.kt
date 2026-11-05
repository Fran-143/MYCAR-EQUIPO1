package com.mycar.app.features.alertas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.DetalleAlerta
import com.mycar.app.features.alertas.domain.SolicitudAtenderAlerta
import com.mycar.app.ui.theme.MyCarStatusPendiente
import com.mycar.app.ui.theme.MyCarStatusProgramada
import com.mycar.app.ui.theme.MyCarStatusProxima
import com.mycar.app.ui.theme.MyCarStatusRealizada
import com.mycar.app.ui.theme.MyCarStatusVencida
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FaseDetalleAlerta { CARGANDO, LISTO, ERROR }

data class EstadoDetalleAlerta(
    val fase: FaseDetalleAlerta = FaseDetalleAlerta.CARGANDO,
    val detalle: DetalleAlerta? = null,
    val mensaje: String? = null,
    val ocupado: Boolean = false,
    val aviso: String? = null
)

class DetalleAlertaViewModel(
    private val repository: AlertasRepository,
    private val idVehiculo: Long,
    private val idAlerta: Long
) : ViewModel() {
    private val mutable = MutableStateFlow(EstadoDetalleAlerta())
    val estado = mutable.asStateFlow()

    init { cargar() }

    fun cargar() {
        mutable.value = EstadoDetalleAlerta(fase = FaseDetalleAlerta.CARGANDO)
        viewModelScope.launch {
            try {
                val d = repository.detalle(idVehiculo, idAlerta)
                mutable.value = EstadoDetalleAlerta(fase = FaseDetalleAlerta.LISTO, detalle = d)
            } catch (e: Exception) {
                mutable.value = EstadoDetalleAlerta(
                    fase = FaseDetalleAlerta.ERROR,
                    mensaje = e.message ?: "No se pudo cargar el detalle."
                )
            }
        }
    }

    fun atender(km: Int, fecha: String, costo: String?, obs: String?) {
        val actual = mutable.value
        if (actual.ocupado || actual.detalle?.puedeAtender != true) return
        mutable.value = actual.copy(ocupado = true, aviso = null)
        viewModelScope.launch {
            try {
                repository.atender(idVehiculo, idAlerta, SolicitudAtenderAlerta(fecha, km, costo, obs))
                cargar()
                mutable.value = mutable.value.copy(aviso = "Alerta marcada como REALIZADA.")
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    aviso = e.message ?: "No se pudo atender la alerta."
                )
            }
        }
    }

    fun anular() {
        val actual = mutable.value
        if (actual.ocupado || actual.detalle?.puedeAnular != true) return
        mutable.value = actual.copy(ocupado = true, aviso = null)
        viewModelScope.launch {
            try {
                repository.anular(idVehiculo, idAlerta)
                mutable.value = EstadoDetalleAlerta(
                    fase = FaseDetalleAlerta.LISTO,
                    mensaje = "Alerta anulada.",
                    aviso = "La alerta quedó ANULADA."
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    aviso = e.message ?: "No se pudo anular la alerta."
                )
            }
        }
    }
}

@Composable
fun DetalleAlertaRoute(
    repository: AlertasRepository,
    idVehiculo: Long,
    idAlerta: Long,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: DetalleAlertaViewModel = viewModel(key = "rf24-$idVehiculo-$idAlerta") {
        DetalleAlertaViewModel(repository, idVehiculo, idAlerta)
    }
    val estado by vm.estado.collectAsStateWithLifecycle()
    DetalleAlertaScreen(
        estado = estado,
        onVolver = onVolver,
        onRecargar = vm::cargar,
        onAtender = vm::atender,
        onAnular = vm::anular,
        modifier = modifier
    )
}

@Composable
fun DetalleAlertaScreen(
    estado: EstadoDetalleAlerta,
    onVolver: () -> Unit,
    onRecargar: () -> Unit,
    onAtender: (Int, String, String?, String?) -> Unit,
    onAnular: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var dialogAtender by remember { mutableStateOf(false) }
    var dialogAnular by remember { mutableStateOf(false) }
    var kmTexto by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background,
        topBar = {
            Surface(color = colors.surface, shadowElevation = 1.dp) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onVolver) { Text("Volver") }
                    Text(
                        "Detalle de alerta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (estado.fase) {
                FaseDetalleAlerta.CARGANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Consultando alerta…")
                }
                FaseDetalleAlerta.ERROR -> {
                    Text(estado.mensaje ?: "No se pudo cargar el detalle.")
                    OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
                }
                FaseDetalleAlerta.LISTO -> {
                    estado.aviso?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                    }
                    val d = estado.detalle
                    if (d == null) {
                        Text(estado.mensaje ?: "Sin datos de la alerta.")
                    } else {
                        ContenidoDetalleAlerta(d)
                        if (d.puedeAtender || d.puedeAnular) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (d.puedeAtender) {
                                    Button(
                                        onClick = {
                                            kmTexto = d.kmObjetivo?.toString().orEmpty()
                                            dialogAtender = true
                                        },
                                        enabled = !estado.ocupado,
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Registrar como realizado") }
                                }
                                if (d.puedeAnular) {
                                    OutlinedButton(
                                        onClick = { dialogAnular = true },
                                        enabled = !estado.ocupado,
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Anular") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogAtender) {
        AlertDialog(
            onDismissRequest = { dialogAtender = false },
            title = { Text("Registrar como realizado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se registrará un mantenimiento y la alerta pasará a REALIZADA.")
                    OutlinedTextField(
                        value = kmTexto,
                        onValueChange = { kmTexto = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Km del servicio") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val km = kmTexto.toIntOrNull()
                    if (km != null && km > 0) {
                        dialogAtender = false
                        onAtender(km, LocalDate.now().toString(), null, null)
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { dialogAtender = false }) { Text("Cancelar") } }
        )
    }
    if (dialogAnular) {
        AlertDialog(
            onDismissRequest = { dialogAnular = false },
            title = { Text("¿Anular alerta?") },
            text = { Text("La alerta quedará ANULADA. El mantenimiento histórico de referencia no se anula.") },
            confirmButton = {
                TextButton(onClick = { dialogAnular = false; onAnular() }) { Text("Anular") }
            },
            dismissButton = { TextButton(onClick = { dialogAnular = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ContenidoDetalleAlerta(d: DetalleAlerta) {
    val colors = MaterialTheme.colorScheme
    val badge = when (d.estado) {
        "PROGRAMADA" -> MyCarStatusProgramada
        "PROXIMA" -> MyCarStatusProxima
        "PENDIENTE" -> MyCarStatusPendiente
        "VENCIDA" -> MyCarStatusVencida
        "REALIZADA" -> MyCarStatusRealizada
        else -> MyCarStatusRealizada
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    d.tipo.ifBlank { d.servicioOtro ?: "Servicio" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = badge.copy(alpha = 0.15f)) {
                    Text(
                        d.estado,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badge
                    )
                }
            }
            Text(d.vehiculoEtiqueta, color = colors.onSurfaceVariant)
            Text("Categoría: ${d.categoria}", style = MaterialTheme.typography.bodySmall)
        }
    }

    Text("Objetivos e intervalos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Fila("Criterio", etiquetaCriterioDetalle(d.criterio))
    Fila("Origen de cálculo", d.origenCalculo.ifBlank { "—" })
    d.fechaObjetivo?.let { Fila("Fecha objetivo", it) }
    d.kmObjetivo?.let { Fila("Km objetivo", "%,d km".format(it).replace(',', ' ')) }
    d.kmBase?.let { Fila("Km base", "%,d km".format(it).replace(',', ' ')) }
    d.intervaloKm?.let { Fila("Intervalo km", "%,d km".format(it).replace(',', ' ')) }
    if (d.unidadTiempo != null && d.cantidadTiempo != null) {
        Fila("Intervalo tiempo", "${d.cantidadTiempo} ${d.unidadTiempo.lowercase()}")
    }
    d.fechaBase?.let { Fila("Fecha base", it) }
    d.servicioOtro?.takeIf { it.isNotBlank() }?.let { Fila("Servicio (otro)", it) }
    d.fechaEstadoUtc?.let { Fila("Último cambio de estado", it) }
    d.idMantenimientoReferencia?.let { Fila("Mantenimiento referencia", "#$it") }
    d.idMantenimientoAtencion?.let { Fila("Mantenimiento de atención", "#$it") }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun etiquetaCriterioDetalle(criterio: String): String = when (criterio) {
    "POR_TIEMPO" -> "Por tiempo"
    "POR_KILOMETRAJE" -> "Por kilometraje"
    "AMBOS" -> "Por tiempo y kilometraje"
    else -> criterio.ifBlank { "—" }
}
