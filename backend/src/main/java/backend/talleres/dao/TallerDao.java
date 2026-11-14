package backend.talleres.dao;

import backend.talleres.model.DatosTaller.Taller;
import backend.talleres.model.DatosTaller.TallerGlobal;
import java.util.List;
import java.util.Optional;

public interface TallerDao {
    List<Taller> listarActivos(long idUsuario, String filtroOrigen);

    Optional<Taller> propio(long idUsuario, long idTaller);

    Optional<Taller> propioActivo(long idUsuario, long idTaller);

    /** true si hay otro taller ACTIVO del usuario con ese nombre (excluye idExcluir si > 0). */
    boolean existeNombreActivo(long idUsuario, String nombre, long idExcluir);

    boolean existeTelefonoManualActivo(long idUsuario, String telefono, long idExcluir);

    Taller insertarManual(long idUsuario, String nombre, String telefono, String correo,
                          String direccion, String notas, boolean tieneWhatsapp);

    Optional<TallerGlobal> globalActivo(long idTallerGlobal);

    /** Catálogo global; filas es_demo solo visibles para {@code idUsuarioPilotoDemo}. */
    List<TallerGlobal> listarGlobalesActivos(long idUsuario, String busqueda, long idUsuarioPilotoDemo);

    boolean existeVinculoAsociadoActivo(long idUsuario, long idTallerGlobal);

    Taller insertarAsociado(long idUsuario, long idTallerGlobal, String nombre, String telefono,
                            String correo, String direccion, boolean tieneWhatsapp);

    boolean actualizarManual(long idUsuario, long idTaller, int versionFila,
                             String nombre, String telefono, String correo,
                             String direccion, String notas, boolean tieneWhatsapp);

    boolean retirar(long idUsuario, long idTaller, int versionFila);

    boolean marcarFavoritoAtomico(long idUsuario, long idTaller);

    boolean desmarcarFavorito(long idUsuario, long idTaller);

    Optional<Taller> favoritoActivo(long idUsuario);
}
