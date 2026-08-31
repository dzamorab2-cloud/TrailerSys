-- ============================================================================
-- Corrige dos problemas detectados en el panel "Alertas operativas" de
-- Seguimiento despues de la limpieza de 05_limpieza_datos_volumen.sql:
--
-- 1) Las 100 alertas de mantenimiento vencido mostraban TODAS la misma
--    fecha ("venció el 2024-01-01"). Causa: SeguimientoService.obtenerAlertas()
--    toma "los 100 mantenimientos mas vencidos ordenados por proximo_servicio
--    ascendente", y exactamente 100 de los 100.000 registros sinteticos
--    (04_carga_millon.sql) compartian ese mismo valor minimo como "piso" de
--    la formula original - asi que ESOS 100 eran siempre los unicos que
--    entraban en el limite, todos con la misma fecha.
-- 2) No aparecia ninguna alerta de "viaje Programado atrasado" ni "En Curso"
--    porque 05_limpieza_datos_volumen.sql paso TODOS los viajes sinteticos
--    activos a Finalizado (correcto para las fechas de 2024 que tenian),
--    pero como efecto secundario dejo el sistema sin ningun viaje Programado
--    o En Curso real - ni para esas alertas ni para probar el seguimiento
--    "en tiempo real" (que necesita un viaje En Curso para interpolar la
--    posicion sobre su ruta).
--
-- Seguro de correr mas de una vez.
-- ============================================================================

BEGIN;

-- 1) Mantenimientos: se reparte proximo_servicio en una ventana realista
--    (200 dias atras a 60 dias adelante), conservando el orden relativo
--    original - deja una mezcla de vencidos (la mayoria, como es de
--    esperar en una flota con mantenimiento atrasado) y proximos dentro
--    de los siguientes dias, en vez de 100.000 filas apiladas en un
--    puñado de fechas identicas. "fecha" (cuando se hizo el servicio
--    anterior) se recalcula 30 dias antes del nuevo proximo_servicio,
--    igual que el espaciado original.
WITH rango AS (
  SELECT min(proximo_servicio) AS lo, max(proximo_servicio) AS hi
  FROM mantenimientos
  WHERE descripcion LIKE 'Mantenimiento sint%'
),
nuevos AS (
  SELECT m.id,
         (CURRENT_DATE - 200) + ROUND(
           (m.proximo_servicio - r.lo)::numeric * 260 / GREATEST(1, (r.hi - r.lo))
         )::int AS nuevo_proximo
  FROM mantenimientos m, rango r
  WHERE m.descripcion LIKE 'Mantenimiento sint%'
)
UPDATE mantenimientos m
SET proximo_servicio = n.nuevo_proximo,
    fecha = n.nuevo_proximo - 30
FROM nuevos n
WHERE m.id = n.id;

-- 2a) Revive ~60 viajes sinteticos (ya Finalizado) como PROGRAMADO, con
--     salida entre 1 y 14 dias en el futuro - para "Próximos viajes" del
--     Dashboard y la variedad de estados que se pidio ver. Se eligen del
--     grupo de mayor id (los ultimos 3.000 insertados) para que aparezcan
--     en la primera pagina de las listas (ordenadas por id descendente).
WITH pool AS (
  SELECT id FROM viajes
  WHERE observaciones = 'Registro generado para prueba de volumen' AND estado = 'FINALIZADO'
  ORDER BY id DESC LIMIT 3000
),
elegidos AS (
  SELECT id FROM pool ORDER BY random() LIMIT 60
)
UPDATE viajes v
SET estado = 'PROGRAMADO',
    fecha_salida = CURRENT_TIMESTAMP
      + (1 + floor(random() * 14)) * INTERVAL '1 day'
      + floor(random() * 24) * INTERVAL '1 hour'
FROM elegidos e
WHERE v.id = e.id;

-- 2b) Revive otros ~60 como EN_CURSO, con salida entre "ahora mismo" y
--     70% de su duracion estimada atras - para que el mapa de Seguimiento
--     tenga viajes reales sobre los que interpolar una posicion "en
--     tiempo real" (todavia no llegan segun su propia duracion estimada).
WITH pool AS (
  SELECT id FROM viajes
  WHERE observaciones = 'Registro generado para prueba de volumen' AND estado = 'FINALIZADO'
  ORDER BY id DESC LIMIT 3000
),
elegidos AS (
  SELECT id FROM pool ORDER BY random() LIMIT 60
)
UPDATE viajes v
SET estado = 'EN_CURSO',
    fecha_salida = CURRENT_TIMESTAMP
      - (random() * COALESCE(v.ruta_duracion_min, 120) * 0.7) * INTERVAL '1 minute'
FROM elegidos e
WHERE v.id = e.id;

-- 2c) Sincroniza el estado de la carga ligada a los viajes revividos
--     (estaba en ENTREGADA por el paso anterior; ya no corresponde si el
--     viaje volvio a estar Programado o En Curso).
UPDATE cargas c
SET estado = CASE
    WHEN EXISTS (SELECT 1 FROM viajes v WHERE v.carga_id = c.id AND v.estado = 'EN_CURSO') THEN 'EN_TRANSITO'
    ELSE 'ASIGNADA'
  END
WHERE EXISTS (
  SELECT 1 FROM viajes v
  WHERE v.carga_id = c.id AND v.estado IN ('PROGRAMADO', 'EN_CURSO')
);

-- 2d) Salvaguarda: cada vehiculo/conductor/carga se reutiliza varias veces
--     en el set sintetico, asi que hay una probabilidad baja pero real de
--     que dos de los viajes revividos arriba hayan quedado compitiendo por
--     el mismo recurso (el mismo choque que ya se resolvio para el resto
--     del dataset en 05_limpieza_datos_volumen.sql). Se deja activo solo
--     el mas reciente de cada grupo y se finaliza el resto - misma regla
--     que ya aplica el backend al crear/editar un viaje.
WITH dup_vehiculo AS (
  SELECT id, row_number() OVER (PARTITION BY vehiculo_id ORDER BY fecha_salida DESC) AS rn
  FROM viajes WHERE estado IN ('PROGRAMADO', 'EN_CURSO')
),
dup_conductor AS (
  SELECT id, row_number() OVER (PARTITION BY conductor_id ORDER BY fecha_salida DESC) AS rn
  FROM viajes WHERE estado IN ('PROGRAMADO', 'EN_CURSO')
),
dup_carga AS (
  SELECT id, row_number() OVER (PARTITION BY carga_id ORDER BY fecha_salida DESC) AS rn
  FROM viajes WHERE estado IN ('PROGRAMADO', 'EN_CURSO') AND carga_id IS NOT NULL
)
UPDATE viajes
SET estado = 'FINALIZADO'
WHERE id IN (
  SELECT id FROM dup_vehiculo WHERE rn > 1
  UNION
  SELECT id FROM dup_conductor WHERE rn > 1
  UNION
  SELECT id FROM dup_carga WHERE rn > 1
);

COMMIT;
