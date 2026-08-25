-- Ejecutar como propietario de la base trailersys (normalmente postgres).
-- Los roles de grupo no inician sesion; se asignan a usuarios LOGIN reales.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_lectura') THEN CREATE ROLE trailersys_lectura NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_operacion') THEN CREATE ROLE trailersys_operacion NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_auditoria') THEN CREATE ROLE trailersys_auditoria NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_administracion') THEN CREATE ROLE trailersys_administracion NOLOGIN CREATEROLE; END IF;
END $$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE trailersys TO trailersys_lectura, trailersys_operacion, trailersys_auditoria, trailersys_administracion;
GRANT USAGE ON SCHEMA public TO trailersys_lectura, trailersys_operacion, trailersys_auditoria, trailersys_administracion;
GRANT SELECT ON vehiculos, conductores, clientes, cargas, viajes, seguimiento_eventos, mantenimientos TO trailersys_lectura;
GRANT SELECT, INSERT, UPDATE ON vehiculos, conductores, clientes, cargas, viajes, seguimiento_eventos, mantenimientos TO trailersys_operacion;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO trailersys_operacion;
GRANT SELECT ON auditoria TO trailersys_auditoria;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO trailersys_administracion;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO trailersys_administracion;

ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO trailersys_lectura;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO trailersys_operacion;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO trailersys_administracion;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO trailersys_operacion, trailersys_administracion;

-- Crear cada usuario LOGIN aparte, sin guardar su clave en Git, y asignarle un rol:
-- CREATE USER operador_trailersys WITH LOGIN PASSWORD 'clave-segura';
-- GRANT trailersys_operacion TO operador_trailersys;
