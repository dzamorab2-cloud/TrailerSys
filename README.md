# TrailerSys

Sistema de gestión de flota y transporte: vehículos, conductores,
clientes, cargas, viajes (con mapa y ruta gratuitos), seguimiento
(alertas operativas + confirmación/validación de entregas) y
mantenimientos. Control de acceso por rol (Administrador, Coordinador,
Responsable de Mantenimiento, Conductor, Supervisor).

- **Backend**: Java 21 + Spring Boot 3.5, API REST sobre PostgreSQL,
  autenticación con JWT.
- **Frontend**: HTML/CSS/JS sin frameworks ni build step, consumiendo la
  API con `fetch()`.

## Requisitos

- **Java 21** (el proyecto trae el wrapper de Maven, no hace falta
  instalar Maven aparte).
- **PostgreSQL** instalado y corriendo.
- **Node.js** (solo para servir el frontend en desarrollo; no se usa
  para compilar nada).

## 1. Base de datos

Crea la base de datos una sola vez:

```bash
psql -U postgres -c "CREATE DATABASE trailersys;"
```

## 2. Backend

La contraseña de PostgreSQL no está escrita en el código: se lee de la
variable de entorno `DB_PASSWORD`.

```powershell
# PowerShell
cd backend
$env:DB_PASSWORD = "tu-contraseña"
.\mvnw.cmd spring-boot:run
```

```bash
# Bash / Linux / Mac
cd backend
export DB_PASSWORD=tu-contraseña
./mvnw spring-boot:run
```

**Desde IntelliJ IDEA:** abre `backend/` como proyecto Maven, y en la
configuración de ejecución de `TrailerSysBackendApplication` agrega la
variable de entorno `DB_PASSWORD`. Más detalle en
[`backend/README.md`](backend/README.md).

La API queda escuchando en `http://localhost:8080`. Al arrancar por
primera vez crea las tablas automáticamente y siembra datos de
demostración (ver credenciales más abajo).

## 3. Frontend

En otra terminal, desde la raíz del proyecto:

```bash
node dev/server.js . 5173
```

Abre `http://localhost:5173` en el navegador.

## Usuarios de prueba

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin1234` | Administrador |
| `coordinador` | `coordinador1234` | Coordinador / Operador |
| `mantenimiento` | `mantenimiento1234` | Responsable de Mantenimiento |
| `conductor` | `conductor1234` | Conductor |
| `supervisor` | `supervisor1234` | Supervisor / Consulta |

## Pruebas automatizadas

```bash
cd backend
./mvnw test
```

78 pruebas contra una base H2 embebida (no necesitas PostgreSQL
corriendo para ejecutarlas).

## Estructura del proyecto

```
├── index.html, app.html   # Login y shell de la aplicación (frontend)
├── css/, js/               # Estilos y lógica de cada módulo (frontend)
├── dev/server.js           # Servidor estático simple para desarrollo local
└── backend/                # API REST (Spring Boot + PostgreSQL)
    └── src/main/java/com/trailersys/backend/
        ├── auth/            # Login (JWT) y perfil del usuario autenticado
        ├── vehiculo/ conductor/ cliente/ carga/
        ├── viaje/           # Incluye ruta, simulación automática y confirmación de entrega
        ├── seguimiento/     # Eventos de viaje y alertas operativas
        └── mantenimiento/
```

## Módulos

Vehículos, Conductores, Clientes, Cargas, Viajes (con cálculo de ruta
vía OpenStreetMap/OSRM y mapa Leaflet), Seguimiento (timeline de
eventos + alertas operativas + confirmación/validación de entregas),
Mantenimientos, Reportes (indicadores, filtros, exportar CSV,
imprimir/PDF), Dashboard (indicadores en vivo) y Configuración (perfil
de la cuenta autenticada, solo lectura).
