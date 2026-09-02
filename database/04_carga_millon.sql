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
SELECT (ARRAY['Comercializadora','Distribuidora','Importadora','Exportadora','Logística','Industrias',
              'Agroindustrias','Constructora','Textiles','Alimentos','Metalmecánica','Plásticos',
              'Maderas','Tecnología','Servicios','Repuestos','Bebidas','Empaques','Suministros','Productos'])[(mezcla.n % 20) + 1]
       || ' ' ||
       (ARRAY['Andina','Pacífico','Equatorial','Guayas','Manabí','Sierra','Litoral','Amazonas','Cotopaxi','Pichincha',
              'Imbabura','Esmeraldas','Azuay','Oriente','Galápagos','Quito','Manta','Cuenca','Ambato','Durán'])[((mezcla.n / 20) % 20) + 1]
       || ' ' ||
       (ARRAY['Global','Integral','Nacional','Moderna','Continental','Unida','Premium','Industrial','Comercial','Empresarial',
              'Mayorista','Selecta','Productiva','Estratégica','Dinámica','Consolidada','Regional','Profesional','Avanzada','Sostenible'])[((mezcla.n / 400) % 20) + 1]
       || ' ' ||
       (ARRAY['del Ecuador','del Pacífico','del Litoral','de los Andes','del Guayas','de la Costa','de la Sierra','del Austro',
              'del Norte','del Sur','del Centro','de Manabí','de Pichincha','de Azuay','de Imbabura','de Cotopaxi',
              'de Esmeraldas','de El Oro','de Tungurahua','de Santo Domingo'])[((mezcla.n / 8000) % 20) + 1]
       || ' ' || (ARRAY['S.A.','Cía. Ltda.','S.A.S.','Ltda.'])[((mezcla.n / 2500) % 4) + 1],
       'SYN-CLI-' || lpad(g::text, 6, '0'),
       CASE WHEN g % 20 = 0 THEN 'INACTIVO' ELSE 'ACTIVO' END,
       '09' || lpad((g % 100000000)::text, 8, '0'),
       'contacto' || g || '@clientes.trailersys.test',
       'Av. Principal ' || (100 + (g % 900)) || ' y Calle ' || (1 + (g % 80)) || ', Ecuador',
       CASE WHEN g % 2 = 0 THEN 'Carga seca' ELSE 'Refrigerados' END,
       'Registro generado para prueba de volumen'
FROM generate_series(1, 50000) AS g
CROSS JOIN LATERAL (SELECT ((g * 3571) % 50000) AS n) AS mezcla;

INSERT INTO vehiculos (placa, marca, modelo, tipo, anio, color, estado, kilometraje, capacidad, observaciones, foto)
SELECT 'SYN-' || lpad(g::text, 6, '0'),
       (ARRAY['Freightliner','Kenworth','Peterbilt','Volvo Trucks','Mack','International',
              'Scania','Mercedes-Benz','MAN','DAF','Iveco','Renault Trucks','Western Star',
              'Isuzu','Hino','Fuso','UD Trucks','Sinotruk','Shacman','JAC'])[(g % 20) + 1],
       CASE g % 20
         WHEN 0 THEN (ARRAY['Cascadia 126','Cascadia 116','M2 106'])[(g % 3) + 1]
         WHEN 1 THEN (ARRAY['T680 Next Gen','W900L','T880'])[(g % 3) + 1]
         WHEN 2 THEN (ARRAY['Model 579','Model 389X','Model 567'])[(g % 3) + 1]
         WHEN 3 THEN (ARRAY['VNL 860','VNR 660','VHD 300'])[(g % 3) + 1]
         WHEN 4 THEN (ARRAY['Anthem 70-inch','Pinnacle 64T','Granite 64FR'])[(g % 3) + 1]
         WHEN 5 THEN (ARRAY['LT625','RH613','MV607'])[(g % 3) + 1]
         WHEN 6 THEN (ARRAY['R 500','S 650','G 410'])[(g % 3) + 1]
         WHEN 7 THEN (ARRAY['Actros 2645','Arocs 3345','Atego 1726'])[(g % 3) + 1]
         WHEN 8 THEN (ARRAY['TGX 26.510','TGS 33.480','TGM 18.290'])[(g % 3) + 1]
         WHEN 9 THEN (ARRAY['XF 480','XG 530','CF 450'])[(g % 3) + 1]
         WHEN 10 THEN (ARRAY['S-Way AS440S','Stralis 480','Eurocargo ML180'])[(g % 3) + 1]
         WHEN 11 THEN (ARRAY['T High 520','T 480','C 440'])[(g % 3) + 1]
         WHEN 12 THEN (ARRAY['49X 600','57X 600','47X 500'])[(g % 3) + 1]
         WHEN 13 THEN (ARRAY['FVR 34K','NPR 75L','Giga CYZ'])[(g % 3) + 1]
         WHEN 14 THEN (ARRAY['Dutro 616','500 FC','700 SS'])[(g % 3) + 1]
         WHEN 15 THEN (ARRAY['Canter 815','Fighter 1627','Super Great 6R20'])[(g % 3) + 1]
         WHEN 16 THEN (ARRAY['Quon GW','Croner PKE','Quester GWE'])[(g % 3) + 1]
         WHEN 17 THEN (ARRAY['HOWO T7H 540','HOWO TX 440','HOWO A7 420'])[(g % 3) + 1]
         WHEN 18 THEN (ARRAY['X6000 550','X3000 430','F3000 385'])[(g % 3) + 1]
         ELSE (ARRAY['Gallop K7 540','Gallop K5 420','N90'])[(g % 3) + 1]
       END,
       CASE WHEN g % 3 = 0 THEN 'Tráiler' ELSE 'Camión' END,
       2015 + (g % 11), CASE WHEN g % 2 = 0 THEN 'Blanco' ELSE 'Azul' END,
       CASE WHEN g % 10 = 0 THEN 'MANTENIMIENTO' WHEN g % 3 = 0 THEN 'EN_RUTA' ELSE 'DISPONIBLE' END,
       10000 + (g % 490000), 5000 + (g % 30000),
       'Registro generado para prueba de volumen', NULL
FROM generate_series(1, 50000) AS g;

INSERT INTO conductores (nombres, identificacion, telefono, correo, licencia_numero,
                         licencia_categoria, licencia_vencimiento, estado, vehiculo_id, observaciones, foto)
SELECT (ARRAY['Carlos','Luis','José','Jorge','Miguel','Andrés','Diego','Fernando','Ricardo','Daniel',
              'Santiago','Alejandro','Gabriel','Manuel','David','Marco','Pedro','Juan','Eduardo','Roberto'])[(mezcla.n % 20) + 1]
       || ' ' ||
       (ARRAY['Andrés','Alberto','Antonio','Eduardo','Enrique','Esteban','Felipe','Javier','Leonardo','Mauricio',
              'Nicolás','Patricio','Rafael','Sebastián','Vicente','Xavier','Mateo','Emilio','Adrián','Cristian'])[((mezcla.n / 20) % 20) + 1]
       || ' ' ||
       (ARRAY['García','Rodríguez','Zambrano','Mendoza','Cedeño','Vera','Moreira','Castillo','López','Torres',
              'Paredes','Mora','Sánchez','Ramírez','Guerrero','Ortiz','Navarro','Espinoza','Vargas','Salazar'])[((mezcla.n / 400) % 20) + 1]
       || ' ' ||
       (ARRAY['Macias','Alvarado','Chávez','Delgado','Reyes','Ponce','Carrillo','Cabrera','Suárez','Acosta',
              'Benítez','Córdova','Flores','Herrera','Jaramillo','León','Molina','Pazmiño','Rojas','Valencia'])[((mezcla.n / 8000) % 20) + 1],
       'SYN-CON-' || lpad(g::text, 6, '0'),
       '09' || lpad((g % 100000000)::text, 8, '0'), 'conductor' || g || '@carga.test',
       'SYN-LIC-' || lpad(g::text, 6, '0'), 'Tipo E', DATE '2027-01-01' + (g % 1460),
       CASE WHEN g % 5 = 0 THEN 'EN_RUTA' ELSE 'DISPONIBLE' END,
       NULL, 'Registro generado para prueba de volumen', NULL
FROM generate_series(1, 50000) AS g
CROSS JOIN LATERAL (SELECT ((g * 7919) % 50000) AS n) AS mezcla;

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
SELECT (ARRAY[
           'Banano de exportación', 'Camarón congelado', 'Cacao en grano',
           'Flores frescas', 'Atún en conserva', 'Repuestos automotrices',
           'Materiales de construcción', 'Productos lácteos', 'Arroz pilado',
           'Medicamentos', 'Electrodomésticos', 'Textiles y confecciones',
           'Frutas tropicales', 'Aceite vegetal', 'Alimentos balanceados'
       ])[1 + ((g - 1) % 15)] || ' · Lote ' || lpad(g::text, 6, '0'), c.id,
       (ARRAY[
           'Agrícola', 'Refrigerada', 'Agrícola', 'Refrigerada', 'Alimentos',
           'Automotriz', 'Construcción', 'Refrigerada', 'Alimentos', 'Farmacéutica',
           'Electrodomésticos', 'Textil', 'Refrigerada', 'Alimentos', 'Agroindustrial'
       ])[1 + ((g - 1) % 15)],
       500 + (g % 29500),
       (ARRAY['Guayaquil', 'Quito', 'Cuenca', 'Manta', 'Machala', 'Ambato', 'Santo Domingo', 'Loja', 'Quevedo', 'Esmeraldas'])[1 + ((g - 1) % 10)],
       (ARRAY['Quito', 'Guayaquil', 'Loja', 'Cuenca', 'Manta', 'Riobamba', 'Ibarra', 'Machala', 'Ambato', 'Portoviejo'])[1 + ((g * 7 - 1) % 10)],
       CASE WHEN g % 5 = 0 THEN 'ENTREGADA' WHEN g % 3 = 0 THEN 'EN_TRANSITO' ELSE 'PENDIENTE' END,
       'Mercancía comercial registrada para operación logística nacional'
FROM generate_series(1, 150000) AS g
JOIN mapa_clientes c ON c.rn = ((g - 1) % 50000) + 1;

CREATE TEMP TABLE mapa_cargas ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS rn FROM cargas WHERE observaciones = 'Mercancía comercial registrada para operación logística nacional';
CREATE UNIQUE INDEX ON mapa_cargas (rn);

INSERT INTO viajes (vehiculo_id, conductor_id, cliente_id, carga_id, origen, destino,
                    fecha_salida, estado, observaciones, entrega_confirmada,
                    entrega_validada, paradas_simuladas_registradas, ruta_origen_lat,
                    ruta_origen_lng, ruta_destino_lat, ruta_destino_lng,
                    ruta_distancia_km, ruta_duracion_min, ruta_path)
SELECT v.id, co.id, cl.id, ca.id,
       'SYN-ORIGEN-' || (g % 100), 'SYN-DESTINO-' || (g % 100),
       TIMESTAMP '2024-01-01 00:00:00' + (g || ' minutes')::interval,
       CASE WHEN g % 10 = 0 THEN 'CANCELADO' WHEN g % 4 = 0 THEN 'FINALIZADO'
            WHEN g % 3 = 0 THEN 'EN_CURSO' ELSE 'PROGRAMADO' END,
       'Registro generado para prueba de volumen', false, false, 0,
       -2.1894, -79.8891, -0.2201641, -78.5123274, 424.5, 372.6,
       '[{"lat":-2.1894,"lng":-79.8891},{"lat":-1.2491,"lng":-78.6168},{"lat":-0.2201641,"lng":-78.5123274}]'
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
       CURRENT_DATE - (g % 1000), 10000 + (g % 490000),
       CASE WHEN g % 3 = 0
            THEN 180.0 + (g % 721)
            ELSE 65.0 + (g % 286)
       END,
       (CURRENT_DATE - (g % 1000) + INTERVAL '1 month')::date,
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
