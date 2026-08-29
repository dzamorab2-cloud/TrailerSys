-- Actualiza una base TrailerSys existente para admitir el autoservicio CLIENTE.
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS usuarios_rol_check;
ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol_check
CHECK (rol IN ('ADMINISTRADOR','COORDINADOR','MANTENIMIENTO','CONDUCTOR','SUPERVISOR','CLIENTE'));
