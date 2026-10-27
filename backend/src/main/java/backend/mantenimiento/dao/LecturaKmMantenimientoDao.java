package backend.mantenimiento.dao;

import backend.mantenimiento.model.LecturaKmReferencia;

import java.time.LocalDate;
import java.util.Optional;

public interface LecturaKmMantenimientoDao {

    Optional<LecturaKmReferencia> buscarPorId(Long idLecturaKm);

    Optional<LecturaKmReferencia> buscarPrimeraLecturaPosterior(
            Long idVehiculo, LocalDate fechaServicio);

    Optional<LecturaKmReferencia> buscarUltimaLecturaAnterior(
            Long idVehiculo, LocalDate fechaServicio);
}
