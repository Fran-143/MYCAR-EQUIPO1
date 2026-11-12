package backend.talleres.dao;

import backend.talleres.model.DatosTaller.Taller;
import java.util.List;
import java.util.Optional;

public interface TallerDao {
    List<Taller> listarActivos(long idUsuario);

    Optional<Taller> propio(long idUsuario, long idTaller);

    Optional<Taller> propioActivo(long idUsuario, long idTaller);

    /** true si hay otro taller ACTIVO del usuario con ese nombre (excluye idExcluir si > 0). */
    boolean existeNombreActivo(long idUsuario, String nombre, long idExcluir);

    Taller insertar(long idUsuario, String nombre, String telefono, String correo,
                    String direccion, String notas);

    boolean actualizar(long idUsuario, long idTaller, int versionFila,
                       String nombre, String telefono, String correo,
                       String direccion, String notas);

    boolean retirar(long idUsuario, long idTaller, int versionFila);

    /** Desmarca cualquier favorito ACTIVO del usuario y marca el indicado. */
    boolean marcarFavoritoAtomico(long idUsuario, long idTaller);

    boolean desmarcarFavorito(long idUsuario, long idTaller);
}
