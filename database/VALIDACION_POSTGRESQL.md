# Evidencia de validación en PostgreSQL

Fecha: 2026-08-24  
Motor: PostgreSQL 18.4  
Base principal: `trailersys`  
Base aislada de restauración: `trailersys_restore_validacion`

## Resultado

- Se crearon las ocho tablas funcionales mediante Hibernate y se cargaron los
  datos iniciales del proyecto.
- Se instalaron cuatro roles PostgreSQL sin permiso LOGIN: lectura, operación,
  auditoría y administración.
- Se instalaron 12 índices de negocio/auditoría.
- Se instalaron ocho triggers, cada uno atendiendo INSERT, UPDATE y DELETE.
- Una actualización controlada sobre `vehiculos` produjo una fila en
  `auditoria` con `usuario_app = 'verificacion_auditoria'`, operación `UPDATE`,
  tabla `vehiculos` y registro `1`.
- `pg_dump` creó un respaldo custom y `pg_restore --list` lo validó.
- SHA-256 del respaldo de prueba:
  `421A5E85CB37F1EB4FDC60A870DEFD8B64911B33EFBAB23CB43591DF60C984CF`.
- El respaldo se restauró sin errores en `trailersys_restore_validacion`.
- Conteos comprobados tras restaurar: 5 usuarios, 2 vehículos y 1 evento de
  auditoría.

## Análisis de consultas

Los planes completos están en [`explain-resultados.txt`](explain-resultados.txt).
Con solo dos filas por tabla PostgreSQL eligió `Seq Scan`; esto es esperado y
más económico que recorrer un índice y luego la tabla. Los tiempos observados
fueron de 0.034 a 0.067 ms para las consultas operativas y 0.038 ms para la
búsqueda de usuario. Los índices quedan disponibles para cuando aumente el
volumen y el planificador determine que `Index Scan` o `Bitmap Index Scan`
reduce el costo.

## Repetición

La validación puede repetirse con los scripts `02_auditoria_indices.sql`,
`03_explain_analyze.sql`, `backup.ps1` y `restore.ps1`. La contraseña se pasó
solo como variable de entorno y no está almacenada en el repositorio.
