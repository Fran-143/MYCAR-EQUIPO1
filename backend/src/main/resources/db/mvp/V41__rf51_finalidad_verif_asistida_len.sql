-- V41: acortar finalidad de verificación asistida (VARCHAR(24)).
ALTER TABLE mycar.codigo_recuperacion DROP CONSTRAINT IF EXISTS codigo_recuperacion_finalidad_check;
ALTER TABLE mycar.codigo_recuperacion
    ADD CONSTRAINT codigo_recuperacion_finalidad_check
    CHECK (finalidad IN (
        'RECUPERAR_CONTRASENA',
        'ELIMINAR_CUENTA',
        'REACTIVAR_CUENTA',
        'VERIF_REACTIV_ADMIN'
    ));
