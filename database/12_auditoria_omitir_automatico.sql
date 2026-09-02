-- Actualiza una base TrailerSys existente para que el trigger de auditoria
-- respete la bandera de sesion trailersys.omitir_auditoria: sin esto, cada
-- tick del scheduler de ViajeSimulacionService (cada 60s, mientras haya
-- viajes "En Curso") quedaba auditado como si fuera una accion de un
-- usuario real - en la practica eso termino siendo la gran mayoria del
-- volumen de la tabla auditoria (926.402 de las filas de "viajes" eran
-- UPDATE con usuario_app en NULL, es decir, sin ninguna persona detras).
--
-- Ver ViajeService.iniciarViajesProgramadosVencidos() y
-- ViajeSimulacionService.ejecutarSimulacion(), que ahora hacen
-- SET LOCAL trailersys.omitir_auditoria='true' al entrar - solo dentro de
-- esa transaccion puntual, nunca afecta a la escritura manual de un
-- usuario real (ej. el conductor confirmando la llegada a mano) aunque
-- comparta el mismo metodo de servicio (registrarLlegada).
--
-- No borra las filas ya acumuladas por el comportamiento viejo: si se
-- quiere liberar ese espacio, es un DELETE aparte, deliberado.
CREATE OR REPLACE FUNCTION trailersys_registrar_auditoria() RETURNS TRIGGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, public AS $$
DECLARE anterior JSONB; nuevo JSONB;
BEGIN
    IF current_setting('trailersys.omitir_auditoria', true) = 'true' THEN
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;

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
