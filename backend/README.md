# TrailerSys Backend

API REST en Java + Spring Boot 3.5 que reemplaza, modulo por modulo, la
capa `localStorage` del prototipo frontend. Por ahora incluye:

- **Autenticacion** con JWT (`POST /api/auth/login`), reemplazando el
  login simulado en `sessionStorage` (`js/auth.js` del frontend).
- **Modulo Vehiculos** completo (`/api/vehiculos`), como patron de
  referencia para migrar Conductores, Clientes, Cargas, Viajes,
  Seguimiento y Mantenimientos en sesiones siguientes, repitiendo la
  misma estructura (entidad + repositorio + servicio + controller).

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

Edita `src/main/resources/application.properties` con tus datos reales:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/trailersys
spring.datasource.username=postgres
spring.datasource.password=tu-contraseña
```

`spring.jpa.hibernate.ddl-auto=update` ya esta configurado para crear las
tablas automaticamente la primera vez que arranca la aplicacion, a partir
de las entidades (`Usuario`, `Vehiculo`). No necesitas escribir el `CREATE
TABLE` a mano.

## 3. Ejecutar el backend

**Desde IntelliJ IDEA** (como indica el documento del proyecto): abre la
carpeta `backend/` como proyecto Maven y ejecuta la clase
`TrailerSysBackendApplication`. IntelliJ trae su propio Maven integrado,
no necesitas instalar nada mas.

**Desde la terminal**, usando el wrapper incluido (descarga Maven la
primera vez, no requiere tenerlo instalado):

```bash
./mvnw spring-boot:run          # Linux/Mac
mvnw.cmd spring-boot:run        # Windows
```

La API queda escuchando en `http://localhost:8080`.

## 4. Usuario de prueba

Al arrancar por primera vez (con la base de datos vacia), se crea
automaticamente un usuario administrador:

- **Usuario:** `admin`
- **Contraseña:** `admin1234`

Cambia esta contraseña (o borra y recrea el usuario) antes de usar esto
en un entorno real.

## 5. Probar la API

```bash
# Login: obtiene el token JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin1234"}'

# Listar vehiculos (usa el token recibido arriba)
curl http://localhost:8080/api/vehiculos \
  -H "Authorization: Bearer <token>"
```

Los permisos por endpoint replican `TRAILERSYS_ROLES` del frontend
(`js/roles.js`): todos los roles con acceso al modulo pueden consultar
(`GET`), pero solo Administrador y Coordinador pueden crear, editar o
eliminar vehiculos.

## 6. Pruebas automatizadas

```bash
./mvnw test
```

Las pruebas corren contra una base H2 embebida en modo compatible con
PostgreSQL (ver `src/test/resources/application.properties`), asi que
no necesitas PostgreSQL corriendo para ejecutarlas.

## Siguiente paso

Con este patron ya probado (entidad + JPA + DTOs + validaciones +
permisos por rol + tests), los proximos modulos (Conductores, Clientes,
Cargas, Viajes, Seguimiento, Mantenimientos) se agregan repitiendo la
misma estructura del paquete `vehiculo/`. El frontend seguira
funcionando con `localStorage` hasta que cada modulo se conecte
explicitamente a estos endpoints reales.
