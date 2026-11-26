package backend.vehiculos.eliminacion;

import backend.documentos.AlmacenamientoDocumentos;
import backend.fotos.AlmacenamientoFotos;
import backend.vehiculos.dao.CondicionesRegistroVehiculoDao;
import backend.vehiculos.dao.OperacionVehiculoDao;
import backend.vehiculos.dao.VehiculoDao;
import backend.vehiculos.domain.ErrorRegistroVehiculo;
import backend.vehiculos.eliminacion.EliminacionVehiculoDao.CandidatoPrincipal;
import backend.vehiculos.eliminacion.EliminacionVehiculoDao.FilaVehiculo;
import backend.vehiculos.eliminacion.EliminacionVehiculoModelos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class EliminacionVehiculoService {
    private static final Logger log = LoggerFactory.getLogger(EliminacionVehiculoService.class);
    private static final String TIPO = "ELIMINAR_VEHICULO";

    private final CondicionesRegistroVehiculoDao condiciones;
    private final VehiculoDao vehiculos;
    private final EliminacionVehiculoDao dao;
    private final OperacionVehiculoDao operaciones;
    private final ObjectMapper json;
    private final AlmacenamientoDocumentos documentos;
    private final AlmacenamientoFotos fotos;

    public EliminacionVehiculoService(
            CondicionesRegistroVehiculoDao condiciones,
            VehiculoDao vehiculos,
            EliminacionVehiculoDao dao,
            OperacionVehiculoDao operaciones,
            ObjectMapper json,
            AlmacenamientoDocumentos documentos,
            AlmacenamientoFotos fotos
    ) {
        this.condiciones = condiciones;
        this.vehiculos = vehiculos;
        this.dao = dao;
        this.operaciones = operaciones;
        this.json = json;
        this.documentos = documentos;
        this.fotos = fotos;
    }

    @Transactional(readOnly = true)
    public PrepararEliminacionResponse preparar(long idUsuario, long idVehiculo) {
        condiciones.exigirCuentaActiva(idUsuario);
        FilaVehiculo v = dao.obtenerPropio(idUsuario, idVehiculo)
                .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
        if ("ELIMINADO".equalsIgnoreCase(v.estadoRegistro())) {
            throw new ErrorRegistroVehiculo("VEHICULO_YA_ELIMINADO");
        }
        if (!"ACTIVO".equals(v.estadoVehiculo()) && !"INACTIVO".equals(v.estadoVehiculo())) {
            throw new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO");
        }

        CandidatoPrincipal cand = dao.candidatoPrincipalTrasEliminar(idUsuario, idVehiculo).orElse(null);
        boolean quedaraSin = v.esPrincipal() && cand == null;
        String mensaje;
        if (!v.esPrincipal()) {
            mensaje = null;
        } else if (cand != null) {
            mensaje = "Este es tu vehículo principal. Al eliminarlo, otro vehículo activo podrá convertirse en el principal.";
        } else {
            mensaje = "Después de eliminarlo, tu cuenta quedará sin vehículo principal.";
        }

        return new PrepararEliminacionResponse(
                v.idVehiculo(), v.placa(), v.marca(), v.modelo(), v.estadoVehiculo(),
                v.esPrincipal(), v.versionRegistro(), quedaraSin,
                cand == null ? null : cand.idVehiculo(),
                cand == null ? null : cand.placa(),
                cand == null ? null : cand.marca(),
                cand == null ? null : cand.modelo(),
                dao.dependencias(idVehiculo),
                mensaje
        );
    }

    @Transactional
    public ConfirmarEliminacionResponse confirmar(
            long idUsuario, long idVehiculo, ConfirmarEliminacionRequest req
    ) {
        if (req == null || req.idOperacion() == null) {
            throw new ErrorRegistroVehiculo("DATOS_INVALIDOS");
        }
        UUID idOp = req.idOperacion();
        String hash = hashSolicitud(idVehiculo, req.versionRegistro());

        condiciones.exigirCuentaActiva(idUsuario);

        boolean reservada = operaciones.reservar(idOp, idUsuario, TIPO, hash);
        if (!reservada) {
            var existente = operaciones.buscarParaActualizar(idOp, idUsuario, TIPO)
                    .orElseThrow(() -> new ErrorRegistroVehiculo("OPERACION_NO_ENCONTRADA"));
            if (!hash.equals(existente.hashSolicitud())) {
                throw new ErrorRegistroVehiculo("CONFLICTO_IDEMPOTENCIA");
            }
            if ("EN_PROCESO".equals(existente.estado())) {
                throw new ErrorRegistroVehiculo("OPERACION_EN_PROCESO");
            }
            return leerRespuesta(existente.respuestaJson());
        }

        try {
            vehiculos.bloquearVehiculosDelUsuario(idUsuario);
            FilaVehiculo v = dao.obtenerPropio(idUsuario, idVehiculo)
                    .orElseThrow(() -> new ErrorRegistroVehiculo("VEHICULO_NO_ENCONTRADO"));
            if ("ELIMINADO".equalsIgnoreCase(v.estadoRegistro())) {
                throw new ErrorRegistroVehiculo("VEHICULO_YA_ELIMINADO");
            }
            if (v.versionRegistro() != req.versionRegistro()) {
                throw new ErrorRegistroVehiculo("VERSION_DESACTUALIZADA");
            }

            // 1) Purga operativa (misma TX) → 2) baja lógica + auditoría → 3) storage post-commit
            List<String> objectKeys = new ArrayList<>(dao.purgarDatosOperativos(idVehiculo));

            CandidatoPrincipal cand = dao.candidatoPrincipalTrasEliminar(idUsuario, idVehiculo).orElse(null);
            if (!dao.marcarEliminado(idUsuario, idVehiculo, req.versionRegistro())) {
                throw new ErrorRegistroVehiculo("VERSION_DESACTUALIZADA");
            }

            Long idPrincipal = null;
            if (v.esPrincipal()) {
                dao.limpiarPrincipal(idUsuario);
                if (cand != null) {
                    if (!dao.asignarPrincipal(idUsuario, cand.idVehiculo())) {
                        throw new ErrorRegistroVehiculo("PRINCIPAL_NO_ASIGNABLE");
                    }
                    idPrincipal = cand.idVehiculo();
                }
            }

            dao.registrarAuditoria(
                    idUsuario, idVehiculo, v.estadoVehiculo(), v.esPrincipal(), idOp, idPrincipal);

            programarBorradoAlmacenamiento(objectKeys);

            ConfirmarEliminacionResponse resp = new ConfirmarEliminacionResponse(
                    idVehiculo,
                    "ELIMINADO",
                    idPrincipal != null,
                    idPrincipal,
                    "El vehículo y su información asociada fueron eliminados de tu cuenta."
            );
            String respuestaJson = json.writeValueAsString(resp);
            if (!operaciones.confirmar(idOp, idUsuario, TIPO, hash, respuestaJson)) {
                throw new ErrorRegistroVehiculo("OPERACION_NO_CONFIRMADA");
            }
            return resp;
        } catch (ErrorRegistroVehiculo ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("RF62 eliminación fallida idVehiculo={}: {}", idVehiculo, ex.toString());
            throw new ErrorRegistroVehiculo("ELIMINACION_FALLIDA");
        }
    }

    private void programarBorradoAlmacenamiento(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        List<String> copia = List.copyOf(objectKeys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            borrarAlmacenamientoBestEffort(copia);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                borrarAlmacenamientoBestEffort(copia);
            }
        });
    }

    private void borrarAlmacenamientoBestEffort(List<String> objectKeys) {
        for (String key : objectKeys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                if (key.startsWith("vehiculos/")) {
                    fotos.borrarLogicoHuerfano(key);
                } else {
                    documentos.borrarLogicoHuerfano(key);
                }
            } catch (Exception ex) {
                log.warn("RF62: no se pudo borrar objeto privado '{}': {}", key, ex.toString());
            }
        }
    }

    private ConfirmarEliminacionResponse leerRespuesta(String respuestaJson) {
        try {
            return json.readValue(respuestaJson, ConfirmarEliminacionResponse.class);
        } catch (Exception ex) {
            throw new ErrorRegistroVehiculo("OPERACION_RESPUESTA_INVALIDA");
        }
    }

    static String hashSolicitud(long idVehiculo, int versionRegistro) {
        try {
            String raw = "ELIMINAR_VEHICULO|" + idVehiculo + "|" + versionRegistro;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
