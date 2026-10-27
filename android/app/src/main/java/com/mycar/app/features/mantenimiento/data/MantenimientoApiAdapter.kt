package com.mycar.app.features.mantenimiento.data

import com.mycar.app.features.mantenimiento.domain.MantenimientoRepository
import retrofit2.Retrofit

/** Recibe la instancia compartida ya configurada con la sesión de Elsa. */
object MantenimientoApiAdapter {
    fun desde(retrofitCompartidoAutenticado: Retrofit): MantenimientoRepository =
        RemoteMantenimientoRepository(retrofitCompartidoAutenticado.create(MantenimientoApi::class.java))
}
