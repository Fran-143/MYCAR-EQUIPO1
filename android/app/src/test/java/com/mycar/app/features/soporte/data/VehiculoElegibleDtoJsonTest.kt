package com.mycar.app.features.soporte.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehiculoElegibleDtoJsonTest {
    private val gson = Gson()

    @Test
    fun parseaCamelCaseDelBackend() {
        val json = """
            {"idVehiculo":2,"placa":"ABC123","marca":"TOYOTA","modelo":"YARIS",
             "numeroVin":"1HGCM82633A004352","tieneTive":true}
        """.trimIndent()
        val dto = gson.fromJson(json, VehiculoElegibleDto::class.java)
        assertEquals(2L, dto.idVehiculo)
        assertEquals("1HGCM82633A004352", dto.numeroVin)
        assertTrue(dto.tieneTive)
    }

    @Test
    fun parseaSnakeCaseAlterno() {
        val json = """
            {"idVehiculo":9,"placa":"XYZ987","marca":"KIA","modelo":"RIO",
             "numero_vin":"1HGCM82633A004353","tiene_tive":false}
        """.trimIndent()
        val dto = gson.fromJson(json, VehiculoElegibleDto::class.java)
        assertEquals("1HGCM82633A004353", dto.numeroVin)
        assertEquals(false, dto.tieneTive)
    }

    @Test
    fun vinNuloSeConserva() {
        val json = """
            {"idVehiculo":3,"placa":"DEF456","marca":"HONDA","modelo":"CIVIC",
             "numeroVin":null,"tieneTive":false}
        """.trimIndent()
        val dto = gson.fromJson(json, VehiculoElegibleDto::class.java)
        assertEquals(null, dto.numeroVin)
    }
}
