package com.mycar.app.features.fotos.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.mycar.app.R
import com.mycar.app.features.fotos.domain.FotoPersonalizadaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ImagenBytesFoto(
    bytes: ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit
) {
    val bitmap = remember(bytes) {
        if (bytes == null || bytes.isEmpty()) null
        else runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            placeholder()
        }
    }
}

@Composable
fun MiniaturaFotoVehiculo(
    idVehiculo: Long,
    tieneFoto: Boolean,
    fotos: FotoPersonalizadaRepository?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholder: @Composable () -> Unit
) {
    var bytes by remember(idVehiculo, tieneFoto) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(idVehiculo, tieneFoto, fotos) {
        if (!tieneFoto || fotos == null) {
            bytes = null
            return@LaunchedEffect
        }
        bytes = withContext(Dispatchers.IO) {
            runCatching { fotos.bytesVehiculo(idVehiculo) }.getOrNull()
        }
    }
    ImagenBytesFoto(
        bytes = bytes,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        placeholder = placeholder
    )
}

/** Imagen predeterminada de vehículo (ilustración frontal). */
@Composable
fun ImagenVehiculoPredeterminada(
    modifier: Modifier = Modifier,
    contentDescription: String? = "Imagen predeterminada del vehículo"
) {
    Image(
        painter = painterResource(R.drawable.ic_vehiculo_foto_default),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
