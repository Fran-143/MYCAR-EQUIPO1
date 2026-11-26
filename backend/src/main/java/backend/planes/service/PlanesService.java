package backend.planes.service;

import backend.planes.PlanesProperties;
import backend.planes.api.dto.ContratoPlanes;
import backend.planes.dao.PlanesJdbcDao;
import backend.planes.dao.PlanesJdbcDao.EventoRow;
import backend.planes.dao.PlanesJdbcDao.OperacionRow;
import backend.planes.dao.PlanesJdbcDao.PlanRow;
import backend.planes.dao.PlanesJdbcDao.SolicitudRow;
import backend.planes.dao.PlanesJdbcDao.SuscripcionRow;
import backend.planes.mercadopago.MercadoPagoTestClient;
import backend.planes.model.ErrorPlanes;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanesService {
    private final PlanesJdbcDao dao;
    private final PlanesProperties props;
    private final MercadoPagoTestClient mp;
    private final boolean localTestEnabled;

    public PlanesService(
            PlanesJdbcDao dao,
            PlanesProperties props,
            MercadoPagoTestClient mp,
            @Value("${mycar.local-test.enabled:false}") boolean localTestEnabled) {
        this.dao = dao;
        this.props = props;
        this.mp = mp;
        this.localTestEnabled = localTestEnabled;
    }

    public ContratoPlanes.CatalogoResponse catalogo() {
        boolean sandboxOk = sandboxOperativo();
        boolean mpOk = mp.disponible();
        boolean pruebaOk = pruebaGratuitaOperativa();
        List<ContratoPlanes.PlanDto> planes = dao.listarActivos().stream()
                .map(p -> toPlanDto(p, sandboxOk, pruebaOk))
                .toList();
        String preferido = mpOk ? "MERCADO_PAGO_TEST" : (sandboxOk ? "SIMULADOR_LOCAL" : null);
        return new ContratoPlanes.CatalogoResponse(planes, sandboxOk, mpOk, pruebaOk, preferido);
    }

    @Transactional
    public ContratoPlanes.MiSuscripcionResponse miSuscripcion(long idUsuario) {
        reconciliarVencimiento(idUsuario);
        SuscripcionRow s = dao.suscripcionActiva(idUsuario)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "No hay suscripción activa."));
        // Autocura: Free con varios ACTIVO (p. ej. tras rechazo/apagar) → solo el principal.
        if ("FREE".equals(s.planCodigo())) {
            exigirLimiteActivosPlanFree(idUsuario);
            s = dao.suscripcionActiva(idUsuario).orElse(s);
        }
        return toMiSuscripcion(s);
    }

    /** RF26 / RF29: derecho Premium efectivo vigente (prueba o sandbox aprobados y no vencidos). */
    @Transactional
    public boolean tienePremiumEfectivo(long idUsuario) {
        reconciliarVencimiento(idUsuario);
        SuscripcionRow s = dao.suscripcionActiva(idUsuario).orElse(null);
        return s != null && esPremiumActivo(s);
    }

    @Transactional(readOnly = true)
    public List<ContratoPlanes.EventoDto> misEventos(long idUsuario) {
        return dao.eventosUsuario(idUsuario, 50).stream().map(this::toEvento).toList();
    }

    @Transactional(readOnly = true)
    public List<ContratoPlanes.SolicitudDto> misSolicitudes(long idUsuario) {
        return dao.solicitudesUsuario(idUsuario, 30).stream().map(this::toSolicitud).toList();
    }

    @Transactional(readOnly = true)
    public ContratoPlanes.SolicitudDetalleDto miSolicitudDetalle(long idUsuario, String codigo) {
        SolicitudRow s = dao.solicitudPorCodigo(codigo)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Solicitud no encontrada."));
        if (s.idUsuario() != idUsuario) {
            throw new ErrorPlanes("FORBIDDEN", "No puedes consultar esta solicitud.");
        }
        return new ContratoPlanes.SolicitudDetalleDto(
                toSolicitud(s),
                dao.eventosUsuario(idUsuario, 40).stream().map(this::toEvento).toList());
    }

    @Transactional
    public ContratoPlanes.SolicitudDto solicitarPruebaGratuita(long idUsuario) {
        if (!dao.cuentaActiva(idUsuario)) {
            throw new ErrorPlanes("NO_AUTORIZADO", "La cuenta debe estar ACTIVA.");
        }
        reconciliarVencimiento(idUsuario);
        exigirElegibilidadPrueba(idUsuario);
        PlanRow plan = dao.porCodigo("PREMIUM")
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Plan PREMIUM no disponible."));
        validarPruebaGratuitaConfigurada();
        if (dao.solicitudAbiertaUsuario(idUsuario).isPresent()) {
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }
        String codigo = nuevoCodigoSolicitud("PRG");
        try {
            dao.crearSolicitud(codigo, idUsuario, plan.idPlan(), "PRUEBA_GRATUITA",
                    "PENDIENTE_APROBACION", null);
        } catch (DataIntegrityViolationException e) {
            // Índice abierto o unicidad de prueba gratuita (V48).
            if (dao.countPruebasSolicitadasUsuario(idUsuario) >= 1) {
                throw new ErrorPlanes("PRUEBA_NO_ELEGIBLE",
                        "Ya utilizaste tu prueba gratuita Premium.");
            }
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }
        SuscripcionRow actual = dao.suscripcionActiva(idUsuario).orElse(null);
        dao.registrarEvento(idUsuario, actual == null ? null : actual.idSuscripcion(), null,
                "SOLICITUD_PRUEBA_CREADA", "codigo=" + codigo);
        return toSolicitud(dao.solicitudPorCodigo(codigo).orElseThrow());
    }

    @Transactional
    public ContratoPlanes.OperacionPagoResponse crearOperacion(
            long idUsuario, String planCodigo, String proveedorSolicitado, String correo) {
        exigirSandbox();
        if (!dao.cuentaActiva(idUsuario)) {
            throw new ErrorPlanes("NO_AUTORIZADO", "La cuenta debe estar ACTIVA.");
        }
        reconciliarVencimiento(idUsuario);
        SuscripcionRow actual = dao.suscripcionActiva(idUsuario)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "No hay suscripción activa."));
        if (esPremiumActivo(actual)) {
            throw new ErrorPlanes("YA_PREMIUM", "Ya tienes Premium activo.");
        }
        if (dao.solicitudAbiertaUsuario(idUsuario).isPresent()) {
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }

        PlanRow plan = dao.porCodigo(planCodigo == null ? "PREMIUM" : planCodigo.strip().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Plan no disponible."));
        if (!"PREMIUM".equals(plan.codigo())) {
            throw new ErrorPlanes("DATOS_INVALIDOS", "Solo se puede contratar PREMIUM en sandbox.");
        }
        validarPlanListoParaContratar(plan);

        String proveedor = resolverProveedor(proveedorSolicitado);
        String referencia = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        long idOp = dao.crearOperacion(
                idUsuario, plan.idPlan(), referencia, proveedor,
                plan.precioDemo(), plan.moneda(), "CREADA");

        String init = null;
        String sandboxInit = null;
        if ("MERCADO_PAGO_TEST".equals(proveedor)) {
            String mail = correo;
            if (mail == null || mail.isBlank()) {
                mail = dao.usuarioBasico(idUsuario)
                        .map(m -> (String) m.get("correo"))
                        .orElse("test@mycar.test");
            }
            var pref = mp.crearPreferencia(
                    referencia, "MyCar Premium (TEST)", plan.precioDemo(), plan.moneda(), mail);
            dao.actualizarInitPoints(idOp, pref.initPoint(), pref.sandboxInitPoint(), pref.idExterno());
            init = pref.initPoint();
            sandboxInit = pref.sandboxInitPoint();
            dao.forzarEstadoOperacion(idOp, "PENDIENTE", "preferencia_mp_creada");
        }

        String codigoSol = nuevoCodigoSolicitud("PSS");
        try {
            dao.crearSolicitud(codigoSol, idUsuario, plan.idPlan(), "PAGO_SANDBOX",
                    "PENDIENTE_PAGO", idOp);
        } catch (DataIntegrityViolationException e) {
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }

        dao.registrarEvento(idUsuario, actual.idSuscripcion(), idOp, "OPERACION_CREADA",
                "proveedor=" + proveedor + "; ref=" + referencia + "; sol=" + codigoSol);

        OperacionRow op = dao.operacionPorReferencia(referencia)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Operación no persistida."));
        return toOperacion(op, false, init, sandboxInit, codigoSol, "PENDIENTE_PAGO",
                "Operación de prueba creada. Completa el pago simulado.");
    }

    @Transactional
    public ContratoPlanes.OperacionPagoResponse simular(
            long idUsuario, String referencia, String escenario) {
        exigirSandbox();
        OperacionRow op = dao.operacionPorReferenciaUsuario(referencia, idUsuario)
                .orElseThrow(() -> new ErrorPlanes("OPERACION_NO_ENCONTRADA", "Operación no encontrada."));
        if (!"SIMULADOR_LOCAL".equals(op.proveedor())) {
            throw new ErrorPlanes("ESTADO_INVALIDO",
                    "Solo el simulador interno acepta escenarios locales.");
        }
        SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
        if (!List.of("CREADA", "PENDIENTE").contains(op.estado())) {
            return toOperacion(op, false, null, null,
                    sol == null ? null : sol.codigo(),
                    sol == null ? null : sol.estado(),
                    mensajePostPago(sol));
        }
        String destino = normalizarEscenario(escenario);
        String evidencia = "simulador_interno;escenario=" + destino;
        int n = dao.forzarEstadoOperacion(op.id(), destino, evidencia);
        if (n != 1) {
            OperacionRow actual = dao.operacionPorReferencia(referencia).orElse(op);
            return toOperacion(actual, false, null, null,
                    sol == null ? null : sol.codigo(),
                    sol == null ? null : sol.estado(), null);
        }
        String estadoSol = sol == null ? null : sol.estado();
        String mensaje;
        if ("APROBADA".equals(destino)) {
            if (sol != null) {
                int u = dao.actualizarEstadoSolicitud(sol.idSolicitud(), sol.versionFila(),
                        "PENDIENTE_PAGO", "PENDIENTE_APROBACION");
                if (u != 1 && !"PENDIENTE_APROBACION".equals(sol.estado())) {
                    throw new ErrorPlanes("CONFLICTO", "No se pudo actualizar la solicitud.");
                }
                estadoSol = "PENDIENTE_APROBACION";
            }
            dao.registrarEvento(idUsuario, null, op.id(), "PAGO_PRUEBA_APROBADO",
                    evidencia + "; pendiente_aprobacion_admin");
            mensaje = "Pago de prueba completado. Tu solicitud Premium está pendiente de aprobación administrativa.";
        } else if ("PENDIENTE".equals(destino)) {
            dao.registrarEvento(idUsuario, null, op.id(), "OPERACION_PENDIENTE", evidencia);
            mensaje = "Esperando confirmación de pago de prueba.";
        } else {
            if (sol != null && "PENDIENTE_PAGO".equals(sol.estado())) {
                dao.decidirSolicitud(sol.idSolicitud(), sol.versionFila(), "PENDIENTE_PAGO",
                        "CANCELADA", null, "Pago de prueba " + destino.toLowerCase(Locale.ROOT),
                        null, null);
                estadoSol = "CANCELADA";
            }
            dao.registrarEvento(idUsuario, null, op.id(), "OPERACION_" + destino, evidencia);
            mensaje = "Pago de prueba " + destino.toLowerCase(Locale.ROOT) + ".";
        }
        OperacionRow finalOp = dao.operacionPorReferencia(referencia).orElse(op);
        SolicitudRow solFinal = dao.solicitudPorOperacion(op.id()).orElse(sol);
        return toOperacion(finalOp, false, null, null,
                solFinal == null ? null : solFinal.codigo(),
                solFinal == null ? estadoSol : solFinal.estado(),
                mensaje);
    }

    @Transactional
    public ContratoPlanes.OperacionPagoResponse consultarOperacion(long idUsuario, String referencia) {
        OperacionRow op = dao.operacionPorReferenciaUsuario(referencia, idUsuario)
                .orElseThrow(() -> new ErrorPlanes("OPERACION_NO_ENCONTRADA", "Operación no encontrada."));
        if ("MERCADO_PAGO_TEST".equals(op.proveedor())
                && List.of("CREADA", "PENDIENTE").contains(op.estado())
                && mp.disponible()) {
            return verificarYAplicarMp(idUsuario, op);
        }
        SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
        return toOperacion(op, false, null, null,
                sol == null ? null : sol.codigo(),
                sol == null ? null : sol.estado(),
                mensajePostPago(sol));
    }

    @Transactional
    public ContratoPlanes.VerificarResponse verificarAdmin(String referencia) {
        OperacionRow op = dao.operacionPorReferencia(referencia)
                .orElseThrow(() -> new ErrorPlanes("OPERACION_NO_ENCONTRADA", "Operación no encontrada."));
        if ("SIMULADOR_LOCAL".equals(op.proveedor())) {
            return new ContratoPlanes.VerificarResponse(
                    op.referencia(), op.proveedor(), op.estado(),
                    "Resultado generado por simulador interno", op.evidencia());
        }
        if (!mp.disponible()) {
            throw new ErrorPlanes("PROVEEDOR_NO_CONFIGURADO",
                    "Mercado Pago TEST no configurado; no hay verificación S2S.");
        }
        var ver = mp.verificarPorPreferenciaOPago(op.idExterno(), op.referencia());
        if (List.of("CREADA", "PENDIENTE").contains(op.estado())
                && !"PENDIENTE".equals(ver.estadoNormalizado())) {
            dao.forzarEstadoOperacion(op.id(), ver.estadoNormalizado(), ver.evidenciaSegura());
            if ("APROBADA".equals(ver.estadoNormalizado())) {
                promoverSolicitudTrasPago(op);
            }
        }
        OperacionRow actual = dao.operacionPorReferencia(referencia).orElse(op);
        return new ContratoPlanes.VerificarResponse(
                actual.referencia(), actual.proveedor(), actual.estado(),
                "Verificación servidor-servidor Mercado Pago TEST", actual.evidencia());
    }

    @Transactional
    public ContratoPlanes.DecisionResponse aprobarSolicitud(long idAdmin, String codigo, String observacion) {
        SolicitudRow sol = dao.solicitudPorCodigoForUpdate(codigo)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Solicitud no encontrada."));
        if (!"PENDIENTE_APROBACION".equals(sol.estado())) {
            throw new ErrorPlanes("ESTADO_INVALIDO",
                    "La solicitud no está pendiente de aprobación (estado=" + sol.estado() + ").");
        }
        if ("PAGO_SANDBOX".equals(sol.modalidad())) {
            // Flujo simplificado: solicitud sin operación (pago simulado directo) o con op APROBADA.
            if (sol.idOperacionPago() != null) {
                OperacionRow op = dao.operacionPorId(sol.idOperacionPago())
                        .orElseThrow(() -> new ErrorPlanes("PAGO_NO_CONFIRMADO",
                                "No es posible activar Premium porque la operación de prueba no está confirmada."));
                if (!"APROBADA".equals(op.estado())) {
                    throw new ErrorPlanes("PAGO_NO_CONFIRMADO",
                            "No es posible activar Premium porque la operación de prueba no está confirmada.");
                }
            }
            props.getSandbox().vigenciaOrThrow();
        } else if ("PRUEBA_GRATUITA".equals(sol.modalidad())) {
            // La solicitud actual ya consume la única prueba; no revalidar count>=1.
            validarPruebaGratuitaConfigurada();
        } else {
            throw new ErrorPlanes("DATOS_INVALIDOS", "Modalidad no soportada.");
        }

        Duration vigencia = "PRUEBA_GRATUITA".equals(sol.modalidad())
                ? props.getPruebaGratuita().vigenciaOrThrow()
                : props.getSandbox().vigenciaOrThrow();
        String origen = "PRUEBA_GRATUITA".equals(sol.modalidad())
                ? "SANDBOX_LOCAL"
                : (sol.proveedorOperacion() == null ? "SANDBOX_LOCAL" : sol.proveedorOperacion());
        if ("MERCADO_PAGO_TEST".equals(sol.proveedorOperacion())) {
            origen = "MERCADO_PAGO_TEST";
        }

        reconciliarVencimiento(sol.idUsuario());
        SuscripcionRow actual = dao.suscripcionActiva(sol.idUsuario()).orElse(null);
        if (actual != null && esPremiumActivo(actual)) {
            throw new ErrorPlanes("YA_PREMIUM", "El usuario ya tiene Premium activo.");
        }
        if (actual != null) {
            dao.marcarVencida(actual.idSuscripcion());
            dao.registrarEvento(sol.idUsuario(), actual.idSuscripcion(), sol.idOperacionPago(),
                    "SUSCRIPCION_VENCIDA_POR_UPGRADE", "prev=" + actual.planCodigo());
        }
        Instant fin = Instant.now().plus(vigencia);
        long idNueva = dao.insertarSuscripcion(
                sol.idUsuario(), sol.idPlan(), origen, sol.modalidad(), fin, sol.idOperacionPago());

        int decided = dao.decidirSolicitud(
                sol.idSolicitud(), sol.versionFila(), "PENDIENTE_APROBACION", "APROBADA",
                idAdmin, null, observacion, idNueva);
        if (decided != 1) {
            throw new ErrorPlanes("CONFLICTO",
                    "Otro administrador ya decidió esta solicitud.", false);
        }
        dao.registrarEvento(sol.idUsuario(), idNueva, sol.idOperacionPago(),
                "SOLICITUD_APROBADA", "admin=" + idAdmin + ";codigo=" + codigo);
        dao.registrarEvento(sol.idUsuario(), idNueva, sol.idOperacionPago(),
                "PREMIUM_ACTIVADO", "modalidad=" + sol.modalidad() + ";fin=" + fin);
        return new ContratoPlanes.DecisionResponse(
                codigo, "APROBADA", "Solicitud aprobada. Premium activado.",
                fin.toString(), sol.modalidad());
    }

    /**
     * Admin cancela Premium de prueba/sandbox activo → FREE + conciliación vehículos.
     * No falsifica pagos; solo revoca el derecho concedido.
     */
    @Transactional
    public ContratoPlanes.DecisionResponse cancelarPremiumActivo(long idAdmin, long idUsuario, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ErrorPlanes("DATOS_INVALIDOS", "Indica el motivo de la cancelación.");
        }
        SuscripcionRow s = dao.suscripcionActiva(idUsuario)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Sin suscripción activa."));
        if (!"PREMIUM".equals(s.planCodigo()) || !vigente(s)) {
            throw new ErrorPlanes("ESTADO_INVALIDO", "No hay Premium activo para cancelar.");
        }
        if (!"PRUEBA_GRATUITA".equals(s.modalidad()) && !"PAGO_SANDBOX".equals(s.modalidad())) {
            throw new ErrorPlanes("ESTADO_INVALIDO",
                    "Solo se puede cancelar Premium de prueba o sandbox desde administración.");
        }
        String modalidad = s.modalidad();
        Long idOp = s.idOperacionPago();
        // Vence TODAS las Premium activas (evita quedar con Premium residual).
        int vencidas = dao.marcarPremiumActivasVencidas(idUsuario);
        if (vencidas < 1) {
            dao.marcarVencida(s.idSuscripcion());
        }
        dao.registrarEvento(idUsuario, s.idSuscripcion(), idOp,
                "PREMIUM_CANCELADO_ADMIN",
                "admin=" + idAdmin + ";motivo=" + motivo.strip() + ";modalidad=" + modalidad);
        long idFree = asegurarFree(idUsuario);
        dao.registrarEvento(idUsuario, idFree, null, "FREE_REACTIVADA", "post_cancelacion_admin");
        // FREE: un solo vehículo activo (principal); el resto pasa a INACTIVO.
        exigirLimiteActivosPlanFree(idUsuario);
        return new ContratoPlanes.DecisionResponse(
                null, "CANCELADA",
                "Premium cancelado por administración. Usuario en FREE; solo queda 1 vehículo activo.",
                null, "FREE");
    }

    @Transactional
    public ContratoPlanes.DecisionResponse rechazarSolicitud(long idAdmin, String codigo, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ErrorPlanes("DATOS_INVALIDOS", "El motivo del rechazo es obligatorio.");
        }
        SolicitudRow sol = dao.solicitudPorCodigoForUpdate(codigo)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Solicitud no encontrada."));
        if (!"PENDIENTE_APROBACION".equals(sol.estado()) && !"PENDIENTE_PAGO".equals(sol.estado())) {
            throw new ErrorPlanes("ESTADO_INVALIDO",
                    "La solicitud no admite rechazo (estado=" + sol.estado() + ").");
        }
        int decided = dao.decidirSolicitud(
                sol.idSolicitud(), sol.versionFila(), sol.estado(), "RECHAZADA",
                idAdmin, motivo.strip(), null, null);
        if (decided != 1) {
            throw new ErrorPlanes("CONFLICTO",
                    "Otro administrador ya decidió esta solicitud.", false);
        }
        dao.registrarEvento(sol.idUsuario(), null, sol.idOperacionPago(),
                "SOLICITUD_RECHAZADA", "motivo=" + motivo.strip());
        // Si queda en Free (o nunca salió de Free), forzar 1 vehículo activo.
        exigirLimiteActivosPlanFree(sol.idUsuario());
        return new ContratoPlanes.DecisionResponse(
                codigo, "RECHAZADA", "Solicitud rechazada.", null, sol.modalidad());
    }

    public ContratoPlanes.AdminResumenResponse resumenAdmin() {
        return new ContratoPlanes.AdminResumenResponse(
                dao.countFreeActivas(),
                dao.countPremiumPruebaActivas(),
                dao.countPremiumSandboxActivas(),
                dao.countVencidas(),
                dao.countSolicitudesPorEstado("PENDIENTE_APROBACION"),
                dao.countOperacionesPorEstados(List.of("CREADA", "PENDIENTE")),
                dao.countOperacionesPorEstados(List.of("APROBADA", "RECHAZADA", "CANCELADA", "ERROR")),
                "Contadores sandbox; no representan ingresos reales."
        );
    }

    @Transactional
    public List<ContratoPlanes.AdminSuscripcionItem> listarAdmin(String filtro, String q) {
        Instant ahora = Instant.now();
        return dao.listarAdmin(filtro, q, 100).stream()
                .map(m -> {
                    Integer dias = null;
                    Object finRaw = m.get("finUtc");
                    if (finRaw != null) {
                        Instant fin = Instant.parse(finRaw.toString());
                        long d = ChronoUnit.DAYS.between(
                                ahora.truncatedTo(ChronoUnit.DAYS),
                                fin.truncatedTo(ChronoUnit.DAYS));
                        dias = (int) Math.max(0, d);
                    }
                    return new ContratoPlanes.AdminSuscripcionItem(
                            ((Number) m.get("idUsuario")).longValue(),
                            (String) m.get("correo"),
                            (String) m.get("nombre"),
                            (String) m.get("planCodigo"),
                            (String) m.get("modalidad"),
                            (String) m.get("estado"),
                            (String) m.get("origen"),
                            (String) m.get("inicioUtc"),
                            (String) m.get("finUtc"),
                            Boolean.TRUE.equals(m.get("etiquetaSandbox")),
                            dias
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ContratoPlanes.AdminSolicitudItem> listarSolicitudesAdmin(String filtro, String q) {
        return dao.listarSolicitudesAdmin(filtro, q, 100).stream()
                .map(m -> new ContratoPlanes.AdminSolicitudItem(
                        (String) m.get("codigo"),
                        ((Number) m.get("idUsuario")).longValue(),
                        (String) m.get("correo"),
                        (String) m.get("nombre"),
                        (String) m.get("modalidad"),
                        (String) m.get("estado"),
                        (String) m.get("creadoUtc"),
                        (String) m.get("referenciaOperacion"),
                        (String) m.get("proveedorOperacion"),
                        (String) m.get("estadoOperacion")
                ))
                .toList();
    }

    @Transactional
    public ContratoPlanes.AdminDetalleResponse detalleAdmin(long idUsuario) {
        Map<String, Object> u = dao.usuarioBasico(idUsuario)
                .orElseThrow(() -> new ErrorPlanes("USUARIO_NO_ENCONTRADO", "Usuario no encontrado."));
        reconciliarVencimiento(idUsuario);
        SuscripcionRow s = dao.suscripcionActiva(idUsuario).orElse(null);
        ContratoPlanes.MiSuscripcionResponse mi = s == null ? null : toMiSuscripcion(s);
        List<ContratoPlanes.EventoDto> eventos = dao.eventosUsuario(idUsuario, 40).stream()
                .map(this::toEvento).toList();
        ContratoPlanes.OperacionPagoResponse ultima = dao.ultimaOperacionUsuario(idUsuario)
                .map(op -> {
                    SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
                    return toOperacion(op, false, null, null,
                            sol == null ? null : sol.codigo(),
                            sol == null ? null : sol.estado(), null);
                })
                .orElse(null);
        Map<String, Object> veh = new LinkedHashMap<>();
        veh.put("registrados", dao.contarVehiculos(idUsuario));
        veh.put("activos", dao.contarVehiculosActivos(idUsuario));
        veh.put("inactivos", dao.contarVehiculosInactivos(idUsuario));
        veh.put("idPrincipal", dao.idVehiculoPrincipal(idUsuario).orElse(null));
        return new ContratoPlanes.AdminDetalleResponse(
                idUsuario,
                (String) u.get("correo"),
                (String) u.get("nombre"),
                (String) u.get("estadoCuenta"),
                mi, eventos, ultima, veh
        );
    }

    @Transactional
    public ContratoPlanes.AdminSolicitudDetalleDto detalleSolicitudAdmin(String codigo) {
        SolicitudRow sol = dao.solicitudPorCodigo(codigo)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Solicitud no encontrada."));
        Map<String, Object> u = dao.usuarioBasico(sol.idUsuario())
                .orElseThrow(() -> new ErrorPlanes("USUARIO_NO_ENCONTRADO", "Usuario no encontrado."));
        PlanRow plan = dao.porCodigo("PREMIUM").orElse(null);
        String vigenciaTxt;
        String motivoNo = null;
        boolean puede = "PENDIENTE_APROBACION".equals(sol.estado());
        try {
            Duration d = "PRUEBA_GRATUITA".equals(sol.modalidad())
                    ? props.getPruebaGratuita().vigenciaOrThrow()
                    : props.getSandbox().vigenciaOrThrow();
            vigenciaTxt = d.toString();
        } catch (ErrorPlanes e) {
            vigenciaTxt = null;
            puede = false;
            motivoNo = e.getMessage();
        }
        if (puede && "PAGO_SANDBOX".equals(sol.modalidad()) && sol.idOperacionPago() != null) {
            if (!"APROBADA".equals(sol.estadoOperacion())) {
                puede = false;
                motivoNo = "No es posible activar Premium porque la operación de prueba no está confirmada.";
            }
        }
        List<ContratoPlanes.AdminSolicitudItem> anteriores = dao
                .solicitudesHermanasUsuario(sol.idUsuario(), sol.codigo(), 30)
                .stream()
                .map(m -> new ContratoPlanes.AdminSolicitudItem(
                        (String) m.get("codigo"),
                        sol.idUsuario(),
                        (String) u.get("correo"),
                        (String) u.get("nombre"),
                        (String) m.get("modalidad"),
                        (String) m.get("estado"),
                        (String) m.get("creadoUtc"),
                        null, null, null
                ))
                .toList();
        return new ContratoPlanes.AdminSolicitudDetalleDto(
                toSolicitud(sol),
                sol.idUsuario(),
                (String) u.get("correo"),
                (String) u.get("nombre"),
                vigenciaTxt,
                plan == null ? null : plan.maxVehiculos(),
                plan == null ? null : plan.descripcionBeneficios(),
                puede,
                motivoNo,
                dao.eventosUsuario(sol.idUsuario(), 40).stream().map(this::toEvento).toList(),
                anteriores
        );
    }

    /** Job / local-test: vence Premium caducados y reconcilia vehículos. */
    @Transactional
    public int procesarVencimientosPendientes() {
        int n = 0;
        for (Long idUsuario : dao.usuariosPremiumVencidosPendientes()) {
            reconciliarVencimiento(idUsuario);
            n++;
        }
        return n;
    }

    /** Solo local-test: fuerza vencimiento Premium + conciliación de vehículos. */
    @Transactional
    public Map<String, Object> forzarVencimientoLocal(long idUsuario) {
        exigirSandbox();
        SuscripcionRow s = dao.suscripcionActiva(idUsuario)
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Sin suscripción activa."));
        if (!"PREMIUM".equals(s.planCodigo())) {
            throw new ErrorPlanes("ESTADO_INVALIDO", "Solo Premium puede forzarse a vencer.");
        }
        dao.marcarVencida(s.idSuscripcion());
        dao.registrarEvento(idUsuario, s.idSuscripcion(), s.idOperacionPago(),
                "SUSCRIPCION_VENCIDA", "forzar_vencimiento_local; modalidad=" + s.modalidad());
        long idFree = asegurarFree(idUsuario);
        dao.registrarEvento(idUsuario, idFree, null, "FREE_REACTIVADA", "forzar_vencimiento_local");
        dao.reconciliarActivosFree(idUsuario, 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("idUsuario", idUsuario);
        out.put("plan", "FREE");
        out.put("vehiculosActivos", dao.contarVehiculosActivos(idUsuario));
        out.put("vehiculosRegistrados", dao.contarVehiculos(idUsuario));
        out.put("idPrincipal", dao.idVehiculoPrincipal(idUsuario).orElse(null));
        return out;
    }

    private ContratoPlanes.OperacionPagoResponse verificarYAplicarMp(long idUsuario, OperacionRow op) {
        var ver = mp.verificarPorPreferenciaOPago(op.idExterno(), op.referencia());
        if ("PENDIENTE".equals(ver.estadoNormalizado())) {
            SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
            return toOperacion(op, false, null, null,
                    sol == null ? null : sol.codigo(),
                    sol == null ? null : sol.estado(),
                    "Esperando confirmación de pago de prueba.");
        }
        dao.forzarEstadoOperacion(op.id(), ver.estadoNormalizado(), ver.evidenciaSegura());
        if ("APROBADA".equals(ver.estadoNormalizado())) {
            promoverSolicitudTrasPago(op);
        } else {
            SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
            if (sol != null && "PENDIENTE_PAGO".equals(sol.estado())) {
                dao.decidirSolicitud(sol.idSolicitud(), sol.versionFila(), "PENDIENTE_PAGO",
                        "CANCELADA", null, "Pago MP " + ver.estadoNormalizado(), null, null);
            }
            dao.registrarEvento(idUsuario, null, op.id(), "OPERACION_" + ver.estadoNormalizado(),
                    ver.evidenciaSegura());
        }
        SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
        return toOperacion(dao.operacionPorReferencia(op.referencia()).orElse(op), false, null, null,
                sol == null ? null : sol.codigo(),
                sol == null ? null : sol.estado(),
                mensajePostPago(sol));
    }

    private void promoverSolicitudTrasPago(OperacionRow op) {
        SolicitudRow sol = dao.solicitudPorOperacion(op.id()).orElse(null);
        if (sol != null && "PENDIENTE_PAGO".equals(sol.estado())) {
            dao.actualizarEstadoSolicitud(sol.idSolicitud(), sol.versionFila(),
                    "PENDIENTE_PAGO", "PENDIENTE_APROBACION");
            dao.registrarEvento(op.idUsuario(), null, op.id(), "PAGO_PRUEBA_APROBADO",
                    "pendiente_aprobacion_admin");
        }
    }

    void reconciliarVencimiento(long idUsuario) {
        SuscripcionRow s = dao.suscripcionActiva(idUsuario).orElse(null);
        if (s == null) {
            asegurarFree(idUsuario);
            return;
        }
        if (s.finUtc() != null && !s.finUtc().isAfter(Instant.now())) {
            dao.marcarVencida(s.idSuscripcion());
            dao.registrarEvento(idUsuario, s.idSuscripcion(), s.idOperacionPago(),
                    "SUSCRIPCION_VENCIDA", "fin_utc alcanzado; modalidad=" + s.modalidad());
            long idFree = asegurarFree(idUsuario);
            dao.registrarEvento(idUsuario, idFree, null, "FREE_REACTIVADA", "post_vencimiento");
            dao.reconciliarActivosFree(idUsuario, 1);
        }
    }

    private long asegurarFree(long idUsuario) {
        SuscripcionRow activa = dao.suscripcionActiva(idUsuario).orElse(null);
        if (activa != null && "FREE".equals(activa.planCodigo()) && vigente(activa)) {
            exigirLimiteActivosPlanFree(idUsuario);
            return activa.idSuscripcion();
        }
        if (activa != null) {
            dao.marcarVencida(activa.idSuscripcion());
        }
        PlanRow free = dao.porCodigo("FREE")
                .orElseThrow(() -> new ErrorPlanes("CONFIGURACION_PENDIENTE", "Plan FREE no disponible."));
        long id = dao.insertarSuscripcion(idUsuario, free.idPlan(), "REGISTRO", "FREE", null, null);
        exigirLimiteActivosPlanFree(idUsuario);
        return id;
    }

    /** Free: como máximo 1 vehículo ACTIVO (principal). Los demás pasan a INACTIVO. */
    private void exigirLimiteActivosPlanFree(long idUsuario) {
        SuscripcionRow s = dao.suscripcionActiva(idUsuario).orElse(null);
        if (s == null || !"FREE".equals(s.planCodigo())) {
            return;
        }
        if (dao.contarVehiculosActivos(idUsuario) > 1) {
            dao.reconciliarActivosFree(idUsuario, 1);
            dao.registrarEvento(idUsuario, s.idSuscripcion(), null,
                    "VEHICULOS_RECONCILIADOS_FREE", "limite_activos=1");
        }
    }

    private void exigirSandbox() {
        // Operaciones local-test (escenarios / crear operación) siguen acotadas al sidecar local.
        if (!localTestEnabled || !props.getSandbox().isEnabled()) {
            throw new ErrorPlanes("SANDBOX_DESHABILITADO",
                    "Contratación sandbox no disponible en este entorno.");
        }
    }

    /** Catálogo / pago simulado académico: solo depende del flag de planes (no de local-test). */
    private boolean sandboxOperativo() {
        return props.getSandbox().isEnabled();
    }

    private boolean pruebaGratuitaOperativa() {
        if (!props.getPruebaGratuita().isEnabled()) return false;
        try {
            props.getPruebaGratuita().vigenciaOrThrow();
            props.getPruebaGratuita().maxPorUsuarioOrThrow();
            return true;
        } catch (ErrorPlanes e) {
            return false;
        }
    }

    /**
     * Pago simulado académico: crea solicitud PAGO_SANDBOX sin cobro real.
     * Si {@code mycar.planes.sandbox.activacion-inmediata=true}, activa Premium
     * con la misma vigencia/reglas que la aprobación admin (sin pasarela).
     * Idempotente ante doble toque: SOLICITUD_ABIERTA / YA_PREMIUM.
     */
    @Transactional
    public ContratoPlanes.SolicitudDto solicitarPagoSimulado(long idUsuario) {
        if (!dao.cuentaActiva(idUsuario)) {
            throw new ErrorPlanes("NO_AUTORIZADO", "La cuenta debe estar ACTIVA.");
        }
        if (!sandboxOperativo()) {
            throw new ErrorPlanes("SANDBOX_DESHABILITADO",
                    "Simulación de planes no disponible en este entorno.");
        }
        reconciliarVencimiento(idUsuario);
        SuscripcionRow actual = dao.suscripcionActiva(idUsuario).orElse(null);
        if (actual != null && esPremiumActivo(actual)) {
            throw new ErrorPlanes("YA_PREMIUM", "Ya tienes Premium activo.");
        }
        if (dao.solicitudAbiertaUsuario(idUsuario).isPresent()) {
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }
        PlanRow plan = dao.porCodigo("PREMIUM")
                .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Plan PREMIUM no disponible."));
        if (!plan.contratacionSandbox()) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE",
                    "Contratación simulada deshabilitada en el plan PREMIUM.");
        }
        Duration vigencia = props.getSandbox().vigenciaOrThrow();
        String codigo = nuevoCodigoSolicitud("PSS");
        try {
            dao.crearSolicitud(codigo, idUsuario, plan.idPlan(), "PAGO_SANDBOX",
                    "PENDIENTE_APROBACION", null);
        } catch (DataIntegrityViolationException e) {
            throw new ErrorPlanes("SOLICITUD_ABIERTA",
                    "Ya tienes una solicitud Premium abierta.");
        }
        dao.registrarEvento(idUsuario, actual == null ? null : actual.idSuscripcion(), null,
                "SOLICITUD_PAGO_SIMULADO_CREADA", "codigo=" + codigo + ";simulado=true");

        if (props.getSandbox().isActivacionInmediata()) {
            SolicitudRow sol = dao.solicitudPorCodigoForUpdate(codigo)
                    .orElseThrow(() -> new ErrorPlanes("NO_ENCONTRADO", "Solicitud no encontrada."));
            if (actual != null) {
                dao.marcarVencida(actual.idSuscripcion());
                dao.registrarEvento(idUsuario, actual.idSuscripcion(), null,
                        "SUSCRIPCION_VENCIDA_POR_UPGRADE", "prev=" + actual.planCodigo());
            }
            Instant fin = Instant.now().plus(vigencia);
            long idNueva = dao.insertarSuscripcion(
                    idUsuario, sol.idPlan(), "SANDBOX_LOCAL", "PAGO_SANDBOX", fin, null);
            int decided = dao.decidirSolicitud(
                    sol.idSolicitud(), sol.versionFila(), "PENDIENTE_APROBACION", "APROBADA",
                    null, null, "Activación automática — simulación académica (sin cobro real).", idNueva);
            if (decided != 1) {
                throw new ErrorPlanes("CONFLICTO",
                        "La solicitud ya fue decidida. Evita pulsaciones duplicadas.", false);
            }
            dao.registrarEvento(idUsuario, idNueva, null,
                    "SOLICITUD_APROBADA", "activacion_inmediata=true;codigo=" + codigo);
            dao.registrarEvento(idUsuario, idNueva, null,
                    "PREMIUM_ACTIVADO", "modalidad=PAGO_SANDBOX;fin=" + fin + ";simulado=true");
        }
        return toSolicitud(dao.solicitudPorCodigo(codigo).orElseThrow());
    }

    private void validarPruebaGratuitaConfigurada() {
        if (!props.getPruebaGratuita().isEnabled()) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE",
                    "mycar.planes.prueba-gratuita.enabled=false.");
        }
        props.getPruebaGratuita().vigenciaOrThrow();
        props.getPruebaGratuita().maxPorUsuarioOrThrow();
    }

    private void exigirElegibilidadPrueba(long idUsuario) {
        SuscripcionRow actual = dao.suscripcionActiva(idUsuario).orElse(null);
        if (actual != null && esPremiumActivo(actual)) {
            throw new ErrorPlanes("YA_PREMIUM", "Ya tienes Premium activo.");
        }
        // Regla permanente: una sola prueba gratuita por cuenta (cualquier estado histórico).
        long usadas = dao.countPruebasSolicitadasUsuario(idUsuario);
        if (usadas >= 1) {
            throw new ErrorPlanes("PRUEBA_NO_ELEGIBLE",
                    "Ya utilizaste tu prueba gratuita Premium.");
        }
    }

    private String resolverProveedor(String solicitado) {
        String p = solicitado == null ? "" : solicitado.strip().toUpperCase(Locale.ROOT);
        if (p.isBlank() || "AUTO".equals(p)) {
            return mp.disponible() ? "MERCADO_PAGO_TEST" : "SIMULADOR_LOCAL";
        }
        if ("MERCADO_PAGO_TEST".equals(p)) {
            if (!mp.disponible()) {
                throw new ErrorPlanes("PROVEEDOR_NO_CONFIGURADO",
                        "Mercado Pago TEST sin credenciales. Usa SIMULADOR_LOCAL.");
            }
            return p;
        }
        if ("SIMULADOR_LOCAL".equals(p)) {
            return p;
        }
        throw new ErrorPlanes("DATOS_INVALIDOS", "Proveedor no soportado: " + solicitado);
    }

    private void validarPlanListoParaContratar(PlanRow plan) {
        if (!plan.contratacionSandbox()) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE",
                    "contratacion_sandbox_habilitada=false en plan PREMIUM.");
        }
        if (plan.precioDemo() == null || plan.precioDemo().compareTo(BigDecimal.ZERO) < 0) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE", "Falta precio_demo en plan PREMIUM.");
        }
        if (plan.moneda() == null || plan.moneda().isBlank()) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE", "Falta moneda en plan PREMIUM.");
        }
        if (plan.periodicidad() == null || plan.periodicidad().isBlank()) {
            throw new ErrorPlanes("CONFIGURACION_PENDIENTE", "Falta periodicidad en plan PREMIUM.");
        }
        props.getSandbox().vigenciaOrThrow();
    }

    private ContratoPlanes.PlanDto toPlanDto(PlanRow p, boolean sandboxOk, boolean pruebaOk) {
        String motivoSandbox = null;
        boolean listoSandbox = false;
        String motivoPrueba = null;
        boolean listoPrueba = false;
        if ("PREMIUM".equals(p.codigo())) {
            if (!sandboxOk) {
                motivoSandbox = "Sandbox deshabilitado en este entorno.";
            } else if (!p.contratacionSandbox()) {
                motivoSandbox = "CONFIGURACION_PENDIENTE: contratacion_sandbox_habilitada";
            } else if (p.precioDemo() == null || p.moneda() == null || p.moneda().isBlank()
                    || p.periodicidad() == null || p.periodicidad().isBlank()) {
                motivoSandbox = "CONFIGURACION_PENDIENTE: precio_demo/moneda/periodicidad";
            } else if (props.getSandbox().getVigencia() == null
                    || props.getSandbox().getVigencia().isBlank()) {
                motivoSandbox = "CONFIGURACION_PENDIENTE: mycar.planes.sandbox.vigencia";
            } else {
                listoSandbox = true;
            }
            if (!pruebaOk) {
                motivoPrueba = "CONFIGURACION_PENDIENTE: prueba gratuita (enabled/vigencia/max-por-usuario)";
            } else {
                listoPrueba = true;
            }
        }
        return new ContratoPlanes.PlanDto(
                p.codigo(), p.nombre(), p.maxVehiculos(), p.precioDemo(), p.moneda(),
                p.periodicidad(), p.descripcionBeneficios(), p.contratacionSandbox(),
                listoSandbox, motivoSandbox, listoPrueba, motivoPrueba,
                blankToNull(props.getPruebaGratuita().getVigencia()),
                blankToNull(props.getSandbox().getVigencia())
        );
    }

    private ContratoPlanes.MiSuscripcionResponse toMiSuscripcion(SuscripcionRow s) {
        boolean sandbox = "PAGO_SANDBOX".equals(s.modalidad()) || "PRUEBA_GRATUITA".equals(s.modalidad());
        Integer dias = null;
        if (s.finUtc() != null) {
            long d = ChronoUnit.DAYS.between(Instant.now().truncatedTo(ChronoUnit.DAYS),
                    s.finUtc().truncatedTo(ChronoUnit.DAYS));
            dias = (int) Math.max(0, d);
        }
        var cat = catalogo();
        var prem = cat.planes().stream().filter(p -> "PREMIUM".equals(p.codigo())).findFirst();
        boolean puedePrueba = false;
        String motivoPrueba = null;
        boolean puedeSandbox = false;
        String motivoSandbox = null;
        if (esPremiumActivo(s)) {
            motivoPrueba = "Ya tienes Premium activo.";
            motivoSandbox = motivoPrueba;
        } else if (dao.solicitudAbiertaUsuario(s.idUsuario()).isPresent()) {
            motivoPrueba = "Ya tienes una solicitud abierta.";
            motivoSandbox = motivoPrueba;
        } else {
            if (prem.isEmpty()) {
                motivoPrueba = "Plan PREMIUM no disponible.";
                motivoSandbox = motivoPrueba;
            } else {
                if (prem.get().pruebaGratuitaDisponible()) {
                    try {
                        exigirElegibilidadPrueba(s.idUsuario());
                        puedePrueba = true;
                    } catch (ErrorPlanes e) {
                        motivoPrueba = e.getMessage();
                    }
                } else {
                    motivoPrueba = prem.get().motivoNoPruebaGratuita();
                }
                if (prem.get().listoParaContratar()) {
                    puedeSandbox = true;
                } else {
                    motivoSandbox = prem.get().motivoNoContratable();
                }
            }
        }
        String refUltima = dao.ultimaOperacionUsuario(s.idUsuario())
                .map(OperacionRow::referencia).orElse(null);
        String solAbierta = dao.solicitudAbiertaUsuario(s.idUsuario())
                .map(SolicitudRow::codigo).orElse(null);
        int maxVeh = "FREE".equals(s.planCodigo()) ? 1 : Math.max(1, s.maxVehiculos());
        String aviso = null;
        if ("FREE".equals(s.planCodigo())
                && dao.ultimoEventoTipoReciente(s.idUsuario(), "PREMIUM_CANCELADO_ADMIN").isPresent()
                && !dao.huboEventoTras(s.idUsuario(), "PREMIUM_ACTIVADO", "PREMIUM_CANCELADO_ADMIN")) {
            aviso = "Tu Premium fue cancelado por un administrador. Volviste al plan Free "
                    + "(1 vehículo activo; no puedes registrar más autos).";
        }
        return new ContratoPlanes.MiSuscripcionResponse(
                s.planCodigo(),
                s.planNombre(),
                s.modalidad() == null ? ("FREE".equals(s.planCodigo()) ? "FREE" : null) : s.modalidad(),
                estadoUi(s),
                s.estado(),
                s.origen(),
                s.inicioUtc() == null ? null : s.inicioUtc().toString(),
                s.finUtc() == null ? null : s.finUtc().toString(),
                dias,
                maxVeh,
                dao.contarVehiculos(s.idUsuario()),
                dao.contarVehiculosActivos(s.idUsuario()),
                sandbox,
                puedePrueba,
                motivoPrueba,
                puedeSandbox,
                motivoSandbox,
                refUltima,
                solAbierta,
                aviso
        );
    }

    private static String estadoUi(SuscripcionRow s) {
        if ("FREE".equals(s.planCodigo())) return "FREE";
        if ("VENCIDA".equals(s.estado()) || (s.finUtc() != null && !s.finUtc().isAfter(Instant.now()))) {
            return "PREMIUM_VENCIDA";
        }
        if ("PRUEBA_GRATUITA".equals(s.modalidad())) return "PREMIUM_PRUEBA_ACTIVA";
        if ("PAGO_SANDBOX".equals(s.modalidad())) return "PREMIUM_SANDBOX_ACTIVA";
        return "PREMIUM";
    }

    private ContratoPlanes.SolicitudDto toSolicitud(SolicitudRow s) {
        return new ContratoPlanes.SolicitudDto(
                s.codigo(),
                s.modalidad(),
                s.estado(),
                s.creadoUtc() == null ? null : s.creadoUtc().toString(),
                s.decididoUtc() == null ? null : s.decididoUtc().toString(),
                s.motivoRechazo(),
                s.observacionAdmin(),
                s.referenciaOperacion(),
                s.proveedorOperacion(),
                s.estadoOperacion(),
                s.importeOperacion(),
                s.monedaOperacion()
        );
    }

    private ContratoPlanes.EventoDto toEvento(EventoRow e) {
        return new ContratoPlanes.EventoDto(
                e.idEvento(), e.tipoEvento(), e.detalle(),
                e.creadoUtc() == null ? null : e.creadoUtc().toString(),
                e.referenciaOperacion()
        );
    }

    private ContratoPlanes.OperacionPagoResponse toOperacion(
            OperacionRow op, boolean suscripcionActivada, String initOverride, String sandboxOverride,
            String codigoSolicitud, String estadoSolicitud, String mensaje) {
        return new ContratoPlanes.OperacionPagoResponse(
                op.referencia(),
                op.proveedor(),
                op.estado(),
                op.moneda(),
                op.importe(),
                initOverride != null ? initOverride : op.initPoint(),
                sandboxOverride != null ? sandboxOverride : op.sandboxInitPoint(),
                op.creadoUtc() == null ? null : op.creadoUtc().toString(),
                op.verificadoUtc() == null ? null : op.verificadoUtc().toString(),
                op.evidencia(),
                suscripcionActivada,
                codigoSolicitud,
                estadoSolicitud,
                mensaje
        );
    }

    private static boolean vigente(SuscripcionRow s) {
        return s.finUtc() == null || s.finUtc().isAfter(Instant.now());
    }

    private static boolean esPremiumActivo(SuscripcionRow s) {
        return "PREMIUM".equals(s.planCodigo()) && vigente(s);
    }

    private static String mensajePostPago(SolicitudRow sol) {
        if (sol == null) return null;
        return switch (sol.estado()) {
            case "PENDIENTE_APROBACION" ->
                    "Pago de prueba completado. Pendiente de aprobación administrativa.";
            case "PENDIENTE_PAGO" -> "Esperando confirmación de pago de prueba.";
            case "APROBADA" -> "Solicitud aprobada.";
            case "RECHAZADA" -> "Solicitud rechazada.";
            case "CANCELADA" -> "Solicitud cancelada.";
            default -> null;
        };
    }

    private static String nuevoCodigoSolicitud(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String normalizarEscenario(String escenario) {
        if (escenario == null || escenario.isBlank()) {
            throw new ErrorPlanes("DATOS_INVALIDOS", "Escenario requerido.");
        }
        String e = escenario.strip().toUpperCase(Locale.ROOT);
        return switch (e) {
            case "APROBADA", "RECHAZADA", "PENDIENTE", "CANCELADA" -> e;
            default -> throw new ErrorPlanes("DATOS_INVALIDOS",
                    "Escenario inválido. Usa APROBADA|RECHAZADA|PENDIENTE|CANCELADA.");
        };
    }
}
