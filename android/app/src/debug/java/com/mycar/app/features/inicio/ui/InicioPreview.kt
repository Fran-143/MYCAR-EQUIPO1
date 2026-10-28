package com.mycar.app.features.inicio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mycar.app.features.inicio.domain.*
import com.mycar.app.features.vehiculos.data.VehiculoResumenDto
import com.mycar.app.features.historial.domain.MantenimientoHistorial
import com.mycar.app.ui.theme.MyCarTheme
import java.time.Instant
import java.time.LocalDate

/** Solo diseño DEBUG: no es un repositorio ni carga ejemplos en la app/BD. */
@Preview(name="Inicio RF50 · diseño",showBackground=true,widthDp=390,heightDp=840)
@Preview(name="Inicio RF50 · letra grande",showBackground=true,widthDp=360,heightDp=840,fontScale=1.3f)
@Composable
private fun InicioPreview() {
    val estado=EstadoResumenInicio(FaseInicio.LISTO,listOf(VehiculoResumenDto(1,"A1B234","Toyota","Corolla",2020,
        "Particular","Gris","Combustión","Gasolina","ACTIVO",true,58240)),1,
        BloquesInicio(58240,Instant.parse("2026-09-22T17:00:00Z"),MantenimientoHistorial(1,"FRENOS","Cambio de pastillas de freno",null,
            LocalDate.of(2026,9,10),57800,"REALIZADO",null),DisponibilidadInicio.DISPONIBLE,DisponibilidadInicio.DISPONIBLE),nombreUsuario="Carlos")
    MyCarTheme(dynamicColor=false) { HomeScreen(estado,{}, {},{}, {},{}, {},{}, {},{}) }
}
