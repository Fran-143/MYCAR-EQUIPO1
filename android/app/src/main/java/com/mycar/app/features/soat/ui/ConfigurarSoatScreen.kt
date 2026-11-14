package com.mycar.app.features.soat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy

@Composable
fun ConfigurarSoatScreen(
    onVolver: () -> Unit,
    onCorregir: () -> Unit,
    onRenovar: () -> Unit,
    onRecordatorio: () -> Unit,
    recordatorioActivo: Boolean?,
    modifier: Modifier = Modifier
) {
    SoatScaffold("Configurar SOAT", onVolver, modifier = modifier) {
        Text(
            "Elige una acción sobre el SOAT vigente.",
            color = MyCarMuted,
            fontWeight = FontWeight.Medium
        )
        recordatorioActivo?.let {
            Text(
                if (it) "Recordatorio: activo" else "Recordatorio: inactivo",
                color = MyCarNavy,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SoatBotonPrincipal("Corregir datos", onCorregir)
            SoatBotonSecundario("Renovar SOAT", onRenovar)
            SoatBotonSecundario("Recordatorio de vencimiento", onRecordatorio)
        }
    }
}