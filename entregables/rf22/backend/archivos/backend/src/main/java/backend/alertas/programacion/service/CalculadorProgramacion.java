package backend.alertas.programacion.service;

import backend.alertas.programacion.model.ProgramacionAlerta.*;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/** Regla pura: sin SQL ni reloj de Android. Fecha y km ya provienen del contexto autorizado. */
@Component
public class CalculadorProgramacion {
    public Objetivos calcular(Configuracion c, LocalDate fecha, Integer km) {
        if (c==null || fecha==null || c.criterio()==null || c.idVehiculo()==null || c.idVehiculo()<=0
            || c.idTipoMantenimiento()==null || c.idTipoMantenimiento()<=0
            || c.idMantenimientoReferencia()!=null && c.idMantenimientoReferencia()<=0)
            throw new IllegalArgumentException("Revisa vehículo, tipo y criterio");
        boolean tiempo=c.criterio()!=Criterio.POR_KILOMETRAJE;
        boolean kilometros=c.criterio()!=Criterio.POR_TIEMPO;
        if (tiempo && (c.unidadTiempo()==null || c.cantidadTiempo()==null || c.cantidadTiempo()<=0)
            || !tiempo && (c.unidadTiempo()!=null || c.cantidadTiempo()!=null)
            || kilometros && (c.intervaloKm()==null || c.intervaloKm()<=0 || km==null || km<=0)
            || !kilometros && c.intervaloKm()!=null)
            throw new IllegalArgumentException("Revisa intervalos y lectura requerida");
        try {
            LocalDate objetivo=tiempo ? (c.unidadTiempo()==Unidad.SEMANAS
                ? fecha.plusDays(Math.multiplyExact(c.cantidadTiempo(),7)) : fecha.plusMonths(c.cantidadTiempo())) : null;
            // PostgreSQL DATE tiene un rango menor que LocalDate. No trasladar fallos de rango al INSERT.
            if (objetivo!=null && (objetivo.getYear()>9999 || !objetivo.isAfter(fecha))) throw new IllegalArgumentException("Fecha fuera de rango");
            Integer objetivoKm=kilometros ? Math.addExact(km,c.intervaloKm()) : null;
            String estado="PROGRAMADA";
            if (tiempo) {
                long dias=ChronoUnit.DAYS.between(fecha,objetivo);
                estado=dias<=7 ? "PENDIENTE" : dias<=30 ? "PROXIMA" : "PROGRAMADA";
            }
            // Al crear, el faltante KM es el intervalo completo positivo: supera su margen exacto del 10%.
            return new Objetivos(objetivo,objetivoKm,estado);
        } catch (ArithmeticException | DateTimeException e) { throw new IllegalArgumentException("Intervalo fuera de rango"); }
    }
}
