package com.mycar.app.features.inicio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.mycar.app.MainActivity
import org.junit.Rule
import org.junit.Test

/** Solo navegación sin sesión: no crea usuarios, sesiones ni registros. */
class InicioNavegacionTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun inicioNoMuestraLosDatosDeEjemploDeLaReferencia() {
        compose.onNodeWithText("Hola").assertExists()
        compose.onNodeWithText("Hola, Carlos").assertDoesNotExist()
        compose.onNodeWithText("58 240 km").assertDoesNotExist()
        listOf("Inicio","Vehículos","Mantenimientos","Alertas").forEach { compose.onNodeWithText(it).assertExists() }
    }
    @Test fun perfilRegresaAInicioYAlertasConservaLaBarra() {
        compose.onNodeWithContentDescription("Perfil").performClick()
        compose.onNodeWithText("Próximamente").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Hola").assertExists()
        compose.onNodeWithText("Alertas").performClick()
        compose.onNodeWithText("Próximamente").assertExists()
        compose.onNodeWithText("Vehículos").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Hola").assertExists()
    }
    @Test fun vehiculosUsaElAccesoRealYNoElFormularioDemo() {
        compose.onNodeWithText("Vehículos").performClick()
        compose.onNodeWithText("Correo").assertExists()
        compose.onNodeWithText("Contraseña").assertExists()
        compose.onNodeWithText("Modo de prueba · no guarda vehículos en la BD").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText("Hola").assertExists()
    }
}
