-- Pruebas manuales de los objetos creados en 08_procedimientos_funciones_cursores_disparadores.sql.
-- Ejecutar cada bloque o cada CALL por separado con autocommit habilitado.
-- No seleccionar todo dentro de un BEGIN, porque los procedimientos administran
-- internamente COMMIT y ROLLBACK.

-- Inventario de objetos instalados.
SELECT routine_type, routine_name
FROM information_schema.routines
WHERE routine_schema='public' AND (routine_name LIKE 'sp\_%' ESCAPE '\' OR routine_name LIKE 'fn\_%' ESCAPE '\')
ORDER BY routine_type, routine_name;

SELECT event_object_table AS tabla, trigger_name, event_manipulation AS evento
FROM information_schema.triggers
WHERE trigger_schema='public'
ORDER BY tabla, trigger_name, evento;

-- FUNCIONES.
SELECT * FROM fn_disponibilidad_flota();
SELECT * FROM fn_proximos_mantenimientos(30);
SELECT * FROM fn_resumen_cliente((SELECT min(id) FROM clientes));
SELECT * FROM fn_historial_viaje((SELECT min(id) FROM viajes));

-- CURSOR: la captura debe mostrar el FETCH con viajes programados/en curso.
BEGIN;
SELECT fn_abrir_viajes_pendientes('cur_viajes');
FETCH ALL FROM cur_viajes;
COMMIT;

-- DISPARADORES: caso incorrecto. Debe rechazar peso negativo.
-- Ejecute solo si existe al menos un cliente.
INSERT INTO cargas(descripcion,cliente_id,tipo,peso,origen,destino,estado,observaciones,fecha_creacion)
SELECT 'Prueba de trigger',min(id),'General',-1,'Quito','Guayaquil','PENDIENTE','Debe fallar',clock_timestamp()
FROM clientes;

-- PROCEDIMIENTO 1. Correcto y luego incorrecto por identificacion duplicada.
CALL sp_registrar_cliente('Cliente Practica SQL','PRC-CLIENTE-001','0991112233','practica@trailersys.ec','Quito');
CALL sp_registrar_cliente('Cliente Duplicado','PRC-CLIENTE-001','0991112233','duplicado@trailersys.ec','Quito');

-- Obtenga recursos reales para las pruebas restantes.
SELECT id AS cliente_prueba_id FROM clientes WHERE identificacion='PRC-CLIENTE-001' \gset
SELECT id AS vehiculo_prueba_id, placa AS vehiculo_prueba_placa FROM vehiculos WHERE estado='DISPONIBLE' ORDER BY id LIMIT 1 \gset
SELECT id AS conductor_prueba_id, identificacion AS conductor_prueba_identificacion FROM conductores WHERE estado='DISPONIBLE' ORDER BY id LIMIT 1 \gset

-- PROCEDIMIENTO 2. Correcto e incorrecto por peso negativo.
CALL sp_registrar_carga(:cliente_prueba_id,'Alimentos no perecibles','Alimentos',1200,'Quito','Guayaquil','Practica SQL');
CALL sp_registrar_carga(:cliente_prueba_id,'Carga invalida','General',-10,'Quito','Guayaquil','Debe hacer ROLLBACK');
SELECT max(id) AS carga_prueba_id FROM cargas WHERE cliente_id=:cliente_prueba_id \gset

-- PROCEDIMIENTO 3. Correcto e incorrecto al reutilizar la misma carga.
CALL sp_asignar_carga_viaje(:carga_prueba_id,:vehiculo_prueba_id,:conductor_prueba_id,clock_timestamp()+interval '1 hour');
CALL sp_asignar_carga_viaje(:carga_prueba_id,:vehiculo_prueba_id,:conductor_prueba_id,clock_timestamp()+interval '2 hours');
SELECT max(id) AS viaje_prueba_id FROM viajes WHERE carga_id=:carga_prueba_id \gset

-- PROCEDIMIENTO 4. Correcto y luego incorrecto por estado EN_CURSO.
CALL sp_iniciar_viaje(:viaje_prueba_id,'Terminal de Quito');
CALL sp_iniciar_viaje(:viaje_prueba_id,'Intento duplicado');

-- PROCEDIMIENTO 5. Correcto e incorrecto por tipo de evento.
CALL sp_registrar_evento_seguimiento(:viaje_prueba_id,'PARADA','Ambato','Control preventivo');
CALL sp_registrar_evento_seguimiento(:viaje_prueba_id,'EVENTO_INVALIDO','Ambato','Debe hacer ROLLBACK');

-- PROCEDIMIENTO 6. Correcto y luego incorrecto porque ya finalizo.
CALL sp_confirmar_entrega(:viaje_prueba_id,'conductor_prueba','Entrega completa');
CALL sp_confirmar_entrega(:viaje_prueba_id,'conductor_prueba','Intento duplicado');

-- PROCEDIMIENTO 7. Correcto y luego incorrecto porque ya fue validada.
CALL sp_validar_entrega(:viaje_prueba_id,'supervisor_prueba','Entrega verificada');
CALL sp_validar_entrega(:viaje_prueba_id,'supervisor_prueba','Intento duplicado');

-- PROCEDIMIENTO 8. Use ahora el vehiculo liberado. Correcto y costo negativo.
CALL sp_registrar_mantenimiento(:vehiculo_prueba_id,'PREVENTIVO',current_date,50000,125.50,'Revision mensual');
CALL sp_registrar_mantenimiento(:vehiculo_prueba_id,'PREVENTIVO',current_date,50000,-1,'Debe hacer ROLLBACK');

-- PROCEDIMIENTO 9: para probar cancelacion cree otro conjunto de carga/viaje.
-- Primero cambie el vehiculo de MANTENIMIENTO a DISPONIBLE para la practica.
UPDATE vehiculos SET estado='DISPONIBLE' WHERE id=:vehiculo_prueba_id;
CALL sp_registrar_carga(:cliente_prueba_id,'Carga para cancelar','General',500,'Quito','Cuenca','Practica SQL');
SELECT max(id) AS carga_cancelar_id FROM cargas WHERE cliente_id=:cliente_prueba_id \gset
CALL sp_asignar_carga_viaje(:carga_cancelar_id,:vehiculo_prueba_id,:conductor_prueba_id,clock_timestamp()+interval '1 day');
SELECT max(id) AS viaje_cancelar_id FROM viajes WHERE carga_id=:carga_cancelar_id \gset
CALL sp_cancelar_viaje(:viaje_cancelar_id,'Cambio solicitado por el cliente');
CALL sp_cancelar_viaje(:viaje_cancelar_id,'Intento duplicado');

-- PROCEDIMIENTO 10: requiere un segundo vehiculo/conductor disponibles.
SELECT id AS vehiculo_nuevo_id, placa AS vehiculo_nuevo_placa FROM vehiculos
WHERE estado='DISPONIBLE' AND id<>:vehiculo_prueba_id ORDER BY id LIMIT 1 \gset
SELECT id AS conductor_nuevo_id, identificacion AS conductor_nuevo_identificacion FROM conductores
WHERE estado='DISPONIBLE' AND id<>:conductor_prueba_id ORDER BY id LIMIT 1 \gset
CALL sp_registrar_carga(:cliente_prueba_id,'Carga para reasignar','General',600,'Manta','Loja','Practica SQL');
SELECT max(id) AS carga_reasignar_id FROM cargas WHERE cliente_id=:cliente_prueba_id \gset
CALL sp_asignar_carga_viaje(:carga_reasignar_id,:vehiculo_prueba_id,:conductor_prueba_id,clock_timestamp()+interval '2 days');
SELECT max(id) AS viaje_reasignar_id FROM viajes WHERE carga_id=:carga_reasignar_id \gset
CALL sp_reasignar_viaje(:viaje_reasignar_id,:'vehiculo_nuevo_placa',:'conductor_nuevo_identificacion');
CALL sp_reasignar_viaje(:viaje_reasignar_id,'PLACA-INEXISTENTE',:'conductor_nuevo_identificacion');

-- Evidencia final.
SELECT id,estado,vehiculo_id,conductor_id,carga_id,entrega_confirmada,entrega_validada
FROM viajes WHERE cliente_id=:cliente_prueba_id ORDER BY id;
SELECT id,descripcion,estado FROM cargas WHERE cliente_id=:cliente_prueba_id ORDER BY id;
