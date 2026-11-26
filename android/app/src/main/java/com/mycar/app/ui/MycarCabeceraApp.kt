package com.mycar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mycar.app.R
import com.mycar.app.features.fotos.ui.ImagenBytesFoto
import com.mycar.app.features.notificaciones.domain.NotificacionesBadgeStore

private val Tinta = Color(0xFF163468)
private val AzulMarca = Color(0xFF079FDC)
private val TealAvatar = Color(0xFF0D9488)
private val RojoBadge = Color(0xFFE53935)

/**
 * Cabecera uniforme MyCar: logo/nombre + notificaciones + perfil.
 * Badge de campana: solo NO LEÍDAS (0 oculto, 1–9 exacto, 10+ → 9+).
 */
@Composable
fun MycarCabeceraApp(
    onNotificaciones: (() -> Unit)? = null,
    onPerfil: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    conStatusBars: Boolean = true,
    avatarBytes: ByteArray? = null,
    iniciales: String? = null,
    /** Si null, usa [NotificacionesBadgeStore]. Pasa un valor para tests o override. */
    notificacionesNoLeidas: Int? = null
) {
    val storeNoLeidas by NotificacionesBadgeStore.noLeidas.collectAsStateWithLifecycle()
    val noLeidas = notificacionesNoLeidas ?: storeNoLeidas
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
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            painterResource(R.drawable.ic_inicio_campana),
                            contentDescription = "Notificaciones",
                            tint = Tinta,
                            modifier = Modifier.size(24.dp)
                        )
                        BadgeNoLeidas(noLeidas, Modifier.offset(x = 6.dp, y = (-4).dp))
                    }
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
fun BadgeNoLeidas(cantidad: Int, modifier: Modifier = Modifier) {
    if (cantidad <= 0) return
    val texto = if (cantidad > 9) "9+" else cantidad.toString()
    val esPlus = cantidad > 9
    Box(
        modifier = modifier
            .height(14.dp)
            .then(if (esPlus) Modifier.widthIn(min = 18.dp) else Modifier.size(14.dp))
            .clip(if (esPlus) RoundedCornerShape(50) else CircleShape)
            .background(RojoBadge)
            .padding(horizontal = if (esPlus) 4.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 8.sp,
            maxLines = 1
        )
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
