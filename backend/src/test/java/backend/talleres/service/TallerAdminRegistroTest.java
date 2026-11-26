package backend.talleres.service;

import backend.talleres.dao.TallerDao;
import backend.talleres.model.DatosTaller.RegistrarTallerGlobalRequest;
import backend.talleres.model.DatosTaller.TallerGlobal;
import backend.talleres.model.TallerNombreDuplicado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TallerAdminRegistroTest {
    TallerDao dao;
    TallerService servicio;

    @BeforeEach
    void preparar() {
        dao = mock(TallerDao.class);
        servicio = new TallerService(dao, new ValidadorTaller(), 0L);
    }

    @Test
    void registraGlobalSinContactoPorDefecto() {
        when(dao.insertarGlobal(eq("OFICIAL"), eq("987654321"), isNull(), isNull(), eq(true), eq(false), eq(false)))
                .thenReturn(new TallerGlobal(9, "OFICIAL", "987654321", null, null, true, false, "ACTIVO", false));
        var r = servicio.registrarGlobalAdmin(new RegistrarTallerGlobalRequest(
                "Oficial", "987654321", null, null, true, false, false));
        assertEquals(9, r.idTallerGlobal());
        assertFalse(r.contactoAutorizado());
    }

    @Test
    void contactoAutorizadoExigeTelefonoYWhatsapp() {
        assertThrows(IllegalArgumentException.class, () ->
                servicio.registrarGlobalAdmin(new RegistrarTallerGlobalRequest(
                        "X", null, null, null, false, true, false)));
    }

    @Test
    void nombreDuplicadoEnGlobal() {
        when(dao.insertarGlobal(anyString(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("uq"));
        assertThrows(TallerNombreDuplicado.class, () ->
                servicio.registrarGlobalAdmin(new RegistrarTallerGlobalRequest(
                        "Dup", "987654321", null, null, true, false, false)));
    }
}
