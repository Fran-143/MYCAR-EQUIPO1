package com.mycar.app.features.sesion

import com.mycar.app.features.sesion.domain.PoliticaRedLocal
import org.junit.Assert.*
import org.junit.Test

class PoliticaRedLocalTest {
    @Test fun emuladorAndroid17RequierePermisoEnDebug() {
        assertTrue(PoliticaRedLocal.requierePermiso(true, 37, "http://10.0.2.2:8081/"))
    }
    @Test fun versionesPosterioresTambienLoRequieren() {
        assertTrue(PoliticaRedLocal.requierePermiso(true, 38, "http://10.0.2.2:8081/"))
    }
    @Test fun loopbackDePruebasTambienRequierePermiso() {
        assertTrue(PoliticaRedLocal.requierePermiso(true, 37, "http://127.0.0.1:8081/"))
    }
    @Test fun releaseNoSolicitaPermisoDeDesarrolloLocal() {
        assertFalse(PoliticaRedLocal.requierePermiso(false, 37, "http://10.0.2.2:8081/"))
    }
    @Test fun androidAnteriorNoSolicitaPermisoInexistente() {
        assertFalse(PoliticaRedLocal.requierePermiso(true, 36, "http://10.0.2.2:8081/"))
    }
    @Test fun servidorRemotoNoRequiereElPermisoLocalDeDebug() {
        assertFalse(PoliticaRedLocal.requierePermiso(true, 37, "https://api.example.invalid/"))
    }
    @Test fun unaRutaNoSeConfundeConElHostLocal() {
        assertFalse(PoliticaRedLocal.requierePermiso(true, 37, "https://example.invalid/10.0.2.2"))
    }
    @Test fun configuracionInvalidaNoProvocaUnaSolicitudDePermiso() {
        assertFalse(PoliticaRedLocal.requierePermiso(true, 37, "http://["))
    }
}
