package com.mycar.app.features.alertas.programacion

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mycar.app.features.alertas.programacion.domain.*
import com.mycar.app.features.alertas.programacion.ui.*
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProgramacionPantallasTest {
    @get:Rule val compose = createComposeRule()
    private val contexto = ContextoRf22("FIXTURE", "2026-10-28", listOf(VehiculoRf22(1, "ABC-123", "Toyota", "Corolla", true, "Sintético")), listOf(TipoRf22(1, "CAMBIO_ACEITE", "Cambio de aceite")))
    private fun acciones(revisar: () -> Unit = {}, confirmar: () -> Unit = {}, cancelar: () -> Unit = {}) = AccionesProgramacion(
        cancelar, {}, {}, {}, {}, {}, {}, {}, {}, {}, revisar, confirmar, {}, {}, {}, {}, {})
    @Test fun sinKmMantieneTiempoYBloqueaOtrosCriterios() {
        val e = EstadoProgramacion(FaseProgramacion.FORMULARIO, contexto, 1, 1, antecedentes = AntecedentesRf22("2026-10-28", null, null, emptyList(), null))
        compose.setContent { MyCarTheme { ProgramarAlertaScreen(e, acciones()) } }
        compose.onNodeWithTag("rf22.criterio.AMBOS").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("rf22.criterio.POR_TIEMPO").assertIsEnabled()
        compose.onNodeWithText("No registrado").assertExists()
    }
    @Test fun formularioPermiteEditarIntervaloYRevisar() {
        var revisiones = 0
        compose.setContent { MyCarTheme {
            var e by remember { mutableStateOf(EstadoProgramacion(FaseProgramacion.FORMULARIO, contexto, 1, 1,
                antecedentes = AntecedentesRf22("2026-10-28", 1, 50000, emptyList(), null))) }
            ProgramarAlertaScreen(e, acciones(revisar = { revisiones++ }).copy(cantidad = { e = e.copy(cantidad = it) }))
        } }
        compose.onNodeWithTag("rf22.cantidad").performScrollTo().performTextInput("2")
        compose.onNodeWithTag("rf22.revisar").performScrollTo().performClick()
        Assert.assertEquals(1, revisiones)
    }
    @Test fun resumenMuestraObjetivoYExigeBotonDeConfirmacion() {
        var guardados = 0
        val r = RevisionRf22(ConfiguracionRf22(1, 1, null, CriterioRf22.POR_KILOMETRAJE, null, null, 5000, null),
            BasesRf22("2026-10-28", 1, 50000), ObjetivosRf22(null, 55000, "PROGRAMADA"), null)
        compose.setContent { MyCarTheme { ProgramarAlertaScreen(EstadoProgramacion(FaseProgramacion.REVISION, contexto, 1, 1, revision = r), acciones(confirmar = { guardados++ })) } }
        compose.onNodeWithText("55000 km").performScrollTo().assertExists()
        Assert.assertEquals(0, guardados)
        compose.onNodeWithTag("rf22.confirmar").performScrollTo().performClick(); Assert.assertEquals(1, guardados)
    }
    @Test fun resultadoInciertoOfreceConsultaSinCrearOtraOperacion() {
        var comprobaciones = 0
        val r = RevisionRf22(ConfiguracionRf22(1, 1, null, CriterioRf22.POR_TIEMPO, UnidadRf22.MESES, 1, null, null),
            BasesRf22("2026-10-28", null, null), ObjetivosRf22("2026-11-28", null, "PROGRAMADA"), null)
        compose.setContent { MyCarTheme { ProgramarAlertaScreen(EstadoProgramacion(FaseProgramacion.POR_COMPROBAR, contexto, pendiente = SolicitudRf22(UUID.randomUUID().toString(), r)),
            acciones().copy(comprobar = { comprobaciones++ })) } }
        compose.onNodeWithTag("rf22.comprobar").performClick(); Assert.assertEquals(1, comprobaciones)
        compose.onNodeWithTag("rf22.confirmar").assertDoesNotExist(); compose.onNodeWithTag("rf22.reenviar").assertDoesNotExist()
    }
}
