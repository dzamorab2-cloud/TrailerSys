-- Base de datos TrailerSys - creacion del esquema (PostgreSQL 18.4)
-- Ejecutar con: psql -U postgres -f 00_esquema_trailersys.sql
-- La carga de datos va aparte en 04_carga_millon.sql

CREATE DATABASE trailersys;
\connect trailersys

-- Roles de grupo (no inician sesion). Las cuentas reales se crean
-- aparte y heredan uno de estos roles:
--   lectura        -> solo consultar
--   operacion      -> consultar, insertar y actualizar (no borra)
--   auditoria      -> solo leer la tabla auditoria
--   administracion -> control total y puede crear roles
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_lectura') THEN
        CREATE ROLE trailersys_lectura NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_operacion') THEN
        CREATE ROLE trailersys_operacion NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_auditoria') THEN
        CREATE ROLE trailersys_auditoria NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'trailersys_administracion') THEN
        CREATE ROLE trailersys_administracion NOLOGIN CREATEROLE;
    END IF;
END $$;

-- Quitar permisos por defecto de PUBLIC
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;

-- ------------------------------------------------------------
-- Definiciones exportadas con pg_dump (PostgreSQL 18.4)
-- ------------------------------------------------------------

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- ============================================================
-- FUNCIONES, PROCEDIMIENTOS Y DISPARADORES
-- ============================================================
-- Funcion: abre un cursor con los viajes PROGRAMADOS o EN_CURSO

CREATE FUNCTION public.fn_abrir_viajes_pendientes(p_cursor refcursor DEFAULT 'cur_viajes'::refcursor) RETURNS refcursor
    LANGUAGE plpgsql
    AS $$
BEGIN
    OPEN p_cursor FOR
        SELECT vi.id, vi.fecha_salida, vi.origen, vi.destino, vi.estado,
               ve.placa, co.nombres AS conductor, cl.nombre AS cliente
        FROM viajes vi
        JOIN vehiculos ve ON ve.id = vi.vehiculo_id
        JOIN conductores co ON co.id = vi.conductor_id
        JOIN clientes cl ON cl.id = vi.cliente_id
        WHERE vi.estado IN ('PROGRAMADO', 'EN_CURSO')
        ORDER BY vi.fecha_salida;
    RETURN p_cursor;
END;
$$;


ALTER FUNCTION public.fn_abrir_viajes_pendientes(p_cursor refcursor) OWNER TO postgres;

COMMENT ON FUNCTION public.fn_abrir_viajes_pendientes(p_cursor refcursor) IS 'Ejemplo de cursor explicito para consultar viajes activos.';


-- Funcion: cuenta vehiculos y conductores por estado

CREATE FUNCTION public.fn_disponibilidad_flota() RETURNS TABLE(recurso text, estado text, cantidad bigint)
    LANGUAGE sql STABLE
    AS $$
    SELECT 'VEHICULO', v.estado, count(*) FROM vehiculos v GROUP BY v.estado
    UNION ALL
    SELECT 'CONDUCTOR', c.estado, count(*) FROM conductores c GROUP BY c.estado
    ORDER BY 1, 2;
$$;


ALTER FUNCTION public.fn_disponibilidad_flota() OWNER TO postgres;

-- Funcion: linea de tiempo de un viaje (programacion + eventos de seguimiento)

CREATE FUNCTION public.fn_historial_viaje(p_viaje_id bigint) RETURNS TABLE(fecha_hora timestamp without time zone, evento text, ubicacion text, observacion text)
    LANGUAGE sql STABLE
    AS $$
    SELECT vi.fecha_salida, 'PROGRAMACION', vi.origen, vi.observaciones
    FROM viajes vi WHERE vi.id = p_viaje_id
    UNION ALL
    SELECT se.fecha_hora, se.evento, se.ubicacion, se.observacion
    FROM seguimiento_eventos se WHERE se.viaje_id = p_viaje_id
    ORDER BY 1;
$$;


ALTER FUNCTION public.fn_historial_viaje(p_viaje_id bigint) OWNER TO postgres;

-- Funcion: mantenimientos por vencer dentro de los proximos N dias

CREATE FUNCTION public.fn_proximos_mantenimientos(p_dias integer DEFAULT 30) RETURNS TABLE(mantenimiento_id bigint, placa character varying, fecha date, proximo_servicio date, dias_restantes integer)
    LANGUAGE sql STABLE
    AS $$
    SELECT m.id, v.placa, m.fecha, m.proximo_servicio,
           (m.proximo_servicio - current_date)::integer
    FROM mantenimientos m
    JOIN vehiculos v ON v.id = m.vehiculo_id
    WHERE m.proximo_servicio BETWEEN current_date AND current_date + GREATEST(p_dias, 0)
    ORDER BY m.proximo_servicio, v.placa;
$$;


ALTER FUNCTION public.fn_proximos_mantenimientos(p_dias integer) OWNER TO postgres;

-- Funcion: resumen de cargas, viajes y reclamos de un cliente

CREATE FUNCTION public.fn_resumen_cliente(p_cliente_id bigint) RETURNS TABLE(cliente text, cargas bigint, viajes bigint, entregados bigint, reclamos_abiertos bigint)
    LANGUAGE sql STABLE
    AS $$
    SELECT cl.nombre,
           (SELECT count(*) FROM cargas ca WHERE ca.cliente_id = cl.id),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id AND vi.estado = 'FINALIZADO'),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id AND vi.estado_reclamo_cliente IN ('ABIERTO','EN_REVISION'))
    FROM clientes cl
    WHERE cl.id = p_cliente_id;
$$;


ALTER FUNCTION public.fn_resumen_cliente(p_cliente_id bigint) OWNER TO postgres;

-- Procedimiento: crea un viaje con una carga pendiente, un vehiculo y un conductor disponibles

CREATE PROCEDURE public.sp_asignar_carga_viaje(IN p_carga_id bigint, IN p_vehiculo_id bigint, IN p_conductor_id bigint, IN p_fecha_salida timestamp without time zone)
    LANGUAGE plpgsql
    AS $$
DECLARE v_cliente_id bigint; v_origen varchar; v_destino varchar;
BEGIN
    SELECT cliente_id, origen, destino INTO v_cliente_id, v_origen, v_destino
    FROM cargas WHERE id = p_carga_id AND estado = 'PENDIENTE' FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: carga inexistente o no pendiente.'; RETURN; END IF;
    PERFORM 1 FROM vehiculos WHERE id = p_vehiculo_id AND estado = 'DISPONIBLE' FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: vehiculo no disponible.'; RETURN; END IF;
    PERFORM 1 FROM conductores WHERE id = p_conductor_id AND estado = 'DISPONIBLE' FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: conductor no disponible.'; RETURN; END IF;
    INSERT INTO viajes(vehiculo_id, conductor_id, cliente_id, carga_id, origen, destino, fecha_salida,
                       estado, observaciones, entrega_confirmada, entrega_validada)
    VALUES (p_vehiculo_id, p_conductor_id, v_cliente_id, p_carga_id, v_origen, v_destino,
            coalesce(p_fecha_salida, clock_timestamp()), 'PROGRAMADO', 'Asignado mediante procedimiento', false, false);
    UPDATE vehiculos SET estado = 'EN_RUTA' WHERE id = p_vehiculo_id;
    UPDATE conductores SET estado = 'EN_RUTA', vehiculo_id = p_vehiculo_id WHERE id = p_conductor_id;
    UPDATE cargas SET estado = 'ASIGNADA' WHERE id = p_carga_id;
    COMMIT; RAISE NOTICE 'COMMIT: carga asignada y viaje programado.';
END;
$$;


ALTER PROCEDURE public.sp_asignar_carga_viaje(IN p_carga_id bigint, IN p_vehiculo_id bigint, IN p_conductor_id bigint, IN p_fecha_salida timestamp without time zone) OWNER TO postgres;

-- Procedimiento: cancela un viaje y libera el vehiculo y el conductor

CREATE PROCEDURE public.sp_cancelar_viaje(IN p_viaje_id bigint, IN p_motivo text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_estado varchar; v_vehiculo_id bigint; v_conductor_id bigint; v_carga_id bigint;
BEGIN
    SELECT estado,vehiculo_id,conductor_id,carga_id INTO v_estado,v_vehiculo_id,v_conductor_id,v_carga_id
    FROM viajes WHERE id=p_viaje_id FOR UPDATE;
    IF NOT FOUND OR v_estado IN ('FINALIZADO','CANCELADO') THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: el viaje no puede cancelarse.'; RETURN;
    END IF;
    UPDATE viajes SET estado='CANCELADO', observaciones=concat_ws(' | ',observaciones,'Cancelado: '||coalesce(p_motivo,'Sin motivo')) WHERE id=p_viaje_id;
    UPDATE vehiculos SET estado='DISPONIBLE' WHERE id=v_vehiculo_id;
    UPDATE conductores SET estado='DISPONIBLE', vehiculo_id=NULL WHERE id=v_conductor_id;
    UPDATE cargas SET estado='PENDIENTE' WHERE id=v_carga_id AND estado <> 'ENTREGADA';
    COMMIT; RAISE NOTICE 'COMMIT: viaje cancelado y recursos liberados.';
END;
$$;


ALTER PROCEDURE public.sp_cancelar_viaje(IN p_viaje_id bigint, IN p_motivo text) OWNER TO postgres;

-- Procedimiento: el conductor confirma la entrega y se cierra el viaje

CREATE PROCEDURE public.sp_confirmar_entrega(IN p_viaje_id bigint, IN p_confirmado_por character varying, IN p_observacion text DEFAULT NULL::text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_estado varchar; v_vehiculo_id bigint; v_conductor_id bigint; v_carga_id bigint;
BEGIN
    SELECT estado, vehiculo_id, conductor_id, carga_id
    INTO v_estado, v_vehiculo_id, v_conductor_id, v_carga_id
    FROM viajes WHERE id = p_viaje_id FOR UPDATE;
    IF NOT FOUND OR v_estado <> 'EN_CURSO' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: el viaje no existe o no esta en curso.'; RETURN;
    END IF;
    UPDATE viajes SET estado='FINALIZADO', entrega_confirmada=true,
        fecha_entrega_confirmada=clock_timestamp(), observacion_entrega=p_observacion,
        confirmado_por=btrim(p_confirmado_por) WHERE id=p_viaje_id;
    UPDATE vehiculos SET estado='DISPONIBLE' WHERE id=v_vehiculo_id;
    UPDATE conductores SET estado='DISPONIBLE', vehiculo_id=NULL WHERE id=v_conductor_id;
    UPDATE cargas SET estado='ENTREGADA' WHERE id=v_carga_id;
    INSERT INTO seguimiento_eventos(viaje_id, vehiculo_id, fecha_hora, evento, ubicacion, observacion)
    SELECT p_viaje_id, v_vehiculo_id, clock_timestamp(), 'LLEGADA', destino, p_observacion
    FROM viajes WHERE id=p_viaje_id;
    COMMIT; RAISE NOTICE 'COMMIT: entrega confirmada y recursos liberados.';
END;
$$;


ALTER PROCEDURE public.sp_confirmar_entrega(IN p_viaje_id bigint, IN p_confirmado_por character varying, IN p_observacion text) OWNER TO postgres;

-- Procedimiento: pasa un viaje de PROGRAMADO a EN_CURSO y registra el evento de salida

CREATE PROCEDURE public.sp_iniciar_viaje(IN p_viaje_id bigint, IN p_ubicacion character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE v_estado varchar; v_vehiculo_id bigint; v_carga_id bigint;
BEGIN
    SELECT estado, vehiculo_id, carga_id INTO v_estado, v_vehiculo_id, v_carga_id
    FROM viajes WHERE id = p_viaje_id FOR UPDATE;
    IF NOT FOUND OR v_estado <> 'PROGRAMADO' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: el viaje no existe o no esta programado.'; RETURN;
    END IF;
    IF coalesce(btrim(p_ubicacion),'') = '' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: la ubicacion es obligatoria.'; RETURN;
    END IF;
    UPDATE viajes SET estado = 'EN_CURSO' WHERE id = p_viaje_id;
    UPDATE cargas SET estado = 'EN_TRANSITO' WHERE id = v_carga_id;
    INSERT INTO seguimiento_eventos(viaje_id, vehiculo_id, fecha_hora, evento, ubicacion, observacion)
    VALUES (p_viaje_id, v_vehiculo_id, clock_timestamp(), 'SALIDA', btrim(p_ubicacion), 'Inicio del viaje');
    COMMIT; RAISE NOTICE 'COMMIT: viaje % iniciado.', p_viaje_id;
END;
$$;


ALTER PROCEDURE public.sp_iniciar_viaje(IN p_viaje_id bigint, IN p_ubicacion character varying) OWNER TO postgres;

-- Procedimiento: cambia el vehiculo y el conductor de un viaje sin dejar recursos bloqueados

CREATE PROCEDURE public.sp_reasignar_viaje(IN p_viaje_id bigint, IN p_nueva_placa character varying, IN p_nueva_identificacion_conductor character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE v_estado varchar; v_vehiculo_anterior bigint; v_conductor_anterior bigint;
        v_vehiculo_nuevo bigint; v_conductor_nuevo bigint;
BEGIN
    SELECT estado,vehiculo_id,conductor_id INTO v_estado,v_vehiculo_anterior,v_conductor_anterior
    FROM viajes WHERE id=p_viaje_id FOR UPDATE;
    IF NOT FOUND OR v_estado NOT IN ('PROGRAMADO','EN_CURSO') THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: viaje inexistente o no reasignable.'; RETURN;
    END IF;
    SELECT id INTO v_vehiculo_nuevo FROM vehiculos
    WHERE upper(placa)=upper(btrim(p_nueva_placa)) AND (estado='DISPONIBLE' OR id=v_vehiculo_anterior) FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: nuevo vehiculo no disponible.'; RETURN; END IF;
    SELECT id INTO v_conductor_nuevo FROM conductores
    WHERE upper(identificacion)=upper(btrim(p_nueva_identificacion_conductor))
      AND (estado='DISPONIBLE' OR id=v_conductor_anterior) FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: nuevo conductor no disponible.'; RETURN; END IF;
    UPDATE viajes SET vehiculo_id=v_vehiculo_nuevo, conductor_id=v_conductor_nuevo WHERE id=p_viaje_id;
    UPDATE vehiculos SET estado='DISPONIBLE' WHERE id=v_vehiculo_anterior AND id<>v_vehiculo_nuevo
      AND NOT EXISTS(SELECT 1 FROM viajes WHERE vehiculo_id=v_vehiculo_anterior AND id<>p_viaje_id AND estado IN('PROGRAMADO','EN_CURSO'));
    UPDATE conductores SET estado='DISPONIBLE',vehiculo_id=NULL WHERE id=v_conductor_anterior AND id<>v_conductor_nuevo
      AND NOT EXISTS(SELECT 1 FROM viajes WHERE conductor_id=v_conductor_anterior AND id<>p_viaje_id AND estado IN('PROGRAMADO','EN_CURSO'));
    UPDATE vehiculos SET estado='EN_RUTA' WHERE id=v_vehiculo_nuevo;
    UPDATE conductores SET estado='EN_RUTA',vehiculo_id=v_vehiculo_nuevo WHERE id=v_conductor_nuevo;
    COMMIT; RAISE NOTICE 'COMMIT: viaje reasignado de forma segura.';
END;
$$;


ALTER PROCEDURE public.sp_reasignar_viaje(IN p_viaje_id bigint, IN p_nueva_placa character varying, IN p_nueva_identificacion_conductor character varying) OWNER TO postgres;

COMMENT ON PROCEDURE public.sp_reasignar_viaje(IN p_viaje_id bigint, IN p_nueva_placa character varying, IN p_nueva_identificacion_conductor character varying) IS 'Usa FOR UPDATE para evitar reasignaciones concurrentes inconsistentes.';


-- Procedimiento: registra una carga validando que el cliente este activo y los datos obligatorios

CREATE PROCEDURE public.sp_registrar_carga(IN p_cliente_id bigint, IN p_descripcion character varying, IN p_tipo character varying, IN p_peso integer, IN p_origen character varying, IN p_destino character varying, IN p_observaciones text DEFAULT NULL::text)
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM clientes WHERE id = p_cliente_id AND estado = 'ACTIVO') THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: cliente inexistente o inactivo.'; RETURN;
    END IF;
    IF p_peso IS NULL OR p_peso <= 0 OR coalesce(btrim(p_descripcion),'') = ''
       OR coalesce(btrim(p_origen),'') = '' OR coalesce(btrim(p_destino),'') = '' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: descripcion, peso positivo, origen y destino son obligatorios.'; RETURN;
    END IF;
    INSERT INTO cargas(descripcion, cliente_id, tipo, peso, origen, destino, estado, observaciones, fecha_creacion)
    VALUES (btrim(p_descripcion), p_cliente_id, btrim(p_tipo), p_peso, btrim(p_origen), btrim(p_destino),
            'PENDIENTE', p_observaciones, clock_timestamp());
    COMMIT; RAISE NOTICE 'COMMIT: carga registrada correctamente.';
END;
$$;


ALTER PROCEDURE public.sp_registrar_carga(IN p_cliente_id bigint, IN p_descripcion character varying, IN p_tipo character varying, IN p_peso integer, IN p_origen character varying, IN p_destino character varying, IN p_observaciones text) OWNER TO postgres;

-- Procedimiento: registra un cliente y evita identificaciones repetidas

CREATE PROCEDURE public.sp_registrar_cliente(IN p_nombre character varying, IN p_identificacion character varying, IN p_telefono character varying, IN p_correo character varying, IN p_direccion character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF coalesce(btrim(p_nombre),'') = '' OR coalesce(btrim(p_identificacion),'') = ''
       OR coalesce(btrim(p_telefono),'') = '' OR coalesce(btrim(p_direccion),'') = '' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: nombre, identificacion, telefono y direccion son obligatorios.'; RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM clientes WHERE upper(identificacion) = upper(btrim(p_identificacion))) THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: ya existe un cliente con identificacion %.', p_identificacion; RETURN;
    END IF;
    INSERT INTO clientes(nombre, identificacion, estado, telefono, correo, direccion, servicios, observaciones)
    VALUES (btrim(p_nombre), btrim(p_identificacion), 'ACTIVO', btrim(p_telefono), nullif(btrim(p_correo),''),
            btrim(p_direccion), NULL, 'Registrado mediante procedimiento almacenado');
    COMMIT; RAISE NOTICE 'COMMIT: cliente % registrado correctamente.', p_identificacion;
END;
$$;


ALTER PROCEDURE public.sp_registrar_cliente(IN p_nombre character varying, IN p_identificacion character varying, IN p_telefono character varying, IN p_correo character varying, IN p_direccion character varying) OWNER TO postgres;

-- Procedimiento: agrega un evento (parada, retraso, incidente) a un viaje en curso

CREATE PROCEDURE public.sp_registrar_evento_seguimiento(IN p_viaje_id bigint, IN p_evento character varying, IN p_ubicacion character varying, IN p_observacion text DEFAULT NULL::text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_vehiculo_id bigint; v_evento varchar := upper(btrim(p_evento));
BEGIN
    SELECT vehiculo_id INTO v_vehiculo_id FROM viajes
    WHERE id = p_viaje_id AND estado = 'EN_CURSO' FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: solo se registran eventos en viajes en curso.'; RETURN; END IF;
    IF v_evento NOT IN ('SALIDA','PARADA','RETRASO','INCIDENTE','LLEGADA','OTRO')
       OR coalesce(btrim(p_ubicacion),'') = '' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: tipo de evento o ubicacion invalidos.'; RETURN;
    END IF;
    INSERT INTO seguimiento_eventos(viaje_id, vehiculo_id, fecha_hora, evento, ubicacion, observacion)
    VALUES (p_viaje_id, v_vehiculo_id, clock_timestamp(), v_evento, btrim(p_ubicacion), p_observacion);
    COMMIT; RAISE NOTICE 'COMMIT: evento % registrado.', v_evento;
END;
$$;


ALTER PROCEDURE public.sp_registrar_evento_seguimiento(IN p_viaje_id bigint, IN p_evento character varying, IN p_ubicacion character varying, IN p_observacion text) OWNER TO postgres;

-- Procedimiento: registra un mantenimiento y pone el vehiculo en estado MANTENIMIENTO

CREATE PROCEDURE public.sp_registrar_mantenimiento(IN p_vehiculo_id bigint, IN p_tipo character varying, IN p_fecha date, IN p_kilometraje integer, IN p_costo numeric, IN p_descripcion character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE v_tipo varchar := upper(btrim(p_tipo));
BEGIN
    PERFORM 1 FROM vehiculos WHERE id=p_vehiculo_id AND estado <> 'EN_RUTA' FOR UPDATE;
    IF NOT FOUND THEN ROLLBACK; RAISE NOTICE 'ROLLBACK: vehiculo inexistente o actualmente en ruta.'; RETURN; END IF;
    IF v_tipo NOT IN ('PREVENTIVO','CORRECTIVO') OR p_kilometraje < 0 OR p_costo < 0
       OR coalesce(btrim(p_descripcion),'') = '' THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: datos de mantenimiento invalidos.'; RETURN;
    END IF;
    INSERT INTO mantenimientos(vehiculo_id,tipo,fecha,kilometraje,costo,proximo_servicio,descripcion)
    VALUES(p_vehiculo_id,v_tipo,coalesce(p_fecha,current_date),p_kilometraje,p_costo,
           CASE WHEN v_tipo='PREVENTIVO' THEN (coalesce(p_fecha,current_date)+interval '1 month')::date ELSE NULL END,btrim(p_descripcion));
    UPDATE vehiculos SET estado='MANTENIMIENTO', kilometraje=GREATEST(kilometraje,p_kilometraje) WHERE id=p_vehiculo_id;
    COMMIT; RAISE NOTICE 'COMMIT: mantenimiento registrado.';
END;
$$;


ALTER PROCEDURE public.sp_registrar_mantenimiento(IN p_vehiculo_id bigint, IN p_tipo character varying, IN p_fecha date, IN p_kilometraje integer, IN p_costo numeric, IN p_descripcion character varying) OWNER TO postgres;

-- Procedimiento: el supervisor valida una entrega que ya fue confirmada

CREATE PROCEDURE public.sp_validar_entrega(IN p_viaje_id bigint, IN p_validado_por character varying, IN p_observacion text DEFAULT NULL::text)
    LANGUAGE plpgsql
    AS $$
DECLARE v_confirmada boolean; v_validada boolean; v_estado varchar;
BEGIN
    SELECT entrega_confirmada, entrega_validada, estado INTO v_confirmada, v_validada, v_estado
    FROM viajes WHERE id=p_viaje_id FOR UPDATE;
    IF NOT FOUND OR v_estado <> 'FINALIZADO' OR NOT v_confirmada OR v_validada THEN
        ROLLBACK; RAISE NOTICE 'ROLLBACK: entrega inexistente, no confirmada o ya validada.'; RETURN;
    END IF;
    UPDATE viajes SET entrega_validada=true, fecha_validacion_entrega=clock_timestamp(),
        observacion_validacion=p_observacion, validado_por=btrim(p_validado_por) WHERE id=p_viaje_id;
    COMMIT; RAISE NOTICE 'COMMIT: entrega validada por %.', p_validado_por;
END;
$$;


ALTER PROCEDURE public.sp_validar_entrega(IN p_viaje_id bigint, IN p_validado_por character varying, IN p_observacion text) OWNER TO postgres;

-- Funcion de auditoria: guarda cada INSERT, UPDATE y DELETE en la tabla auditoria

CREATE FUNCTION public.trailersys_registrar_auditoria() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
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


ALTER FUNCTION public.trailersys_registrar_auditoria() OWNER TO postgres;

-- Funcion de disparador: valida los valores y calcula el proximo servicio

CREATE FUNCTION public.trg_preparar_mantenimiento() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.kilometraje < 0 OR NEW.costo < 0 THEN
        RAISE EXCEPTION 'Kilometraje y costo no pueden ser negativos.';
    END IF;
    IF NEW.tipo = 'PREVENTIVO' AND NEW.proximo_servicio IS NULL THEN
        NEW.proximo_servicio := (NEW.fecha + interval '1 month')::date;
    END IF;
    IF NEW.proximo_servicio IS NOT NULL AND NEW.proximo_servicio < NEW.fecha THEN
        RAISE EXCEPTION 'El proximo servicio no puede ser anterior al mantenimiento.';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.trg_preparar_mantenimiento() OWNER TO postgres;

-- Funcion de disparador: exige peso mayor a cero y origen/destino no vacios

CREATE FUNCTION public.trg_validar_carga() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.peso IS NULL OR NEW.peso <= 0 THEN
        RAISE EXCEPTION 'El peso de la carga debe ser mayor que cero.';
    END IF;
    IF btrim(NEW.origen) = '' OR btrim(NEW.destino) = '' THEN
        RAISE EXCEPTION 'El origen y el destino son obligatorios.';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.trg_validar_carga() OWNER TO postgres;

-- Funcion de disparador: impide eventos con fecha futura o sin ubicacion

CREATE FUNCTION public.trg_validar_evento_seguimiento() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.fecha_hora > clock_timestamp() + interval '1 minute' THEN
        RAISE EXCEPTION 'La fecha y hora del evento no pueden ser futuras.';
    END IF;
    IF btrim(NEW.ubicacion) = '' THEN
        RAISE EXCEPTION 'La ubicacion del evento es obligatoria.';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.trg_validar_evento_seguimiento() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

-- ============================================================
-- TABLAS (con claves, valores por defecto y validaciones CHECK)
-- ============================================================
-- Tabla auditoria: bitacora de operaciones (quien, cuando, sobre que tabla y que cambio)

CREATE TABLE public.auditoria (
    id bigint NOT NULL,
    fecha_hora timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    usuario_bd text DEFAULT SESSION_USER NOT NULL,
    usuario_app text,
    operacion text NOT NULL,
    esquema text NOT NULL,
    tabla text NOT NULL,
    registro_id text,
    datos_anteriores jsonb,
    datos_nuevos jsonb,
    cliente_ip inet DEFAULT inet_client_addr(),
    transaccion_id bigint DEFAULT txid_current() NOT NULL,
    CONSTRAINT auditoria_operacion_check CHECK ((operacion = ANY (ARRAY['INSERT'::text, 'UPDATE'::text, 'DELETE'::text])))
);


ALTER TABLE public.auditoria OWNER TO postgres;

ALTER TABLE public.auditoria ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.auditoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla cargas: mercancia a transportar; cada carga pertenece a un cliente

CREATE TABLE public.cargas (
    id bigint NOT NULL,
    descripcion character varying(255) NOT NULL,
    destino character varying(150) NOT NULL,
    estado character varying(20) NOT NULL,
    observaciones text,
    origen character varying(150) NOT NULL,
    peso integer NOT NULL,
    tipo character varying(60) NOT NULL,
    cliente_id bigint NOT NULL,
    fecha_creacion timestamp(6) without time zone,
    CONSTRAINT cargas_estado_check CHECK (((estado)::text = ANY ((ARRAY['PENDIENTE'::character varying, 'ASIGNADA'::character varying, 'EN_TRANSITO'::character varying, 'ENTREGADA'::character varying, 'CANCELADA'::character varying])::text[]))),
    CONSTRAINT ck_cargas_peso_positivo CHECK ((peso > 0)),
    CONSTRAINT ck_cargas_ruta_obligatoria CHECK (((btrim((origen)::text) <> ''::text) AND (btrim((destino)::text) <> ''::text)))
);


ALTER TABLE public.cargas OWNER TO postgres;

ALTER TABLE public.cargas ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.cargas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla clientes: empresas que solicitan el servicio de transporte

CREATE TABLE public.clientes (
    id bigint NOT NULL,
    correo character varying(120),
    direccion character varying(255) NOT NULL,
    estado character varying(20) NOT NULL,
    identificacion character varying(30) NOT NULL,
    nombre character varying(150) NOT NULL,
    observaciones text,
    servicios text,
    telefono character varying(30) NOT NULL,
    CONSTRAINT clientes_estado_check CHECK (((estado)::text = ANY ((ARRAY['ACTIVO'::character varying, 'INACTIVO'::character varying])::text[])))
);


ALTER TABLE public.clientes OWNER TO postgres;

ALTER TABLE public.clientes ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.clientes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla conductores: datos del conductor y de su licencia

CREATE TABLE public.conductores (
    id bigint NOT NULL,
    correo character varying(120),
    estado character varying(20) NOT NULL,
    foto text,
    identificacion character varying(30) NOT NULL,
    licencia_categoria character varying(20) NOT NULL,
    licencia_numero character varying(40) NOT NULL,
    licencia_vencimiento date NOT NULL,
    nombres character varying(150) NOT NULL,
    observaciones text,
    telefono character varying(30) NOT NULL,
    vehiculo_id bigint,
    fecha_nacimiento date,
    CONSTRAINT conductores_estado_check CHECK (((estado)::text = ANY ((ARRAY['DISPONIBLE'::character varying, 'EN_RUTA'::character varying, 'DESCANSO'::character varying, 'INACTIVO'::character varying])::text[])))
);


ALTER TABLE public.conductores OWNER TO postgres;

ALTER TABLE public.conductores ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.conductores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla configuracion_respaldo: parametros del respaldo automatico (frecuencia y hora)

CREATE TABLE public.configuracion_respaldo (
    id bigint NOT NULL,
    activo boolean NOT NULL,
    hora_programada time(6) without time zone NOT NULL,
    ultima_ejecucion_programada date,
    dia_mes integer,
    dia_semana character varying(15),
    frecuencia character varying(20) DEFAULT 'DIARIO'::character varying NOT NULL,
    CONSTRAINT configuracion_respaldo_dia_semana_check CHECK (((dia_semana)::text = ANY ((ARRAY['MONDAY'::character varying, 'TUESDAY'::character varying, 'WEDNESDAY'::character varying, 'THURSDAY'::character varying, 'FRIDAY'::character varying, 'SATURDAY'::character varying, 'SUNDAY'::character varying])::text[]))),
    CONSTRAINT configuracion_respaldo_frecuencia_check CHECK (((frecuencia)::text = ANY ((ARRAY['DIARIO'::character varying, 'SEMANAL'::character varying, 'MENSUAL'::character varying])::text[])))
);


ALTER TABLE public.configuracion_respaldo OWNER TO postgres;

ALTER TABLE public.configuracion_respaldo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.configuracion_respaldo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla mantenimiento_evidencias: archivos adjuntos de un mantenimiento

CREATE TABLE public.mantenimiento_evidencias (
    id bigint NOT NULL,
    categoria character varying(30) NOT NULL,
    contenido bytea NOT NULL,
    fecha_carga timestamp(6) without time zone NOT NULL,
    nombre character varying(255) NOT NULL,
    tamano bigint NOT NULL,
    tipo_contenido character varying(100) NOT NULL,
    mantenimiento_id bigint NOT NULL
);


ALTER TABLE public.mantenimiento_evidencias OWNER TO postgres;

ALTER TABLE public.mantenimiento_evidencias ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.mantenimiento_evidencias_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla mantenimientos: servicios preventivos y correctivos de un vehiculo

CREATE TABLE public.mantenimientos (
    id bigint NOT NULL,
    costo double precision NOT NULL,
    descripcion character varying(255) NOT NULL,
    fecha date NOT NULL,
    kilometraje integer NOT NULL,
    proximo_servicio date,
    tipo character varying(20) NOT NULL,
    vehiculo_id bigint NOT NULL,
    CONSTRAINT ck_mantenimientos_fechas_coherentes CHECK (((proximo_servicio IS NULL) OR (proximo_servicio >= fecha))),
    CONSTRAINT ck_mantenimientos_valores_positivos CHECK (((kilometraje >= 0) AND (costo >= (0)::double precision))),
    CONSTRAINT mantenimientos_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['PREVENTIVO'::character varying, 'CORRECTIVO'::character varying])::text[])))
);


ALTER TABLE public.mantenimientos OWNER TO postgres;

ALTER TABLE public.mantenimientos ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.mantenimientos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla respaldos: historial de respaldos generados desde la aplicacion

CREATE TABLE public.respaldos (
    id bigint NOT NULL,
    archivo_ruta character varying(500),
    estado character varying(20) NOT NULL,
    fecha_hora timestamp(6) without time zone NOT NULL,
    generado_por character varying(60) NOT NULL,
    mensaje_error character varying(1000),
    registros_capturados integer,
    respaldo_anterior_id bigint,
    tamano_bytes bigint,
    tipo character varying(20) NOT NULL,
    CONSTRAINT respaldos_estado_check CHECK (((estado)::text = ANY ((ARRAY['EN_PROGRESO'::character varying, 'COMPLETADO'::character varying, 'FALLIDO'::character varying])::text[]))),
    CONSTRAINT respaldos_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['COMPLETO'::character varying, 'INCREMENTAL'::character varying])::text[])))
);


ALTER TABLE public.respaldos OWNER TO postgres;

ALTER TABLE public.respaldos ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.respaldos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla seguimiento_eventos: eventos de la ruta de cada viaje

CREATE TABLE public.seguimiento_eventos (
    id bigint NOT NULL,
    evento character varying(20) NOT NULL,
    fecha_hora timestamp(6) without time zone NOT NULL,
    observacion text,
    ubicacion character varying(255) NOT NULL,
    vehiculo_id bigint NOT NULL,
    viaje_id bigint NOT NULL,
    latitud_reportada double precision,
    longitud_reportada double precision,
    CONSTRAINT seguimiento_eventos_evento_check CHECK (((evento)::text = ANY ((ARRAY['SALIDA'::character varying, 'PARADA'::character varying, 'RETRASO'::character varying, 'INCIDENTE'::character varying, 'LLEGADA'::character varying, 'OTRO'::character varying])::text[])))
);


ALTER TABLE public.seguimiento_eventos OWNER TO postgres;

ALTER TABLE public.seguimiento_eventos ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.seguimiento_eventos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla usuarios: cuentas de acceso, con rol y contrasena cifrada

CREATE TABLE public.usuarios (
    id bigint NOT NULL,
    activo boolean NOT NULL,
    correo character varying(120),
    nombre character varying(120) NOT NULL,
    password_hash character varying(255) NOT NULL,
    rol character varying(20) NOT NULL,
    username character varying(60) NOT NULL,
    cliente_id bigint,
    conductor_id bigint,
    foto text,
    bloqueado_hasta timestamp(6) without time zone,
    intentos_fallidos integer DEFAULT 0 NOT NULL,
    CONSTRAINT usuarios_rol_check CHECK (((rol)::text = ANY ((ARRAY['ADMINISTRADOR'::character varying, 'COORDINADOR'::character varying, 'MANTENIMIENTO'::character varying, 'CONDUCTOR'::character varying, 'SUPERVISOR'::character varying, 'CLIENTE'::character varying])::text[])))
);


ALTER TABLE public.usuarios OWNER TO postgres;

ALTER TABLE public.usuarios ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla vehiculos: flota de camiones y trailers

CREATE TABLE public.vehiculos (
    id bigint NOT NULL,
    anio integer NOT NULL,
    capacidad integer NOT NULL,
    color character varying(40) NOT NULL,
    estado character varying(30) NOT NULL,
    foto text,
    kilometraje integer NOT NULL,
    marca character varying(60) NOT NULL,
    modelo character varying(60) NOT NULL,
    observaciones text,
    placa character varying(20) NOT NULL,
    tipo character varying(40) NOT NULL,
    CONSTRAINT ck_vehiculos_anio_valido CHECK (((anio >= 1980) AND (anio <= 2100))),
    CONSTRAINT ck_vehiculos_valores_positivos CHECK (((kilometraje >= 0) AND (capacidad > 0))),
    CONSTRAINT vehiculos_estado_check CHECK (((estado)::text = ANY ((ARRAY['DISPONIBLE'::character varying, 'EN_RUTA'::character varying, 'MANTENIMIENTO'::character varying, 'FUERA_DE_SERVICIO'::character varying])::text[])))
);


ALTER TABLE public.vehiculos OWNER TO postgres;

ALTER TABLE public.vehiculos ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.vehiculos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- Tabla viajes: nucleo del sistema; relaciona vehiculo, conductor, cliente y carga

CREATE TABLE public.viajes (
    id bigint NOT NULL,
    destino character varying(150) NOT NULL,
    estado character varying(20) NOT NULL,
    fecha_salida timestamp(6) without time zone NOT NULL,
    observaciones text,
    origen character varying(150) NOT NULL,
    ruta_destino_lat double precision,
    ruta_destino_lng double precision,
    ruta_distancia_km double precision,
    ruta_duracion_min double precision,
    ruta_origen_lat double precision,
    ruta_origen_lng double precision,
    ruta_path text,
    carga_id bigint,
    cliente_id bigint NOT NULL,
    conductor_id bigint NOT NULL,
    vehiculo_id bigint NOT NULL,
    confirmado_por character varying(60),
    fecha_entrega_confirmada timestamp(6) without time zone,
    fecha_validacion_entrega timestamp(6) without time zone,
    observacion_entrega text,
    observacion_validacion text,
    validado_por character varying(60),
    entrega_confirmada boolean DEFAULT false NOT NULL,
    entrega_validada boolean DEFAULT false NOT NULL,
    paradas_simuladas_registradas integer,
    confirmado_por_cliente character varying(60),
    entrega_confirmada_cliente boolean,
    estado_reclamo_cliente character varying(20),
    evidencia_recepcion_cliente text,
    fecha_confirmacion_cliente timestamp(6) without time zone,
    fecha_resolucion_reclamo_cliente timestamp(6) without time zone,
    novedad_recepcion_cliente character varying(40),
    observacion_confirmacion_cliente text,
    respuesta_reclamo_cliente text,
    CONSTRAINT ck_viajes_ruta_obligatoria CHECK (((btrim((origen)::text) <> ''::text) AND (btrim((destino)::text) <> ''::text))),
    CONSTRAINT viajes_estado_check CHECK (((estado)::text = ANY ((ARRAY['PROGRAMADO'::character varying, 'EN_CURSO'::character varying, 'FINALIZADO'::character varying, 'CANCELADO'::character varying])::text[])))
);


ALTER TABLE public.viajes OWNER TO postgres;

ALTER TABLE public.viajes ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.viajes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


-- ============================================================
-- VISTAS
-- ============================================================
-- Vista: disponibilidad de vehiculos y conductores en una sola lista

CREATE VIEW public.vw_disponibilidad_flota AS
 SELECT 'VEHICULO'::text AS recurso,
    v.id AS recurso_id,
    (v.placa)::text AS codigo,
    concat_ws(' '::text, v.marca, v.modelo) AS nombre,
    (v.estado)::text AS estado,
        CASE
            WHEN ((v.estado)::text = 'DISPONIBLE'::text) THEN 'LISTO PARA ASIGNAR'::text
            WHEN ((v.estado)::text = 'EN_RUTA'::text) THEN 'OCUPADO'::text
            WHEN ((v.estado)::text = 'MANTENIMIENTO'::text) THEN 'NO DISPONIBLE'::text
            ELSE 'FUERA DE SERVICIO'::text
        END AS disponibilidad
   FROM public.vehiculos v
UNION ALL
 SELECT 'CONDUCTOR'::text AS recurso,
    c.id AS recurso_id,
    (c.identificacion)::text AS codigo,
    (c.nombres)::text AS nombre,
    (c.estado)::text AS estado,
        CASE
            WHEN (c.licencia_vencimiento < CURRENT_DATE) THEN 'DOCUMENTO VENCIDO'::text
            WHEN ((c.estado)::text = 'DISPONIBLE'::text) THEN 'LISTO PARA ASIGNAR'::text
            WHEN ((c.estado)::text = 'EN_RUTA'::text) THEN 'OCUPADO'::text
            ELSE 'NO DISPONIBLE'::text
        END AS disponibilidad
   FROM public.conductores c;


ALTER VIEW public.vw_disponibilidad_flota OWNER TO postgres;

COMMENT ON VIEW public.vw_disponibilidad_flota IS 'Disponibilidad unificada de vehiculos y conductores.';


-- Vista: ultimo mantenimiento y alerta calculada por vehiculo

CREATE VIEW public.vw_mantenimientos_proximos AS
 SELECT DISTINCT ON (v.id) v.id AS vehiculo_id,
    v.placa,
    v.marca,
    v.modelo,
    v.estado AS estado_vehiculo,
    m.id AS mantenimiento_id,
    m.tipo,
    m.fecha AS ultimo_mantenimiento,
    m.proximo_servicio,
    (m.proximo_servicio - CURRENT_DATE) AS dias_restantes,
    m.costo,
        CASE
            WHEN (m.proximo_servicio IS NULL) THEN 'SIN PROGRAMAR'::text
            WHEN (m.proximo_servicio < CURRENT_DATE) THEN 'VENCIDO'::text
            WHEN (m.proximo_servicio <= (CURRENT_DATE + 7)) THEN 'URGENTE'::text
            WHEN (m.proximo_servicio <= (CURRENT_DATE + 30)) THEN 'PROXIMO'::text
            ELSE 'AL DIA'::text
        END AS alerta
   FROM (public.vehiculos v
     LEFT JOIN public.mantenimientos m ON ((m.vehiculo_id = v.id)))
  ORDER BY v.id, m.fecha DESC NULLS LAST, m.id DESC NULLS LAST;


ALTER VIEW public.vw_mantenimientos_proximos OWNER TO postgres;

COMMENT ON VIEW public.vw_mantenimientos_proximos IS 'Ultimo mantenimiento y alerta calculada por vehiculo.';


-- Vista: reclamos y novedades reportados por los clientes

CREATE VIEW public.vw_reclamos_clientes AS
 SELECT vi.id AS viaje_id,
    cl.id AS cliente_id,
    cl.nombre AS cliente,
    cl.identificacion AS cliente_identificacion,
    vi.fecha_confirmacion_cliente,
    vi.novedad_recepcion_cliente,
    vi.estado_reclamo_cliente,
    vi.respuesta_reclamo_cliente,
    vi.fecha_resolucion_reclamo_cliente,
    ca.id AS carga_id,
    ca.descripcion AS carga,
    vi.origen,
    vi.destino
   FROM ((public.viajes vi
     JOIN public.clientes cl ON ((cl.id = vi.cliente_id)))
     LEFT JOIN public.cargas ca ON ((ca.id = vi.carga_id)))
  WHERE (vi.estado_reclamo_cliente IS NOT NULL);


ALTER VIEW public.vw_reclamos_clientes OWNER TO postgres;

COMMENT ON VIEW public.vw_reclamos_clientes IS 'Reclamos y novedades informados por los clientes.';


-- Vista: cada viaje con los datos de su vehiculo, conductor, cliente y carga

CREATE VIEW public.vw_viajes_detallados AS
 SELECT vi.id AS viaje_id,
    vi.estado AS estado_viaje,
    vi.fecha_salida,
    vi.origen,
    vi.destino,
    vi.ruta_distancia_km,
    vi.ruta_duracion_min,
    ve.id AS vehiculo_id,
    ve.placa,
    ve.marca,
    ve.modelo,
    co.id AS conductor_id,
    co.nombres AS conductor,
    co.identificacion AS conductor_identificacion,
    cl.id AS cliente_id,
    cl.nombre AS cliente,
    ca.id AS carga_id,
    ca.descripcion AS carga,
    ca.peso AS peso_kg,
    ca.estado AS estado_carga,
    vi.entrega_confirmada,
    vi.entrega_validada,
    COALESCE(vi.entrega_confirmada_cliente, false) AS recepcion_cliente,
    vi.estado_reclamo_cliente
   FROM ((((public.viajes vi
     JOIN public.vehiculos ve ON ((ve.id = vi.vehiculo_id)))
     JOIN public.conductores co ON ((co.id = vi.conductor_id)))
     JOIN public.clientes cl ON ((cl.id = vi.cliente_id)))
     LEFT JOIN public.cargas ca ON ((ca.id = vi.carga_id)));


ALTER VIEW public.vw_viajes_detallados OWNER TO postgres;

COMMENT ON VIEW public.vw_viajes_detallados IS 'Vista integral de viajes, recursos, cliente, carga y entrega.';


-- ============================================================
-- CLAVES PRIMARIAS Y VALORES UNICOS
-- ============================================================
-- Clave primaria de la tabla auditoria

ALTER TABLE ONLY public.auditoria
    ADD CONSTRAINT auditoria_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla cargas

ALTER TABLE ONLY public.cargas
    ADD CONSTRAINT cargas_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla clientes

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla conductores

ALTER TABLE ONLY public.conductores
    ADD CONSTRAINT conductores_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla configuracion_respaldo

ALTER TABLE ONLY public.configuracion_respaldo
    ADD CONSTRAINT configuracion_respaldo_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla mantenimiento_evidencias

ALTER TABLE ONLY public.mantenimiento_evidencias
    ADD CONSTRAINT mantenimiento_evidencias_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla mantenimientos

ALTER TABLE ONLY public.mantenimientos
    ADD CONSTRAINT mantenimientos_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla respaldos

ALTER TABLE ONLY public.respaldos
    ADD CONSTRAINT respaldos_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla seguimiento_eventos

ALTER TABLE ONLY public.seguimiento_eventos
    ADD CONSTRAINT seguimiento_eventos_pkey PRIMARY KEY (id);


-- Restriccion de valor unico en la tabla vehiculos

ALTER TABLE ONLY public.vehiculos
    ADD CONSTRAINT uka8w6omovfa10q8eyjalqas391 UNIQUE (placa);


-- Restriccion de valor unico en la tabla usuarios

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT ukm2dvbwfge291euvmk6vkkocao UNIQUE (username);


-- Restriccion de valor unico en la tabla clientes

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT ukmvqpaay4xno9pnlalro0awadi UNIQUE (identificacion);


-- Restriccion de valor unico en la tabla conductores

ALTER TABLE ONLY public.conductores
    ADD CONSTRAINT ukqod8d7yn6kug5jd5j91po1m4i UNIQUE (identificacion);


-- Clave primaria de la tabla usuarios

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla vehiculos

ALTER TABLE ONLY public.vehiculos
    ADD CONSTRAINT vehiculos_pkey PRIMARY KEY (id);


-- Clave primaria de la tabla viajes

ALTER TABLE ONLY public.viajes
    ADD CONSTRAINT viajes_pkey PRIMARY KEY (id);


-- ============================================================
-- INDICES
-- ============================================================
-- Indice para acelerar consultas: idx_auditoria_fecha

CREATE INDEX idx_auditoria_fecha ON public.auditoria USING btree (fecha_hora DESC);


-- Indice para acelerar consultas: idx_auditoria_tabla_fecha

CREATE INDEX idx_auditoria_tabla_fecha ON public.auditoria USING btree (tabla, fecha_hora DESC);


-- Indice para acelerar consultas: idx_auditoria_usuario_fecha

CREATE INDEX idx_auditoria_usuario_fecha ON public.auditoria USING btree (usuario_app, fecha_hora DESC);


-- Indice para acelerar consultas: idx_mantenimientos_vehiculo_fecha

CREATE INDEX idx_mantenimientos_vehiculo_fecha ON public.mantenimientos USING btree (vehiculo_id, fecha DESC);


-- Indice para acelerar consultas: idx_seguimiento_viaje_fecha

CREATE INDEX idx_seguimiento_viaje_fecha ON public.seguimiento_eventos USING btree (viaje_id, fecha_hora DESC);


-- Indice para acelerar consultas: idx_viajes_carga

CREATE INDEX idx_viajes_carga ON public.viajes USING btree (carga_id);


-- Indice para acelerar consultas: idx_viajes_conductor

CREATE INDEX idx_viajes_conductor ON public.viajes USING btree (conductor_id);


-- Indice para acelerar consultas: idx_viajes_estado_fecha

CREATE INDEX idx_viajes_estado_fecha ON public.viajes USING btree (estado, fecha_salida DESC);


-- Indice para acelerar consultas: idx_viajes_vehiculo

CREATE INDEX idx_viajes_vehiculo ON public.viajes USING btree (vehiculo_id);


-- Indice unico (sin distinguir mayusculas): uq_clientes_identificacion_upper

CREATE UNIQUE INDEX uq_clientes_identificacion_upper ON public.clientes USING btree (upper((identificacion)::text));


-- Indice unico (sin distinguir mayusculas): uq_conductores_identificacion_upper

CREATE UNIQUE INDEX uq_conductores_identificacion_upper ON public.conductores USING btree (upper((identificacion)::text));


-- Indice unico (sin distinguir mayusculas): uq_usuarios_username_upper

CREATE UNIQUE INDEX uq_usuarios_username_upper ON public.usuarios USING btree (upper((username)::text));


-- Indice unico (sin distinguir mayusculas): uq_vehiculos_placa_upper

CREATE UNIQUE INDEX uq_vehiculos_placa_upper ON public.vehiculos USING btree (upper((placa)::text));


-- ============================================================
-- DISPARADORES (auditoria y validaciones)
-- ============================================================
-- Disparador trg_auditar en la tabla cargas

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.cargas FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla clientes

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.clientes FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla conductores

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.conductores FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla mantenimientos

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.mantenimientos FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla seguimiento_eventos

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.seguimiento_eventos FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla usuarios

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.usuarios FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla vehiculos

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.vehiculos FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_auditar en la tabla viajes

CREATE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.viajes FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();


-- Disparador trg_preparar_mantenimiento en la tabla mantenimientos

CREATE TRIGGER trg_preparar_mantenimiento BEFORE INSERT OR UPDATE OF tipo, fecha, kilometraje, costo, proximo_servicio ON public.mantenimientos FOR EACH ROW EXECUTE FUNCTION public.trg_preparar_mantenimiento();


-- Disparador trg_validar_carga en la tabla cargas

CREATE TRIGGER trg_validar_carga BEFORE INSERT OR UPDATE OF peso, origen, destino ON public.cargas FOR EACH ROW EXECUTE FUNCTION public.trg_validar_carga();


-- Disparador trg_validar_evento_seguimiento en la tabla seguimiento_eventos

CREATE TRIGGER trg_validar_evento_seguimiento BEFORE INSERT OR UPDATE OF fecha_hora, ubicacion ON public.seguimiento_eventos FOR EACH ROW EXECUTE FUNCTION public.trg_validar_evento_seguimiento();


-- ============================================================
-- CLAVES FORANEAS
-- ============================================================
-- Clave foranea de la tabla cargas

ALTER TABLE ONLY public.cargas
    ADD CONSTRAINT fk1ox75lkmivc00jd6tqsbdkipe FOREIGN KEY (cliente_id) REFERENCES public.clientes(id);


-- Clave foranea de la tabla mantenimientos

ALTER TABLE ONLY public.mantenimientos
    ADD CONSTRAINT fk67y1lce9p7e5c260xola7j702 FOREIGN KEY (vehiculo_id) REFERENCES public.vehiculos(id);


-- Clave foranea de la tabla seguimiento_eventos

ALTER TABLE ONLY public.seguimiento_eventos
    ADD CONSTRAINT fk8spkfxil11oovp2yeeyefg21x FOREIGN KEY (viaje_id) REFERENCES public.viajes(id);


-- Clave foranea de la tabla seguimiento_eventos

ALTER TABLE ONLY public.seguimiento_eventos
    ADD CONSTRAINT fkbdkq7l1kt80klehdklygmawi FOREIGN KEY (vehiculo_id) REFERENCES public.vehiculos(id);


-- Clave foranea de la tabla conductores

ALTER TABLE ONLY public.conductores
    ADD CONSTRAINT fkdfik91w9urp02e5n9ybr3v6i9 FOREIGN KEY (vehiculo_id) REFERENCES public.vehiculos(id);


-- Clave foranea de la tabla usuarios

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT fkdx76w4skuwj8wldmr7ebfyegq FOREIGN KEY (cliente_id) REFERENCES public.clientes(id);


-- Clave foranea de la tabla viajes

ALTER TABLE ONLY public.viajes
    ADD CONSTRAINT fkev6teav9yjsdakyr116f88ecs FOREIGN KEY (conductor_id) REFERENCES public.conductores(id);


-- Clave foranea de la tabla viajes

ALTER TABLE ONLY public.viajes
    ADD CONSTRAINT fkgeve1bs56hqqbbvocuufbr89h FOREIGN KEY (vehiculo_id) REFERENCES public.vehiculos(id);


-- Clave foranea de la tabla viajes

ALTER TABLE ONLY public.viajes
    ADD CONSTRAINT fkinlrljnb7nmwtdabja4895q5e FOREIGN KEY (cliente_id) REFERENCES public.clientes(id);


-- Clave foranea de la tabla mantenimiento_evidencias

ALTER TABLE ONLY public.mantenimiento_evidencias
    ADD CONSTRAINT fkp7hgufwrgya3ig7tc2najyuhr FOREIGN KEY (mantenimiento_id) REFERENCES public.mantenimientos(id);


-- Clave foranea de la tabla viajes

ALTER TABLE ONLY public.viajes
    ADD CONSTRAINT fks0u17o85bdk9o9rvlgb5hh7nf FOREIGN KEY (carga_id) REFERENCES public.cargas(id);


-- Clave foranea de la tabla usuarios

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT fksvclxc0txucpqpcek4ta6jh3t FOREIGN KEY (conductor_id) REFERENCES public.conductores(id);


-- ============================================================
-- PERMISOS POR ROL
-- ============================================================
-- Permisos de uso del esquema public

GRANT USAGE ON SCHEMA public TO trailersys_lectura;
GRANT USAGE ON SCHEMA public TO trailersys_operacion;
GRANT USAGE ON SCHEMA public TO trailersys_auditoria;
GRANT USAGE ON SCHEMA public TO trailersys_administracion;


-- Permisos sobre auditoria segun el rol

GRANT SELECT ON TABLE public.auditoria TO trailersys_auditoria;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.auditoria TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.auditoria_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.auditoria_id_seq TO trailersys_administracion;


-- Permisos sobre cargas segun el rol

GRANT SELECT ON TABLE public.cargas TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.cargas TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.cargas TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.cargas_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.cargas_id_seq TO trailersys_administracion;


-- Permisos sobre clientes segun el rol

GRANT SELECT ON TABLE public.clientes TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.clientes TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.clientes TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.clientes_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.clientes_id_seq TO trailersys_administracion;


-- Permisos sobre conductores segun el rol

GRANT SELECT ON TABLE public.conductores TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.conductores TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.conductores TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.conductores_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.conductores_id_seq TO trailersys_administracion;


-- Permisos sobre configuracion_respaldo segun el rol

GRANT SELECT ON TABLE public.configuracion_respaldo TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.configuracion_respaldo TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.configuracion_respaldo TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.configuracion_respaldo_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.configuracion_respaldo_id_seq TO trailersys_administracion;


-- Permisos sobre mantenimiento_evidencias segun el rol

GRANT SELECT ON TABLE public.mantenimiento_evidencias TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.mantenimiento_evidencias TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.mantenimiento_evidencias TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.mantenimiento_evidencias_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.mantenimiento_evidencias_id_seq TO trailersys_administracion;


-- Permisos sobre mantenimientos segun el rol

GRANT SELECT ON TABLE public.mantenimientos TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.mantenimientos TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.mantenimientos TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.mantenimientos_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.mantenimientos_id_seq TO trailersys_administracion;


-- Permisos sobre respaldos segun el rol

GRANT SELECT ON TABLE public.respaldos TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.respaldos TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.respaldos TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.respaldos_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.respaldos_id_seq TO trailersys_administracion;


-- Permisos sobre seguimiento_eventos segun el rol

GRANT SELECT ON TABLE public.seguimiento_eventos TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.seguimiento_eventos TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.seguimiento_eventos TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.seguimiento_eventos_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.seguimiento_eventos_id_seq TO trailersys_administracion;


-- Permisos sobre usuarios segun el rol

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.usuarios TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.usuarios_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.usuarios_id_seq TO trailersys_administracion;


-- Permisos sobre vehiculos segun el rol

GRANT SELECT ON TABLE public.vehiculos TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.vehiculos TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.vehiculos TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.vehiculos_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.vehiculos_id_seq TO trailersys_administracion;


-- Permisos sobre viajes segun el rol

GRANT SELECT ON TABLE public.viajes TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.viajes TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.viajes TO trailersys_administracion;


GRANT SELECT,USAGE ON SEQUENCE public.viajes_id_seq TO trailersys_operacion;
GRANT SELECT,USAGE ON SEQUENCE public.viajes_id_seq TO trailersys_administracion;


-- Permisos sobre vw_disponibilidad_flota segun el rol

GRANT SELECT ON TABLE public.vw_disponibilidad_flota TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.vw_disponibilidad_flota TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.vw_disponibilidad_flota TO trailersys_administracion;


-- Permisos sobre vw_mantenimientos_proximos segun el rol

GRANT SELECT ON TABLE public.vw_mantenimientos_proximos TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.vw_mantenimientos_proximos TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.vw_mantenimientos_proximos TO trailersys_administracion;


-- Permisos sobre vw_reclamos_clientes segun el rol

GRANT SELECT ON TABLE public.vw_reclamos_clientes TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.vw_reclamos_clientes TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.vw_reclamos_clientes TO trailersys_administracion;


-- Permisos sobre vw_viajes_detallados segun el rol

GRANT SELECT ON TABLE public.vw_viajes_detallados TO trailersys_lectura;
GRANT SELECT,INSERT,UPDATE ON TABLE public.vw_viajes_detallados TO trailersys_operacion;
GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.vw_viajes_detallados TO trailersys_administracion;


-- Permisos que heredaran las tablas y secuencias que se creen despues

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT,USAGE ON SEQUENCES TO trailersys_operacion;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT,USAGE ON SEQUENCES TO trailersys_administracion;


-- Permisos que heredaran las tablas y secuencias que se creen despues

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT ON TABLES TO trailersys_lectura;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT,INSERT,UPDATE ON TABLES TO trailersys_operacion;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT,INSERT,DELETE,UPDATE ON TABLES TO trailersys_administracion;


-- Fin del script
