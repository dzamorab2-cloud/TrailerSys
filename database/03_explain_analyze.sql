-- Ejecuta SELECT reales y muestra tiempos/bloques; no modifica filas.
ANALYZE;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM seguimiento_eventos WHERE viaje_id = 1 ORDER BY fecha_hora DESC;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM mantenimientos WHERE vehiculo_id = 1 ORDER BY fecha DESC;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM viajes WHERE estado = 'EN_CURSO' ORDER BY fecha_salida DESC;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM usuarios WHERE upper(username) = upper('admin');
