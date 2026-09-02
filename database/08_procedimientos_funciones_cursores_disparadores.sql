-- TrailerSys: procedimientos, funciones, cursores y disparadores PostgreSQL.
-- Requisitos: iniciar primero el backend para que Hibernate cree las tablas.
-- Ejecutar con autocommit habilitado:
--   psql -U postgres -d trailersys -v ON_ERROR_STOP=1 -f database/08_procedimientos_funciones_cursores_disparadores.sql
--
-- IMPORTANTE: los procedimientos incluyen COMMIT y ROLLBACK internos para la
-- practica. Cada CALL debe ejecutarse como sentencia independiente,
-- nunca dentro de BEGIN/COMMIT ni desde una transaccion administrada por JPA.

-- ---------------------------------------------------------------------------
-- FUNCIONES DE CONSULTA
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_disponibilidad_flota()
RETURNS TABLE(recurso text, estado text, cantidad bigint)
LANGUAGE sql STABLE AS $$
    SELECT 'VEHICULO', v.estado, count(*) FROM vehiculos v GROUP BY v.estado
    UNION ALL
    SELECT 'CONDUCTOR', c.estado, count(*) FROM conductores c GROUP BY c.estado
    ORDER BY 1, 2;
$$;

CREATE OR REPLACE FUNCTION fn_resumen_cliente(p_cliente_id bigint)
RETURNS TABLE(cliente text, cargas bigint, viajes bigint, entregados bigint, reclamos_abiertos bigint)
LANGUAGE sql STABLE AS $$
    SELECT cl.nombre,
           (SELECT count(*) FROM cargas ca WHERE ca.cliente_id = cl.id),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id AND vi.estado = 'FINALIZADO'),
           (SELECT count(*) FROM viajes vi WHERE vi.cliente_id = cl.id AND vi.estado_reclamo_cliente IN ('ABIERTO','EN_REVISION'))
    FROM clientes cl
    WHERE cl.id = p_cliente_id;
$$;

CREATE OR REPLACE FUNCTION fn_proximos_mantenimientos(p_dias integer DEFAULT 30)
RETURNS TABLE(mantenimiento_id bigint, placa varchar, fecha date, proximo_servicio date, dias_restantes integer)
LANGUAGE sql STABLE AS $$
    SELECT m.id, v.placa, m.fecha, m.proximo_servicio,
           (m.proximo_servicio - current_date)::integer
    FROM mantenimientos m
    JOIN vehiculos v ON v.id = m.vehiculo_id
    WHERE m.proximo_servicio BETWEEN current_date AND current_date + GREATEST(p_dias, 0)
    ORDER BY m.proximo_servicio, v.placa;
$$;

CREATE OR REPLACE FUNCTION fn_historial_viaje(p_viaje_id bigint)
RETURNS TABLE(fecha_hora timestamp, evento text, ubicacion text, observacion text)
LANGUAGE sql STABLE AS $$
    SELECT vi.fecha_salida, 'PROGRAMACION', vi.origen, vi.observaciones
    FROM viajes vi WHERE vi.id = p_viaje_id
    UNION ALL
    SELECT se.fecha_hora, se.evento, se.ubicacion, se.observacion
    FROM seguimiento_eventos se WHERE se.viaje_id = p_viaje_id
    ORDER BY 1;
$$;

-- Cursor explicito. Debe consumirse dentro de una transaccion:
-- BEGIN; SELECT fn_abrir_viajes_pendientes('cur_viajes');
-- FETCH ALL FROM cur_viajes; COMMIT;
CREATE OR REPLACE FUNCTION fn_abrir_viajes_pendientes(p_cursor refcursor DEFAULT 'cur_viajes')
RETURNS refcursor
LANGUAGE plpgsql AS $$
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

-- ---------------------------------------------------------------------------
-- DISPARADORES DE INTEGRIDAD
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION trg_validar_carga() RETURNS trigger
LANGUAGE plpgsql AS $$
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

DROP TRIGGER IF EXISTS trg_validar_carga ON cargas;
CREATE TRIGGER trg_validar_carga
BEFORE INSERT OR UPDATE OF peso, origen, destino ON cargas
FOR EACH ROW EXECUTE FUNCTION trg_validar_carga();

CREATE OR REPLACE FUNCTION trg_preparar_mantenimiento() RETURNS trigger
LANGUAGE plpgsql AS $$
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

DROP TRIGGER IF EXISTS trg_preparar_mantenimiento ON mantenimientos;
CREATE TRIGGER trg_preparar_mantenimiento
BEFORE INSERT OR UPDATE OF tipo, fecha, kilometraje, costo, proximo_servicio ON mantenimientos
FOR EACH ROW EXECUTE FUNCTION trg_preparar_mantenimiento();

CREATE OR REPLACE FUNCTION trg_validar_evento_seguimiento() RETURNS trigger
LANGUAGE plpgsql AS $$
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

DROP TRIGGER IF EXISTS trg_validar_evento_seguimiento ON seguimiento_eventos;
CREATE TRIGGER trg_validar_evento_seguimiento
BEFORE INSERT OR UPDATE OF fecha_hora, ubicacion ON seguimiento_eventos
FOR EACH ROW EXECUTE FUNCTION trg_validar_evento_seguimiento();

-- ---------------------------------------------------------------------------
-- 10 PROCEDIMIENTOS TRANSACCIONALES
-- ---------------------------------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_registrar_cliente(
    p_nombre varchar, p_identificacion varchar, p_telefono varchar,
    p_correo varchar, p_direccion varchar)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_registrar_carga(
    p_cliente_id bigint, p_descripcion varchar, p_tipo varchar, p_peso integer,
    p_origen varchar, p_destino varchar, p_observaciones text DEFAULT NULL)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_asignar_carga_viaje(
    p_carga_id bigint, p_vehiculo_id bigint, p_conductor_id bigint, p_fecha_salida timestamp)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_iniciar_viaje(p_viaje_id bigint, p_ubicacion varchar)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_registrar_evento_seguimiento(
    p_viaje_id bigint, p_evento varchar, p_ubicacion varchar, p_observacion text DEFAULT NULL)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_confirmar_entrega(
    p_viaje_id bigint, p_confirmado_por varchar, p_observacion text DEFAULT NULL)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_validar_entrega(
    p_viaje_id bigint, p_validado_por varchar, p_observacion text DEFAULT NULL)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_registrar_mantenimiento(
    p_vehiculo_id bigint, p_tipo varchar, p_fecha date, p_kilometraje integer,
    p_costo numeric, p_descripcion varchar)
LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE PROCEDURE sp_cancelar_viaje(p_viaje_id bigint, p_motivo text)
LANGUAGE plpgsql AS $$
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

-- PostgreSQL no permite cambiar los nombres de parametros con CREATE OR
-- REPLACE. Se elimina primero para poder sustituir una version academica
-- anterior que pudiera existir en la base local.
DROP PROCEDURE IF EXISTS sp_reasignar_viaje(bigint, varchar, varchar);
CREATE PROCEDURE sp_reasignar_viaje(
    p_viaje_id bigint, p_nueva_placa varchar, p_nueva_identificacion_conductor varchar)
LANGUAGE plpgsql AS $$
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

COMMENT ON FUNCTION fn_abrir_viajes_pendientes(refcursor) IS 'Ejemplo de cursor explicito para consultar viajes activos.';
COMMENT ON PROCEDURE sp_reasignar_viaje(bigint,varchar,varchar) IS 'Usa FOR UPDATE para evitar reasignaciones concurrentes inconsistentes.';
