package backend.soat.service;

import backend.crypto.DatosSensiblesCrypto;
import backend.crypto.DatosSensiblesCryptoGate;
import backend.soat.dao.SoatJdbcDao;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SoatCifradoBackfill {
    private static final int LOTE = 200;

    private final SoatJdbcDao dao;
    private final DatosSensiblesCryptoGate cryptoGate;

    public SoatCifradoBackfill(SoatJdbcDao dao, DatosSensiblesCryptoGate cryptoGate) {
        this.dao = dao;
        this.cryptoGate = cryptoGate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrarFilasLegacy() {
        if (!cryptoGate.habilitado()) {
            return;
        }
        try {
            var crypto = cryptoGate.exigir();
            while (true) {
                var pendientes = dao.listarSinCifrado(LOTE);
                if (pendientes.isEmpty()) {
                    return;
                }
                for (var fila : pendientes) {
                    migrarUna(fila, crypto);
                }
                if (pendientes.size() < LOTE) {
                    return;
                }
            }
        } catch (RuntimeException ex) {
            // No tumbar arranque local/USB si el esquema aún no tiene columnas de cifrado.
            org.slf4j.LoggerFactory.getLogger(SoatCifradoBackfill.class)
                    .warn("Backfill SOAT cifrado omitido: {}", ex.getMessage());
        }
    }

    private void migrarUna(SoatJdbcDao.FilaSinCifrado fila, DatosSensiblesCrypto crypto) {
        String poliza = fila.numeroPoliza();
        if (poliza == null || poliza.isBlank()) {
            return;
        }
        String aadPoliza = "soat|poliza|" + fila.idSoat();
        String aadNombre = "soat|contratante|" + fila.idSoat();
        String polizaPlano = DatosSensiblesCrypto.pareceSellado(poliza)
                ? crypto.abrir(poliza, aadPoliza)
                : poliza.strip();
        String blind = crypto.indiceCiego(polizaPlano.replaceAll("[^0-9]", ""));
        String cifrada = DatosSensiblesCrypto.pareceSellado(poliza)
                ? poliza
                : crypto.sellar(polizaPlano, aadPoliza);
        String nombre = fila.nombreContratante();
        String nombreCifrado = null;
        if (nombre != null && !nombre.isBlank()) {
            nombreCifrado = DatosSensiblesCrypto.pareceSellado(nombre)
                    ? nombre
                    : crypto.sellar(nombre.strip(), aadNombre);
        }
        dao.actualizarCifrado(fila.idSoat(), cifrada, nombreCifrado, blind, crypto.keyId());
    }
}