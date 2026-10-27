package backend.vehiculos.domain;

import java.util.Set;

/** Error funcional interno; el adaptador HTTP acordará su representación pública. */
public class ErrorRegistroVehiculo extends RuntimeException {
    private final String codigo;
    private final Set<String> campos;

    public ErrorRegistroVehiculo(String codigo) {
        this(codigo, Set.of());
    }

    public ErrorRegistroVehiculo(String codigo, Set<String> campos) {
        super(codigo);
        this.codigo = codigo;
        this.campos = Set.copyOf(campos);
    }

    public String codigo() { return codigo; }
    public Set<String> campos() { return campos; }
}
