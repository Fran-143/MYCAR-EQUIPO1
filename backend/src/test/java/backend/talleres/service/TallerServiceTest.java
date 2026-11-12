package backend.talleres.service;

import backend.talleres.dao.TallerDao;
import backend.talleres.model.DatosTaller.*;
import backend.talleres.model.TallerNombreDuplicado;
import backend.talleres.model.TallerNoDisponible;
import backend.talleres.model.TallerVersionConflicto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TallerServiceTest {
    TallerDao dao;
    TallerService servicio;

    @BeforeEach void preparar() {
        dao = mock(TallerDao.class);
        servicio = new TallerService(dao, new ValidadorTaller());
    }

    private Taller activo(long id, int version, boolean favorito) {
        return new Taller(id, "TALLER " + id, null, null, null, null, "ACTIVO", favorito, version, Instant.now());
    }

    @Test void registrarInsertaActivoNoFavoritoMayusculas() {
        when(dao.existeNombreActivo(7L, "MI TALLER", 0L)).thenReturn(false);
        when(dao.insertar(eq(7L), eq("MI TALLER"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(activo(11, 0, false));
        var r = servicio.registrar(7, new RegistrarTallerRequest("  Mi taller ", null, null, null, null));
        assertEquals(11, r.idTaller());
        assertFalse(r.favorito());
        verify(dao).insertar(7L, "MI TALLER", null, null, null, null);
    }

    @Test void registrarNombreDuplicadoLanza() {
        when(dao.existeNombreActivo(7L, "CENTRO", 0L)).thenReturn(true);
        assertThrows(TallerNombreDuplicado.class, () ->
                servicio.registrar(7, new RegistrarTallerRequest("Centro", null, null, null, null)));
        verify(dao, never()).insertar(anyLong(), any(), any(), any(), any(), any());
    }

    @Test void actualizarConVersionObsoletaLanza409() {
        when(dao.propioActivo(7, 11)).thenReturn(Optional.of(activo(11, 2, false)));
        assertThrows(TallerVersionConflicto.class, () ->
                servicio.actualizar(7, 11, new ActualizarTallerRequest("Nuevo", null, null, null, null, 1)));
        verify(dao, never()).actualizar(anyLong(), anyLong(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test void retirarSoftDelete() {
        when(dao.propioActivo(7, 11)).thenReturn(Optional.of(activo(11, 0, true)));
        when(dao.retirar(7, 11, 0)).thenReturn(true);
        when(dao.propio(7, 11)).thenReturn(Optional.of(
                new Taller(11, "TALLER 11", null, null, null, null, "INACTIVO", false, 1, Instant.now())));
        var r = servicio.retirar(7, 11, new RetirarTallerRequest(0));
        assertEquals("INACTIVO", r.estado());
        assertFalse(r.favorito());
    }

    @Test void marcarFavoritoExigeActivoPropio() {
        when(dao.propioActivo(7, 11)).thenReturn(Optional.empty());
        assertThrows(TallerNoDisponible.class, () -> servicio.marcarFavorito(7, 11));
        verify(dao, never()).marcarFavoritoAtomico(anyLong(), anyLong());
    }

    @Test void marcarFavoritoAtomicoOk() {
        when(dao.propioActivo(7, 11)).thenReturn(Optional.of(activo(11, 0, false)));
        when(dao.marcarFavoritoAtomico(7, 11)).thenReturn(true);
        when(dao.propio(7, 11)).thenReturn(Optional.of(activo(11, 0, true)));
        assertTrue(servicio.marcarFavorito(7, 11).favorito());
    }

    @Test void marcarFavoritoYaFavoritoEsNoOp() {
        when(dao.propioActivo(7, 11)).thenReturn(Optional.of(activo(11, 0, true)));
        assertTrue(servicio.marcarFavorito(7, 11).favorito());
        verify(dao, never()).marcarFavoritoAtomico(anyLong(), anyLong());
    }

    @Test void listarSoloActivos() {
        when(dao.listarActivos(7)).thenReturn(List.of(activo(1, 0, true), activo(2, 0, false)));
        assertEquals(2, servicio.listar(7).talleres().size());
    }

    @Test void exigirActivoPropioNullEsSinTaller() {
        assertNull(servicio.exigirActivoPropioSiPresente(7, null));
        verifyNoInteractions(dao);
    }

    @Test void exigirActivoPropioAjenoOInactivo404() {
        when(dao.propioActivo(7, 99)).thenReturn(Optional.empty());
        assertThrows(TallerNoDisponible.class, () -> servicio.exigirActivoPropioSiPresente(7, 99L));
    }
}
