package com.mycar.app.features.inicio.data

import com.mycar.app.core.network.HealthApi
import com.mycar.app.features.inicio.domain.HealthRepository

class RemoteHealthRepository(
    private val api: HealthApi
) : HealthRepository {

    override suspend fun isServerAvailable(): Boolean {
        return api.getHealth().status == "UP"
    }
}