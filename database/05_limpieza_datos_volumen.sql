-- ============================================================================
-- Limpieza de los datos "de prueba de volumen" sembrados por
-- 04_carga_millon.sql. Corrige tres problemas detectados usando la app:
--
-- 1) Fechas "horribles": las 250.000 fechas de salida de viajes estaban
--    todas apretadas entre enero y junio de 2024 (mas de 2 años en el
--    pasado respecto a hoy) - un monton de viajes "Programado"/"En Curso"
--    con una fecha que ya paso hace años.
-- 2) Viajes activos con fecha vencida: si la fecha de salida ya paso hace
--    mucho, no tiene sentido que el viaje siga "Programado" o "En Curso"
--    indefinidamente - se cierran como Finalizado. Esto ademas resuelve de
--    raiz el problema de "esta carga/vehiculo/conductor ya esta asignado a
--    otro viaje activo" documentado en NOTA_CONFLICTOS_VIAJES.md: al no
--    quedar mas viajes duplicados en estado activo, ese choque desaparece.
-- 3) Ruta que no existe: origen/destino eran literales "SYN-ORIGEN-N" sin
--    relacion con ninguna coordenada real, y las 250.000 filas compartian
--    EXACTAMENTE la misma ruta (mismo segmento fijo para todas). El mapa
--    de Seguimiento dibujaba esa misma linea sin importar que ciudades
--    decia el viaje.
--
-- Seguro de correr mas de una vez (todo se recalcula desde el estado
-- actual de la fila, no acumula sobre corridas anteriores).
-- ============================================================================

BEGIN;

-- 1) Fechas de salida: se reparten proporcionalmente (se conserva el orden
--    relativo original) en una ventana de los ultimos ~13 meses, terminando
--    30 dias atras - asi todo lo que se pase a Finalizado mas abajo tiene
--    una fecha de salida ya vencida, consistente con su estado.
WITH rango AS (
  SELECT min(fecha_salida) AS lo, max(fecha_salida) AS hi
  FROM viajes
  WHERE observaciones = 'Registro generado para prueba de volumen'
)
UPDATE viajes v
SET fecha_salida = (CURRENT_DATE - INTERVAL '395 days')
    + (v.fecha_salida - r.lo)
      * (EXTRACT(EPOCH FROM ((CURRENT_DATE - INTERVAL '30 days') - (CURRENT_DATE - INTERVAL '395 days')))
         / EXTRACT(EPOCH FROM (r.hi - r.lo)))
FROM rango r
WHERE v.observaciones = 'Registro generado para prueba de volumen';

-- 2) Estado: un viaje "Programado" o "En Curso" con fecha de salida de
--    hace mas de un mes ya paso - se cierra como Finalizado. Cancelado se
--    deja igual (es un estado terminal valido, tener una fecha vieja no lo
--    contradice).
UPDATE viajes
SET estado = 'FINALIZADO'
WHERE observaciones = 'Registro generado para prueba de volumen'
  AND estado IN ('PROGRAMADO', 'EN_CURSO');

-- 3) Cargas: se sincroniza el estado de la carga con el viaje que quedo
--    Finalizado en el paso anterior, para no dejar una carga marcada
--    "Pendiente" enganchada a un viaje que ya dice "Finalizado".
UPDATE cargas c
SET estado = 'ENTREGADA'
WHERE c.estado <> 'ENTREGADA'
  AND EXISTS (
    SELECT 1 FROM viajes v
    WHERE v.carga_id = c.id
      AND v.estado = 'FINALIZADO'
      AND v.observaciones = 'Registro generado para prueba de volumen'
  );

-- 4) Ruta: se reemplaza el origen/destino ficticio por ciudades reales de
--    Ecuador (mismo catalogo que ya usan las Cargas) con sus coordenadas
--    reales. El "path" (trazo dibujado en el mapa) se deja en NULL a
--    proposito: el frontend ya sabe calcular una ruta real via OSRM cuando
--    abre el mapa de Seguimiento y no encuentra un path guardado (ver
--    trailersysGetRoute en seguimiento.js) - mejor que inventar aqui una
--    linea recta.
WITH numerados AS (
  SELECT id, row_number() OVER (ORDER BY id) - 1 AS idx
  FROM viajes
  WHERE observaciones = 'Registro generado para prueba de volumen'
),
ciudades_origen(pos, nombre, lat, lng) AS (
  VALUES
    (0,'Guayaquil',-2.1894,-79.8891), (1,'Quito',-0.1807,-78.4678),
    (2,'Cuenca',-2.9006,-79.0045),    (3,'Manta',-0.9677,-80.7089),
    (4,'Machala',-3.2581,-79.9553),   (5,'Ambato',-1.2543,-78.6229),
    (6,'Santo Domingo',-0.2528,-79.1750), (7,'Loja',-3.9931,-79.2042),
    (8,'Quevedo',-1.0225,-79.4614),   (9,'Esmeraldas',0.9682,-79.6517)
),
ciudades_destino(pos, nombre, lat, lng) AS (
  VALUES
    (0,'Quito',-0.1807,-78.4678),     (1,'Guayaquil',-2.1894,-79.8891),
    (2,'Loja',-3.9931,-79.2042),      (3,'Cuenca',-2.9006,-79.0045),
    (4,'Manta',-0.9677,-80.7089),     (5,'Riobamba',-1.6636,-78.6546),
    (6,'Ibarra',0.3517,-78.1223),     (7,'Machala',-3.2581,-79.9553),
    (8,'Ambato',-1.2543,-78.6229),    (9,'Portoviejo',-1.0546,-80.4525)
)
UPDATE viajes v
SET origen = co.nombre,
    destino = cd.nombre,
    ruta_origen_lat = co.lat, ruta_origen_lng = co.lng,
    ruta_destino_lat = cd.lat, ruta_destino_lng = cd.lng,
    ruta_path = NULL
FROM numerados n
JOIN ciudades_origen co ON co.pos = (n.idx % 10)
JOIN ciudades_destino cd ON cd.pos = ((n.idx * 7) % 10)
WHERE v.id = n.id;

-- 4b) Distancia/duracion reales via formula de Haversine, ahora que las
--     coordenadas son de ciudades reales (se asume ~70 km/h de promedio en
--     carretera para estimar la duracion, ya que no hay ruteo real aqui).
UPDATE viajes
SET ruta_distancia_km = ROUND((6371 * acos(
        LEAST(1.0, GREATEST(-1.0,
          cos(radians(ruta_origen_lat)) * cos(radians(ruta_destino_lat)) *
          cos(radians(ruta_destino_lng) - radians(ruta_origen_lng)) +
          sin(radians(ruta_origen_lat)) * sin(radians(ruta_destino_lat))
        ))
      ))::numeric, 1),
    ruta_duracion_min = ROUND((6371 * acos(
        LEAST(1.0, GREATEST(-1.0,
          cos(radians(ruta_origen_lat)) * cos(radians(ruta_destino_lat)) *
          cos(radians(ruta_destino_lng) - radians(ruta_origen_lng)) +
          sin(radians(ruta_origen_lat)) * sin(radians(ruta_destino_lat))
        ))
      ) / 70.0 * 60)::numeric, 1)
WHERE observaciones = 'Registro generado para prueba de volumen';

-- 5) Seguimiento: la fecha de cada evento no tenia relacion con la fecha de
--    salida de SU PROPIO viaje (se generaron con un generate_series
--    independiente) - un evento podia quedar registrado años antes o
--    despues de que el viaje siquiera existiera. Se reancla cada evento a
--    la (ya corregida) fecha de salida de su viaje, conservando el orden
--    en que ya estaban (por id), separados 40 minutos entre si.
WITH ordenados AS (
  SELECT se.id AS evento_id, se.viaje_id,
         row_number() OVER (PARTITION BY se.viaje_id ORDER BY se.id) AS rn
  FROM seguimiento_eventos se
  WHERE se.observacion = 'Registro generado para prueba de volumen'
)
UPDATE seguimiento_eventos se
SET fecha_hora = v.fecha_salida + (o.rn - 1) * INTERVAL '40 minutes'
FROM ordenados o
JOIN viajes v ON v.id = o.viaje_id
WHERE se.id = o.evento_id;

COMMIT;
