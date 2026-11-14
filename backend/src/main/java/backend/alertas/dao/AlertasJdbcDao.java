package backend.alertas.dao;

import backend.alertas.api.dto.ContratoRf22;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class AlertasJdbcDao {
    private final JdbcTemplate jdbc;

    public AlertasJdbcDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void verificarVehiculoPropioActivo(long usuario, long vehiculo) {
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public void exigirVehiculoPropioActivo(long usuario, long vehiculo) {
        exigirTx();
        Integer ok = jdbc.queryForObject("""
                SELECT 1 FROM mycar.vehiculo
                WHERE id_vehiculo = ? AND id_usuario = ? AND estado_vehiculo = 'ACTIVO'
                FOR UPDATE
                """, Integer.class, vehiculo, usuario);
        if (ok == null) throw new EmptyResultDataAccessException(1);
    }

    public LecturaActual ultimaLectura(long vehiculo) {
        return jdbc.query("""
                SELECT id_lectura_km, valor_km
                FROM mycar.lectura_kilometraje
                WHERE id_vehiculo = ?
                ORDER BY fecha_lectura_utc DESC, id_lectura_km DESC
                LIMIT 1
                """, rs -> rs.next()
                ? new LecturaActual(rs.getLong(1), rs.getInt(2))
                : null, vehiculo);
    }

    public LocalDate hoyLima() {
        return jdbc.queryForObject(
                "SELECT (clock_timestamp() AT TIME ZONE 'America/Lima')::date", LocalDate.class);
    }

    public LocalDate calcularFechaObjetivo(LocalDate base, String unidad, int cantidad) {
        if ("SEMANAS".equals(unidad)) {
            return jdbc.queryForObject("SELECT ?::date + ? * 7", LocalDate.class, base, cantidad);
        }
        return jdbc.queryForObject(
                "SELECT (?::date + make_interval(months => ?))::date", LocalDate.class, base, cantidad);
    }

    public String normalizarServicioOtro(String texto) {
        if (texto == null || texto.isBlank()) return "";
        return jdbc.queryForObject("SELECT mycar.normalizar_servicio_otro(?)", String.class, texto);
    }

    public long idTipoPorCodigo(String codigo) {
        return jdbc.queryForObject(
                "SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo = ? AND activo",
                Long.class, codigo);
    }

    public String calcularEstadoInicial(
            String criterio, LocalDate fechaObjetivo, LocalDate hoy,
            Integer kmObjetivo, Integer intervaloKm, int kmActual) {
        return jdbc.queryForObject("""
                SELECT mycar.calcular_estado_alerta(?, ?, ?, ?, ?, ?)
                """, String.class, criterio, fechaObjetivo, hoy, kmObjetivo, intervaloKm, kmActual);
    }

    public long insertarAlerta(DatosInsercion d) {
        return jdbc.queryForObject("""
                INSERT INTO mycar.alerta(
                    id_vehiculo, id_tipo_mantenimiento, criterio, origen_calculo, servicio_otro,
                    unidad_tiempo, cantidad_tiempo, fecha_base, fecha_objetivo,
                    km_base, intervalo_km, km_objetivo, estado, firma_equivalencia,
                    id_mantenimiento_referencia)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                RETURNING id_alerta
                """, Long.class,
                d.idVehiculo(), d.idTipo(), d.criterio(), d.origenCalculo(), d.servicioOtro(),
                d.unidadTiempo(), d.cantidadTiempo(), d.fechaBase(), d.fechaObjetivo(),
                d.kmBase(), d.intervaloKm(), d.kmObjetivo(), d.estado(), d.firma(),
                d.idReferencia());
    }

    public void historialInicial(long idAlerta, String estado) {
        jdbc.update("""
                INSERT INTO mycar.historial_alerta(id_alerta, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'INICIAL_RF22', 0)
                """, idAlerta, estado);
    }

    public int sincronizar(long vehiculo, long usuario) {
        Integer n = jdbc.queryForObject(
                "SELECT mycar.sincronizar_alertas_vehiculo(?, ?)", Integer.class, vehiculo, usuario);
        return n == null ? 0 : n;
    }

    /** Vehículos ACTIVO con al menos una alerta evaluable (para job diario RF23). */
    public List<VehiculoActivo> listarVehiculosActivosConAlertasEvaluables() {
        return jdbc.query("""
                SELECT DISTINCT v.id_vehiculo, v.id_usuario
                FROM mycar.vehiculo v
                JOIN mycar.usuario u ON u.id_usuario = v.id_usuario AND u.estado_cuenta = 'ACTIVA'
                JOIN mycar.alerta a ON a.id_vehiculo = v.id_vehiculo
                WHERE v.estado_vehiculo = 'ACTIVO'
                  AND a.estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                ORDER BY v.id_vehiculo
                """, (rs, n) -> new VehiculoActivo(rs.getLong(1), rs.getLong(2)));
    }

    public record VehiculoActivo(long idVehiculo, long idUsuario) { }

    public List<ContratoRf22.AlertaDto> listar(long vehiculo) {
        return jdbc.query("""
                SELECT a.id_alerta, tm.codigo, tm.nombre, a.criterio, a.origen_calculo, a.estado,
                       a.fecha_objetivo, a.km_objetivo, a.km_base, a.intervalo_km,
                       a.servicio_otro, a.version_fila
                FROM mycar.alerta a
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_vehiculo = ? AND a.estado <> 'ANULADA'
                ORDER BY a.creada_utc DESC, a.id_alerta DESC
                """, (rs, n) -> new ContratoRf22.AlertaDto(
                rs.getLong("id_alerta"),
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("criterio"),
                rs.getString("origen_calculo"),
                rs.getString("estado"),
                rs.getObject("fecha_objetivo", LocalDate.class),
                rs.getObject("km_objetivo", Integer.class),
                rs.getObject("km_base", Integer.class),
                rs.getObject("intervalo_km", Integer.class),
                rs.getString("servicio_otro"),
                rs.getInt("version_fila")), vehiculo);
    }

    /** RF24: detalle propio (categoria=MANTENIMIENTO) con ficha de vehículo. */
    public Optional<DetalleAlertaFila> obtenerDetallePropio(long usuario, long vehiculo, long idAlerta) {
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, a.id_tipo_mantenimiento, tm.codigo, tm.nombre,
                       a.criterio, a.origen_calculo, a.estado,
                       to_char(a.fecha_estado_utc AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS fecha_estado,
                       a.fecha_objetivo, a.km_objetivo, a.km_base, a.intervalo_km,
                       a.unidad_tiempo, a.cantidad_tiempo, a.fecha_base, a.servicio_otro,
                       a.id_mantenimiento_referencia, a.id_mantenimiento_atencion, a.version_fila,
                       a.id_taller_contacto,
                       v.placa, v.marca, v.modelo, v.estado_vehiculo
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_alerta = ? AND a.id_vehiculo = ? AND v.id_usuario = ?
                  AND a.categoria = 'MANTENIMIENTO' AND a.estado <> 'ANULADA'
                """, (rs, n) -> new DetalleAlertaFila(
                rs.getLong("id_alerta"), rs.getLong("id_vehiculo"), rs.getLong("id_tipo_mantenimiento"),
                rs.getString("codigo"), rs.getString("nombre"), rs.getString("criterio"),
                rs.getString("origen_calculo"), rs.getString("estado"), rs.getString("fecha_estado"),
                rs.getObject("fecha_objetivo", LocalDate.class), rs.getObject("km_objetivo", Integer.class),
                rs.getObject("km_base", Integer.class), rs.getObject("intervalo_km", Integer.class),
                rs.getString("unidad_tiempo"), rs.getObject("cantidad_tiempo", Integer.class),
                rs.getObject("fecha_base", LocalDate.class), rs.getString("servicio_otro"),
                rs.getObject("id_mantenimiento_referencia", Long.class),
                rs.getObject("id_mantenimiento_atencion", Long.class), rs.getInt("version_fila"),
                rs.getObject("id_taller_contacto", Long.class),
                rs.getString("placa"), rs.getString("marca"), rs.getString("modelo"),
                rs.getString("estado_vehiculo")), idAlerta, vehiculo, usuario).stream().findFirst();
    }

    public record DetalleAlertaFila(
            long idAlerta, long idVehiculo, long idTipoMantenimiento,
            String codigoTipo, String nombreTipo, String criterio, String origenCalculo, String estado,
            String fechaEstadoUtc, LocalDate fechaObjetivo, Integer kmObjetivo, Integer kmBase,
            Integer intervaloKm, String unidadTiempo, Integer cantidadTiempo, LocalDate fechaBase,
            String servicioOtro, Long idMantenimientoReferencia, Long idMantenimientoAtencion,
            int versionFila, Long idTallerContacto,
            String placa, String marca, String modelo, String estadoVehiculo
    ) { }

    public record TallerContactoFila(
            long idTaller, String nombre, String telefono, boolean tieneWhatsapp,
            boolean contactoAutorizado, String origen
    ) { }

    public Optional<TallerContactoFila> tallerActivoPropio(long usuario, long idTaller) {
        return jdbc.query("""
                SELECT id_taller, nombre, telefono, tiene_whatsapp,
                       COALESCE(contacto_autorizado, false), origen
                FROM mycar.taller
                WHERE id_taller = ? AND id_usuario = ? AND estado = 'ACTIVO'
                """, (rs, n) -> new TallerContactoFila(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getString(6)
        ), idTaller, usuario).stream().findFirst();
    }

    public Optional<TallerContactoFila> tallerDeMantenimientoReferencia(long usuario, Long idMantenimiento) {
        if (idMantenimiento == null) return Optional.empty();
        return jdbc.query("""
                SELECT t.id_taller, t.nombre, t.telefono, t.tiene_whatsapp,
                       COALESCE(t.contacto_autorizado, false), t.origen
                FROM mycar.mantenimiento m
                JOIN mycar.taller t ON t.id_taller = m.id_taller
                JOIN mycar.vehiculo v ON v.id_vehiculo = m.id_vehiculo
                WHERE m.id_mantenimiento = ? AND v.id_usuario = ?
                  AND t.id_usuario = ? AND t.estado = 'ACTIVO'
                """, (rs, n) -> new TallerContactoFila(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getString(6)
        ), idMantenimiento, usuario, usuario).stream().findFirst();
    }

    public Optional<TallerContactoFila> tallerFavoritoActivo(long usuario) {
        return jdbc.query("""
                SELECT id_taller, nombre, telefono, tiene_whatsapp,
                       COALESCE(contacto_autorizado, false), origen
                FROM mycar.taller
                WHERE id_usuario = ? AND estado = 'ACTIVO' AND favorito = true
                ORDER BY id_taller LIMIT 1
                """, (rs, n) -> new TallerContactoFila(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4),
                rs.getBoolean(5), rs.getString(6)
        ), usuario).stream().findFirst();
    }

    public boolean asignarTallerContacto(long usuario, long vehiculo, long idAlerta, Long idTaller) {
        return jdbc.update("""
                UPDATE mycar.alerta a
                SET id_taller_contacto = ?
                FROM mycar.vehiculo v
                WHERE a.id_alerta = ? AND a.id_vehiculo = ? AND v.id_vehiculo = a.id_vehiculo
                  AND v.id_usuario = ? AND a.categoria = 'MANTENIMIENTO' AND a.estado <> 'ANULADA'
                """, idTaller, idAlerta, vehiculo, usuario) == 1;
    }

    public List<ContratoRf22.TipoDto> tiposActivos() {
        return jdbc.query("""
                SELECT id_tipo_mantenimiento, codigo, nombre
                FROM mycar.tipo_mantenimiento WHERE activo ORDER BY nombre
                """, (rs, n) -> new ContratoRf22.TipoDto(rs.getLong(1), rs.getString(2), rs.getString(3)));
    }

    /**
     * RF22 DESDE_MANTENIMIENTO: hasta 3 REALIZADO del vehículo con el mismo tipo
     * (y servicio OTRO normalizado cuando aplica). Sin tipo → lista vacía.
     */
    public List<ContratoRf22.ReferenciaDto> referenciasRealizadas(
            long vehiculo, String codigoTipo, String servicioOtro) {
        if (codigoTipo == null || codigoTipo.isBlank()) {
            return List.of();
        }
        if ("OTRO".equals(codigoTipo)) {
            if (servicioOtro == null || servicioOtro.isBlank()) {
                return List.of();
            }
            return jdbc.query("""
                    SELECT m.id_mantenimiento, tm.codigo, m.fecha_servicio, m.km_servicio, m.servicio_otro
                    FROM mycar.mantenimiento m
                    JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                    WHERE m.id_vehiculo = ? AND m.estado = 'REALIZADO' AND tm.codigo = 'OTRO'
                      AND mycar.normalizar_servicio_otro(COALESCE(m.servicio_otro, ''))
                        = mycar.normalizar_servicio_otro(?)
                    ORDER BY m.fecha_servicio DESC, m.id_mantenimiento DESC
                    LIMIT 3
                    """, (rs, n) -> new ContratoRf22.ReferenciaDto(
                    rs.getLong(1), rs.getString(2),
                    rs.getObject(3, LocalDate.class),
                    rs.getObject(4, Integer.class),
                    rs.getString(5)), vehiculo, servicioOtro);
        }
        return jdbc.query("""
                SELECT m.id_mantenimiento, tm.codigo, m.fecha_servicio, m.km_servicio, m.servicio_otro
                FROM mycar.mantenimiento m
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                WHERE m.id_vehiculo = ? AND m.estado = 'REALIZADO' AND tm.codigo = ?
                ORDER BY m.fecha_servicio DESC, m.id_mantenimiento DESC
                LIMIT 3
                """, (rs, n) -> new ContratoRf22.ReferenciaDto(
                rs.getLong(1), rs.getString(2),
                rs.getObject(3, LocalDate.class),
                rs.getObject(4, Integer.class),
                rs.getString(5)), vehiculo, codigoTipo);
    }

    public Optional<MantenimientoBase> mantenimientoRealizado(long vehiculo, long idMantenimiento) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT m.id_mantenimiento, m.id_tipo_mantenimiento, tm.codigo, m.fecha_servicio,
                           m.km_servicio, m.servicio_otro, m.estado
                    FROM mycar.mantenimiento m
                    JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = m.id_tipo_mantenimiento
                    WHERE m.id_vehiculo = ? AND m.id_mantenimiento = ?
                    """, (rs, n) -> new MantenimientoBase(
                    rs.getLong(1), rs.getLong(2), rs.getString(3),
                    rs.getObject(4, LocalDate.class), rs.getObject(5, Integer.class),
                    rs.getString(6), rs.getString(7)), vehiculo, idMantenimiento));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Long> vehiculoDeAlerta(long idAlerta) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id_vehiculo FROM mycar.alerta WHERE id_alerta = ?", Long.class, idAlerta));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<AlertaOperable> bloquearAlertaPropia(long usuario, long vehiculo, long idAlerta) {
        exigirTx();
        return jdbc.query("""
                SELECT a.id_alerta, a.id_vehiculo, a.estado, a.version_fila, tm.codigo, a.servicio_otro,
                       a.fecha_vencida_verificable, a.id_lectura_vencida_evidencia, a.id_mantenimiento_atencion
                FROM mycar.alerta a
                JOIN mycar.vehiculo v ON v.id_vehiculo = a.id_vehiculo
                JOIN mycar.tipo_mantenimiento tm ON tm.id_tipo_mantenimiento = a.id_tipo_mantenimiento
                WHERE a.id_alerta = ? AND a.id_vehiculo = ? AND v.id_usuario = ?
                  AND v.estado_vehiculo = 'ACTIVO'
                FOR UPDATE OF a
                """, (rs, n) -> new AlertaOperable(
                rs.getLong("id_alerta"), rs.getLong("id_vehiculo"), rs.getString("estado"),
                rs.getInt("version_fila"), rs.getString("codigo"), rs.getString("servicio_otro"),
                rs.getObject("fecha_vencida_verificable", LocalDate.class),
                rs.getObject("id_lectura_vencida_evidencia", Long.class),
                rs.getObject("id_mantenimiento_atencion", Long.class)), idAlerta, vehiculo, usuario)
                .stream().findFirst();
    }

    public void marcarRealizada(long idAlerta, String estadoAnterior, int versionEsperada,
                                long idMantenimientoAtencion, String motivo) {
        exigirTx();
        int filas = jdbc.update("""
                UPDATE mycar.alerta
                SET estado = 'REALIZADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp(),
                    id_mantenimiento_atencion = ?
                WHERE id_alerta = ? AND version_fila = ?
                  AND estado IN ('PROXIMA','PENDIENTE','VENCIDA')
                """, idMantenimientoAtencion, idAlerta, versionEsperada);
        if (filas != 1) throw new IllegalStateException("No se pudo marcar la alerta como REALIZADA");
        jdbc.update("""
                INSERT INTO mycar.historial_alerta
                  (id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'REALIZADA', ?, ?)
                """, idAlerta, estadoAnterior, motivo, versionEsperada + 1);
    }

    public void marcarAnuladaManual(long idAlerta, String estadoAnterior, int versionEsperada) {
        marcarAnuladaManual(idAlerta, estadoAnterior, versionEsperada, "ANULACION_MANUAL_RF24");
    }

    public void marcarAnuladaManual(long idAlerta, String estadoAnterior, int versionEsperada, String motivo) {
        exigirTx();
        String motivoHist = (motivo == null || motivo.isBlank()) ? "ANULACION_MANUAL_RF24" : motivo.trim();
        if (motivoHist.length() > 48) motivoHist = motivoHist.substring(0, 48);
        int filas = jdbc.update("""
                UPDATE mycar.alerta
                SET estado = 'ANULADA', version_fila = version_fila + 1,
                    fecha_estado_utc = clock_timestamp()
                WHERE id_alerta = ? AND version_fila = ?
                  AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
                """, idAlerta, versionEsperada);
        if (filas != 1) throw new IllegalStateException("No se pudo anular la alerta");
        jdbc.update("""
                INSERT INTO mycar.historial_alerta
                  (id_alerta, estado_anterior, estado_nuevo, motivo, version_destino)
                VALUES (?, ?, 'ANULADA', ?, ?)
                """, idAlerta, estadoAnterior, motivoHist, versionEsperada + 1);
    }

    private void exigirTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Alertas requiere transacción activa");
        }
    }

    public record AlertaOperable(
            long idAlerta, long idVehiculo, String estado, int versionFila,
            String codigoTipo, String servicioOtro,
            LocalDate fechaVencidaVerificable, Long idLecturaVencidaEvidencia,
            Long idMantenimientoAtencion
    ) { }

    public record LecturaActual(long idLecturaKm, int valorKm) { }

    public record MantenimientoBase(
            long idMantenimiento, long idTipo, String codigoTipo, LocalDate fechaServicio,
            Integer kmServicio, String servicioOtro, String estado
    ) { }

    public record DatosInsercion(
            long idVehiculo, long idTipo, String criterio, String origenCalculo, String servicioOtro,
            String unidadTiempo, Integer cantidadTiempo, LocalDate fechaBase, LocalDate fechaObjetivo,
            Integer kmBase, Integer intervaloKm, Integer kmObjetivo, String estado, String firma,
            Long idReferencia
    ) { }
}
