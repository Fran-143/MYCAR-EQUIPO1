-- Seed local V39 cuentas RF (idempotente). NO ejecutar en Neon.
-- Password compartida de pruebas: PruebaCuentas35! (ver %LOCALAPPDATA%\MyCar\pruebas-cuentas-rf-v35.json)

-- rf.bloq.recup: bloqueo de RECUPERACIÓN vigente (cuenta ACTIVA; login con contraseña permitido).
-- Motivo histórico: si bloqueado_hasta_utc está NULL o vencido, emitir código es correcto.
INSERT INTO mycar.restriccion_recuperacion(id_usuario, finalidad, solicitudes_ciclo, nivel_bloqueo, bloqueado_hasta_utc, actualizado_utc)
SELECT id_usuario, 'RECUPERAR_CONTRASENA', 0, 1, clock_timestamp() + interval '55 minutes', clock_timestamp()
FROM mycar.usuario WHERE lower(correo)='rf.bloq.recup@mycar.test'
ON CONFLICT (id_usuario) DO UPDATE
 SET solicitudes_ciclo=0, nivel_bloqueo=GREATEST(mycar.restriccion_recuperacion.nivel_bloqueo, 1),
     bloqueado_hasta_utc=EXCLUDED.bloqueado_hasta_utc,
     actualizado_utc=EXCLUDED.actualizado_utc;

UPDATE mycar.usuario
   SET login_bloqueo_hasta_utc = clock_timestamp() + interval '10 minutes', estado_cuenta='ACTIVA'
 WHERE lower(correo)='rf.bloq.login@mycar.test';

UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA', motivo_estado='SEED'
 WHERE lower(correo)='rf.suspendida@mycar.test';

UPDATE mycar.usuario SET estado_cuenta='BLOQUEADA', tipo_bloqueo='ADMINISTRATIVA',
  bloqueo_admin_hasta_utc=clock_timestamp()+interval '2 hours', motivo_estado='SEED'
 WHERE lower(correo)='rf.bloq.admin@mycar.test';

UPDATE mycar.usuario SET estado_cuenta='PENDIENTE_ELIMINACION',
  eliminacion_programada_utc=clock_timestamp()+interval '30 days'
 WHERE lower(correo)='rf.pendiente@mycar.test';

INSERT INTO mycar.solicitud_eliminacion(id_usuario,estado,solicitada_utc,vence_utc,origen)
SELECT id_usuario,'VIGENTE',clock_timestamp(),clock_timestamp()+interval '30 days','TITULAR'
FROM mycar.usuario WHERE lower(correo)='rf.pendiente@mycar.test'
  AND NOT EXISTS (
    SELECT 1 FROM mycar.solicitud_eliminacion s
     WHERE s.id_usuario=mycar.usuario.id_usuario AND s.estado='VIGENTE'
  );
