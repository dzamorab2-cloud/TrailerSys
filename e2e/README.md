# Suite E2E — Login (Playwright)

Automatización end-to-end del flujo de login de TrailerSys (`index.html` → `js/auth.js`), con 4 casos:

1. Credenciales válidas → navega de `index.html` a `app.html` y se ve el Dashboard.
2. Contraseña incorrecta → aparece el mensaje de error real del backend y la página **no** se recarga ni redirige (ver `INFORME_CORRECCIONES.md`, sección 3, sobre el bug ya corregido).
3. Campos vacíos → nunca se llama a `/api/auth/login` y ambos campos se marcan como obligatorios.
4. Login exitoso → el token queda guardado en `sessionStorage` (clave `trailersys_session`).

## Requisitos previos

Esta suite **no levanta el backend**. Antes de correrla necesitas, aparte:

1. **PostgreSQL** corriendo y accesible con la configuración de `backend/src/main/resources/application.properties` (ver el README de la raíz, sección de variables de entorno / `DB_PASSWORD`).
2. **El backend Spring Boot corriendo en `http://localhost:8080`**:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   Al arrancar, `DataSeeder` crea (si no existen) las cuentas de prueba documentadas en el README de la raíz, incluida `admin` / `admin1234`, que es la que usan estos tests.

El frontend estático (puerto 5173) **sí** lo levanta automáticamente Playwright (ver `webServer` en `playwright.config.js`, usando el mismo `dev/server.js` que documenta el README de la raíz) — no hace falta arrancarlo a mano, aunque si ya lo tienes corriendo en 5173, Playwright lo reutiliza en vez de fallar por el puerto ocupado.

## Instalación

Desde la carpeta `e2e/`:

```bash
cd e2e
npm install
npx playwright install chromium
```

(`npx playwright install chromium` descarga el navegador que usa la suite; solo hace falta la primera vez, o cuando se actualice la versión de `@playwright/test`.)

## Ejecutar la suite

Con el backend y PostgreSQL ya corriendo (ver arriba), desde `e2e/`:

```bash
npm test
```

Otras variantes útiles:

```bash
npm run test:headed   # corre con el navegador visible, para ver el flujo
npm run test:ui       # abre el modo interactivo (UI Mode) de Playwright
npm run report        # abre el reporte HTML de la ultima corrida
```

## Notas

- Los tests asumen las credenciales de prueba `admin` / `admin1234` sembradas por `DataSeeder`. Si esa cuenta se elimina o se le cambia la contraseña en tu base, ajusta las constantes al inicio de `tests/login.spec.js`.
- El caso de "contraseña incorrecta" depende de que el mensaje de error del backend siga siendo `"Usuario o contraseña incorrectos."` (`AuthController`/`GlobalExceptionHandler`); si ese texto cambia, actualiza la expresión regular del test.
- `playwright.config.js` toma capturas de pantalla solo cuando un test falla (`screenshot: 'only-on-failure'`) y guarda el *trace* en el primer reintento; revísalos con `npm run report` si algo falla.
