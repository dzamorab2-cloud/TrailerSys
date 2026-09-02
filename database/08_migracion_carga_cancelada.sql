-- Actualiza una base TrailerSys existente para permitir que una Carga quede
-- en estado "Cancelada": cancelar un pedido Pendiente (autoservicio del
-- Cliente) ya no borra la fila, la archiva con este estado (ver
-- PedidoClienteService.eliminarPedido()) - antes desaparecia sin dejar
-- ningun rastro visible.
--
-- Hibernate genera un CHECK constraint para una columna @Enumerated(STRING)
-- a partir de los valores del enum EN EL MOMENTO en que la tabla se crea;
-- ddl-auto=update no lo actualiza solo porque el enum Java (EstadoCarga)
-- sume un valor nuevo, asi que sin este script CUALQUIER intento de guardar
-- una Carga en "Cancelada" contra una base ya existente falla con una
-- violacion de esa restriccion (mismo patron que 06_migracion_cliente.sql
-- para el CHECK de usuarios.rol).
ALTER TABLE cargas DROP CONSTRAINT IF EXISTS cargas_estado_check;
ALTER TABLE cargas ADD CONSTRAINT cargas_estado_check
CHECK (estado IN ('PENDIENTE','ASIGNADA','EN_TRANSITO','ENTREGADA','CANCELADA'));
