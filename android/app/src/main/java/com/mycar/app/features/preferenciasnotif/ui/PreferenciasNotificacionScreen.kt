package com.mycar.app.features.preferenciasnotif.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mycar.app.features.preferenciasnotif.domain.PermisoDispositivo
import com.mycar.app.features.preferenciasnotif.domain.PreferenciasNotificacionRepository
import com.mycar.app.features.preferenciasnotif.viewmodel.PreferenciasNotificacionViewModel
import com.mycar.app.features.push.data.PushTokenSync
import com.mycar.app.ui.theme.MyCarBg
import com.mycar.app.ui.theme.MyCarMuted
import com.mycar.app.ui.theme.MyCarNavy
import com.mycar.app.ui.theme.MyCarStatusActivo
import com.mycar.app.ui.theme.MyCarTeal
import com.mycar.app.ui.theme.MyCarTile
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Composable
fun PreferenciasNotificacionRoute(
    repository: PreferenciasNotificacionRepository,
    onVolver: () -> Unit,
    onSesionInvalida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<CancellableContinuation<PermisoDispositivo>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pending?.resume(if (granted) PermisoDispositivo.CONCEDIDO else PermisoDispositivo.DENEGADO)
        pending = null
    }

    fun permisoOsActual(): PermisoDispositivo {
        if (Build.VERSION.SDK_INT < 33) return PermisoDispositivo.CONCEDIDO
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) PermisoDispositivo.CONCEDIDO else PermisoDispositivo.DENEGADO
    }

    suspend fun solicitarPermiso(): PermisoDispositivo {
        if (Build.VERSION.SDK_INT < 33) return PermisoDispositivo.CONCEDIDO
        if (permisoOsActual() == PermisoDispositivo.CONCEDIDO) return PermisoDispositivo.CONCEDIDO
        return suspendCancellableCoroutine { cont ->
            pending = cont
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            cont.invokeOnCancellation { pending = null }
        }
    }

    val factory = remember(repository, context) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PreferenciasNotificacionViewModel(
                    repository,
                    consultarPermisoOs = { permisoOsActual() },
                    solicitarPermisoOs = { solicitarPermiso() },
                    onPermisoConcedidoParaPush = {
                        withContext(Dispatchers.IO) {
                            PushTokenSync.registrarSiSesionActiva(context.applicationContext, forzar = true)
                        }
                    }
                ) as T
            }
        }
    }
    val vm: PreferenciasNotificacionViewModel = viewModel(factory = factory)
    val estado by vm.estado.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var probando by remember { mutableStateOf(false) }
    var mensajePrueba by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(estado.sesionInvalida) { if (estado.sesionInvalida) onSesionInvalida() }

    PreferenciasNotificacionScreen(
        estado = estado,
        onVolver = onVolver,
        onMantenimiento = vm::cambiarMantenimiento,
        onSoat = vm::cambiarSoat,
        onGuardar = vm::guardar,
        probando = probando,
        mensajePrueba = mensajePrueba,
        onProbarPush = {
            if (probando) return@PreferenciasNotificacionScreen
            scope.launch {
                probando = true
                mensajePrueba = null
                val msg = withContext(Dispatchers.IO) {
                    PushTokenSync.probarPush(context.applicationContext)
                }
                mensajePrueba = msg
                probando = false
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenciasNotificacionScreen(
    estado: com.mycar.app.features.preferenciasnotif.viewmodel.EstadoPreferenciasNotificacion,
    onVolver: () -> Unit,
    onMantenimiento: (Boolean) -> Unit,
    onSoat: (Boolean) -> Unit,
    onGuardar: () -> Unit,
    probando: Boolean = false,
    mensajePrueba: String? = null,
    onProbarPush: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize().background(MyCarBg),
        containerColor = MyCarBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = MyCarTile, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Notifications,
                                    contentDescription = null,
                                    tint = MyCarTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            "Notificaciones externas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MyCarNavy,
                            maxLines = 2
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MyCarNavy
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MyCarBg),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Cada interruptor se guarda al cambiarlo. Si el permiso del dispositivo está denegado, no recibirás push aunque el switch esté activo.",
                color = MyCarMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            if (estado.cargando) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MyCarNavy)
                }
                return@Column
            }
            TarjetaPreferencia(
                icono = Icons.Filled.Build,
                titulo = "Mantenimiento",
                descripcion = "Avisos externos sobre servicios y recordatorios de mantenimiento.",
                valor = estado.mantenimiento,
                enabled = !estado.cargando,
                onChange = onMantenimiento
            )
            TarjetaPreferencia(
                icono = Icons.Filled.Security,
                titulo = "SOAT",
                descripcion = "Recordatorios externos de vencimiento y renovación del SOAT.",
                valor = estado.soat,
                enabled = !estado.cargando,
                onChange = onSoat
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MyCarTile) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Info, null, tint = MyCarTeal, modifier = Modifier.size(20.dp))
                    Text(
                        "Los recordatorios de kilometraje se configuran en Perfil → Recordatorios de kilometraje.",
                        color = MyCarMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "Permiso del dispositivo: ${estado.permisoDispositivo.api}",
                color = MyCarMuted,
                style = MaterialTheme.typography.bodySmall
            )
            estado.avisoPermiso?.let {
                Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
            }
            estado.mensajeError?.let {
                Text(it, color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
            }
            estado.mensajeExito?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MyCarStatusActivo, modifier = Modifier.size(18.dp))
                    Text(it, color = MyCarStatusActivo, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            if (estado.guardando) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MyCarNavy, strokeWidth = 2.dp)
                    Spacer(Modifier.size(10.dp))
                    Text("Guardando…", color = MyCarMuted)
                }
            }
            if (estado.permisoDispositivo == PermisoDispositivo.DENEGADO) {
                Button(
                    onClick = onGuardar,
                    enabled = !estado.guardando,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MyCarNavy)
                ) {
                    Text("Activar permiso de notificaciones", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Verificación FCM",
                fontWeight = FontWeight.SemiBold,
                color = MyCarNavy
            )
            Text(
                "Envía un push de prueba solo a tus dispositivos registrados.",
                color = MyCarMuted,
                style = MaterialTheme.typography.bodySmall
            )
            mensajePrueba?.let {
                Text(
                    it,
                    color = if (it.contains("enviado", ignoreCase = true)) MyCarStatusActivo
                    else Color(0xFFC62828),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = onProbarPush,
                enabled = !probando && !estado.guardando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MyCarTeal)
            ) {
                if (probando) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    if (probando) "Enviando…" else "Enviar push de prueba",
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TarjetaPreferencia(
    icono: ImageVector,
    titulo: String,
    descripcion: String,
    valor: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MyCarTile, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icono, null, tint = MyCarTeal, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, color = MyCarNavy, fontWeight = FontWeight.SemiBold)
                Text(descripcion, color = MyCarMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = valor,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MyCarNavy,
                    checkedThumbColor = Color.White
                )
            )
        }
    }
}
