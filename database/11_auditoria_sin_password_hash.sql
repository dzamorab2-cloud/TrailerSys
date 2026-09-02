-- Actualiza una base TrailerSys existente para que la auditoria deje de
-- guardar el password_hash de usuarios: el trigger volcaba la fila entera
-- con to_jsonb(OLD)/to_jsonb(NEW) sin distinguir columnas sensibles, asi
-- que cada INSERT/UPDATE/DELETE sobre "usuarios" quedaba con el bcrypt
-- completo dentro de datos_anteriores/datos_nuevos - visible para
-- cualquier Administrador via GET /api/auditoria (o "Ver cambios" en
-- Configuracion). No es la contraseña en claro, pero sigue siendo un
-- secreto que no deberia duplicarse en una bitacora aparte.
--
-- 1) Reemplaza la funcion del trigger (ver 02_auditoria_indices.sql, ya
--    actualizado) para que excluya password_hash de aqui en adelante.
CREATE OR REPLACE FUNCTION trailersys_registrar_auditoria() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
DECLARE anterior JSONB; nuevo JSONB;
BEGIN
    anterior := CASE WHEN TG_OP IN ('UPDATE', 'DELETE') THEN to_jsonb(OLD) END;
    nuevo := CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN to_jsonb(NEW) END;
    IF TG_TABLE_NAME = 'usuarios' THEN
        anterior := anterior - 'password_hash';
        nuevo := nuevo - 'password_hash';
    END IF;
    INSERT INTO public.auditoria (usuario_bd, usuario_app, operacion, esquema, tabla,
        registro_id, datos_anteriores, datos_nuevos)
    VALUES (session_user, NULLIF(current_setting('trailersys.usuario', true), ''),
        TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME,
        COALESCE(nuevo ->> 'id', anterior ->> 'id'), anterior, nuevo);
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

-- 2) Limpia lo ya guardado con la version vieja del trigger.
UPDATE auditoria SET datos_anteriores = datos_anteriores - 'password_hash'
WHERE tabla = 'usuarios' AND datos_anteriores ? 'password_hash';
UPDATE auditoria SET datos_nuevos = datos_nuevos - 'password_hash'
WHERE tabla = 'usuarios' AND datos_nuevos ? 'password_hash';
