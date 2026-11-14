package com.mycar.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycar.app.R

private val Tinta = Color(0xFF163468)
private val AzulMarca = Color(0xFF079FDC)

/**
 * Cabecera uniforme MyCar: logo/nombre + notificaciones + perfil.
 * Sin rediseño; reutiliza iconos y tipografía ya usados en Inicio.
 */
@Composable
fun MycarCabeceraApp(
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    conStatusBars: Boolean = true
) {
    Surface(color = Color.White, shadowElevation = 0.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (conStatusBars) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_vehiculo_predeterminado_rf08),
                contentDescription = null,
                tint = Tinta,
                modifier = Modifier.size(32.dp)
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Tinta, fontWeight = FontWeight.ExtraBold)) { append("My") }
                    withStyle(SpanStyle(color = AzulMarca, fontWeight = FontWeight.ExtraBold)) { append("Car") }
                },
                fontSize = 22.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            )
            if (onNotificaciones != null) {
                IconButton(onClick = onNotificaciones) {
                    Icon(
                        painterResource(R.drawable.ic_inicio_campana),
                        contentDescription = "Notificaciones",
                        tint = Tinta
                    )
                }
            }
            if (onPerfil != null) {
                FilledTonalIconButton(onClick = onPerfil) {
                    Icon(
                        painterResource(R.drawable.ic_inicio_perfil),
                        contentDescription = "Perfil",
                        tint = Tinta
                    )
                }
            }
        }
    }
}
