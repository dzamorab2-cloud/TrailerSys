# Validación con más de un millón de registros

Fecha: 2026-08-24  
Motor: PostgreSQL 18.4  
Tamaño observado de la base: 245 MB

## Volumen cargado

| Tabla | Registros |
|---|---:|
| `auditoria` | 1 |
| `cargas` | 150.002 |
| `clientes` | 50.002 |
| `conductores` | 50.002 |
| `mantenimientos` | 100.002 |
| `seguimiento_eventos` | 400.002 |
| `usuarios` | 5 |
| `vehiculos` | 50.002 |
| `viajes` | 250.002 |
| **Total** | **1.050.020** |

La carga agregó 1.050.000 filas sintéticas relacionadas y conservó los 20
registros existentes. Las claves foráneas permanecieron activas durante todo el
proceso. El script es transaccional e impide una segunda ejecución accidental.

## Comportamiento observado

Los planes completos están en
[`explain-resultados-millon.txt`](explain-resultados-millon.txt).

- Seguimiento por viaje/fecha: `Index Scan` sobre
  `idx_seguimiento_viaje_fecha`, 0,061 ms.
- Mantenimiento por vehículo/fecha: `Index Scan` sobre
  `idx_mantenimientos_vehiculo_fecha`, 0,024 ms.
- Todos los viajes en curso: 58.334 filas, `Parallel Seq Scan` y ordenamiento
  externo de aproximadamente 8,6 MB; 99,142 ms. El plan es razonable porque la
  consulta solicita una fracción grande de la tabla, pero no debe usarse sin
  paginación en una API.
- Primera página de 100 viajes en curso: `Index Scan` sobre
  `idx_viajes_estado_fecha`, 0,197 ms y sin ordenamiento externo.
- Búsqueda del usuario `admin`: 0,029 ms. Con solo cinco usuarios, PostgreSQL
  elige correctamente `Seq Scan` en lugar del índice funcional.

La comparación de viajes demuestra la estrategia aplicada: índice compuesto
`(estado, fecha_salida DESC)` más paginación. Para la primera página, el tiempo
bajó de 99,142 ms a 0,197 ms y se evitó el uso de disco temporal.

## Reproducción

```powershell
psql -U postgres -d trailersys -v ON_ERROR_STOP=1 -f database/04_carga_millon.sql
psql -U postgres -d trailersys -v ON_ERROR_STOP=1 -f database/03_explain_analyze.sql
```

No se almacenan contraseñas ni datos personales reales en los scripts.
