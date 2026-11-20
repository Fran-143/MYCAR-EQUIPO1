package com.mycar.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferencia local de apariencia (claro/oscuro). Sin red. */
enum class ModoApariencia { CLARO, OSCURO }

object AparienciaPreferencias {
    private const val PREFS = "mycar_apariencia"
    private const val KEY = "modo"
    private val mutable = MutableStateFlow(ModoApariencia.CLARO)
    val flujo: StateFlow<ModoApariencia> = mutable.asStateFlow()

    fun cargar(context: Context) {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ModoApariencia.CLARO.name)
        mutable.value = runCatching { ModoApariencia.valueOf(raw ?: ModoApariencia.CLARO.name) }
            .getOrDefault(ModoApariencia.CLARO)
    }

    fun guardar(context: Context, modo: ModoApariencia) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, modo.name)
            .apply()
        mutable.value = modo
    }
}
