package com.mycar.app.features.autenticacion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import com.mycar.app.features.autenticacion.data.RegistrationService
import com.mycar.app.features.autenticacion.domain.PrivacyPolicyConfiguration
import com.mycar.app.features.autenticacion.domain.RegistrationForm
import com.mycar.app.ui.theme.MyCarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Sólo UI y recurso local; no AuthGraph, red, credenciales ni PostgreSQL. */
class LocalPrivacyNavigationTest {
    @get:Rule val compose = createComposeRule()
    private var calls = 0
    private fun show() {
        val policy = PrivacyPolicyConfiguration.academicLocal()
        val vm = RegistrationViewModel(RegistrationService { calls++ }, policy)
        vm.edit { RegistrationForm("Ana", "Pérez", "persona@example.org", "01/01/1995", "Aa1!abcd", "Aa1!abcd") }
        compose.setContent {
            val state by vm.state.collectAsState()
            MyCarTheme {
                RegistrationContent(state, policy, vm::edit, vm::submit, {}, {},
                    vm::openLocalPolicy, vm::closeLocalPolicy, vm::localPolicyLoaded)
            }
        }
    }
    private fun readAndReturn() {
        compose.onNodeWithText("Consultar Política de Privacidad").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onAllNodesWithText("Ley N.º 29733", substring = true).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.onNodeWithText("Volver al registro").performClick()
    }
    @Test fun localReaderReturnsWithConsentUnchecked() {
        show()
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
        readAndReturn()
        compose.onNode(isToggleable()).assertIsEnabled().assertIsOff()
        compose.onNodeWithText("Ana").assertExists()
        compose.onNode(isToggleable()).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { click -> click() }.assertIsOn()
        assertEquals(0, calls)
    }
    @Test fun readingWithoutConsentCannotSendRegistration() {
        show()
        readAndReturn()
        compose.onNodeWithTag("registration.submit").performScrollTo().performClick()
        compose.onNodeWithText("Debes aceptar expresamente la política de privacidad.").assertExists()
        assertEquals(0, calls)
    }
}
