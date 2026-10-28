package com.mycar.app

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.mycar.app.features.inicio.ui.MyCarNavigation
import com.mycar.app.ui.theme.MyCarTheme

class MainActivity : ComponentActivity() {
    private var solicitudesVehiculos by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        if(intent.getBooleanExtra("abrir_vehiculos",false)) solicitudesVehiculos++
        setContent { MyCarTheme(dynamicColor=false) { MyCarNavigation(solicitudesVehiculos) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if(intent.getBooleanExtra("abrir_vehiculos",false)) solicitudesVehiculos++
    }
}
