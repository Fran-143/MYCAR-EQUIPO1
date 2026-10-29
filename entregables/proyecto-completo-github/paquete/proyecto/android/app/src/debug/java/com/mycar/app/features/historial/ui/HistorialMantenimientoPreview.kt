package com.mycar.app.features.historial.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.features.historial.domain.*
import com.mycar.app.ui.theme.MyCarTheme
import java.math.BigDecimal
import java.time.LocalDate

/** Solo debug: estos datos nunca se utilizan en la navegación normal ni hacen peticiones. */
internal object EjemplosHistorial {
    val activo = VehiculoHistorial(10, "ABC123", "Toyota", "Corolla", "ACTIVO", true)
    val inactivo = VehiculoHistorial(11, "DEF456", "Nissan", "Versa", "INACTIVO", false)
    val vehiculos = listOf(activo, inactivo)
    val registros = listOf(
        MantenimientoHistorial(21, "FRENOS", "Revisión de frenos", null, LocalDate.of(2026, 10, 10), 49000, "ANULADO", BigDecimal.ZERO),
        MantenimientoHistorial(20, "CAMBIO_ACEITE", "Cambio de aceite", null, LocalDate.of(2026, 9, 20), 47000, "REALIZADO", null),
        MantenimientoHistorial(19, "OTRO", "Otro", "Alineación y balanceo", LocalDate.of(2026, 8, 10), 45000, "REALIZADO", BigDecimal("180.50")))
    val listo = EstadoHistorial(FaseHistorial.LISTO, vehiculos, activo, registros)
}

@Composable
internal fun VistaHistorial(inicial: EstadoHistorial, onCerrar: (() -> Unit)? = null) {
    var estado by remember { mutableStateOf(inicial) }
    var avisoVista by remember { mutableStateOf<String?>(null) }
    MyCarTheme {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("DISEÑO RF18 · DATOS DE EJEMPLO · Sin conexión ni guardado real")
            }
            avisoVista?.let { Text(it) }
            HistorialMantenimientoScreen(estado,
                onVolver = { if (onCerrar != null) onCerrar() else avisoVista = "Vista previa: volver a Inicio" },
                onSeleccionar = { id ->
                    val v = estado.vehiculos.first { it.id == id }
                    estado = estado.copy(fase = FaseHistorial.LISTO, seleccionado = v,
                        mantenimientos = if (v.activo) EjemplosHistorial.registros else emptyList(), aviso = null, mensaje = null)
                },
                onReintentar = { estado = EjemplosHistorial.listo },
                onActualizar = { avisoVista = "Vista previa: historial actualizado" },
                onRegistrar = { avisoVista = "Vista previa: abrir RF17 para ${estado.seleccionado?.placa}. No se guarda un mantenimiento" },
                registroDisponible = true)
        }
    }
}

@Preview(name = "01 · Historial interactivo", showBackground = true, widthDp = 390, heightDp = 860)
@Composable private fun HistorialCompleto() = VistaHistorial(EjemplosHistorial.listo)
@Preview(name = "02 · Sin mantenimientos", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun HistorialVacio() = VistaHistorial(EjemplosHistorial.listo.copy(mantenimientos = emptyList()))
@Preview(name = "03 · Vehículo inactivo", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun HistorialInactivo() = VistaHistorial(EjemplosHistorial.listo.copy(seleccionado = EjemplosHistorial.inactivo))
@Preview(name = "04 · Sin principal", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun SinPrincipal() = VistaHistorial(EstadoHistorial(FaseHistorial.SIN_SELECCION,
    EjemplosHistorial.vehiculos.map { it.copy(principal = false) }))
@Preview(name = "05 · Sin vehículos", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun SinVehiculos() = VistaHistorial(EstadoHistorial(FaseHistorial.SIN_VEHICULOS))
@Preview(name = "06 · Cargando", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun Cargando() = VistaHistorial(EstadoHistorial(FaseHistorial.CARGANDO_HISTORIAL,
    EjemplosHistorial.vehiculos, EjemplosHistorial.activo))
@Preview(name = "07 · Error y reintento", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun ErrorConsulta() = VistaHistorial(EstadoHistorial(FaseHistorial.ERROR_HISTORIAL,
    EjemplosHistorial.vehiculos, EjemplosHistorial.activo, mensaje = "No se pudo cargar el historial"))
@Preview(name = "08 · Sesión requerida", showBackground = true, widthDp = 390, heightDp = 820)
@Composable private fun SesionRequerida() = VistaHistorial(EstadoHistorial())
