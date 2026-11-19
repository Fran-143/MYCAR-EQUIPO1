package com.mycar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycar.app.R
import com.mycar.app.features.fotos.ui.ImagenBytesFoto

private val Tinta = Color(0xFF163468)
private val AzulMarca = Color(0xFF079FDC)
private val TealAvatar = Color(0xFF0D9488)

/**
 * Cabecera uniforme MyCar: logo/nombre + notificaciones + perfil.
 * Sin rediseño; reutiliza iconos y tipografía ya usados en Inicio.
 * Avatar: foto de sesión o iniciales; si no hay datos, ícono genérico.
 */
@Composable
fun MycarCabeceraApp(
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    conStatusBars: Boolean = true,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null
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
                    AvatarCabecera(avatarBytes = avatarBytes, iniciales = iniciales)
                }
            }
        }
    }
}

@Composable
fun AvatarCabecera(
    avatarBytes: ByteArray?,
    iniciales: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (avatarBytes != null || !iniciales.isNullOrBlank()) TealAvatar else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        ImagenBytesFoto(
            bytes = avatarBytes,
            contentDescription = "Perfil",
            modifier = Modifier.fillMaxSize()
        ) {
            if (!iniciales.isNullOrBlank()) {
                Text(
                    iniciales.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 12.sp
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_inicio_perfil),
                    contentDescription = "Perfil",
                    tint = Tinta,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
