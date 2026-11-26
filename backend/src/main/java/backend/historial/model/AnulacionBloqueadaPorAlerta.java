package backend.historial.model;

/** RF21-EA03/EA04: existe alerta activa con este mantenimiento como referencia. */
public class AnulacionBloqueadaPorAlerta extends RuntimeException {
    private final long idAlerta;

    public AnulacionBloqueadaPorAlerta(long idAlerta) {
        super("Hay una alerta activa que usa este mantenimiento como referencia");
        this.idAlerta = idAlerta;
    }

    public long idAlerta() { return idAlerta; }
}
