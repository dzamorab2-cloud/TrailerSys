# TrailerSys Backend

API REST en Java + Spring Boot 3.5 que reemplazara, modulo por modulo, la
capa `localStorage` del prototipo frontend. Incluye:

- **Autenticacion** con JWT (`POST /api/auth/login`), reemplazando el
  login simulado en `sessionStorage` (`js/auth.js` del frontend).
- Los **7 modulos con datos** del documento del proyecto, cada uno con
  entidad + repositorio + servicio + controller + DTOs con validacion:
  `/api/vehiculos`, `/api/conductores`, `/api/clientes`, `/api/cargas`,
  `/api/viajes`, `/api/seguimiento` (eventos + alertas operativas) y
  `/api/mantenimientos`.
- Permisos por endpoint que replican `TRAILERSYS_ROLES` de `js/roles.js`
  exactamente (mismos roles, mismos modulos visibles, misma distincion
  entre "consultar" y "gestionar").

Verificado funcionando de punta a punta contra PostgreSQL 18 real (no
solo la base H2 de los tests): login, los 7 modulos y las alertas de
Seguimiento devuelven datos correctos, incluyendo acentos y caracteres
especiales.

## 1. Instalar PostgreSQL

Si no tienes PostgreSQL instalado en Windows:

1. Descarga el instalador desde https://www.postgresql.org/download/windows/
   (o instala con `winget install PostgreSQL.PostgreSQL` en una terminal).
2. Durante la instalacion, define una contraseña para el usuario `postgres`
   y anota el puerto (por defecto `5432`).
3. Crea la base de datos del proyecto. Puedes usar pgAdmin (se instala junto
   con PostgreSQL) o la terminal `psql`:

```bash
psql -U postgres -c "CREATE DATABASE trailersys;"
```

## 2. Configurar la conexion

`src/main/resources/application.properties` ya apunta a
`localhost:5432/trailersys` con usuario `postgres`. La contraseña **no**
esta escrita en ese archivo (para no subirla a git); se lee de la
variable de entorno `DB_PASSWORD`, con `postgres` como valor por
defecto si no la defines:

```powershell
# PowerShell
$env:DB_PASSWORD = "tu-contraseña"
```

```bash
# Bash
export DB_PASSWORD=tu-contraseña
```

Si tu instalacion usa otro usuario, puerto o nombre de base, edita
`spring.datasource.url` / `spring.datasource.username` directamente.

`spring.jpa.hibernate.ddl-auto=update` crea/ajusta las tablas
automaticamente a partir de las entidades la primera vez que arranca la
aplicacion. No necesitas escribir ningun `CREATE TABLE` a mano.

## 3. Ejecutar el backend

**Desde IntelliJ IDEA** (como indica el documento del proyecto): abre la
carpeta `backend/` como proyecto Maven, configura la variable de entorno
`DB_PASSWORD` en la configuracion de ejecucion, y corre
`TrailerSysBackendApplication`. IntelliJ trae su propio Maven integrado,
no necesitas instalar nada mas.

**Desde la terminal**, usando el wrapper incluido (descarga Maven la
primera vez, no requiere tenerlo instalado):

```bash
./mvnw spring-boot:run          # Linux/Mac
mvnw.cmd spring-boot:run        # Windows
```

La API queda escuchando en `http://localhost:8080`.

## 4. Usuarios de prueba

`DataSeeder` crea automaticamente (y mantiene, aunque la base de datos ya
tenga datos: la contraseña se re-sincroniza en cada arranque) una cuenta
por cada rol, ademas de los datos demo para los 7 modulos:

| Usuario         | Contraseña           | Rol            |
|-----------------|-----------------------|----------------|
| `admin`         | `admin1234`           | Administrador  |
| `coordinador`   | `coordinador1234`     | Coordinador    |
| `mantenimiento` | `mantenimiento1234`   | Mantenimiento  |
| `conductor`     | `conductor1234`       | Conductor      |
| `supervisor`    | `supervisor1234`      | Supervisor     |

Cambia estas contraseñas (o borra y recrea los usuarios) antes de usar
esto en un entorno real.

## 5. Probar la API

```bash
# Login: obtiene el token JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'

# Listar vehiculos (usa el token recibido arriba)
curl http://localhost:8080/api/vehiculos \
  -H "Authorization: Bearer <token>"

# Alertas operativas (licencias vencidas, vehiculos en mantenimiento
# con viaje activo, viajes retrasados o sin ruta, mantenimientos vencidos)
curl http://localhost:8080/api/seguimiento/alertas \
  -H "Authorization: Bearer <token>"
```

## 6. Pruebas automatizadas

```bash
./mvnw test
```

54 pruebas en total, todas contra una base H2 embebida en modo
compatible con PostgreSQL (ver `src/test/resources/application.properties`),
asi que no necesitas PostgreSQL corriendo para ejecutarlas.

## Que falta para que el sistema este completo

Este backend por si solo no es el proyecto terminado. Falta:

1. **Conectar el frontend a esta API real.** Hoy `js/vehiculos.js`,
   `js/conductores.js`, etc. siguen leyendo/escribiendo en
   `localStorage`. Cada modulo necesita reescribirse para usar
   `fetch()` con el token JWT en vez de `localStorage`, siguiendo el
   mismo patron modulo por modulo.
2. **Fase 11 (Dashboard):** sigue como placeholder, pendiente de
   construirse con datos reales una vez el frontend hable con esta API.
3. **Fase 12 (Pruebas y despliegue):** pruebas end-to-end del sistema
   completo y puesta en marcha.
