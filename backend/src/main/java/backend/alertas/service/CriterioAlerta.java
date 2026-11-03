package backend.alertas.service;

/** Criterios RF22: POR_TIEMPO, POR_KILOMETRAJE, AMBOS. No usar String.contains("TIEMPO"). */
final class CriterioAlerta {
    private CriterioAlerta() { }

    static boolean usaTiempo(String criterio) {
        return "POR_TIEMPO".equals(criterio) || "AMBOS".equals(criterio);
    }

    static boolean usaKm(String criterio) {
        return "POR_KILOMETRAJE".equals(criterio) || "AMBOS".equals(criterio);
    }
}
