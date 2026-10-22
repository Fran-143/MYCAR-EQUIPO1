package com.mycar.app.features.inicio.domain

interface HealthRepository {
    suspend fun isServerAvailable(): Boolean
}