package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoNotificaciones;
import backend.notificaciones.dao.NotificacionJdbcDao.DetalleRaw;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Textos y deltas de detalle de aviso — una sola fuente (servidor). */
final class DetalleNotificacionAssembler {
    private DetalleNotificacionAssembler() {}

    static ContratoNotificaciones.DetalleNotificacionDto ensamblar(DetalleRaw r, LocalDate hoyLima) {
        String cat = r.categoria() == null ? "MANTENIMIENTO" : r.categoria().toUpperCase();
        String estado = r.estadoAlerta();
        Integer diasRest = null;
        Integer diasTrans = null;
        Integer kmRest = null;
        Integer kmExc = null;
        String textoTemp = null;
        String textoKm = null;
        String accion;
        boolean accionOk;

        if ("KILOMETRAJE".equals(cat)) {
            accion = "Actualizar kilometraje";
            accionOk = "PENDIENTE".equals(r.estadoGestion()) && r.idVehiculo() != null;
            return new ContratoNotificaciones.DetalleNotificacionDto(
                    r.idNotificacion(), cat, r.estadoGestion(), r.leida(), r.titulo(), r.cuerpo(),
                    r.destino(), r.idAlerta(), r.idVehiculo(), r.fechaEmisionUtc(),
                    "Recordatorio de kilometraje",
                    etiquetaVehiculo(r),
                    "PENDIENTE".equals(r.estadoGestion()) ? "Pendiente de actualización" : r.estadoGestion(),
                    null, null, null, null, null, null,
                    r.frecuenciaKm(), r.ultimaActualizacionKm(), r.recordatorioCorrespondienteKm(),
                    null, null, accion, accionOk);
        }

        if (r.fechaObjetivo() != null) {
            long d = ChronoUnit.DAYS.between(hoyLima, r.fechaObjetivo());
            if (d >= 0) {
                diasRest = (int) d;
                if (d == 0) {
                    textoTemp = "Vence hoy";
                } else {
                    textoTemp = "Faltan " + d + (d == 1 ? " día" : " días");
                }
            } else {
                diasTrans = (int) (-d);
                textoTemp = "Venció hace " + diasTrans + (diasTrans == 1 ? " día" : " días");
                if ("PENDIENTE".equals(estado) && d < 0) {
                    textoTemp = "Objetivo superado hace " + diasTrans
                            + (diasTrans == 1 ? " día" : " días");
                }
            }
        }

        if (r.kmObjetivo() != null && r.kmActual() != null) {
            int delta = r.kmObjetivo() - r.kmActual();
            if (delta > 0) {
                kmRest = delta;
                textoKm = "Faltan " + delta + " km";
            } else if (delta < 0) {
                kmExc = -delta;
                textoKm = "Excedidos " + kmExc + " km";
            } else {
                textoKm = "Kilometraje objetivo alcanzado";
            }
        }

        boolean alertaActiva = estado != null
                && (estado.equals("PROXIMA") || estado.equals("PENDIENTE") || estado.equals("VENCIDA"));
        boolean cerrada = "ATENDIDA".equals(r.estadoGestion()) || "CERRADA".equals(r.estadoGestion())
                || "REALIZADA".equals(estado) || "ANULADA".equals(estado);

        switch (cat) {
            case "SOAT" -> {
                accion = "Ver SOAT";
                accionOk = !cerrada && r.idVehiculo() != null;
            }
            case "LICENCIA" -> {
                accion = "Ver licencia";
                accionOk = !cerrada;
            }
            default -> {
                accion = alertaActiva ? "Ver alerta" : "Ver historial";
                accionOk = r.idAlerta() != null && r.idVehiculo() != null;
            }
        }

        String nombre = r.nombreEvento() != null ? r.nombreEvento() : r.titulo();
        return new ContratoNotificaciones.DetalleNotificacionDto(
                r.idNotificacion(), cat, r.estadoGestion(), r.leida(), r.titulo(), r.cuerpo(),
                r.destino(), r.idAlerta(), r.idVehiculo(), r.fechaEmisionUtc(),
                nombre, etiquetaVehiculo(r), estado,
                r.fechaObjetivo() == null ? null : r.fechaObjetivo().toString(),
                diasRest, diasTrans, kmRest, kmExc, r.kmObjetivo(),
                null, null, null,
                textoTemp, textoKm, accion, accionOk);
    }

    private static String etiquetaVehiculo(DetalleRaw r) {
        String placa = r.placa() == null ? "" : r.placa().trim();
        String modelo = r.vehiculoEtiqueta() == null ? "" : r.vehiculoEtiqueta().trim();
        if (!placa.isEmpty() && !modelo.isEmpty()) {
            return placa + " · " + modelo;
        }
        if (!placa.isEmpty()) {
            return placa;
        }
        return modelo.isEmpty() ? null : modelo;
    }
}
