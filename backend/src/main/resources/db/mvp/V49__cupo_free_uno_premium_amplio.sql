-- Cupo real: FREE = 1 vehículo registrado; PREMIUM conserva cupo amplio.
UPDATE mycar.plan SET max_vehiculos_registrados = 1 WHERE codigo = 'FREE';
UPDATE mycar.plan SET max_vehiculos_registrados = 20 WHERE codigo = 'PREMIUM';
