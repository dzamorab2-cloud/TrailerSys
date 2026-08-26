-- Convierte las cargas de demostración existentes en registros comerciales realistas.
-- Es seguro ejecutarlo nuevamente: solo transforma filas con el nombre antiguo.
WITH cargas_sinteticas AS (
    SELECT id, row_number() OVER (ORDER BY id) AS rn
    FROM cargas
    WHERE descripcion LIKE 'Carga sintética %'
)
UPDATE cargas AS c
SET descripcion = (ARRAY[
        'Banano de exportación', 'Camarón congelado', 'Cacao en grano',
        'Flores frescas', 'Atún en conserva', 'Repuestos automotrices',
        'Materiales de construcción', 'Productos lácteos', 'Arroz pilado',
        'Medicamentos', 'Electrodomésticos', 'Textiles y confecciones',
        'Frutas tropicales', 'Aceite vegetal', 'Alimentos balanceados'
    ])[1 + ((s.rn - 1) % 15)] || ' · Lote ' || lpad(s.rn::text, 6, '0'),
    tipo = (ARRAY[
        'Agrícola', 'Refrigerada', 'Agrícola', 'Refrigerada', 'Alimentos',
        'Automotriz', 'Construcción', 'Refrigerada', 'Alimentos', 'Farmacéutica',
        'Electrodomésticos', 'Textil', 'Refrigerada', 'Alimentos', 'Agroindustrial'
    ])[1 + ((s.rn - 1) % 15)],
    origen = (ARRAY[
        'Guayaquil', 'Quito', 'Cuenca', 'Manta', 'Machala',
        'Ambato', 'Santo Domingo', 'Loja', 'Quevedo', 'Esmeraldas'
    ])[1 + ((s.rn - 1) % 10)],
    destino = (ARRAY[
        'Quito', 'Guayaquil', 'Loja', 'Cuenca', 'Manta',
        'Riobamba', 'Ibarra', 'Machala', 'Ambato', 'Portoviejo'
    ])[1 + ((s.rn * 7 - 1) % 10)],
    observaciones = 'Mercancía comercial registrada para operación logística nacional'
FROM cargas_sinteticas AS s
WHERE c.id = s.id;

ANALYZE cargas;

SELECT count(*) AS cargas_convertidas
FROM cargas
WHERE observaciones = 'Mercancía comercial registrada para operación logística nacional';
