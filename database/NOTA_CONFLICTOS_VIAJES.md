# Nota para Umaginga: conflictos en los datos masivos de prueba

## Qué pasa

Al editar viajes (y en menor medida vehículos/conductores/cargas) del set de
datos sintético "de volumen" (placas/identificaciones que empiezan con
`SYN-`, generado por [`04_carga_millon.sql`](04_carga_millon.sql)), el
backend rechaza el guardado con un `409 Conflict`, por ejemplo:

- `"Esta carga ya está asignada a otro viaje activo."`
- `"El vehículo seleccionado ya está asignado a otro viaje activo."`
- `"El conductor seleccionado ya está asignado a otro viaje activo."`

Esto **no es un bug en la validación** (`ViajeService.validarCarga` /
`validarDisponibilidad`): esa regla es correcta y necesaria (una carga o un
vehículo no deberían estar en dos viajes Programado/En Curso a la vez). El
problema es que **los datos sembrados por `04_carga_millon.sql` ya vienen
violando esa regla**, porque se insertaron directo por SQL sin pasar por esa
validación.

## Causa raíz (en `04_carga_millon.sql`)

- Se generan 150.000 cargas pero 250.000 viajes, repartidos con módulo
  (`(g - 1) % 150000`). Resultado: **100.000 cargas terminan usadas por 2
  viajes distintos**.
- Cada vehículo/conductor (pool de 50.000) se reutiliza **exactamente 5
  veces** entre los 250.000 viajes (`(g - 1) % 50000`).
- El estado de cada viaje se calcula con `g % 10`, `g % 4`, `g % 3`. Como
  150.000 es múltiplo exacto de 10, 4 y 3, **los dos viajes que comparten
  una misma carga (`g` y `g + 150000`) caen siempre en el mismo estado** —
  si uno queda "Programado", el otro también. Confirmado con un caso real:
  la carga "Materiales de construcción · Lote 099997" tiene 2 viajes, ambos
  "Programado" al mismo tiempo.
- Con vehículos/conductores pasa algo parecido (5 repeticiones cada uno),
  así que la gran mayoría también terminan con más de una asignación activa
  simultánea.

En resumen: una fracción muy grande del ~1M+ de filas sintéticas queda
"atascada" — no se puede editar sin antes resolver el choque, aunque el
usuario no haya hecho nada raro.

## Qué falta hacer (a elegir)

1. **Limpieza rápida (recomendado)**: un `UPDATE` que, por cada
   carga/vehículo/conductor con más de un viaje Programado/En Curso, deje
   solo el más reciente activo y pase los demás a `FINALIZADO`. No borra
   filas ni cambia la cantidad de registros, solo corrige el estado.
2. **Regenerar el script**: reescribir `04_carga_millon.sql` para que la
   asignación de estado tome en cuenta si el recurso (carga/vehículo/
   conductor) ya quedó activo en otro viaje antes de sortear el estado, y
   volver a cargar los ~1M+ filas desde cero.

Verificado en el navegador (rol Administrador) reproduciendo el error al
editar el viaje `GUIA-VIA-250011` (carga id 100004, vehículo SYN-049997):
el `PUT /api/viajes/250011` devuelve 409 con el mensaje de la carga citado
arriba, mientras que crear/editar/eliminar funciona sin problema en
registros que no tienen este choque (probado con un vehículo nuevo,
placa TST-0001, creado y eliminado durante la prueba).
