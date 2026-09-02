-- TrailerSys: vistas operativas y restricciones de integridad.
-- Requisitos: tablas creadas por Hibernate y objetos de 08 instalados.
-- Las restricciones se crean NOT VALID: protegen todas las escrituras nuevas
-- sin bloquear la instalacion si existen datos masivos antiguos por depurar.

-- ---------------------------------------------------------------------------
-- VISTAS
-- ---------------------------------------------------------------------------

CREATE OR REPLACE VIEW vw_viajes_detallados AS
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
       coalesce(vi.entrega_confirmada_cliente, false) AS recepcion_cliente,
       vi.estado_reclamo_cliente
FROM viajes vi
JOIN vehiculos ve ON ve.id = vi.vehiculo_id
JOIN conductores co ON co.id = vi.conductor_id
JOIN clientes cl ON cl.id = vi.cliente_id
LEFT JOIN cargas ca ON ca.id = vi.carga_id;

CREATE OR REPLACE VIEW vw_disponibilidad_flota AS
SELECT 'VEHICULO'::text AS recurso,
       v.id AS recurso_id,
       v.placa::text AS codigo,
       concat_ws(' ', v.marca, v.modelo) AS nombre,
       v.estado::text AS estado,
       CASE
         WHEN v.estado = 'DISPONIBLE' THEN 'LISTO PARA ASIGNAR'
         WHEN v.estado = 'EN_RUTA' THEN 'OCUPADO'
         WHEN v.estado = 'MANTENIMIENTO' THEN 'NO DISPONIBLE'
         ELSE 'FUERA DE SERVICIO'
       END AS disponibilidad
FROM vehiculos v
UNION ALL
SELECT 'CONDUCTOR'::text,
       c.id,
       c.identificacion::text,
       c.nombres::text,
       c.estado::text,
       CASE
         WHEN c.licencia_vencimiento < current_date THEN 'DOCUMENTO VENCIDO'
         WHEN c.estado = 'DISPONIBLE' THEN 'LISTO PARA ASIGNAR'
         WHEN c.estado = 'EN_RUTA' THEN 'OCUPADO'
         ELSE 'NO DISPONIBLE'
       END
FROM conductores c;

CREATE OR REPLACE VIEW vw_mantenimientos_proximos AS
SELECT DISTINCT ON (v.id)
       v.id AS vehiculo_id,
       v.placa,
       v.marca,
       v.modelo,
       v.estado AS estado_vehiculo,
       m.id AS mantenimiento_id,
       m.tipo,
       m.fecha AS ultimo_mantenimiento,
       m.proximo_servicio,
       m.proximo_servicio - current_date AS dias_restantes,
       m.costo,
       CASE
         WHEN m.proximo_servicio IS NULL THEN 'SIN PROGRAMAR'
         WHEN m.proximo_servicio < current_date THEN 'VENCIDO'
         WHEN m.proximo_servicio <= current_date + 7 THEN 'URGENTE'
         WHEN m.proximo_servicio <= current_date + 30 THEN 'PROXIMO'
         ELSE 'AL DIA'
       END AS alerta
FROM vehiculos v
LEFT JOIN mantenimientos m ON m.vehiculo_id = v.id
ORDER BY v.id, m.fecha DESC NULLS LAST, m.id DESC NULLS LAST;

CREATE OR REPLACE VIEW vw_reclamos_clientes AS
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
FROM viajes vi
JOIN clientes cl ON cl.id = vi.cliente_id
LEFT JOIN cargas ca ON ca.id = vi.carga_id
WHERE vi.estado_reclamo_cliente IS NOT NULL;

COMMENT ON VIEW vw_viajes_detallados IS 'Vista integral de viajes, recursos, cliente, carga y entrega.';
COMMENT ON VIEW vw_disponibilidad_flota IS 'Disponibilidad unificada de vehiculos y conductores.';
COMMENT ON VIEW vw_mantenimientos_proximos IS 'Ultimo mantenimiento y alerta calculada por vehiculo.';
COMMENT ON VIEW vw_reclamos_clientes IS 'Reclamos y novedades informados por los clientes.';

-- ---------------------------------------------------------------------------
-- RESTRICCIONES CHECK
-- ---------------------------------------------------------------------------

ALTER TABLE cargas DROP CONSTRAINT IF EXISTS ck_cargas_peso_positivo;
ALTER TABLE cargas ADD CONSTRAINT ck_cargas_peso_positivo
CHECK (peso > 0) NOT VALID;

ALTER TABLE cargas DROP CONSTRAINT IF EXISTS ck_cargas_ruta_obligatoria;
ALTER TABLE cargas ADD CONSTRAINT ck_cargas_ruta_obligatoria
CHECK (btrim(origen) <> '' AND btrim(destino) <> '') NOT VALID;

ALTER TABLE vehiculos DROP CONSTRAINT IF EXISTS ck_vehiculos_valores_positivos;
ALTER TABLE vehiculos ADD CONSTRAINT ck_vehiculos_valores_positivos
CHECK (kilometraje >= 0 AND capacidad > 0) NOT VALID;

ALTER TABLE vehiculos DROP CONSTRAINT IF EXISTS ck_vehiculos_anio_valido;
ALTER TABLE vehiculos ADD CONSTRAINT ck_vehiculos_anio_valido
CHECK (anio BETWEEN 1980 AND 2100) NOT VALID;

ALTER TABLE mantenimientos DROP CONSTRAINT IF EXISTS ck_mantenimientos_valores_positivos;
ALTER TABLE mantenimientos ADD CONSTRAINT ck_mantenimientos_valores_positivos
CHECK (kilometraje >= 0 AND costo >= 0) NOT VALID;

ALTER TABLE mantenimientos DROP CONSTRAINT IF EXISTS ck_mantenimientos_fechas_coherentes;
ALTER TABLE mantenimientos ADD CONSTRAINT ck_mantenimientos_fechas_coherentes
CHECK (proximo_servicio IS NULL OR proximo_servicio >= fecha) NOT VALID;

ALTER TABLE viajes DROP CONSTRAINT IF EXISTS ck_viajes_ruta_obligatoria;
ALTER TABLE viajes ADD CONSTRAINT ck_viajes_ruta_obligatoria
CHECK (btrim(origen) <> '' AND btrim(destino) <> '') NOT VALID;

-- Diagnostico previo a VALIDATE CONSTRAINT. Cada resultado debe ser cero.
SELECT 'cargas_peso' AS regla, count(*) AS incumplimientos FROM cargas WHERE peso <= 0
UNION ALL SELECT 'cargas_ruta', count(*) FROM cargas WHERE btrim(origen)='' OR btrim(destino)=''
UNION ALL SELECT 'vehiculos_valores', count(*) FROM vehiculos WHERE kilometraje < 0 OR capacidad <= 0
UNION ALL SELECT 'vehiculos_anio', count(*) FROM vehiculos WHERE anio NOT BETWEEN 1980 AND 2100
UNION ALL SELECT 'mantenimientos_valores', count(*) FROM mantenimientos WHERE kilometraje < 0 OR costo < 0
UNION ALL SELECT 'mantenimientos_fechas', count(*) FROM mantenimientos WHERE proximo_servicio < fecha
UNION ALL SELECT 'viajes_ruta', count(*) FROM viajes WHERE btrim(origen)='' OR btrim(destino)='';

-- Valida formalmente cada restriccion solo cuando los datos existentes cumplen.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cargas WHERE peso <= 0) THEN
        ALTER TABLE cargas VALIDATE CONSTRAINT ck_cargas_peso_positivo;
    ELSE RAISE NOTICE 'Pendiente: ck_cargas_peso_positivo tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM cargas WHERE btrim(origen)='' OR btrim(destino)='') THEN
        ALTER TABLE cargas VALIDATE CONSTRAINT ck_cargas_ruta_obligatoria;
    ELSE RAISE NOTICE 'Pendiente: ck_cargas_ruta_obligatoria tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM vehiculos WHERE kilometraje < 0 OR capacidad <= 0) THEN
        ALTER TABLE vehiculos VALIDATE CONSTRAINT ck_vehiculos_valores_positivos;
    ELSE RAISE NOTICE 'Pendiente: ck_vehiculos_valores_positivos tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM vehiculos WHERE anio NOT BETWEEN 1980 AND 2100) THEN
        ALTER TABLE vehiculos VALIDATE CONSTRAINT ck_vehiculos_anio_valido;
    ELSE RAISE NOTICE 'Pendiente: ck_vehiculos_anio_valido tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM mantenimientos WHERE kilometraje < 0 OR costo < 0) THEN
        ALTER TABLE mantenimientos VALIDATE CONSTRAINT ck_mantenimientos_valores_positivos;
    ELSE RAISE NOTICE 'Pendiente: ck_mantenimientos_valores_positivos tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM mantenimientos WHERE proximo_servicio < fecha) THEN
        ALTER TABLE mantenimientos VALIDATE CONSTRAINT ck_mantenimientos_fechas_coherentes;
    ELSE RAISE NOTICE 'Pendiente: ck_mantenimientos_fechas_coherentes tiene datos antiguos invalidos.'; END IF;

    IF NOT EXISTS (SELECT 1 FROM viajes WHERE btrim(origen)='' OR btrim(destino)='') THEN
        ALTER TABLE viajes VALIDATE CONSTRAINT ck_viajes_ruta_obligatoria;
    ELSE RAISE NOTICE 'Pendiente: ck_viajes_ruta_obligatoria tiene datos antiguos invalidos.'; END IF;
END;
$$;
