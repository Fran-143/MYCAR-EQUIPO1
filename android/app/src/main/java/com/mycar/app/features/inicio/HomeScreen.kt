package com.mycar.app.features.inicio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mycar.app.R
import com.mycar.app.features.inicio.domain.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AzulInicio=Color(0xFF079FDC)
private val TintaInicio=Color(0xFF163468)

@Composable
fun HomeScreen(estado: EstadoResumenInicio, onSeleccionar: (Long)->Unit,
    onVehiculos: ()->Unit, onMantenimientos: (Long?)->Unit, onAlertas: ()->Unit,
    onPerfil: ()->Unit, onNotificaciones: ()->Unit, onRecargar: ()->Unit,
    onVerVehiculo: (Long)->Unit, onActualizarKm: (Long)->Unit,
    modifier: Modifier=Modifier) {
    var desplegado by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEAF8FF),Color(0xFFF7FCFF))))
        .verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),contentDescription=null,
                tint=TintaInicio,modifier=Modifier.size(36.dp))
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color=TintaInicio,fontWeight=FontWeight.ExtraBold)) { append("My") }
                withStyle(SpanStyle(color=AzulInicio,fontWeight=FontWeight.ExtraBold)) { append("Car") }
            },fontSize=27.sp,modifier=Modifier.weight(1f))
            IconButton(onClick=onNotificaciones) {
                Icon(painterResource(R.drawable.ic_inicio_campana),contentDescription="Notificaciones",tint=TintaInicio)
            }
            FilledTonalIconButton(onClick=onPerfil) {
                Icon(painterResource(R.drawable.ic_inicio_perfil),contentDescription="Perfil",tint=TintaInicio)
            }
        }
        Column {
            Text(estado.nombreUsuario?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola",
                fontSize=25.sp,fontWeight=FontWeight.Bold,color=TintaInicio,modifier=Modifier.semantics { heading() })
            Text("Tu vehículo, al día",color=Color(0xFF6881A5))
        }
        when(estado.fase) {
            FaseInicio.SIN_SESION -> TarjetaInicio {
                Text("Tu resumen en un solo lugar",fontWeight=FontWeight.Bold,color=TintaInicio)
                Text(estado.mensaje ?: "Inicia sesión para consultar tus vehículos y mantenimientos.")
                Button(onClick=onVehiculos) { Text("Iniciar sesión") }
            }
            FaseInicio.CARGANDO -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Cargando tu resumen…",color=TintaInicio)
            }
            FaseInicio.ERROR, FaseInicio.INCONSISTENTE -> TarjetaInicio {
                Text(estado.mensaje ?: "No se pudo cargar el resumen.")
                Button(onClick=onRecargar) { Text("Reintentar") }
                TextButton(onClick=onVehiculos) { Text("Ver mis vehículos") }
            }
            FaseInicio.SIN_VEHICULOS -> TarjetaInicio {
                Text("Aún no tienes vehículos activos",fontWeight=FontWeight.Bold,color=TintaInicio)
                Text("Agrega tu vehículo o consulta tus vehículos registrados.")
                Button(onClick=onVehiculos) { Text("Ir a Vehículos") }
            }
            FaseInicio.LISTO -> {
                val v=estado.vehiculo
                if(v!=null) {
                    Column {
                        Text("Mis vehículos",style=MaterialTheme.typography.labelLarge,color=Color(0xFF6881A5))
                        Box {
                            OutlinedCard(onClick={ desplegado=true },modifier=Modifier.fillMaxWidth(),
                                colors=CardDefaults.outlinedCardColors(containerColor=Color.White),shape=RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),null,tint=TintaInicio,modifier=Modifier.size(24.dp))
                                    Text("${v.marca} ${v.modelo} · ${v.placa}",modifier=Modifier.weight(1f).padding(horizontal=8.dp),color=TintaInicio)
                                    Icon(painterResource(R.drawable.ic_inicio_abajo),"Seleccionar vehículo",tint=AzulInicio)
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
                                Text("${v.marca} ${v.modelo}",color=TintaInicio,fontSize=20.sp,fontWeight=FontWeight.Bold)
                                if(v.principal) Text("Principal",color=AzulInicio,style=MaterialTheme.typography.labelMedium)
                                Text(v.placa,color=Color(0xFF6881A5))
                                TextButton(onClick={ onVerVehiculo(v.idVehiculo) }) { Text("Ver vehículo →") }
                            }
                            Image(painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),"Imagen predeterminada del vehículo",
                                modifier=Modifier.width(110.dp).height(85.dp))
                        }
                    }
                    TarjetaInicio {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(painterResource(R.drawable.ic_inicio_reloj),null,tint=AzulInicio,modifier=Modifier.size(34.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Último kilometraje",color=Color(0xFF6881A5),style=MaterialTheme.typography.labelMedium)
                                val km=estado.bloques?.kilometraje ?: v.kilometrajeActual
                                Text(km?.let { "${String.format(Locale.US,"%,d",it).replace(',',' ')} km" } ?: "Sin lectura disponible",
                                    color=TintaInicio,fontSize=24.sp,fontWeight=FontWeight.Bold)
                                estado.bloques?.fechaLectura?.let { fecha ->
                                    Text("Registrado el "+DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-PE"))
                                        .withZone(ZoneId.of("America/Lima")).format(fecha),style=MaterialTheme.typography.labelSmall,color=Color(0xFF6881A5))
                                }
                            }
                            OutlinedButton(onClick={ onActualizarKm(v.idVehiculo) },contentPadding=PaddingValues(horizontal=10.dp)) { Text("Actualizar") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Mantenimientos por atender",modifier=Modifier.weight(1f).semantics { heading() },
                            color=TintaInicio,fontWeight=FontWeight.Bold)
                        TextButton(onClick=onAlertas) { Text("Ver alertas") }
                    }
                    TarjetaInicio {
                        Text("Próximamente",color=TintaInicio,fontWeight=FontWeight.SemiBold)
                        Text("Aquí podrás consultar las alertas de mantenimiento de tu vehículo.",color=Color(0xFF6881A5))
                    }
                    TarjetaInicio {
                        Text("Último mantenimiento realizado",color=TintaInicio,fontWeight=FontWeight.Bold,modifier=Modifier.semantics { heading() })
                        val b=estado.bloques; val m=b?.ultimoMantenimiento
                        when {
                            b==null || b.historial==DisponibilidadInicio.CARGANDO -> Text("Consultando último servicio…")
                            b.historial==DisponibilidadInicio.NO_DISPONIBLE -> Text("No se pudo consultar el último mantenimiento.")
                            m==null -> Text("Aún no hay mantenimientos realizados para este vehículo.")
                            else -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(painterResource(R.drawable.ic_inicio_llave),null,tint=TintaInicio)
                                Column {
                                    Text(m.titulo,color=TintaInicio,fontWeight=FontWeight.SemiBold)
                                    Text(m.fecha.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("es-PE")))+
                                        " · "+String.format(Locale.US,"%,d",m.kmServicio).replace(',',' ')+" km",style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        TextButton(onClick={ onMantenimientos(v.idVehiculo) }) { Text("Ver historial →") }
                    }
                    TextButton(onClick=onRecargar,modifier=Modifier.align(Alignment.End)) { Text("Actualizar resumen") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TarjetaInicio(contenido: @Composable ColumnScope.()->Unit) {
    Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),elevation=CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=contenido)
    }
}
