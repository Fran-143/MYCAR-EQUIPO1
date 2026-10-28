package com.mycar.app.features.sesion.ui

import android.os.Bundle
import androidx.activity.ComponentActivity

class SesionVehiculosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(android.content.Intent(this, com.mycar.app.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("abrir_vehiculos",true))
        finish()
    }
}
