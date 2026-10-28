package com.mycar.app.features.alertas.programacion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mycar.app.features.alertas.programacion.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class AccionesProgramacion(
    val volver: () -> Unit, val vehiculo: (Long) -> Unit, val tipo: (Long) -> Unit,
    val servicio: (String) -> Unit, val sugerencia: (String) -> Unit, val referencia: (Boolean) -> Unit,
    val criterio: (CriterioRf22) -> Unit, val cantidad: (String) -> Unit, val kilometros: (String) -> Unit,
    val unidad: (UnidadRf22) -> Unit, val revisar: () -> Unit, val confirmar: () -> Unit,
    val editar: () -> Unit, val comprobar: () -> Unit, val reenviar: () -> Unit,
    val recargar: () -> Unit, val cerrarSesion: (() -> Unit)?,
    val verAlertas: (() -> Unit)? = null, val reautenticar: (() -> Unit)? = null
)
private val FondoRf22 = Color(0xFFF0F5FA)
private val AzulRf22 = Color(0xFF12285F)
private val CelesteRf22 = Color(0xFF008BA6)

/** Formulario, resumen y resultado reales. La demostración se construye únicamente en src/debug. */
@Composable
fun ProgramarAlertaScreen(estado: EstadoProgramacion, acciones: AccionesProgramacion,
    modifier: Modifier = Modifier, demostracion: Boolean = false) {
    val ocupado = estado.fase in setOf(FaseProgramacion.CARGANDO, FaseProgramacion.GUARDANDO)
    BackHandler(enabled = !ocupado) {
        if (estado.fase == FaseProgramacion.REVISION) acciones.editar() else acciones.volver()
    }
    Column(modifier.fillMaxSize().background(FondoRf22).testTag("rf22.pantalla")) {
        if (demostracion) Surface(color = Color(0xFFFFEAB5)) {
            Text("DISEÑO · DATOS DE EJEMPLO · NO GUARDA EN LA BD", Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.labelSmall)
        }
        Surface(color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = if (estado.fase == FaseProgramacion.REVISION) acciones.editar else acciones.volver,
                    enabled = !ocupado) { Text("Volver") }
                Text(when (estado.fase) {
                    FaseProgramacion.REVISION -> "Revisar programación"
                    FaseProgramacion.CONFIRMADO -> "Alerta programada"
                    else -> "Programar alerta"
                }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AzulRf22)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            estado.mensaje?.let { TarjetaRf22("Revisa tu programación") { Text(it, Modifier.testTag("rf22.mensaje")) } }
            if (ocupado) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (estado.fase == FaseProgramacion.GUARDANDO) "Comprobando el resultado de tu operación…" else "Cargando datos…")
            }
            when (estado.fase) {
                FaseProgramacion.SIN_VEHICULOS -> TarjetaRf22("No tienes vehículos activos") {
                    Text("Para programar una alerta necesitas un vehículo propio activo. No se ha creado ninguna alerta.")
                }
                FaseProgramacion.ERROR -> {
                    Text("No se pudieron cargar los datos. Vuelve a intentarlo.")
                    Button(onClick = acciones.recargar) { Text("Reintentar") }
                }
                FaseProgramacion.POR_COMPROBAR -> TarjetaRf22("Comprobar programación pendiente") {
                    Text("La respuesta anterior no está confirmada. Conservamos la misma solicitud para evitar duplicados.")
                    Button(onClick = acciones.comprobar, modifier = Modifier.testTag("rf22.comprobar")) { Text("Comprobar resultado") }
                    if (estado.permiteReenvio) OutlinedButton(onClick = acciones.reenviar, modifier = Modifier.testTag("rf22.reenviar")) { Text("Reenviar la misma operación") }
                }
                FaseProgramacion.CONFIRMADO -> estado.resultado?.let { r ->
                    TarjetaRf22(if (demostracion) "Así se verá la confirmación" else "¡Alerta programada!") {
                        Text(r.servicio, fontWeight = FontWeight.Bold)
                        estado.vehiculoActual?.let { Text("${it.marca} ${it.modelo} · ${it.placa}") }
                        r.fechaObjetivo?.let { DatoRf22("Fecha objetivo", fechaRf22(it)) }
                        r.kmObjetivo?.let { DatoRf22("Kilometraje objetivo", "$it km") }
                        DatoRf22("Estado", estadoLegible(r.estadoInicial))
                        if (!demostracion) Text("Tu alerta quedó guardada.")
                        Button(onClick = { acciones.verAlertas?.invoke() }, enabled = acciones.verAlertas != null) {
                            Text(if (acciones.verAlertas == null) "Ver alertas · Próximamente" else "Ver alertas")
                        }
                        TextButton(onClick = acciones.volver) { Text("Ir al inicio") }
                    }
                }
                FaseProgramacion.REVISION -> estado.revision?.let { r ->
                    TarjetaRf22("Revisa los datos de tu alerta") {
                        estado.vehiculoActual?.let { Text("${it.marca} ${it.modelo} · ${it.placa}") }
                        Text(r.configuracion.servicioOtro ?: estado.tipoActual?.nombre.orEmpty(), fontWeight = FontWeight.Bold)
                        DatoRf22("Criterio", criterioLegible(r.configuracion.criterio))
                        DatoRf22("Fecha de revisión", fechaRf22(r.bases.fechaBase))
                        r.configuracion.cantidadTiempo?.let { DatoRf22("Intervalo", "$it ${if (r.configuracion.unidadTiempo == UnidadRf22.SEMANAS) "semanas" else "meses"}") }
                        r.objetivos.fechaObjetivo?.let { DatoRf22("Fecha objetivo", fechaRf22(it)) }
                        r.bases.kmBase?.let { DatoRf22("Kilometraje base", "$it km") }
                        r.configuracion.intervaloKm?.let { DatoRf22("Intervalo de kilometraje", "$it km") }
                        r.objetivos.kmObjetivo?.let { DatoRf22("Kilometraje objetivo", "$it km") }
                        DatoRf22("Estado inicial", estadoLegible(r.objetivos.estado))
                        r.referencia?.let { Text("Referencia: ${fechaRf22(it.fechaServicio)} · ${it.kmServicio} km. Solo informativa.") }
                        if (r.configuracion.criterio == CriterioRf22.AMBOS) Text("Se crea una sola alerta. Se considera la condición que se alcance primero.")
                    }
                    Button(onClick = acciones.confirmar, modifier = Modifier.fillMaxWidth().testTag("rf22.confirmar"),
                        colors = ButtonDefaults.buttonColors(containerColor = CelesteRf22)) {
                        Text(if (demostracion) "Simular confirmación · no guarda" else "Programar alerta")
                    }
                    TextButton(onClick = acciones.editar, modifier = Modifier.fillMaxWidth()) { Text("Volver a editar") }
                    TextButton(onClick = acciones.volver, modifier = Modifier.fillMaxWidth()) { Text("Cancelar sin programar") }
                }
                FaseProgramacion.FORMULARIO -> FormularioProgramacion(estado, acciones)
                else -> Unit
            }
            if (!demostracion && !ocupado) {
                acciones.reautenticar?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Volver a iniciar sesión") } }
                acciones.cerrarSesion?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión y limpiar borrador local") } }
            }
        }
    }
}

@Composable
private fun FormularioProgramacion(e: EstadoProgramacion, a: AccionesProgramacion) {
    TarjetaRf22("Vehículo y mantenimiento") {
        SelectorRf22("Vehículo", e.vehiculoActual?.let { "${it.marca} ${it.modelo} · ${it.placa}" } ?: "Selecciona un vehículo",
            e.contexto?.vehiculos.orEmpty().map { it.idVehiculo to "${it.marca} ${it.modelo} · ${it.placa}" }, a.vehiculo, "rf22.vehiculo")
        SelectorRf22("Tipo de mantenimiento", e.tipoActual?.nombre ?: "Selecciona un mantenimiento",
            e.contexto?.tipos.orEmpty().map { it.idTipoMantenimiento to it.nombre }, a.tipo, "rf22.tipo")
        if (e.tipoActual?.codigo == "CAMBIO_ACEITE") Text("Aceite registrado: ${e.vehiculoActual?.aceite ?: "No registrado"}")
        if (e.esOtro) {
            OutlinedTextField(e.servicio, a.servicio, label = { Text("Especificar servicio") }, modifier = Modifier.fillMaxWidth().testTag("rf22.servicio"), singleLine = true,
                supportingText = { Text("Obligatorio. Hasta 160 caracteres. Puedes escribir un servicio nuevo.") })
            val texto = e.servicio.lowercase()
            val sugerencias = e.antecedentes?.sugerencias.orEmpty().filter { nombre ->
                texto.isBlank() || nombre.lowercase().contains(texto) || texto.split(Regex("\\s+")).any { it.length > 2 && nombre.lowercase().contains(it) }
            }
            if (sugerencias.isEmpty()) Text("Sin servicio anterior coincidente. Puedes continuar con tu texto.")
            sugerencias.forEach { nombre -> TextButton(onClick = { a.sugerencia(nombre) }) { Text("Servicio anterior: $nombre") } }
        }
    }
    if (e.antecedentes != null) {
        TarjetaRf22("Datos actuales · solo lectura") {
            DatoRf22("Fecha actual", fechaRf22(e.antecedentes.fechaServidor))
            DatoRf22("Kilometraje actual", e.antecedentes.kmActual?.let { "$it km" } ?: "No registrado")
        }
        if (e.antecedentes.referencia != null) TarjetaRf22("Último servicio · referencia opcional") {
            val ref = e.antecedentes.referencia
            Text("${fechaRf22(ref.fechaServicio)} · ${ref.kmServicio} km")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(e.referencia != null, a.referencia)
                Text("Usar como referencia informativa")
            }
            Text("Los nuevos objetivos se calculan desde los datos actuales.")
        } else if (e.tipo != null) Text("Sin mantenimiento previo seleccionado. Esto no impide programar.")
        TarjetaRf22("¿Cómo quieres programar la alerta?") {
            CriterioRf22.entries.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(e.criterio == c, onClick = { a.criterio(c) }, enabled = c == CriterioRf22.POR_TIEMPO || e.antecedentes.kmActual != null,
                        modifier = Modifier.testTag("rf22.criterio.${c.name}"))
                    Text(criterioLegible(c))
                }
            }
            if (e.antecedentes.kmActual == null) Text("Sin kilometraje registrado, puedes programar por tiempo.")
            if (e.criterio != CriterioRf22.POR_KILOMETRAJE) {
                OutlinedTextField(e.cantidad, a.cantidad, label = { Text("Cantidad de tiempo") }, modifier = Modifier.fillMaxWidth().testTag("rf22.cantidad"),
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnidadRf22.entries.forEach { u -> FilterChip(e.unidad == u, onClick = { a.unidad(u) }, label = { Text(if (u == UnidadRf22.SEMANAS) "Semanas" else "Meses") }) }
                }
            }
            if (e.criterio != CriterioRf22.POR_TIEMPO) OutlinedTextField(e.kilometros, a.kilometros,
                label = { Text("Kilómetros después de la lectura actual") }, modifier = Modifier.fillMaxWidth().testTag("rf22.intervaloKm"),
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    if (e.vehiculo != null && e.tipo != null && e.antecedentes == null) OutlinedButton(onClick = a.recargar) { Text("Volver a consultar datos") }
    Button(onClick = a.revisar, enabled = e.puedeRevisar, modifier = Modifier.fillMaxWidth().testTag("rf22.revisar"),
        colors = ButtonDefaults.buttonColors(containerColor = CelesteRf22)) { Text("Revisar programación") }
    TextButton(onClick = a.volver, modifier = Modifier.fillMaxWidth().testTag("rf22.cancelar")) { Text("Cancelar") }
}

@Composable
private fun SelectorRf22(titulo: String, actual: String, opciones: List<Pair<Long, String>>, seleccionar: (Long) -> Unit, tag: String) {
    var abierto by remember { mutableStateOf(false) }
    Column {
        Text(titulo, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text("$actual ▾") }
            DropdownMenu(abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { (id, texto) -> DropdownMenuItem(text = { Text(texto) }, onClick = { abierto = false; seleccionar(id) }) }
            }
        }
    }
}
@Composable private fun TarjetaRf22(titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, color = AzulRf22, fontWeight = FontWeight.Bold); contenido()
        }
    }
}
@Composable private fun DatoRf22(etiqueta: String, valor: String) {
    Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = AzulRf22)
    Text(valor, style = MaterialTheme.typography.bodyLarge)
}
private fun fechaRf22(fecha: String) = try { LocalDate.parse(fecha).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) } catch (_: Exception) { "No disponible" }
private fun criterioLegible(c: CriterioRf22) = when (c) { CriterioRf22.POR_TIEMPO -> "Por tiempo"; CriterioRf22.POR_KILOMETRAJE -> "Por kilometraje"; CriterioRf22.AMBOS -> "Ambos: tiempo y kilometraje" }
private fun estadoLegible(e: String) = when (e) { "PROGRAMADA" -> "Programada"; "PROXIMA" -> "Próxima"; "PENDIENTE" -> "Pendiente"; else -> e }
