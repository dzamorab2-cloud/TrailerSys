# Administración de la base de datos

Este módulo completa los controles operativos de PostgreSQL de TrailerSys. No
contiene contraseñas ni respaldos reales.

La ejecución comprobada sobre PostgreSQL 18.4 está documentada en
[`VALIDACION_POSTGRESQL.md`](VALIDACION_POSTGRESQL.md), con los planes reales en
[`explain-resultados.txt`](explain-resultados.txt).

## Instalación

Primero inicia el backend una vez para que Hibernate cree las tablas. Después,
desde la raíz del repositorio, ejecuta como propietario de `trailersys`:

```powershell
psql -U postgres -d trailersys -v ON_ERROR_STOP=1 -f database/02_auditoria_indices.sql
psql -U postgres -d trailersys -v ON_ERROR_STOP=1 -f database/01_roles_privileges.sql
```

El orden es intencional: los privilegios incluyen la tabla `auditoria`.

## Usuarios, roles y privilegios

La API ya aplica cinco roles mediante JWT y `@PreAuthorize`: ADMINISTRADOR,
COORDINADOR, MANTENIMIENTO, CONDUCTOR y SUPERVISOR. PostgreSQL añade defensa en
profundidad:

| Rol PostgreSQL | Privilegios |
|---|---|
| `trailersys_lectura` | SELECT operativo; no ve usuarios ni auditoría |
| `trailersys_operacion` | SELECT/INSERT/UPDATE; sin DELETE, usuarios ni auditoría |
| `trailersys_auditoria` | Consulta exclusiva de la bitácora |
| `trailersys_administracion` | Administración de tablas y secuencias |

Las cuentas LOGIN se crean individualmente y reciben uno de esos roles. Sus
contraseñas deben introducirse fuera del repositorio y rotarse conforme a la
política de la organización. La aplicación debe conectarse con una cuenta
dedicada, no con `postgres`.

## Respaldos y recuperación

- Tipo: respaldo lógico completo, comprimido, en formato custom de `pg_dump`.
- Frecuencia: diario a las 02:00; conservar 7 diarios, 4 semanales y 12
  mensuales. Para RPO menor a 24 horas, complementar con WAL/PITR.
- Regla 3-2-1: tres copias, dos medios, una copia cifrada fuera del servidor.
- Verificación: el script valida el catálogo y genera SHA-256. Debe hacerse una
  restauración de prueba mensual.

```powershell
./database/backup.ps1
./database/restore.ps1 -Backup ./database/backups/trailersys_YYYYMMDD_HHMMSS.dump
```

La restauración usa por defecto `trailersys_restore`, evitando sobrescribir
producción. Tras validar conteos, login y CRUD se planifica el cambio. `-Replace`
elimina y recrea únicamente la base indicada y requiere ventana de mantenimiento.

## Optimización y evidencia

Los índices cubren las búsquedas y ordenamientos de Spring Data: viajes por
vehículo/conductor/carga, seguimiento por viaje/fecha, mantenimiento por
vehículo/fecha, estado/fecha de viajes y búsquedas `IgnoreCase`.

```powershell
psql -U postgres -d trailersys -f database/03_explain_analyze.sql |
  Tee-Object database/explain-resultados.txt
```

En una base pequeña PostgreSQL puede elegir `Seq Scan`, porque suele ser más
barato. Con volumen representativo deben aparecer `Index Scan` o `Bitmap Index
Scan`; compara costo, bloques y tiempo antes/después.

## Auditoría

Un trigger registra INSERT, UPDATE y DELETE de las ocho tablas de negocio.
Guarda fecha, usuario PostgreSQL, usuario de aplicación cuando se establece,
operación, tabla, id, valores anteriores/nuevos JSONB, IP y transacción.

```sql
SELECT fecha_hora, usuario_bd, usuario_app, operacion, tabla, registro_id
FROM auditoria ORDER BY fecha_hora DESC LIMIT 100;

SELECT * FROM auditoria
WHERE tabla = 'viajes' AND registro_id = '1'
ORDER BY fecha_hora;
```

Para asociar una transacción manual con el usuario funcional:

```sql
BEGIN;
SET LOCAL trailersys.usuario = 'admin';
-- operaciones...
COMMIT;
```

La cuenta normal de operación no puede modificar ni borrar la bitácora.
