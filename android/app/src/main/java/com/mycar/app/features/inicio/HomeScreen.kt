package com.mycar.app.features.inicio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycar.app.R
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import com.mycar.app.features.fotos.ui.ImagenVehiculoPredeterminada
import com.mycar.app.features.fotos.ui.MiniaturaFotoVehiculo
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.notificaciones.domain.NotificacionesBadgeStore
import com.mycar.app.ui.AvatarCabecera
import com.mycar.app.ui.BadgeNoLeidas
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarStatusProxima
import com.mycar.app.ui.theme.MyCarStatusVencida
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(estado: EstadoResumenInicio, onSeleccionar: (Long)->Unit,
    onVehiculos: ()->Unit, onMantenimientos: (Long?)->Unit, onAlertas: ()->Unit,
    onPerfil: ()->Unit, onNotificaciones: ()->Unit, onRecargar: ()->Unit,
    onVerVehiculo: (Long)->Unit, onActualizarKm: (Long)->Unit,
    onTalleres: (Long?)->Unit = {},
    onVerDetalleAlerta: (Long)->Unit = {},
    fotosRepository: FotoPersonalizadaRepository? = null,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    modifier: Modifier=Modifier) {
    var desplegado by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val fondoInicio = scheme.background
    val tinta = scheme.onSurface
    val acento = scheme.primary
    val muted = scheme.onSurfaceVariant
    val superficie = scheme.surface
    Column(modifier.fillMaxSize().background(fondoInicio)
        .statusBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),contentDescription=null,
                tint=tinta,modifier=Modifier.size(36.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color=tinta,fontWeight=FontWeight.ExtraBold)) { append("My") }
                withStyle(SpanStyle(color=acento,fontWeight=FontWeight.ExtraBold)) { append("Car") }
            },fontSize=27.sp,modifier=Modifier.weight(1f))
            val noLeidas by NotificacionesBadgeStore.noLeidas.collectAsStateWithLifecycle()
            IconButton(onClick=onNotificaciones) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        painterResource(R.drawable.ic_inicio_campana),
                        contentDescription = "Notificaciones",
                        tint = tinta,
                        modifier = Modifier.size(24.dp)
                    )
                    BadgeNoLeidas(noLeidas, Modifier.offset(x = 6.dp, y = (-4).dp))
                }
            }
            FilledTonalIconButton(onClick=onPerfil) {
                AvatarCabecera(avatarBytes = avatarBytes, iniciales = iniciales)
            }
        }
        Column {
            Text(estado.nombreUsuario?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola",
                fontSize=25.sp,fontWeight=FontWeight.Bold,color=tinta,modifier=Modifier.semantics { heading() })
            Text("Tu vehículo, al día",color=muted)
        }
        when(estado.fase) {
            FaseInicio.SIN_SESION -> TarjetaInicio {
                Text("Tu resumen en un solo lugar",fontWeight=FontWeight.Bold,color=tinta)
                Text(estado.mensaje ?: "Inicia sesión para consultar tus vehículos y mantenimientos.")
                Button(onClick=onVehiculos) { Text("Iniciar sesión") }
            }
            FaseInicio.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Cargando tu resumen…",color=tinta)
            }
            FaseInicio.ERROR, FaseInicio.INCONSISTENTE -> TarjetaInicio {
                Text(estado.mensaje ?: "No se pudo cargar el resumen.")
                Button(onClick=onRecargar) { Text("Reintentar") }
                TextButton(onClick=onVehiculos) { Text("Ver mis vehículos") }
                if (estado.mensaje?.contains("inactivo", ignoreCase = true) == true) {
                    TextButton(onClick={ onMantenimientos(null) }) { Text("Ir a Historial") }
                }
            }
            FaseInicio.SIN_VEHICULOS -> TarjetaInicio {
                Text("Aún no tienes vehículos activos",fontWeight=FontWeight.Bold,color=tinta)
                Text("Agrega tu vehículo o consulta tus vehículos registrados.")
                Button(onClick=onVehiculos) { Text("Ir a Vehículos") }
            }
            FaseInicio.LISTO -> {
                val v=estado.vehiculo
                if(v!=null) {
                    Column {
                        Text("Mis vehículos",style=MaterialTheme.typography.labelLarge,color=muted)
                        Box {
                            OutlinedCard(onClick={ desplegado=true },modifier=Modifier.fillMaxWidth(),
                                colors=CardDefaults.outlinedCardColors(containerColor=superficie),shape=RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),null,tint=tinta,modifier=Modifier.size(24.dp))
                                    Text("${v.marca} ${v.modelo} · ${v.placa}",modifier=Modifier.weight(1f).padding(horizontal=8.dp),color=tinta)
                                    Icon(painterResource(R.drawable.ic_inicio_abajo),"Seleccionar vehículo",tint=acento)
                                }
                            }
                            DropdownMenu(expanded=desplegado,onDismissRequest={ desplegado=false }) {
                                estado.vehiculos.forEach { vehiculo ->
                                    DropdownMenuItem(text={ Text("${vehiculo.marca} ${vehiculo.modelo} · ${vehiculo.placa}"+
                                        if(vehiculo.principal) " · Principal" else "") },
                                        onClick={ desplegado=false; onSeleccionar(vehiculo.idVehiculo) })
                                }
                            }
                        }
                    }
                    TarjetaInicio {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${v.marca} ${v.modelo}",color=tinta,fontSize=20.sp,fontWeight=FontWeight.Bold)
                                if(v.principal) Text("Principal",color=acento,style=MaterialTheme.typography.labelMedium)
                                Text(v.placa,color=muted)
                                TextButton(onClick={ onVerVehiculo(v.idVehiculo) }) { Text("Ver vehículo →") }
                            }
                            Box(Modifier.width(118.dp).height(90.dp).clip(RoundedCornerShape(12.dp))) {
                                MiniaturaFotoVehiculo(
                                    idVehiculo = v.idVehiculo,
                                    tieneFoto = v.tieneFoto,
                                    fotos = fotosRepository,
                                    modifier = Modifier.fillMaxSize(),
                                    contentDescription = "Imagen del vehículo"
                                ) {
                                    ImagenVehiculoPredeterminada(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    TarjetaInicio {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.ic_inicio_reloj),null,tint=acento,modifier=Modifier.size(34.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Último kilometraje",color=muted,style=MaterialTheme.typography.labelMedium)
                                val km=estado.bloques?.kilometraje ?: v.kilometrajeActual
                                Text(km?.let { "${String.format(Locale.US,"%,d",it).replace(',',' ')} km" } ?: "Sin lectura disponible",
                                    color=tinta,fontSize=24.sp,fontWeight=FontWeight.Bold)
                                estado.bloques?.fechaLectura?.let { fecha ->
                                    Text("Registrado el "+DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-PE"))
                                        .withZone(ZoneId.of("America/Lima")).format(fecha),style=MaterialTheme.typography.labelSmall,color=muted)
                                }
                            }
                            OutlinedButton(onClick={ onActualizarKm(v.idVehiculo) },contentPadding=PaddingValues(horizontal=10.dp)) { Text("Actualizar") }
                        }
                    }
                    TarjetaInicio(acento = true) {
                        EncabezadoBloqueInicio("Alertas", Icons.Outlined.Notifications)
                        val bAlertas = estado.bloques
                        when {
                            bAlertas == null || bAlertas.alertasDisponibilidad == DisponibilidadInicio.CARGANDO ->
                                EstadoBloqueInicio("Consultando alertas…", cargando = true)
                            bAlertas.alertasDisponibilidad == DisponibilidadInicio.NO_DISPONIBLE ->
                                EstadoBloqueInicio("No se pudieron cargar las alertas.")
                            bAlertas.alertas.isEmpty() ->
                                EstadoBloqueInicio("No tienes alertas próximas o vencidas.")
                            else -> {
                                bAlertas.alertas.forEach { a ->
                                    val tono = colorEstadoInicio(a.estado)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(a.titulo, color = tinta, fontWeight = FontWeight.SemiBold)
                                            Surface(shape = RoundedCornerShape(50), color = tono.copy(alpha = 0.15f)) {
                                                Text(
                                                    etiquetaEstadoInicio(a.estado),
                                                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    color = tono,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        TextButton(onClick = { onVerDetalleAlerta(a.id) }) { Text("Ver detalle") }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = onAlertas, modifier = Modifier.align(Alignment.End)) { Text("Ver alertas") }
                    }
                    TarjetaInicio(acento = true) {
                        EncabezadoBloqueInicio("Talleres", Icons.Outlined.Storefront)
                        val bTaller = estado.bloques
                        val taller = bTaller?.ultimoTaller
                        when {
                            bTaller == null || bTaller.talleresDisponibilidad == DisponibilidadInicio.CARGANDO ->
                                EstadoBloqueInicio("Consultando talleres…", cargando = true)
                            bTaller.talleresDisponibilidad == DisponibilidadInicio.NO_DISPONIBLE ->
                                EstadoBloqueInicio("No se pudieron cargar los talleres.")
                            taller == null ->
                                EstadoBloqueInicio("Aún no tienes talleres registrados.")
                            else -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconoAcento(Icons.Outlined.Storefront)
                                    Column(Modifier.weight(1f)) {
                                        Text(taller.nombre, color = tinta, fontWeight = FontWeight.SemiBold)
                                        val detalle = listOfNotNull(
                                            etiquetaOrigenTaller(taller.origen),
                                            taller.direccion?.takeIf { it.isNotBlank() }
                                        )
                                        if (detalle.isNotEmpty()) {
                                            Text(detalle.joinToString(" · "), color = muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { onTalleres(v.idVehiculo) }, modifier = Modifier.align(Alignment.End)) {
                            Text("Ver talleres")
                        }
                    }
                    TarjetaInicio(acento = true) {
                        EncabezadoBloqueInicio("Mantenimientos", Icons.Outlined.Handyman)
                        val b = estado.bloques
                        val m = b?.ultimoMantenimiento
                        when {
                            b == null || b.historial == DisponibilidadInicio.CARGANDO ->
                                EstadoBloqueInicio("Consultando último servicio…", cargando = true)
                            b.historial == DisponibilidadInicio.NO_DISPONIBLE ->
                                EstadoBloqueInicio("No se pudo consultar el último mantenimiento.")
                            m == null ->
                                EstadoBloqueInicio("Aún no tienes mantenimientos registrados.")
                            else -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconoAcento(Icons.Outlined.Build)
                                Column(Modifier.weight(1f)) {
                                    Text(m.titulo, color = tinta, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        m.fecha.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))) +
                                            " · " + String.format(Locale.US, "%,d", m.kmServicio).replace(',', ' ') + " km",
                                        color = muted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onMantenimientos(v.idVehiculo) }, modifier = Modifier.align(Alignment.End)) {
                            Text("Ver historial")
                        }
                    }
                    TextButton(onClick=onRecargar,modifier=Modifier.align(Alignment.End)) { Text("Actualizar resumen") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun etiquetaEstadoInicio(estado: String): String = when (estado.uppercase()) {
    "PROXIMA" -> "Próxima"
    "VENCIDA" -> "Vencida"
    else -> estado
}

private fun colorEstadoInicio(estado: String) = when (estado.uppercase()) {
    "PROXIMA" -> MyCarStatusProxima
    "VENCIDA" -> MyCarStatusVencida
    else -> MyCarMuted
}

@Composable
private fun EncabezadoBloqueInicio(titulo: String, icono: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = scheme.secondary.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.18f))
        ) {
            Icon(
                icono,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.padding(8.dp).size(22.dp)
            )
        }
        Text(
            titulo,
            color = scheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun IconoAcento(icono: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.secondary.copy(alpha = 0.16f)) {
        Icon(
            icono,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.padding(8.dp).size(18.dp)
        )
    }
}

@Composable
private fun EstadoBloqueInicio(texto: String, cargando: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = scheme.primaryContainer
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (cargando) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.secondary)
            }
            Text(texto, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun etiquetaOrigenTaller(origen: String?): String? = when (origen?.uppercase()) {
    "ASOCIADO" -> "Asociado"
    "MANUAL" -> "Manual"
    null, "" -> null
    else -> origen
}

@Composable
private fun TarjetaInicio(acento: Boolean = false, contenido: @Composable ColumnScope.()->Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(if (acento) 2.dp else 1.dp),
        border = if (acento) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.14f)) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}
