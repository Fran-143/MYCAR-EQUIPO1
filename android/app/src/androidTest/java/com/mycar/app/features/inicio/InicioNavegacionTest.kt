package com.mycar.app.features.inicio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import com.mycar.app.MainActivity
import org.junit.Rule
import org.junit.Test
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.mycar.app.BuildConfig
import com.mycar.app.features.sesion.domain.PoliticaRedLocal
import org.junit.rules.TestRule
import org.junit.runners.model.Statement

/** Ejecutar sin sesión activa en el emulador de pruebas; no crea cuentas ni registros. */
class InicioNavegacionTest {
    // Conceder antes de lanzar MainActivity: el diálogo del sistema no es parte de estos tests de navegación.
    @get:Rule(order = 0) val redLocal = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= 37 && PoliticaRedLocal.requierePermiso(
                    BuildConfig.DEBUG, Build.VERSION.SDK_INT, BuildConfig.API_BASE_URL)) {
                    val instrumentacion = InstrumentationRegistry.getInstrumentation()
                    instrumentacion.uiAutomation.grantRuntimePermission(
                        instrumentacion.targetContext.packageName, PoliticaRedLocal.PERMISO)
                }
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun bienvenida() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Crear cuenta").fetchSemanticsNodes().isNotEmpty()
        }
    }
    @Test fun entradaUsaBienvenidaRealSinDatosDeEjemplo() {
        bienvenida()
        compose.onNodeWithText("Iniciar sesión").assertExists()
        compose.onNodeWithText("Hola, Carlos").assertDoesNotExist()
        compose.onNodeWithText("58 240 km").assertDoesNotExist()
        compose.onNodeWithText("Comprobar conexión").assertDoesNotExist()
    }
    @Test fun loginDeElsaRegresaABienvenida() {
        bienvenida()
        compose.onNodeWithText("Iniciar sesión").performClick()
        compose.onNodeWithText("Correo electrónico").assertExists()
        compose.onNodeWithText("Contraseña").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Crear cuenta").assertExists()
    }
    @Test fun registroDeCuentaUsaLaPantallaReal() {
        bienvenida()
        compose.onNodeWithText("Crear cuenta").performClick()
        compose.onNodeWithText("Nombre").assertExists()
        compose.onNodeWithText("Fecha de nacimiento").assertExists()
        compose.onNodeWithText("Modo de prueba · no guarda vehículos en la BD").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText("Iniciar sesión").assertExists()
    }
}
