-- ============================================================================
-- Sincroniza vehiculos.estado y conductores.estado con la realidad de si
-- tienen o no un viaje activo (Programado/En Curso) asignado ahora mismo.
--
-- Causa raiz: el estado de vehiculos/conductores en 04_carga_millon.sql se
-- sembro al azar (independiente de que viaje los usa), y ViajeService solo
-- sincroniza este campo al crear/editar/finalizar un viaje a traves de la
-- API - nunca hubo una limpieza de fondo, asi que la enorme mayoria de los
-- "En Ruta" en Vehiculos/Conductores/Dashboard eran ruido de la siembra,
-- sin relacion con ningun viaje real. Se detecto al intentar crear un viaje
-- nuevo en la demo: el conductor Luis Herrera no aparecia en el buscador
-- (filtra por estado=Disponible) pese a estar libre en la practica.
--
-- Tambien expuso un bug real en el codigo (ya corregido en
-- ViajeService.eliminar(), commit aparte): borrar un viaje Programado/En
-- Curso nunca liberaba su vehiculo/conductor, dejandolos en EN_RUTA para
-- siempre. Este script limpia el arrastre de ambos problemas de una vez.
--
-- Dos direcciones:
-- 1) EN_RUTA sin ningun viaje activo real -> DISPONIBLE.
-- 2) Con viaje activo real pero NO marcado EN_RUTA -> EN_RUTA (los casos
--    reales encontrados estaban en DISPONIBLE, nunca en Mantenimiento/
--    Fuera de Servicio/Descanso/Inactivo - no se toca ningun otro estado).
--
-- Seguro de correr mas de una vez.
-- ============================================================================

BEGIN;

UPDATE vehiculos v
SET estado = 'DISPONIBLE'
WHERE v.estado = 'EN_RUTA'
  AND NOT EXISTS (
    SELECT 1 FROM viajes vi WHERE vi.vehiculo_id = v.id AND vi.estado IN ('PROGRAMADO', 'EN_CURSO')
  );

UPDATE vehiculos v
SET estado = 'EN_RUTA'
WHERE v.estado = 'DISPONIBLE'
  AND EXISTS (
    SELECT 1 FROM viajes vi WHERE vi.vehiculo_id = v.id AND vi.estado IN ('PROGRAMADO', 'EN_CURSO')
  );

UPDATE conductores c
SET estado = 'DISPONIBLE'
WHERE c.estado = 'EN_RUTA'
  AND NOT EXISTS (
    SELECT 1 FROM viajes vi WHERE vi.conductor_id = c.id AND vi.estado IN ('PROGRAMADO', 'EN_CURSO')
  );

UPDATE conductores c
SET estado = 'EN_RUTA'
WHERE c.estado = 'DISPONIBLE'
  AND EXISTS (
    SELECT 1 FROM viajes vi WHERE vi.conductor_id = c.id AND vi.estado IN ('PROGRAMADO', 'EN_CURSO')
  );

COMMIT;
