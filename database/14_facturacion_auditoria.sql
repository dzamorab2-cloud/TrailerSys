-- Auditoria de las tablas de facturacion (facturas, factura_detalles, pagos).
-- La aplicacion ya lo hace sola al arrancar (FacturacionAuditoriaInitializer);
-- este script es el equivalente manual. Ejecutar DESPUES de que el backend
-- haya creado las tablas. Requiere PostgreSQL 14 o superior.
\connect trailersys

CREATE OR REPLACE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.facturas
    FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();
CREATE OR REPLACE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.factura_detalles
    FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();
CREATE OR REPLACE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public.pagos
    FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria();

-- Indice para "viajes por facturar" (filtra por cliente y estado, ordena por fecha)
CREATE INDEX IF NOT EXISTS idx_viajes_cliente_estado_fecha
    ON public.viajes (cliente_id, estado, fecha_salida DESC);

-- Permisos de los roles de grupo (ver 01_roles_privileges.sql)
GRANT SELECT ON public.facturas, public.factura_detalles, public.pagos TO trailersys_lectura;
GRANT SELECT, INSERT, UPDATE ON public.facturas, public.factura_detalles, public.pagos TO trailersys_operacion;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO trailersys_operacion;
