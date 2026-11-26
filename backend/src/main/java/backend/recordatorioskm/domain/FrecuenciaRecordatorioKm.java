package backend.recordatorioskm.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Frecuencias RF43 estables (API/BD). No usar textos de UI. */
public enum FrecuenciaRecordatorioKm {
    DIARIO(1),
    INTERDIARIO(2),
    SEMANAL(7),
    QUINCENAL(15),
    MENSUAL(-1);

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Set<String> CODIGOS = Set.of(
            "DIARIO", "INTERDIARIO", "SEMANAL", "QUINCENAL", "MENSUAL");

    private final int dias;

    FrecuenciaRecordatorioKm(int dias) {
        this.dias = dias;
    }

    public static Optional<FrecuenciaRecordatorioKm> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String c = raw.strip().toUpperCase(Locale.ROOT);
        if (!CODIGOS.contains(c)) {
            return Optional.empty();
        }
        return Optional.of(FrecuenciaRecordatorioKm.valueOf(c));
    }

    public String codigo() {
        return name();
    }

    /** Próxima fecha de recordatorio desde la fecha efectiva de la última lectura válida (calendario Lima). */
    public LocalDate calcularProximo(LocalDate fechaBaseLima) {
        if (fechaBaseLima == null) {
            throw new IllegalArgumentException("fechaBaseLima");
        }
        if (this == MENSUAL) {
            return fechaBaseLima.plusMonths(1);
        }
        return fechaBaseLima.plusDays(dias);
    }
}
