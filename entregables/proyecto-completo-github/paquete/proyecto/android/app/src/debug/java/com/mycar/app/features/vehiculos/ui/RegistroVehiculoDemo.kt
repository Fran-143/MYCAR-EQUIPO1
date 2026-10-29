package com.mycar.app.features.vehiculos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.mycar.app.features.vehiculos.data.*
import com.mycar.app.features.vehiculos.domain.*

/**
 * Demostración local exclusiva de DEBUG, compartida por Preview y emulador.
 * No instancia APIs de vehículos, no envía solicitudes y no anuncia un alta confirmada.
 */
@Composable
fun RegistroVehiculoDemo(
    inicial: FormularioRegistroVehiculo = FormularioRegistroVehiculo(),
    onVolver: () -> Unit = {}
) {
    val catalogos = remember { catalogosDemostracion() }
    val gson = remember { Gson() }
    val saver = remember {
        Saver<FormularioRegistroVehiculo, String>(
            save = { gson.toJson(it) },
            restore = { runCatching { gson.fromJson(it, FormularioRegistroVehiculo::class.java) }.getOrNull() }
        )
    }
    var formulario by rememberSaveable(stateSaver = saver) { mutableStateOf(inicial) }
    var errores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    val estado = EstadoRegistroVehiculo(fase = FaseRegistro.FORMULARIO,
        catalogos = catalogos, formulario = formulario, errores = errores, mensaje = mensaje)
    Column(Modifier.fillMaxSize().statusBarsPadding().consumeWindowInsets(WindowInsets.statusBars)) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Text("Modo de prueba · no guarda vehículos en la BD",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        }
        RegistroVehiculoScreen(estado,
            onCambiar = { campo, valor ->
                formulario = formulario.cambiar(campo, valor, catalogos)
                errores = errores - formulario.camposAfectados(campo)
                mensaje = null
            },
            onGuardar = {
                errores = ValidadorFormularioRegistro.validar(formulario, catalogos)
                mensaje = if (errores.isEmpty())
                    "Datos válidos. Esta prueba no envía ni guarda vehículos."
                else "Revisa los campos señalados."
            },
            onRecargar = {}, onComprobar = {}, onReenviar = {}, onVolver = onVolver,
            onIniciarSesion = {}, onVerVehiculos = {}, onIrInicio = {}, modifier = Modifier.weight(1f))
    }
}

/** IDs y nombres de ejemplo; nunca se usan para un POST ni se añaden a PostgreSQL. */
private fun catalogosDemostracion() = CatalogosRegistroDto(
    usos = listOf(UsoRegistroDto("PARTICULAR", "Particular"), UsoRegistroDto("TAXI", "Taxi")),
    tiposMotor = listOf(MotorRegistroDto(1, "COMBUSTION", "Combustión", false),
        MotorRegistroDto(2, "ELECTRICO", "Eléctrico", true), MotorRegistroDto(3, "OTRO", "Otro", false)),
    combustibles = listOf(CombustibleRegistroDto(1, "GASOLINA", "Gasolina"), CombustibleRegistroDto(2, "OTRO", "Otro")),
    tiposAceite = listOf(AceiteRegistroDto(1, "MINERAL", "Mineral"), AceiteRegistroDto(2, "OTRO", "Otro"))
)
