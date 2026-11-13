# Despliegue de TrailerSys en producción

Esta guía pone TrailerSys en un servidor real, accesible desde internet,
con datos reales y sin las cuentas de demostración.

## Qué cambia en producción

| | Desarrollo (local) | Producción (perfil `prod`) |
|---|---|---|
| Frontend | `node dev/server.js` en `:5173` | Lo sirve el mismo backend, en el mismo dominio |
| URL de la API | `http://localhost:8080/api` | `https://tu-dominio/api` (automático) |
| Cuentas demo (`admin/admin1234`…) | Se crean en cada arranque | **No existen**. Se crea un administrador inicial desde `ADMIN_PASSWORD` |
| Datos demo (vehículos, viajes…) | Se crean si la base está vacía | **No se crean** |
| Simulación de viajes | Encendida (paradas y posición simuladas) | **Apagada**: el mapa muestra solo la ubicación que reporta el conductor |
| Secretos (`JWT_SECRET`, `DB_PASSWORD`) | Tienen valores por defecto | **Obligatorios**: sin ellos la app no arranca |
| `pg_dump` (respaldos) | Ruta de Windows | `/usr/bin/pg_dump`, ya incluido en la imagen Docker |

## Antes de desplegar (siempre)

1. Corre las pruebas en tu máquina y confirma que todas pasan:
   ```bash
   cd backend
   ./mvnw test
   ```
2. Genera una clave JWT aleatoria (guárdala, no la subas a git):
   ```bash
   openssl rand -base64 48
   ```

---

## Opción A — Servidor propio con Docker (recomendada)

Sirve para un VPS (DigitalOcean, Contabo, AWS Lightsail, etc.) o para un
servidor de la universidad con Linux. Requisitos: Docker y Docker Compose,
y al menos 2 GB de RAM.

```bash
git clone https://github.com/dzamorab2-cloud/TrailerSys.git
cd TrailerSys
cp .env.ejemplo .env
nano .env            # completa DB_PASSWORD, JWT_SECRET y ADMIN_PASSWORD
docker compose up -d --build
```

La primera vez, PostgreSQL ejecuta `database/00_esquema_trailersys.sql`
automáticamente: crea la base `trailersys` con las tablas, la auditoría,
los triggers, las vistas y las funciones.

La app queda en `http://IP-DEL-SERVIDOR:8080`. Entra con `ADMIN_USUARIO` /
`ADMIN_PASSWORD` y **cambia la contraseña** en *Configuración → Cambiar
contraseña*.

### HTTPS con dominio (necesario para uso real)

1. Compra o consigue un dominio (o subdominio de la universidad) y crea un
   registro **A** que apunte a la IP del servidor.
2. En `.env` pon `DOMINIO=trailersys.tu-dominio.com`.
3. Abre los puertos 80 y 443 del servidor y ejecuta:
   ```bash
   docker compose --profile https up -d
   ```
   Caddy obtiene y renueva el certificado HTTPS gratis (Let's Encrypt).
   Después puedes cerrar el puerto 8080 en el firewall.

### Operación diaria

```bash
docker compose logs -f app          # ver logs
git pull && docker compose up -d --build   # actualizar a la última versión
docker compose restart app          # reiniciar
```

Los respaldos del módulo *Respaldos* quedan en el volumen `backups`. Copia
ese volumen (o el archivo `.dump`) **fuera del servidor** con regularidad:
un respaldo que vive en el mismo disco no protege contra la pérdida del
servidor.

---

## Opción B — Plataforma en la nube (Render, Railway, Fly.io)

1. Crea una base PostgreSQL administrada (Neon, Supabase, Render
   Postgres…). Anota host, puerto, usuario y contraseña.
2. Carga el esquema una sola vez desde tu PC:
   ```bash
   psql "postgresql://USUARIO:CLAVE@HOST:5432/postgres?sslmode=require" \
        -f database/00_esquema_trailersys.sql
   ```
   Si el proveedor ya creó la base `trailersys`, ignora el error del
   `CREATE DATABASE`. Si el usuario no se llama `postgres`, ignora los
   avisos de `OWNER TO postgres`.
3. Crea un servicio web desde este repositorio usando el `Dockerfile`.
4. Define las variables de entorno:

   | Variable | Ejemplo |
   |---|---|
   | `DB_HOST` | `ep-xxx.us-east-2.aws.neon.tech` |
   | `DB_PORT` | `5432` |
   | `DB_NOMBRE` | `trailersys` |
   | `DB_USUARIO` | `trailersys_owner` |
   | `DB_PASSWORD` | *(la de la base)* |
   | `DB_PARAMETROS` | `?sslmode=require` |
   | `JWT_SECRET` | *(salida de `openssl rand -base64 48`)* |
   | `ADMIN_PASSWORD` | *(mínimo 10 caracteres)* |

   La plataforma pone el HTTPS y el dominio automáticamente.

> En los planes gratuitos el servicio "se duerme" si nadie lo usa y tarda
> en despertar, y el disco no es persistente (los respaldos del módulo se
> pierden al reiniciar). Para un piloto real usa un plan pagado básico o la
> opción A.

---

## Variables de entorno (referencia)

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_HOST`, `DB_USUARIO`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `DB_PORT`, `DB_NOMBRE` | No | Por defecto `5432` y `trailersys` |
| `DB_PARAMETROS` | No | Ej. `?sslmode=require` |
| `JWT_SECRET` | Sí | Mínimo 32 caracteres aleatorios |
| `ADMIN_PASSWORD` | Solo la 1.ª vez | Crea el administrador inicial si no hay ninguno |
| `ADMIN_USUARIO`, `ADMIN_CORREO` | No | Por defecto `admin` |
| `SIMULACION_HABILITADA` | No | `false` (real) / `true` (modo demostración) |
| `CORS_ORIGINS` | No | Solo si el frontend se aloja en otro dominio |
| `JWT_EXPIRACION_MINUTOS` | No | Por defecto 480 (8 horas) |
| `PORT` | No | Puerto interno, por defecto 8080 |

## Lista de verificación del piloto

- [ ] `./mvnw test` pasa completo.
- [ ] La app abre con HTTPS y el candado del navegador está correcto.
- [ ] `admin/admin1234` **no** funciona (las cuentas demo no existen).
- [ ] Entré con el administrador inicial y cambié su contraseña.
- [ ] Creé las cuentas reales (coordinador, conductores, clientes) desde
      *Configuración → Usuarios y acceso*.
- [ ] Registré vehículos, conductores y clientes reales.
- [ ] Hice un viaje de prueba completo: pedido → asignación → reporte de
      ubicación del conductor → entrega → confirmación del cliente.
- [ ] Generé un respaldo completo y lo descargué fuera del servidor.

## Limitaciones conocidas

- **Mapas, búsqueda de direcciones y rutas** usan los servidores públicos
  gratuitos de OpenStreetMap (tiles, Nominatim y OSRM). Alcanzan para un
  piloto con pocos usuarios. Si el uso crece, conviene un proveedor con
  plan (MapTiler, Stadia, Geoapify) o un servidor OSRM propio.
- **Ubicación del vehículo**: depende de que el conductor la reporte desde
  *Mis viajes*. No hay GPS automático.
- **Esquema de base de datos**: Hibernate (`ddl-auto=update`) agrega
  columnas nuevas solo. A mediano plazo conviene migrar a Flyway para
  versionar los cambios del esquema.
