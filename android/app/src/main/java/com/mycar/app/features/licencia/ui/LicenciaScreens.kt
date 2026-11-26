package com.mycar.app.features.licencia.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.licencia.domain.LicenciaFormulario
import com.mycar.app.features.licencia.domain.LicenciaRepository
import com.mycar.app.features.licencia.viewmodel.*
import com.mycar.app.features.soat.domain.SoatFormulario
import com.mycar.app.features.soat.ui.SoatBotonPrincipal
import com.mycar.app.features.soat.ui.SoatBotonSecundario
import com.mycar.app.features.soat.ui.SoatCampoFecha
import com.mycar.app.features.soat.ui.leerUriSoat
import com.mycar.app.ui.theme.*

@Composable
fun LicenciaRoute(
    repository: LicenciaRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vm: LicenciaViewModel = viewModel(
        key = "licencia",
        factory = remember(repository) {
            LicenciaViewModel.factory(repository) { uri -> leerUriSoat(context, uri) }
        }
    )
    val estado by vm.estado.collectAsState()
    DisposableEffect(Unit) { onDispose { vm.ocultarSensibles() } }
    LaunchedEffect(estado.pantalla) {
        if (estado.pantalla == PantallaLicencia.SESION) onSesionInvalida()
    }
    val salirAlPadre = estado.pantalla in setOf(
        PantallaLicencia.DETALLE, PantallaLicencia.VACIA, PantallaLicencia.CARGANDO,
        PantallaLicencia.ERROR, PantallaLicencia.SESION, PantallaLicencia.EXITO
    )
    val alVolver: () -> Unit = {
        if (salirAlPadre) onVolver() else vm.volver()
    }
    BackHandler(onBack = alVolver)
    Column(
        modifier
            .fillMaxSize()
            .background(MyCarBg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = alVolver) { Text("Volver") }
            Text("Licencia de conducir", fontWeight = FontWeight.Bold, color = MyCarNavy, modifier = Modifier.weight(1f))
        }
        estado.mensaje?.let { Text(it, color = Color(0xFFC62828)) }
        when (estado.pantalla) {
            PantallaLicencia.CARGANDO -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = MyCarTeal)
            PantallaLicencia.VACIA -> {
                Text("Aún no registras tu licencia.", color = MyCarMuted)
                SoatBotonPrincipal("Registrar licencia", vm::abrirRegistrar)
            }
            PantallaLicencia.REGISTRAR_DATOS, PantallaLicencia.EDITAR, PantallaLicencia.RENOVAR ->
                FormularioLicencia(estado, vm)
            PantallaLicencia.REGISTRAR_FOTOS, PantallaLicencia.EDITAR_FOTOS ->
                FotosLicencia(estado, vm)
            PantallaLicencia.EXITO -> ExitoLicencia(estado, vm)
            PantallaLicencia.DETALLE -> DetalleLicencia(estado, vm)
            PantallaLicencia.ERROR -> {
                Text(estado.mensaje ?: "Error", color = Color(0xFFC62828))
                SoatBotonSecundario("Reintentar", vm::cargar)
            }
            PantallaLicencia.SESION -> Text("Sesión requerida.", color = MyCarMuted)
        }
    }
}

@Composable
private fun ExitoLicencia(estado: EstadoLicencia, vm: LicenciaViewModel) {
    Text(estado.mensajeExito ?: "Listo.", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
    Text(
        "Puedes consultarla, editarla, renovarla cuando venza y activar el recordatorio desde el detalle.",
        color = MyCarMuted
    )
    SoatBotonPrincipal("Ver mi licencia", vm::irADetalleTrasExito)
}

@Composable
private fun FormularioLicencia(estado: EstadoLicencia, vm: LicenciaViewModel) {
    val renovar = estado.pantalla == PantallaLicencia.RENOVAR
    Text(
        when (estado.pantalla) {
            PantallaLicencia.EDITAR -> "Editar licencia"
            PantallaLicencia.RENOVAR -> "Renovar licencia"
            else -> "Registrar licencia"
        },
        fontWeight = FontWeight.Bold,
        color = MyCarNavy
    )
    Text("Categoría", color = MyCarMuted)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LicenciaFormulario.CATEGORIAS.forEach { cat ->
            FilterChip(
                selected = estado.categoria == cat,
                onClick = { vm.actualizarCampo(categoria = cat) },
                label = { Text(cat) }
            )
        }
    }
    estado.errores["categoria"]?.let { Text(it, color = Color(0xFFC62828)) }
    OutlinedTextField(
        value = estado.numero,
        onValueChange = { vm.actualizarCampo(numero = it) },
        label = { Text(if (renovar) "Número nuevo (opcional)" else "Número de licencia *") },
        placeholder = { Text(LicenciaFormulario.PLACEHOLDER_NUMERO) },
        supportingText = {
            Text("Formato: 1 letra + 8 dígitos, sin guiones. Ejemplo: Q12345678")
        },
        isError = estado.errores.containsKey("numero"),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
    estado.errores["numero"]?.let { Text(it, color = Color(0xFFC62828)) }
    SoatCampoFecha(
        etiqueta = "Fecha de emisión *",
        valorUi = estado.fechaEmision,
        onChange = { vm.actualizarCampo(emision = it) },
        error = estado.errores["fechaEmision"],
        enabled = !estado.ocupado
    )
    SoatCampoFecha(
        etiqueta = "Fecha de vencimiento *",
        valorUi = estado.fechaVencimiento,
        onChange = { vm.actualizarCampo(vencimiento = it) },
        error = estado.errores["fechaVencimiento"],
        enabled = !estado.ocupado
    )
    when (estado.pantalla) {
        PantallaLicencia.REGISTRAR_DATOS -> SoatBotonPrincipal("Siguiente", vm::continuarRegistroDatos, !estado.ocupado)
        PantallaLicencia.EDITAR -> {
            SoatBotonSecundario("Editar fotografías", vm::abrirEditarFotos, !estado.ocupado)
            SoatBotonPrincipal("Guardar", vm::confirmarEditar, !estado.ocupado && estado.puedeGuardarEdicion)
        }
        PantallaLicencia.RENOVAR -> {
            Text("Fotos nuevas opcionales; no se reutilizan las anteriores.", color = MyCarMuted)
            SelectorFoto("FRONTAL", estado.fotoFrontal, estado.errorFoto, !estado.ocupado, vm)
            SelectorFoto("POSTERIOR", estado.fotoPosterior, null, !estado.ocupado, vm)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activar recordatorio al renovar", fontWeight = FontWeight.Medium)
                Switch(
                    checked = estado.recordarAlRegistrar,
                    onCheckedChange = vm::setRecordarAlRegistrar,
                    enabled = !estado.ocupado
                )
            }
            SoatBotonPrincipal("Guardar renovación", vm::confirmarRenovar, !estado.ocupado)
        }
        else -> Unit
    }
}

@Composable
private fun FotosLicencia(estado: EstadoLicencia, vm: LicenciaViewModel) {
    Text(
        if (estado.pantalla == PantallaLicencia.EDITAR_FOTOS) "Editar fotografías" else "Agregar fotografías",
        fontWeight = FontWeight.Bold,
        color = MyCarNavy
    )
    Text("Cámara, galería (JPG/PNG) o PDF · imagen hasta 5 MB, PDF hasta 10 MB.", color = MyCarMuted)
    SelectorFoto("FRONTAL", estado.fotoFrontal, estado.errorFoto, !estado.ocupado, vm)
    if (estado.pantalla == PantallaLicencia.EDITAR_FOTOS) {
        TextButton(onClick = { vm.quitarFoto("FRONTAL") }) { Text("Eliminar frontal") }
    }
    SelectorFoto("POSTERIOR", estado.fotoPosterior, null, !estado.ocupado, vm)
    if (estado.pantalla == PantallaLicencia.EDITAR_FOTOS) {
        TextButton(onClick = { vm.quitarFoto("POSTERIOR") }) { Text("Eliminar posterior") }
        SoatBotonPrincipal("Guardar fotos", vm::confirmarEditarFotos, !estado.ocupado && estado.puedeGuardarEdicion)
    } else {
        val vencido = SoatFormulario.vencimientoYaPasado(estado.fechaVencimiento)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recordatorio de vencimiento", fontWeight = FontWeight.Bold, color = MyCarNavy)
                if (vencido) {
                    Text(
                        "El vencimiento ya pasó. Puedes registrar y activar el recordatorio después desde el detalle.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Te avisaremos 30 días antes del vencimiento de tu licencia.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Activar recordatorio", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = estado.recordarAlRegistrar,
                            onCheckedChange = vm::setRecordarAlRegistrar,
                            enabled = !estado.ocupado
                        )
                    }
                }
            }
        }
        SoatBotonPrincipal("Guardar licencia", vm::confirmarRegistro, !estado.ocupado)
    }
}

@Composable
private fun SelectorFoto(
    lado: String,
    adj: AdjuntoLicencia?,
    error: String?,
    enabled: Boolean,
    vm: LicenciaViewModel
) {
    LicenciaSeccionAdjunto(
        lado = lado,
        adjunto = adj,
        error = error,
        enabled = enabled,
        onAdjunto = { vm.setFoto(lado, it) }
    )
}

@Composable
private fun DetalleLicencia(estado: EstadoLicencia, vm: LicenciaViewModel) {
    val actual = estado.consulta?.actual ?: return
    val tieneHist = estado.consulta?.tieneHistorial == true
    val puedeRenovar = LicenciaFormulario.puedeRenovar(actual.fechaVencimiento)
    if (tieneHist) {
        TabRow(selectedTabIndex = if (estado.tabHistorial) 1 else 0) {
            Tab(selected = !estado.tabHistorial, onClick = { vm.setTabHistorial(false) }, text = { Text("Información") })
            Tab(selected = estado.tabHistorial, onClick = { vm.setTabHistorial(true) }, text = { Text("Historial") })
        }
    }
    if (estado.tabHistorial && tieneHist) {
        estado.consulta?.historial.orEmpty().forEach { h ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${h.categoria} · ${h.etiqueta}", fontWeight = FontWeight.Bold, color = MyCarNavy)
                    if (h.numero.isNotBlank()) {
                        Text(
                            LicenciaFormulario.enmascararNumero(h.numero),
                            fontWeight = FontWeight.Medium,
                            color = MyCarBlue
                        )
                    }
                    Text(
                        "${SoatFormulario.formatearFecha(h.fechaEmision)} – ${SoatFormulario.formatearFecha(h.fechaVencimiento)}",
                        color = MyCarMuted
                    )
                }
            }
        }
        return
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(actual.categoria, fontWeight = FontWeight.Bold, color = MyCarNavy)
            Text(actual.estadoVigencia ?: LicenciaFormulario.estadoVigencia(actual.fechaVencimiento), color = MyCarMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (estado.numeroVisible) actual.numero else LicenciaFormulario.enmascararNumero(actual.numero),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = vm::toggleNumero) {
                    Icon(
                        if (estado.numeroVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MyCarBlue
                    )
                }
            }
            Text("Emisión: ${SoatFormulario.formatearFecha(actual.fechaEmision)}", color = MyCarMuted)
            Text("Vencimiento: ${SoatFormulario.formatearFecha(actual.fechaVencimiento)}", color = MyCarMuted)
        }
    }
    if (actual.tieneFotoFrontal) {
        FotoPrivada(
            titulo = "Foto frontal",
            revelada = estado.fotoFrontalRevelada,
            bytes = estado.fotoFrontalBytes,
            onRevelar = { vm.revelarFoto("FRONTAL") },
            onOcultar = { vm.ocultarFoto("FRONTAL") }
        )
    }
    if (actual.tieneFotoPosterior) {
        FotoPrivada(
            titulo = "Foto posterior",
            revelada = estado.fotoPosteriorRevelada,
            bytes = estado.fotoPosteriorBytes,
            onRevelar = { vm.revelarFoto("POSTERIOR") },
            onOcultar = { vm.ocultarFoto("POSTERIOR") }
        )
    }
    SoatBotonPrincipal("Editar", vm::abrirEditar)
    SoatBotonSecundario("Editar fotografías", vm::abrirEditarFotos, !estado.ocupado)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recordatorio de vencimiento", fontWeight = FontWeight.Bold, color = MyCarNavy)
            Text(
                "Aviso 30 días antes del vencimiento.",
                color = MyCarMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (actual.recordatorioActivo) "Activo" else "Inactivo",
                    fontWeight = FontWeight.Medium,
                    color = if (actual.recordatorioActivo) Color(0xFF2E7D32) else MyCarMuted
                )
                Switch(
                    checked = actual.recordatorioActivo,
                    onCheckedChange = vm::setRecordatorioActivo,
                    enabled = !estado.ocupado && !estado.recordatorioOcupado
                )
            }
        }
    }
    if (puedeRenovar) {
        SoatBotonSecundario("Renovar", vm::abrirRenovar)
    } else {
        Text(
            "Renovación disponible desde ${SoatFormulario.formatearFecha(actual.fechaVencimiento)}.",
            color = MyCarMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun FotoPrivada(
    titulo: String,
    revelada: Boolean,
    bytes: ByteArray?,
    onRevelar: () -> Unit,
    onOcultar: () -> Unit
) {
    Text(titulo, fontWeight = FontWeight.SemiBold, color = MyCarNavy)
    if (!revelada) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFE8EEF6), RoundedCornerShape(12.dp))
                .clickable(onClick = onRevelar),
            contentAlignment = Alignment.Center
        ) { Text("Toca para revelar", color = MyCarNavy, fontWeight = FontWeight.SemiBold) }
    } else {
        val bmp = remember(bytes) {
            bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        }
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = titulo,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clickable(onClick = onOcultar),
                contentScale = ContentScale.FillWidth
            )
        } else if (bytes != null && bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            Text(
                "Documento PDF adjunto (${bytes.size / 1024} KB). Toca para ocultar.",
                color = MyCarMuted,
                modifier = Modifier.clickable(onClick = onOcultar)
            )
        } else Text("Cargando…", color = MyCarMuted)
    }
}
