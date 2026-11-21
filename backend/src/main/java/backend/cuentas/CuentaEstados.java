package backend.cuentas;

public final class CuentaEstados {
    public static final String ACTIVA = "ACTIVA";
    public static final String SUSPENDIDA = "SUSPENDIDA";
    public static final String BLOQUEADA = "BLOQUEADA";
    public static final String PENDIENTE_ELIMINACION = "PENDIENTE_ELIMINACION";
    public static final String ELIMINADA = "ELIMINADA";

    public static final String BLOQUEO_ACCESO = "ACCESO";
    public static final String BLOQUEO_ADMIN = "ADMINISTRATIVA";
    public static final String BLOQUEO_RECUPERACION = "RECUPERACION";

    public static final String FINALIDAD_RECUPERAR = "RECUPERAR_CONTRASENA";
    public static final String FINALIDAD_ELIMINAR = "ELIMINAR_CUENTA";
    public static final String FINALIDAD_REACTIVAR = "REACTIVAR_CUENTA";
    public static final String FINALIDAD_VERIFICAR_REACTIVACION_ADMIN = "VERIF_REACTIV_ADMIN";

    private CuentaEstados() { }
}
