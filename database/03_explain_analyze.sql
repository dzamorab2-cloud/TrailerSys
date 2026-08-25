-- Ejecuta SELECT reales y muestra tiempos/bloques; no modifica filas.
ANALYZE;
\echo 'Seguimiento por viaje y fecha'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM seguimiento_eventos WHERE viaje_id = 1 ORDER BY fecha_hora DESC;
\echo 'Mantenimientos por vehiculo y fecha'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM mantenimientos WHERE vehiculo_id = 1 ORDER BY fecha DESC;
\echo 'Consulta costosa sin paginacion: devuelve todos los viajes activos'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM viajes WHERE estado = 'EN_CURSO' ORDER BY fecha_salida DESC;
\echo 'Consulta optimizada: primera pagina de 100 viajes activos'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM viajes WHERE estado = 'EN_CURSO' ORDER BY fecha_salida DESC LIMIT 100;
\echo 'Autenticacion por username sin distinguir mayusculas'
EXPLAIN (ANALYZE, BUFFERS, VERBOSE) SELECT * FROM usuarios WHERE upper(username) = upper('admin');
