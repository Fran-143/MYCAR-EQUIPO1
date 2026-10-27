package com.mycar.app.features.mantenimiento.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mycar.app.features.mantenimiento.data.*
import com.mycar.app.features.mantenimiento.domain.*
import com.mycar.app.features.mantenimiento.viewmodel.MantenimientoUiState
import com.mycar.app.ui.theme.MyCarTheme

// Solo Compose Preview. No hay Activity, identidad autenticada, repositorio ni llamadas HTTP de ejemplo.
private val contextoPreview = ContextoRf17Dto("2026-10-27", "America/Lima",
    listOf(VehiculoRf17Dto(1L, "A1B234", "Toyota", "Yaris", true, 701L, 21000,
        "2026-10-26T15:00:00Z", "5W-30"),
        VehiculoRf17Dto(2L, "B2C345", "Kia", "Rio", false, null, null, null, null)),
    listOf(TipoRf17Dto(1L, "MANTENIMIENTO_GENERAL", "Mantenimiento general"),
        TipoRf17Dto(2L, "CAMBIO_ACEITE", "Cambio de aceite"),
        TipoRf17Dto(3L, "OTRO", "Otro")), registroDisponible = true)
private fun estadoFormularioPreview() = MantenimientoUiState(cargando = false, contexto = contextoPreview,
    borrador = BorradorMantenimiento(idOperacion = "00000000-0000-0000-0000-000000000017",
        idVehiculo = 1L, codigoTipo = "MANTENIMIENTO_GENERAL", fecha = "2026-10-27",
        lectura = 701L, km = "21000", costo = "180.00"),
    lecturas = listOf(LecturaRf17Dto(701L, 21000, "2026-10-26T15:00:00Z")))
private fun resultadoPreview(b: BorradorMantenimiento) = ResultadoRf17Dto(b.idOperacion, 999L,
    "REALIZADO", b.km.toIntOrNull(), b.fecha, ValidacionFormularioMantenimiento.importe(b.costo), b.repuestos.size, false)

@Preview(name = "01 · Formulario interactivo", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun FormularioPreview() = EscenarioPreview(estadoFormularioPreview())

@Preview(name = "02 · Servicio Otro", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun OtroPreview() = EscenarioPreview(estadoFormularioPreview().let { it.copy(borrador = it.borrador.copy(codigoTipo = "OTRO")) })

@Preview(name = "03 · Agregar repuesto", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RepuestoPreview() = EscenarioPreview(estadoFormularioPreview().copy(editor = RepuestoBorrador()))

@Preview(name = "04 · Validaciones", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ErroresPreview() {
    val b = estadoFormularioPreview().borrador.copy(km = "0", costo = "-10", proximoKm = "0", fecha = "2026-10-28")
    EscenarioPreview(estadoFormularioPreview().copy(borrador = b,
        errores = ValidacionFormularioMantenimiento.formulario(b, contextoPreview)))
}

@Preview(name = "05 · Carga", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CargaPreview() = EscenarioPreview(estadoFormularioPreview().copy(cargando = true, contexto = null))

@Preview(name = "06 · Guardando", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun GuardandoPreview() = EscenarioPreview(estadoFormularioPreview().copy(enviando = true))

@Preview(name = "07 · Confirmación visual", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ConfirmacionPreview() = EscenarioPreview(estadoFormularioPreview().let { it.copy(resultado = resultadoPreview(it.borrador)) })

@Preview(name = "08 · Resultado incierto", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun InciertoPreview() = EscenarioPreview(estadoFormularioPreview().let {
    it.copy(incierto = true, borrador = it.borrador.copy(envioPendiente = true))
})

@Preview(name = "09 · Cancelación", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CancelacionPreview() = EscenarioPreview(estadoFormularioPreview().copy(confirmarSalida = true))

@Preview(name = "10 · Sesión requerida", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun SesionPreview() = EscenarioPreview(MantenimientoUiState(cargando = false, requiereSesion = true))

@Composable
private fun EscenarioPreview(inicial: MantenimientoUiState) {
    val control = remember { ControlPreview(inicial) }
    MyCarTheme(darkTheme = false, dynamicColor = false) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Text("PREVIEW · Datos de ejemplo · Sin guardado real", Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium)
                FormularioRegistroMantenimiento(control.estado, control, Modifier.weight(1f),
                    onCerrar = { control.volver() }, onCerrarConfirmado = { control.volver() },
                    onActualizarKm = null, onSesionRequerida = null)
            }
        }
    }
}

/** Simula interacciones visuales. Las validaciones se reutilizan del dominio de producción. */
private class ControlPreview(private val inicial: MantenimientoUiState) : AccionesMantenimiento {
    var estado by mutableStateOf(inicial)
        private set
    fun volver() { estado = inicial.copy(mensaje = "Vista previa: regreso a Inicio.") }
    override fun actualizar(cambio: (BorradorMantenimiento) -> BorradorMantenimiento) {
        if (estado.editable) estado = estado.copy(borrador = cambio(estado.borrador), errores = emptyMap(), mensaje = null)
    }
    override fun recargar() { estado = estado.copy(contexto = contextoPreview, cargando = false, mensaje = null) }
    override fun fecha(fecha: String) { actualizar { it.copy(fecha = fecha, lectura = null, km = if (it.origen == "LECTURA") "" else it.km) } }
    override fun origen(origen: String) { actualizar { it.copy(origen = origen, lectura = null, km = "") } }
    override fun lectura(id: Long) { estado.lecturas.firstOrNull { it.idLecturaKm == id }?.let { l -> actualizar { it.copy(lectura = id, km = l.valorKm.toString()) } } }
    override fun solicitarVehiculo(id: Long) { if (estado.editable && estado.borrador.idVehiculo != id) estado = estado.copy(vehiculoPendiente = id) }
    override fun confirmarVehiculo() { estado = estado.copy(borrador = BorradorMantenimiento(idVehiculo = estado.vehiculoPendiente, fecha = contextoPreview.fechaServidor), vehiculoPendiente = null, lecturas = emptyList()) }
    override fun cancelarVehiculo() { estado = estado.copy(vehiculoPendiente = null) }
    override fun editarRepuesto(id: String?) { if (estado.editable) estado = estado.copy(editor = estado.borrador.repuestos.firstOrNull { it.idLocal == id } ?: RepuestoBorrador(), erroresEditor = emptyMap()) }
    override fun cambiarEditor(cambio: (RepuestoBorrador) -> RepuestoBorrador) { estado.editor?.let { estado = estado.copy(editor = cambio(it), erroresEditor = emptyMap()) } }
    override fun cerrarEditor() { estado = estado.copy(editor = null, erroresEditor = emptyMap()) }
    override fun confirmarRepuesto() {
        val p = estado.editor ?: return
        val errores = ValidacionFormularioMantenimiento.repuesto(p)
        if (errores.isNotEmpty()) { estado = estado.copy(erroresEditor = errores); return }
        actualizar { it.copy(repuestos = it.repuestos.filterNot { r -> r.idLocal == p.idLocal } + p) }
        cerrarEditor()
    }
    override fun quitarRepuesto(id: String) { actualizar { it.copy(repuestos = it.repuestos.filterNot { r -> r.idLocal == id }) } }
    override fun guardar() {
        if (!estado.editable || estado.cargando || estado.contexto?.registroDisponible != true) return
        val errores = ValidacionFormularioMantenimiento.formulario(estado.borrador, estado.contexto)
        estado = if (errores.isNotEmpty()) estado.copy(errores = errores)
            else estado.copy(resultado = resultadoPreview(estado.borrador), errores = emptyMap())
    }
    override fun reintentarMismaOperacion() { estado = estado.copy(mensaje = "Vista previa: no se envió ninguna petición.") }
    override fun consultarOperacion() { estado = estado.copy(mensaje = "Vista previa: el resultado continúa pendiente. No se consultó un servidor.") }
    override fun solicitarSalir() { estado = estado.copy(confirmarSalida = true) }
    override fun seguirEditando() { estado = estado.copy(confirmarSalida = false) }
    override fun descartar() { estado = inicial.copy(borrador = BorradorMantenimiento(), confirmarSalida = false) }
}
