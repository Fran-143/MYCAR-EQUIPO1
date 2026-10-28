package backend.alertas.programacion.model;

import backend.alertas.programacion.model.ProgramacionAlerta.Revision;

public class ErrorProgramacion extends RuntimeException {
    private final String codigo;
    private final Revision revision;
    public ErrorProgramacion(String codigo) { this(codigo,null); }
    public ErrorProgramacion(String codigo, Revision revision) {
        super(codigo); this.codigo=codigo; this.revision=revision;
    }
    public String codigo() { return codigo; }
    public Revision revision() { return revision; }
}
