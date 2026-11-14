package com.mycar.app.features.alertas.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.alertas.domain.AlertasRepository
import com.mycar.app.features.alertas.domain.ContactoTallerAlerta
import com.mycar.app.features.alertas.domain.DetalleAlerta
import com.mycar.app.features.alertas.domain.etiquetaServicioAlerta
import com.mycar.app.features.talleres.domain.Taller
import com.mycar.app.features.talleres.domain.TalleresRepository
import com.mycar.app.features.talleres.domain.PoliticaContactoTaller
import com.mycar.app.features.talleres.domain.WhatsAppEnlace
import com.mycar.app.ui.theme.MyCarStatusPendiente
import com.mycar.app.ui.theme.MyCarStatusProgramada
import com.mycar.app.ui.theme.MyCarStatusProxima
import com.mycar.app.ui.theme.MyCarStatusRealizada
import com.mycar.app.ui.theme.MyCarStatusVencida
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val Fondo = Color(0xFFF0F5FA)
private val Azul = Color(0xFF12285F)
private val Celeste = Color(0xFF007E98)
private val RojoAnular = Color(0xFFC62828)
private val CampoLectura = Color(0xFFE8F4FA)

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

    fun anular(motivo: String) {
        val actual = mutable.value
        if (actual.ocupado || actual.detalle?.puedeAnular != true) return
        val motivoOk = motivo.trim()
        if (motivoOk.isEmpty()) {
            mutable.value = actual.copy(aviso = "Indica el motivo de la anulación.")
            return
        }
        if (motivoOk.length > 48) {
            mutable.value = actual.copy(aviso = "El motivo no puede superar 48 caracteres.")
            return
        }
        mutable.value = actual.copy(ocupado = true, aviso = null)
        viewModelScope.launch {
            try {
                repository.anular(idVehiculo, idAlerta, motivoOk, "")
                val d = runCatching { repository.detalle(idVehiculo, idAlerta) }.getOrNull()
                mutable.value = EstadoDetalleAlerta(
                    fase = FaseDetalleAlerta.LISTO,
                    detalle = d,
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

    fun elegirContacto(idTaller: Long) {
        val actual = mutable.value
        if (actual.ocupado) return
        mutable.value = actual.copy(ocupado = true, aviso = null)
        viewModelScope.launch {
            try {
                val d = repository.elegirContactoTaller(idVehiculo, idAlerta, idTaller)
                mutable.value = EstadoDetalleAlerta(
                    fase = FaseDetalleAlerta.LISTO,
                    detalle = d,
                    aviso = "Taller de contacto actualizado solo para esta alerta."
                )
            } catch (e: Exception) {
                mutable.value = mutable.value.copy(
                    ocupado = false,
                    aviso = e.message ?: "No se pudo cambiar el taller de contacto."
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
    /** RF24: abrir RF17 DESDE_ALERTA en lugar del diálogo rápido. */
    onRegistrarComoRealizado: (DetalleAlerta) -> Unit = {},
    talleresRepository: TalleresRepository? = null,
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
        onRegistrarComoRealizado = onRegistrarComoRealizado,
        onAnular = vm::anular,
        onElegirContacto = { idTaller -> vm.elegirContacto(idTaller) },
        talleresRepository = talleresRepository,
        modifier = modifier
    )
}

@Composable
fun DetalleAlertaScreen(
    estado: EstadoDetalleAlerta,
    onVolver: () -> Unit,
    onRecargar: () -> Unit,
    onRegistrarComoRealizado: (DetalleAlerta) -> Unit,
    onAnular: (String) -> Unit,
    onElegirContacto: (Long) -> Unit = {},
    talleresRepository: TalleresRepository? = null,
    modifier: Modifier = Modifier
) {
    var dialogAnular by remember { mutableStateOf(false) }
    var motivoAnular by remember { mutableStateOf("") }
    var dialogCambiarTaller by remember { mutableStateOf(false) }
    var avisoContacto by remember { mutableStateOf<String?>(null) }
    var telefonoFallback by remember { mutableStateOf<String?>(null) }
    var talleresDisponibles by remember { mutableStateOf<List<Taller>>(emptyList()) }
    val context = LocalContext.current

    Column(modifier.fillMaxSize().background(Fondo)) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onVolver) { Text("Volver") }
                Text(
                    "Detalle de alerta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (estado.fase) {
                FaseDetalleAlerta.CARGANDO -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = Celeste)
                    Text("Consultando alerta…", color = Azul)
                }
                FaseDetalleAlerta.ERROR -> {
                    Text(estado.mensaje ?: "No se pudo cargar el detalle.", color = Azul)
                    OutlinedButton(onClick = onRecargar) { Text("Reintentar") }
                }
                FaseDetalleAlerta.LISTO -> {
                    estado.aviso?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Celeste)
                    }
                    val d = estado.detalle
                    if (d == null) {
                        Text(estado.mensaje ?: "Sin datos de la alerta.", color = Azul)
                    } else {
                        ContenidoDetalleAlerta(d)
                        ContactoTallerAlertaCard(
                            contacto = d.contactoTaller,
                            ocupado = estado.ocupado,
                            onWhatsApp = { tel ->
                                val wa = WhatsAppEnlace.uriWhatsApp(tel)
                                if (wa == null) {
                                    avisoContacto = "Número de WhatsApp no válido para Perú."
                                    return@ContactoTallerAlertaCard
                                }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(wa))
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                } else {
                                    avisoContacto = "WhatsApp no está disponible. Puedes llamar o copiar el número."
                                    telefonoFallback = WhatsAppEnlace.normalizarPeruE164(tel)
                                }
                            },
                            onLlamar = { tel ->
                                val llamada = WhatsAppEnlace.uriLlamada(tel)
                                if (llamada == null) {
                                    avisoContacto = "Número telefónico no disponible."
                                    return@ContactoTallerAlertaCard
                                }
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(llamada)))
                            },
                            onCambiarTaller = {
                                dialogCambiarTaller = true
                                if (talleresRepository != null) {
                                    // Carga diferida en efecto abajo
                                }
                            }
                        )
                        if (d.estado == "VENCIDA" && d.puedeAtender) {
                            Text(
                                "Esta alerta está VENCIDA. Al registrar el mantenimiento se conservará la evidencia de atención tardía.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RojoAnular,
                                modifier = Modifier.testTag("rf24.aviso.vencida")
                            )
                        }
                        if (d.puedeAtender || d.puedeAnular) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (d.puedeAtender) {
                                    Button(
                                        onClick = { onRegistrarComoRealizado(d) },
                                        enabled = !estado.ocupado,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Azul),
                                        modifier = Modifier.testTag("rf24.registrar")
                                    ) { Text("Registrar como realizado") }
                                }
                                if (d.puedeAnular) {
                                    OutlinedButton(
                                        onClick = { dialogAnular = true },
                                        enabled = !estado.ocupado,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RojoAnular)
                                    ) { Text("Anular") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(dialogCambiarTaller, talleresRepository) {
        if (dialogCambiarTaller && talleresRepository != null) {
            talleresDisponibles = runCatching { talleresRepository.listarActivos() }.getOrDefault(emptyList())
        }
    }

    if (dialogCambiarTaller) {
        AlertDialog(
            onDismissRequest = { if (!estado.ocupado) dialogCambiarTaller = false },
            title = { Text("Cambiar taller", fontWeight = FontWeight.Bold, color = Azul) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La elección aplica solo a esta alerta. No modifica el mantenimiento histórico ni el favorito.")
                    if (talleresDisponibles.isEmpty()) {
                        Text("No hay talleres en tu catálogo personal.", color = Color(0xFF6881A5))
                    } else {
                        talleresDisponibles.forEach { t ->
                            TextButton(
                                onClick = {
                                    dialogCambiarTaller = false
                                    onElegirContacto(t.id)
                                },
                                enabled = !estado.ocupado,
                                modifier = Modifier.fillMaxWidth().testTag("rf24.contacto.elegir.${t.id}")
                            ) {
                                Text("${t.nombre}${if (t.esAsociado) " (Asociado)" else ""}")
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { dialogCambiarTaller = false }) { Text("Cerrar") }
            }
        )
    }

    avisoContacto?.let { msg ->
        AlertDialog(
            onDismissRequest = { avisoContacto = null; telefonoFallback = null },
            title = { Text("Contacto", fontWeight = FontWeight.Bold, color = Azul) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(msg)
                    telefonoFallback?.let { Text("Número: +$it", color = Celeste) }
                }
            },
            confirmButton = {
                val tel = telefonoFallback
                if (tel != null) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$tel")))
                        avisoContacto = null
                        telefonoFallback = null
                    }) { Text("Llamar") }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { avisoContacto = null; telefonoFallback = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (dialogAnular) {
        AlertDialog(
            onDismissRequest = { if (!estado.ocupado) dialogAnular = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("¿Anular alerta?", fontWeight = FontWeight.Bold, color = Azul) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("La alerta quedará ANULADA. El mantenimiento histórico de referencia no se anula.")
                    OutlinedTextField(
                        value = motivoAnular,
                        onValueChange = { if (it.length <= 48) motivoAnular = it },
                        label = { Text("Motivo") },
                        singleLine = true,
                        enabled = !estado.ocupado,
                        modifier = Modifier.fillMaxWidth().testTag("rf24.anular.motivo")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = motivoAnular.trim()
                        if (m.isNotEmpty()) {
                            dialogAnular = false
                            onAnular(m)
                            motivoAnular = ""
                        }
                    },
                    enabled = !estado.ocupado && motivoAnular.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = RojoAnular),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Anular") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { dialogAnular = false; motivoAnular = "" },
                    enabled = !estado.ocupado,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ContactoTallerAlertaCard(
    contacto: ContactoTallerAlerta?,
    ocupado: Boolean,
    onWhatsApp: (String) -> Unit,
    onLlamar: (String) -> Unit,
    onCambiarTaller: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("rf24.contacto"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Contactar taller", fontWeight = FontWeight.Bold, color = Celeste)
            Text(
                "Contacto voluntario: no marca la alerta como atendida ni envía datos al taller.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6881A5)
            )
            if (contacto == null || contacto.origenSugerencia == "NINGUNO" || contacto.idTaller == null) {
                Text("Sin taller sugerido.", color = Azul)
                TextButton(onClick = onCambiarTaller, enabled = !ocupado, modifier = Modifier.testTag("rf24.contacto.cambiar")) {
                    Text("Elegir taller")
                }
            } else {
                Text(contacto.nombre ?: "Taller", fontWeight = FontWeight.SemiBold, color = Azul)
                Text(
                    when (contacto.origenSugerencia) {
                        "EXPLICITO" -> "Elegido para esta alerta"
                        "MANTENIMIENTO_REF" -> "Del mantenimiento de origen"
                        "FAVORITO" -> "Tu taller favorito"
                        else -> contacto.origenSugerencia
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6881A5)
                )
                val puedeWa = PoliticaContactoTaller.puedeWhatsApp(contacto)
                val puedeLlamar = PoliticaContactoTaller.puedeLlamar(contacto)
                if (!puedeWa && !puedeLlamar) {
                    val motivo = when {
                        !contacto.origen.equals("ASOCIADO", ignoreCase = true) ->
                            "Contacto WhatsApp/llamada solo para talleres asociados autorizados."
                        !contacto.contactoAutorizado ->
                            "Este taller asociado no tiene contacto autorizado."
                        else -> "Sin contacto disponible"
                    }
                    Text(motivo, color = RojoAnular, modifier = Modifier.testTag("rf24.contacto.sin"))
                } else {
                    val tel = contacto.telefono.orEmpty()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (puedeWa) {
                            Button(
                                onClick = { onWhatsApp(tel) },
                                enabled = !ocupado,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF128C7E)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("rf24.contacto.whatsapp")
                            ) { Text("WhatsApp") }
                        }
                        if (puedeLlamar) {
                            OutlinedButton(
                                onClick = { onLlamar(tel) },
                                enabled = !ocupado,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("rf24.contacto.llamar")
                            ) { Text("Llamar") }
                        }
                    }
                }
                TextButton(onClick = onCambiarTaller, enabled = !ocupado, modifier = Modifier.testTag("rf24.contacto.cambiar")) {
                    Text("Cambiar taller")
                }
            }
        }
    }
}

@Composable
private fun ContenidoDetalleAlerta(d: DetalleAlerta) {
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    etiquetaServicioAlerta(d.codigoTipo, d.tipo, d.servicioOtro),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Azul,
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
            Text(d.vehiculoEtiqueta, color = Color(0xFF6881A5))
            Text("Categoría: ${d.categoria}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6881A5))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Objetivos e intervalos", fontWeight = FontWeight.Bold, color = Celeste)
            Fila("Criterio", etiquetaCriterioDetalle(d.criterio))
            Fila("Origen de cálculo", etiquetaOrigenDetalle(d))
            d.fechaObjetivo?.let { Fila("Fecha objetivo", it) }
            d.kmObjetivo?.let { Fila("Km objetivo", "%,d km".format(it).replace(',', ' ')) }
            d.kmBase?.let { Fila("Km base", "%,d km".format(it).replace(',', ' ')) }
            d.intervaloKm?.let { Fila("Intervalo km", "%,d km".format(it).replace(',', ' ')) }
            if (d.unidadTiempo != null && d.cantidadTiempo != null) {
                Fila("Intervalo tiempo", "${d.cantidadTiempo} ${d.unidadTiempo.lowercase()}")
            }
            d.fechaBase?.let { Fila("Fecha base", it) }
            if (d.codigoTipo == "OTRO") {
                d.servicioOtro?.takeIf { it.isNotBlank() }?.let { Fila("Servicio (otro)", it) }
            }
            d.fechaEstadoUtc?.let { Fila("Último cambio de estado", it) }
            etiquetaMantenimientoReferencia(d)?.let { Fila("Mantenimiento de referencia", it) }
            d.idMantenimientoAtencion?.let { Fila("Mantenimiento de atención", "#$it") }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, valor: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Color(0xFF6881A5))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = CampoLectura) {
            Text(valor, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Azul)
        }
    }
}

private fun etiquetaCriterioDetalle(criterio: String): String = when (criterio) {
    "POR_TIEMPO" -> "Por tiempo"
    "POR_KILOMETRAJE" -> "Por kilometraje"
    "AMBOS" -> "Por tiempo y kilometraje"
    else -> criterio.ifBlank { "—" }
}

private fun etiquetaOrigenDetalle(d: DetalleAlerta): String = when (d.origenCalculo) {
    "DESDE_HOY" -> "Desde hoy"
    "DESDE_MANTENIMIENTO" -> etiquetaMantenimientoReferencia(d)?.let { "Desde: $it" }
        ?: "Desde un mantenimiento realizado"
    else -> d.origenCalculo.ifBlank { "—" }
}

private fun etiquetaMantenimientoReferencia(d: DetalleAlerta): String? {
    if (d.idMantenimientoReferencia == null && d.fechaBase.isNullOrBlank() && d.kmBase == null) {
        return null
    }
    val nombre = etiquetaServicioAlerta(d.codigoTipo, d.tipo, d.servicioOtro)
    val partes = buildList {
        add(nombre)
        d.fechaBase?.take(10)?.takeIf { it.isNotBlank() }?.let { add(it) }
        d.kmBase?.let { add("%,d km".format(it).replace(',', ' ')) }
    }
    return partes.joinToString(" · ")
}
