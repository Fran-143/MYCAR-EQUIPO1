package com.mycar.app.features.soporte.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycar.app.features.soporte.data.SolicitudDetalleDto
import com.mycar.app.features.soporte.domain.SoporteFormulario
import com.mycar.app.features.soat.ui.SoatVistaDocumento
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy

private val BannerPendiente = Color(0xFFFFF8E1)
private val BannerRevision = Color(0xFFE3F2FD)
private val BannerAprobada = Color(0xFFE8F5E9)
private val BannerRechazada = Color(0xFFFFEBEE)

@Composable
fun CabeceraDetalleSolicitud(codigo: String, estado: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            codigo,
            fontWeight = FontWeight.Bold,
            color = MyCarNavy,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        ChipEstado(estado)
    }
}

@Composable
fun CamposDetalleSolicitudVin(
    d: SolicitudDetalleDto,
    mostrarUsuario: Boolean,
    evidenciaBytes: ByteArray? = null,
    evidenciaCargando: Boolean = false,
    evidenciaError: String? = null,
    evidenciaRevelada: Boolean = false,
    onRevelarEvidencia: (() -> Unit)? = null,
    onOcultarEvidencia: (() -> Unit)? = null
) {
    val aprobado = d.estado.equals("APROBADA", ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FilaIconoDetalle(Icons.Outlined.DirectionsCar, "Tipo", "Corrección de VIN")
        if (mostrarUsuario) {
            val nombre = d.nombreUsuario?.takeIf { it.isNotBlank() } ?: "Usuario"
            val correo = d.correoUsuario?.takeIf { it.isNotBlank() }
            FilaIconoDetalle(
                Icons.Outlined.Person,
                "Usuario",
                if (correo != null) "$nombre ($correo)" else nombre
            )
        }
        FilaIconoDetalle(Icons.Outlined.DirectionsCar, "Vehículo", d.vehiculoEtiqueta.ifBlank { d.placa })
        d.vinRegistrado?.let {
            FilaIconoDetalle(
                Icons.Outlined.QrCode2,
                if (aprobado) "VIN anterior" else "VIN actual",
                it
            )
        }
        d.vinPropuesto?.let {
            FilaIconoDetalle(
                Icons.Outlined.QrCode2,
                if (aprobado) "VIN actualizado" else "VIN solicitado",
                it
            )
        }
        when {
            aprobado -> {
                val obs = d.eventos.lastOrNull { it.estadoNuevo.equals("APROBADA", true) }?.observacion
                    ?: "Solicitud aprobada y VIN actualizado correctamente."
                FilaIconoDetalle(Icons.Outlined.ChatBubbleOutline, "Observación", obs)
            }
            d.estado.equals("RECHAZADA", true) -> {
                d.motivoRechazo?.takeIf { it.isNotBlank() }?.let { motivo ->
                    FilaIconoDetalle(Icons.Outlined.ChatBubbleOutline, "Motivo de rechazo", motivo)
                }
                d.motivo?.takeIf { it.isNotBlank() }?.let { motivo ->
                    FilaIconoDetalle(Icons.Outlined.ChatBubbleOutline, "Motivo de la solicitud", motivo)
                }
            }
            else -> d.motivo?.let {
                FilaIconoDetalle(Icons.Outlined.ChatBubbleOutline, "Motivo", it)
            }
        }
        if (d.tieneEvidencia) {
            val peso = d.tamanoEvidenciaBytes?.let { bytes ->
                val mb = bytes / (1024.0 * 1024.0)
                if (mb >= 0.1) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
            }
            val etiquetaArchivo = listOfNotNull(
                d.nombreEvidencia?.takeIf { it.isNotBlank() } ?: "Evidencia",
                peso?.let { "($it)" }
            ).joinToString(" ")
            FilaIconoDetalle(Icons.Outlined.AttachFile, "Evidencia", etiquetaArchivo)
            if (onRevelarEvidencia != null && onOcultarEvidencia != null) {
                SoatVistaDocumento(
                    bytes = evidenciaBytes,
                    esPdf = SoporteFormulario.esPdf(d.contentTypeEvidencia, d.nombreEvidencia),
                    cargando = evidenciaCargando,
                    error = evidenciaError,
                    revelado = evidenciaRevelada,
                    onRevelar = onRevelarEvidencia,
                    onOcultar = onOcultarEvidencia,
                    tieneDocumento = true
                )
            }
        }
        BannerEstadoSolicitud(d.estado)
    }
}

@Composable
fun FilaIconoDetalle(icono: ImageVector, etiqueta: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icono, contentDescription = null, tint = MyCarMuted, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(etiqueta, fontWeight = FontWeight.SemiBold, color = MyCarNavy, style = MaterialTheme.typography.labelLarge)
            Text(valor, color = Color(0xFF2D3748), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BannerEstadoSolicitud(estado: String) {
    val (bg, fg, icono, texto) = when (estado.uppercase()) {
        "PENDIENTE" -> Quad(
            BannerPendiente, Color(0xFFF9A825), Icons.Outlined.Info,
            "Tu solicitud está pendiente de revisión por el equipo de soporte."
        )
        "EN_REVISION", "EN REVISIÓN" -> Quad(
            BannerRevision, Color(0xFF1E88E5), Icons.Outlined.Info,
            "Tu solicitud está en revisión por el equipo de soporte."
        )
        "APROBADA" -> Quad(
            BannerAprobada, Color(0xFF2E7D32), Icons.Outlined.CheckCircle,
            "El VIN del vehículo ya fue actualizado en MyCar."
        )
        "RECHAZADA" -> Quad(
            BannerRechazada, Color(0xFFC62828), Icons.Outlined.WarningAmber,
            "La solicitud fue rechazada. Puedes registrar una nueva solicitud con la evidencia correcta."
        )
        else -> Quad(Color(0xFFF0F5FA), MyCarMuted, Icons.Outlined.Info, SoporteFormulario.etiquetaEstado(estado))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(12.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icono, contentDescription = null, tint = fg)
        Text(texto, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
