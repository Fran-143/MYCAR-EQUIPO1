package backend.mantenimiento.service;

import backend.mantenimiento.dao.LecturaKmMantenimientoDao;
import backend.mantenimiento.model.DatosRegistroMantenimiento;
import backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio;
import backend.mantenimiento.model.LecturaKmReferencia;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public class LecturaKmMantenimientoService {

    private final LecturaKmMantenimientoDao dao;

    public LecturaKmMantenimientoService(
            LecturaKmMantenimientoDao dao
    ) {
        this.dao = dao;
    }

    public void validarReferencia(
            Long idVehiculo,
            DatosRegistroMantenimiento datos
    ) {
        if (datos.origenKmServicio()
                == OrigenKmServicio.HISTORICO_MANUAL) {
            dao.buscarUltimaLecturaAnterior(idVehiculo, datos.fechaServicio())
                    .ifPresent(lectura -> {
                        LocalDate fechaLectura = fechaEnLima(lectura);
                        if (!Objects.equals(lectura.idVehiculo(), idVehiculo)
                                || !fechaLectura.isBefore(datos.fechaServicio())) {
                            throw new IllegalArgumentException(
                                    "La lectura anterior no corresponde al vehículo y fecha del servicio"
                            );
                        }
                        if (datos.kmServicio() < lectura.valorKm()) {
                            throw new IllegalArgumentException(
                                    "kmServicio: es inferior a la última lectura anterior"
                            );
                        }
                    });
            dao.buscarPrimeraLecturaPosterior(idVehiculo, datos.fechaServicio())
                    .ifPresent(lectura -> {
                        LocalDate fechaLectura = fechaEnLima(lectura);
                        if (!Objects.equals(lectura.idVehiculo(), idVehiculo)
                                || !fechaLectura.isAfter(datos.fechaServicio())) {
                            throw new IllegalArgumentException(
                                    "La lectura posterior no corresponde al vehículo y fecha del servicio"
                            );
                        }
                        if (datos.kmServicio() > lectura.valorKm()) {
                            throw new IllegalArgumentException(
                                    "kmServicio: supera la primera lectura posterior"
                            );
                        }
                    });
            return;
        }

        LecturaKmReferencia lectura = dao
                .buscarPorId(datos.idLecturaKmReferencia())
                .orElseThrow(() -> new IllegalArgumentException(
                        "idLecturaKmReferencia: la lectura no existe"
                ));

        if (!Objects.equals(lectura.idVehiculo(), idVehiculo)) {
            throw new IllegalArgumentException(
                    "idLecturaKmReferencia: no pertenece al vehículo"
            );
        }

        if (!Objects.equals(lectura.valorKm(), datos.kmServicio())) {
            throw new IllegalArgumentException(
                    "kmServicio: debe coincidir con la lectura seleccionada"
            );
        }

        LocalDate fechaLecturaEnLima = fechaEnLima(lectura);

        if (datos.fechaServicio().isBefore(fechaLecturaEnLima)) {
            throw new IllegalArgumentException(
                    "fechaServicio: no puede ser anterior al día de la lectura"
            );
        }
    }

    private LocalDate fechaEnLima(LecturaKmReferencia lectura) {
        return lectura.fechaLecturaUtc()
                .atZone(ZoneId.of("America/Lima"))
                .toLocalDate();
    }
}
