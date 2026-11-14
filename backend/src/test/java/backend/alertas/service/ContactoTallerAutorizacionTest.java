package backend.alertas.service;

import backend.alertas.dao.AlertasJdbcDao.TallerContactoFila;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactoTallerAutorizacionTest {

    @Test
    void asociadoAutorizadoExponeTelefonoYFlags() {
        var t = new TallerContactoFila(1, "Asoc", "51987654321", true, true, "ASOCIADO");
        var dto = SincronizarAlertasService.aContacto(t, "EXPLICITO");
        assertEquals("51987654321", dto.telefono());
        assertTrue(dto.tieneWhatsapp());
        assertTrue(dto.contactoAutorizado());
        assertFalse(dto.sinContactoDisponible());
    }

    @Test
    void manualNuncaExponeTelefonoAunqueTengaWa() {
        var t = new TallerContactoFila(2, "Manual", "51987654321", true, true, "MANUAL");
        var dto = SincronizarAlertasService.aContacto(t, "FAVORITO");
        assertNull(dto.telefono());
        assertFalse(dto.tieneWhatsapp());
        assertFalse(dto.contactoAutorizado());
        assertTrue(dto.sinContactoDisponible());
        assertEquals("Manual", dto.nombre());
    }

    @Test
    void asociadoSinAutorizacionRedactaTelefono() {
        var t = new TallerContactoFila(3, "Asoc", "51987654321", true, false, "ASOCIADO");
        var dto = SincronizarAlertasService.aContacto(t, "EXPLICITO");
        assertNull(dto.telefono());
        assertFalse(dto.contactoAutorizado());
        assertTrue(dto.sinContactoDisponible());
    }

    @Test
    void asociadoAutorizadoSinWhatsappNoExpone() {
        var t = new TallerContactoFila(4, "Asoc", "51987654321", false, true, "ASOCIADO");
        var dto = SincronizarAlertasService.aContacto(t, "EXPLICITO");
        assertNull(dto.telefono());
        assertFalse(dto.contactoAutorizado());
    }
}
