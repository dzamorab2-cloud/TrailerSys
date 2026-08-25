-- Genera 1 050 000 filas sinteticas distribuidas entre las tablas operativas.
-- Es idempotente: si existe la marca SYN-CLI-000001 no vuelve a cargar datos.
-- Ejecutar como propietario. Los triggers de FK permanecen activos; solo se
-- suspenden temporalmente los triggers USER de auditoria para no duplicar el
-- volumen ni distorsionar la medicion.
\timing on
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM clientes WHERE identificacion = 'SYN-CLI-000001') THEN
        RAISE EXCEPTION 'La carga sintetica ya existe. No se insertaron duplicados.';
    END IF;
END $$;

ALTER TABLE usuarios DISABLE TRIGGER USER;
ALTER TABLE vehiculos DISABLE TRIGGER USER;
ALTER TABLE conductores DISABLE TRIGGER USER;
ALTER TABLE clientes DISABLE TRIGGER USER;
ALTER TABLE cargas DISABLE TRIGGER USER;
ALTER TABLE viajes DISABLE TRIGGER USER;
ALTER TABLE seguimiento_eventos DISABLE TRIGGER USER;
ALTER TABLE mantenimientos DISABLE TRIGGER USER;

INSERT INTO clientes (nombre, identificacion, estado, telefono, correo, direccion, servicios, observaciones)
SELECT 'Cliente sintético ' || g,
       'SYN-CLI-' || lpad(g::text, 6, '0'),
       CASE WHEN g % 20 = 0 THEN 'INACTIVO' ELSE 'ACTIVO' END,
       '09' || lpad((g % 100000000)::text, 8, '0'),
       'cliente' || g || '@carga.test',
       'Dirección sintética ' || g,
       CASE WHEN g % 2 = 0 THEN 'Carga seca' ELSE 'Refrigerados' END,
       'Registro generado para prueba de volumen'
FROM generate_series(1, 50000) AS g;

INSERT INTO vehiculos (placa, marca, modelo, tipo, anio, color, estado, kilometraje, capacidad, observaciones, foto)
SELECT 'SYN-' || lpad(g::text, 6, '0'), 'Marca ' || (g % 20), 'Modelo ' || (g % 100),
       CASE WHEN g % 3 = 0 THEN 'Tráiler' ELSE 'Camión' END,
       2015 + (g % 11), CASE WHEN g % 2 = 0 THEN 'Blanco' ELSE 'Azul' END,
       CASE WHEN g % 10 = 0 THEN 'MANTENIMIENTO' WHEN g % 3 = 0 THEN 'EN_RUTA' ELSE 'DISPONIBLE' END,
       10000 + (g % 490000), 5000 + (g % 30000),
       'Registro generado para prueba de volumen', NULL
FROM generate_series(1, 50000) AS g;

INSERT INTO conductores (nombres, identificacion, telefono, correo, licencia_numero,
                         licencia_categoria, licencia_vencimiento, estado, vehiculo_id, observaciones, foto)
SELECT 'Conductor sintético ' || g, 'SYN-CON-' || lpad(g::text, 6, '0'),
       '08' || lpad((g % 100000000)::text, 8, '0'), 'conductor' || g || '@carga.test',
       'SYN-LIC-' || lpad(g::text, 6, '0'), 'Tipo E', DATE '2027-01-01' + (g % 1460),
       CASE WHEN g % 5 = 0 THEN 'EN_RUTA' ELSE 'DISPONIBLE' END,
       NULL, 'Registro generado para prueba de volumen', NULL
FROM generate_series(1, 50000) AS g;

CREATE TEMP TABLE mapa_clientes ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM clientes WHERE identificacion LIKE 'SYN-CLI-%';
CREATE TEMP TABLE mapa_vehiculos ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM vehiculos WHERE placa LIKE 'SYN-%';
CREATE TEMP TABLE mapa_conductores ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM conductores WHERE identificacion LIKE 'SYN-CON-%';
CREATE UNIQUE INDEX ON mapa_clientes (rn);
CREATE UNIQUE INDEX ON mapa_vehiculos (rn);
CREATE UNIQUE INDEX ON mapa_conductores (rn);

INSERT INTO cargas (descripcion, cliente_id, tipo, peso, origen, destino, estado, observaciones)
SELECT 'Carga sintética ' || g, c.id,
       CASE WHEN g % 3 = 0 THEN 'Refrigerados' ELSE 'Carga seca' END,
       500 + (g % 29500), 'Origen sintético ' || (g % 100),
       'Destino sintético ' || (g % 100),
       CASE WHEN g % 5 = 0 THEN 'ENTREGADA' WHEN g % 3 = 0 THEN 'EN_TRANSITO' ELSE 'PENDIENTE' END,
       'Registro generado para prueba de volumen'
FROM generate_series(1, 150000) AS g
JOIN mapa_clientes c ON c.rn = ((g - 1) % 50000) + 1;

CREATE TEMP TABLE mapa_cargas ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM cargas WHERE descripcion LIKE 'Carga sintética %';
CREATE UNIQUE INDEX ON mapa_cargas (rn);

INSERT INTO viajes (vehiculo_id, conductor_id, cliente_id, carga_id, origen, destino,
                    fecha_salida, estado, observaciones, entrega_confirmada,
                    entrega_validada, paradas_simuladas_registradas)
SELECT v.id, co.id, cl.id, ca.id,
       'SYN-ORIGEN-' || (g % 100), 'SYN-DESTINO-' || (g % 100),
       TIMESTAMP '2024-01-01 00:00:00' + (g || ' minutes')::interval,
       CASE WHEN g % 10 = 0 THEN 'CANCELADO' WHEN g % 4 = 0 THEN 'FINALIZADO'
            WHEN g % 3 = 0 THEN 'EN_CURSO' ELSE 'PROGRAMADO' END,
       'Registro generado para prueba de volumen', false, false, 0
FROM generate_series(1, 250000) AS g
JOIN mapa_vehiculos v ON v.rn = ((g - 1) % 50000) + 1
JOIN mapa_conductores co ON co.rn = ((g - 1) % 50000) + 1
JOIN mapa_clientes cl ON cl.rn = ((g - 1) % 50000) + 1
JOIN mapa_cargas ca ON ca.rn = ((g - 1) % 150000) + 1;

CREATE TEMP TABLE mapa_viajes ON COMMIT DROP AS
SELECT id, vehiculo_id, row_number() OVER (ORDER BY id) AS rn FROM viajes WHERE origen LIKE 'SYN-ORIGEN-%';
CREATE UNIQUE INDEX ON mapa_viajes (rn);

INSERT INTO seguimiento_eventos (viaje_id, vehiculo_id, fecha_hora, evento, ubicacion, observacion)
SELECT vi.id, vi.vehiculo_id,
       TIMESTAMP '2024-01-01 00:00:00' + (g || ' minutes')::interval,
       CASE WHEN g % 20 = 0 THEN 'INCIDENTE' WHEN g % 5 = 0 THEN 'PARADA' ELSE 'SALIDA' END,
       'SYN-UBICACION-' || (g % 500), 'Registro generado para prueba de volumen'
FROM generate_series(1, 400000) AS g
JOIN mapa_viajes vi ON vi.rn = ((g - 1) % 250000) + 1;

INSERT INTO mantenimientos (vehiculo_id, tipo, fecha, kilometraje, costo, proximo_servicio, descripcion)
SELECT v.id, CASE WHEN g % 3 = 0 THEN 'CORRECTIVO' ELSE 'PREVENTIVO' END,
       DATE '2024-01-01' + (g % 1000), 10000 + (g % 490000),
       50.0 + (g % 5000), DATE '2024-01-01' + (g % 1000) + 90,
       'Mantenimiento sintético ' || g
FROM generate_series(1, 100000) AS g
JOIN mapa_vehiculos v ON v.rn = ((g - 1) % 50000) + 1;

ALTER TABLE usuarios ENABLE TRIGGER USER;
ALTER TABLE vehiculos ENABLE TRIGGER USER;
ALTER TABLE conductores ENABLE TRIGGER USER;
ALTER TABLE clientes ENABLE TRIGGER USER;
ALTER TABLE cargas ENABLE TRIGGER USER;
ALTER TABLE viajes ENABLE TRIGGER USER;
ALTER TABLE seguimiento_eventos ENABLE TRIGGER USER;
ALTER TABLE mantenimientos ENABLE TRIGGER USER;
COMMIT;

VACUUM (ANALYZE);

SELECT 'auditoria' tabla, count(*) registros FROM auditoria UNION ALL
SELECT 'cargas', count(*) FROM cargas UNION ALL
SELECT 'clientes', count(*) FROM clientes UNION ALL
SELECT 'conductores', count(*) FROM conductores UNION ALL
SELECT 'mantenimientos', count(*) FROM mantenimientos UNION ALL
SELECT 'seguimiento_eventos', count(*) FROM seguimiento_eventos UNION ALL
SELECT 'usuarios', count(*) FROM usuarios UNION ALL
SELECT 'vehiculos', count(*) FROM vehiculos UNION ALL
SELECT 'viajes', count(*) FROM viajes ORDER BY tabla;

SELECT sum(n_live_tup)::bigint AS total_estimado
FROM pg_stat_user_tables
WHERE relname IN ('auditoria','cargas','clientes','conductores','mantenimientos',
                  'seguimiento_eventos','usuarios','vehiculos','viajes');
