package com.mycar.app.core.network

import retrofit2.http.GET

interface HealthApi {
    @GET("actuator/health")
    suspend fun getHealth(): HealthResponse
}