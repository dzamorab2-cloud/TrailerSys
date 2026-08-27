# Informe de correcciones — TrailerSys

Revisión completa del proyecto (backend Java/Spring Boot + frontend HTML/CSS/JS) en busca de bugs funcionales, de seguridad y de rendimiento. Se excluyen deliberadamente cambios de estilo o refactors cosméticos: cada punto de este informe es un defecto que afecta el comportamiento real de la aplicación.

Los tests de backend (`backend/src/test`) pasan en su totalidad después de todos los cambios: **86/86 (`BUILD SUCCESS`)**, incluyendo los tests nuevos añadidos para verificar estas correcciones.

---

## 1. El buscador y el filtro de estado solo operaban sobre la página actual (24 registros), no sobre toda la base de datos

**Dónde estaba:**
- [`backend/src/main/java/com/trailersys/backend/common/CatalogoPageController.java`](backend/src/main/java/com/trailersys/backend/common/CatalogoPageController.java) — endpoints `/api/paginas/vehiculos`, `/clientes`, `/cargas`, `/viajes`, `/mantenimientos` (antes de la corrección, líneas 48-79 usaban `findAll(pageable)` sin parámetros de búsqueda).
- [`js/vehiculos.js`](js/vehiculos.js), [`js/clientes.js`](js/clientes.js), [`js/cargas.js`](js/cargas.js), [`js/viajes.js`](js/viajes.js), [`js/mantenimientos.js`](js/mantenimientos.js) — cada módulo pedía una página de 24 registros al backend y luego filtraba ese arreglo en el navegador con `Array.prototype.filter`.

**Por qué era un problema:**
El módulo de Conductores (`js/conductores.js`) sí mandaba `search` y `estado` como parámetros al backend, que a su vez usaba una consulta JPQL paginada (`ConductorRepository.buscar`). Los otros 5 módulos catálogo (Vehículos, Clientes, Cargas, Viajes, Mantenimientos) no seguían ese patrón: pedían solo la página visible y filtraban en JavaScript, así que buscar "Perez" o filtrar por estado "Entregada" solo encontraba coincidencias dentro de los 24 registros ya cargados, ignorando el resto de la tabla. Con la carga de un millón de registros que tiene este proyecto (ver commit `c38f0b5`), esto hacía que el buscador pareciera roto en la práctica: la inmensa mayoría de las búsquedas no encontraban nada aunque el dato sí existiera en la base.

**Qué se hizo:**
- Se agregó un método `buscar(search, estado, Pageable)` (o `buscar(search, vehiculoId, tipo, Pageable)` para Mantenimientos, que no tiene campo `estado`) a `VehiculoRepository`, `ClienteRepository`, `CargaRepository`, `ViajeRepository` y `MantenimientoRepository`, con una consulta JPQL `LIKE` case-insensitive sobre los campos relevantes (incluyendo columnas de relaciones, p. ej. `cliente.nombre` en Cargas, `vehiculo.placa` y `conductor.nombres` en Viajes).
- `CatalogoPageController` ahora recibe `search` y `estado` (o `vehiculoId`/`tipo` para Mantenimientos) como `@RequestParam` y delega en esos métodos, igual que ya hacía el endpoint de Conductores.
- En el frontend, `trailersysPagedRequest` (`js/api-client.js`) ahora acepta un cuarto parámetro con los filtros extra y arma el query string; los 5 módulos afectados le pasan `search`/`estado` (o `vehiculoId`/`tipo`) en cada `render()`, eliminando el filtrado en memoria y agregando debounce de 300 ms en el campo de búsqueda (igual que Conductores) para no disparar una petición por cada tecla.
- Se incluyó **Vehículos** aunque no estaba en la lista original: tenía exactamente el mismo bug de búsqueda/estado (los filtros de tipo/marca de ese módulo siguen operando solo sobre la página cargada, ya que se derivan dinámicamente de esos mismos datos — es una limitación preexistente y menor que no se tocó para no ampliar el alcance).

**Cómo verificar:**
1. `cd backend && ./mvnw test -Dtest=VehiculoRepositoryTest,ClienteRepositoryTest,CargaRepositoryTest,ViajeRepositoryTest,MantenimientoRepositoryTest` — corre los tests nuevos que insertan registros y comprueban que `buscar()` los encuentra por texto y por estado sin importar la página.
2. Con el backend y el frontend corriendo, en cualquiera de los 5 módulos: escribir en el buscador o elegir un estado en el filtro un valor que sepas que existe pero que no está en las primeras 24 filas (por ejemplo, ordenar por fecha y buscar un registro antiguo) — antes de la corrección no aparecía; ahora sí.

---

## 2. Al reasignar un viaje activo a otro vehículo/conductor/carga, los recursos anteriores quedaban "atascados" ocupados para siempre

**Dónde estaba:** [`backend/src/main/java/com/trailersys/backend/viaje/ViajeService.java`](backend/src/main/java/com/trailersys/backend/viaje/ViajeService.java), método `actualizar()` (antes de la corrección, lo que hoy son las líneas 100-135).

**Por qué era un problema:**
`actualizar()` sobrescribía `viaje.setVehiculo(vehiculo)`, `setConductor(conductor)` y `setCarga(carga)` con los **nuevos** valores del request, y solo después llamaba a `sincronizarEstadoCarga(viaje)` y `sincronizarEstadoVehiculoYConductor(viaje)`. Como esos métodos leen `viaje.getVehiculo()`/`getConductor()`/`getCarga()` (ya sobrescritos), nunca tenían forma de saber cuál era el vehículo/conductor/carga **anterior** para liberarlo.

Escenario concreto: un viaje "Programado" con el Vehículo A (que por eso quedó en estado "En Ruta"). El coordinador edita el viaje y lo reasigna al Vehículo B, que está disponible. B pasa correctamente a "En Ruta", pero **A se queda "En Ruta" para siempre**, aunque ya ningún viaje lo referencie — el vehículo se vuelve inutilizable (no puede asignarse a ningún viaje nuevo, porque el formulario solo ofrece vehículos "Disponibles") hasta que alguien lo corrija manualmente en la base de datos. Lo mismo pasa con el conductor y con la carga (que se queda "Asignada"/"En Tránsito" en los reportes aunque ya no la transporte nadie).

**Qué se hizo:**
En `actualizar()` se capturan `cargaAnterior`, `vehiculoAnterior` y `conductorAnterior` **antes** de sobrescribirlos (líneas 103-105). Después de sincronizar el estado de los recursos nuevos, se agregaron dos métodos nuevos:
- `liberarCargaSiCambio(cargaAnterior, cargaNueva)` (línea 266): si la carga cambió y ningún otro viaje activo la sigue referenciando, la regresa a "Pendiente" (salvo que ya esté "Entregada").
- `liberarVehiculoYConductorSiCambiaron(...)` (línea 284): si el vehículo o el conductor cambiaron y el anterior no está en otro viaje activo, lo regresa de "En Ruta" a "Disponible".

Ambos métodos respetan el mismo criterio que ya usaba el código existente: solo tocan la transición `Disponible <-> En Ruta`, sin pisar estados manuales como "Mantenimiento" o "Fuera de Servicio".

**Cómo verificar:**
`cd backend && ./mvnw test -Dtest=ViajeControllerTest` — se agregaron los tests `actualizarViajeConOtroVehiculoYConductorLiberaLosAnteriores` y `actualizarViajeConOtraCargaLiberaLaCargaAnterior` en [`ViajeControllerTest.java`](backend/src/test/java/com/trailersys/backend/viaje/ViajeControllerTest.java), que crean un viaje "Programado", lo reasignan vía `PUT /api/viajes/{id}` a otro vehículo/conductor/carga, y comprueban que los recursos originales vuelven a "Disponible"/"Pendiente" mientras los nuevos quedan "En Ruta"/"Asignada". Manualmente: crear un viaje programado, editarlo cambiando el vehículo, y confirmar en el módulo de Vehículos que el vehículo original volvió a "Disponible".

---

## 3. Un login con usuario o contraseña incorrectos se trataba como "sesión expirada" y recargaba la página de login

**Dónde estaba:** [`js/api-client.js`](js/api-client.js), función `trailersysApiRequest` (antes de la corrección, lo que hoy es la línea 35 aprox.).

**Por qué era un problema:**
`trailersysApiRequest` interceptaba **cualquier** respuesta `401` para tratarla como sesión expirada: limpiaba la sesión y forzaba `window.location.href = "index.html"`. El backend responde `401` tanto para un token vencido como para credenciales de login incorrectas (`AuthController` lanza `BadCredentialsException` con el mensaje "Usuario o contraseña incorrectos.", ver [`GlobalExceptionHandler.java:36`](backend/src/main/java/com/trailersys/backend/common/GlobalExceptionHandler.java)). Como el formulario de login vive en `index.html`, al escribir mal la contraseña el usuario no veía el mensaje real del backend: la página se redirigía a sí misma (recarga), y en el mejor de los casos alcanzaba a ver brevemente "Sesión expirada." — un mensaje que no tiene ningún sentido en la pantalla de login y que no ayuda a saber qué corregir.

**Qué se hizo:**
Se agregó una excepción explícita: cuando la petición es a `/auth/login`, un `401` ya no dispara el `clearSession()` + redirección, sino que cae al manejo genérico de errores, que sí propaga el mensaje real del backend (`error.message`) hasta el `catch` de [`js/auth.js`](js/auth.js), donde se muestra correctamente con `showAlert(...)`.

**Cómo verificar:** con el backend corriendo, ir a `index.html` e intentar iniciar sesión con una contraseña incorrecta. Antes de la corrección la página se recargaba sola o mostraba "Sesión expirada."; ahora debe mostrarse el mensaje "Usuario o contraseña incorrectos." sin recargar la página, permitiendo reintentar.

---

## 4. Un rol de sesión no reconocido otorgaba acceso de Administrador en vez de bloquear el acceso ("fail-open")

**Dónde estaba:** [`js/navigation.js`](js/navigation.js), línea 8 (antes de la corrección):
```js
const roleInfo = TRAILERSYS_ROLES[session.role] || TRAILERSYS_ROLES.administrador;
```

**Por qué era un problema:**
Si `session.role` no coincidía con ninguna clave de `TRAILERSYS_ROLES` (por ejemplo, un rol nuevo agregado en el backend que aún no se tradujo en `ROL_BACKEND_A_FRONTEND` de `js/auth.js`, o una sesión corrupta/manipulada en `sessionStorage`), el código **no bloqueaba el acceso**: degradaba silenciosamente al usuario a permisos de Administrador, mostrando todos los módulos de navegación, incluyendo Auditoría y Configuración. Es un antipatrón de seguridad clásico ("fail-open" en vez de "fail-closed"): un caso límite terminaba dando más acceso, no menos. Cabe aclarar que el backend sigue protegiendo los datos vía `@PreAuthorize` en cada endpoint, así que esto no exponía datos directamente, pero sí mostraba en la interfaz enlaces y secciones a los que el usuario no debería ni siquiera saber que existen.

**Qué se hizo:**
Si `TRAILERSYS_ROLES[session.role]` no existe, ahora se limpia la sesión y se redirige a `index.html` (mismo comportamiento que cuando no hay sesión en absoluto), en vez de usar el rol de Administrador como valor por defecto.

**Cómo verificar:** en la consola del navegador, con una sesión iniciada, ejecutar algo como:
```js
const s = trailersysGetSession(); s.role = "rol_invalido"; trailersysSetSession(s); location.reload();
```
Antes de la corrección, la app cargaba con todos los módulos visibles (incluyendo Auditoría/Configuración). Ahora debe redirigir directamente a la pantalla de login.

---

## 5. XSS almacenado: el campo `foto` de Conductores y Vehículos se insertaba sin escapar en el atributo `src`

**Dónde estaba:**
- [`js/conductores.js`](js/conductores.js) — línea 90 (`setFotoPreview`) y línea 119 (`renderCard`).
- [`js/vehiculos.js`](js/vehiculos.js) — línea 131 (`setFotoPreview`) y línea 164 (`renderCard`).

**Por qué era un problema:**
`foto` se renderiza como `` `<img src="${conductor.foto}" ...>` ``. El resto de los campos del mismo template (nombre, placa) sí pasan por `escapeHtml(...)`, pero `foto` no. El backend (`Conductor.java`, `ConductorRequest.java`, `Vehiculo.java`) no valida el formato de `foto` más allá de guardarlo como texto libre. Cualquier usuario con permiso para gestionar conductores/vehículos (o una llamada directa a la API saltándose el `<input type="file">` del formulario, algo trivial con cualquier cliente HTTP) puede guardar un valor como `x" onerror="alert(document.cookie)` en `foto`, y ese script se ejecuta en el navegador de **cualquier otro usuario** que abra el listado — un XSS almacenado clásico, con impacto directo sobre otras cuentas (incluida la de un Administrador).

**Qué se hizo:** se envolvió `conductor.foto` / `vehiculo.foto` con `escapeHtml(...)` en los 4 puntos donde se inserta como atributo `src`, igual que ya se hacía con los demás campos de usuario en esos mismos templates.

**Cómo verificar:** crear o editar un conductor (o vehículo) y, en la petición a la API (por ejemplo con las herramientas de desarrollador o `curl -X PUT .../api/conductores/{id}`), fijar `"foto": "x\" onerror=\"alert(1)"`. Antes de la corrección, al abrir el listado de Conductores se ejecutaba el `alert`. Ahora el valor se muestra escapado como texto/atributo inofensivo y no se ejecuta ningún script.

---

## 6. XSS en los popups del mapa (Leaflet): origen/destino del viaje sin escapar

**Dónde estaba:** [`js/viajes.js`](js/viajes.js), líneas 617-618, y [`js/seguimiento.js`](js/seguimiento.js), líneas 430-431:
```js
L.marker(origenLatLng).addTo(leafletMapInstance).bindPopup(`Origen: ${viaje.origen}`);
```

**Por qué era un problema:**
Leaflet interpreta el contenido de `bindPopup(...)` como HTML. Los mismos campos `viaje.origen`/`viaje.destino` sí se escapan correctamente en las tarjetas de viaje (`renderCard`, vía `escapeHtml`), pero no en el popup del mapa — una inconsistencia dentro del propio archivo. Si `origen`/`destino` llegan a contener HTML (vía API directa, sin pasar por los `<select>` predefinidos de `js/ecuador-locations.js`), se ejecuta como script al abrir el mapa del viaje.

**Qué se hizo:** se aplicó `escapeHtml(...)` a `viaje.origen` y `viaje.destino` en ambos `bindPopup(...)`, en los dos archivos.

**Cómo verificar:** crear un viaje con `origen` conteniendo `<img src=x onerror=alert(1)>` (vía API directa) y abrir su mapa desde el módulo de Viajes o desde Seguimiento. Antes de la corrección se ejecutaba el `alert` al abrir el popup del marcador; ahora se muestra el texto escapado.

---

## 7. Inyección de fórmulas en la exportación CSV de Reportes

**Dónde estaba:** [`js/reportes.js`](js/reportes.js), función `csvEscape` (antes de la corrección, línea 323).

**Por qué era un problema:**
`csvEscape` solo removía etiquetas HTML y escapaba comillas, pero no neutralizaba valores que Excel/Google Sheets interpretan como fórmulas al abrir el CSV: cualquier celda que empiece con `=`, `+`, `-` o `@`. Un campo como `descripcion` u `observaciones` con un valor tipo `=cmd|'/c calc'!A1` (o variantes que ejecutan macros/abren procesos) se exportaba tal cual, y se ejecutaba automáticamente al abrir el archivo exportado en la hoja de cálculo de quien lo recibe — el ataque clásico de "CSV/Formula Injection".

**Qué se hizo:** en `csvEscape`, si el valor empieza con `=`, `+`, `-` o `@`, se le antepone una comilla simple (`'`) antes de aplicar el resto del escapado. Esto es la mitigación estándar: la hoja de cálculo lo trata como texto literal en vez de como fórmula.

**Cómo verificar:** generar un registro (por ejemplo una carga) con `observaciones` = `=1+1`, exportar el reporte correspondiente a CSV y abrirlo en Excel/Google Sheets. Antes de la corrección la celda mostraba el resultado del cálculo (`2`); ahora debe mostrarse el texto literal `=1+1` (o `'=1+1` según el programa).

---

## Limitaciones conocidas (fuera de alcance de esta corrección)

Durante la revisión se identificaron otros puntos que, por su alcance o riesgo, no se corrigieron en esta pasada — se documentan aquí para que el equipo decida si abordarlos en un trabajo aparte:

- **Condiciones de carrera (TOCTOU)** en `ViajeService.validarDisponibilidad` (asignación concurrente del mismo vehículo/conductor a dos viajes) y en `UsuarioService.impedirUltimoAdministrador` (dos bajas de administrador concurrentes podrían dejar el sistema sin administradores). Requieren bloqueo pesimista o una restricción a nivel de base de datos, un cambio de mayor alcance.
- **Endpoints "legacy" sin paginar** (`GET /api/viajes`, `/api/cargas`, `/api/conductores`, `/api/vehiculos`, `/api/clientes`, `/api/mantenimientos`, `/api/seguimiento/eventos`, servidos por los métodos `listar()` de cada `*Service` con `findAll()` + filtrado en memoria): no los usa el frontend actual (que ya usa exclusivamente `/api/paginas/...`), pero siguen expuestos y autenticados; con la tabla de ~1 millón de registros, invocarlos directamente sería muy costoso.
- **Fuga de `setInterval`/mapa Leaflet en Seguimiento** (`js/seguimiento.js`): el polling de actualización y el mapa del modal de detalle solo se limpian al cerrar el modal explícitamente, no al navegar a otro módulo con el modal abierto.
- **Errores de carga silenciados en Reportes** (`js/reportes.js`): si el backend falla, los reportes muestran "sin datos" en vez de un mensaje de error, ocultando el fallo real.

---

## Resumen de archivos modificados

**Backend:**
- `backend/src/main/java/com/trailersys/backend/common/CatalogoPageController.java`
- `backend/src/main/java/com/trailersys/backend/vehiculo/VehiculoRepository.java`
- `backend/src/main/java/com/trailersys/backend/cliente/ClienteRepository.java`
- `backend/src/main/java/com/trailersys/backend/carga/CargaRepository.java`
- `backend/src/main/java/com/trailersys/backend/viaje/ViajeRepository.java`
- `backend/src/main/java/com/trailersys/backend/mantenimiento/MantenimientoRepository.java`
- `backend/src/main/java/com/trailersys/backend/viaje/ViajeService.java`
- Tests: `VehiculoRepositoryTest.java`, `ClienteRepositoryTest.java`, `CargaRepositoryTest.java`, `ViajeRepositoryTest.java`, `MantenimientoRepositoryTest.java`, `ViajeControllerTest.java`

**Frontend:**
- `js/api-client.js`, `js/vehiculos.js`, `js/clientes.js`, `js/cargas.js`, `js/viajes.js`, `js/mantenimientos.js`, `js/conductores.js`, `js/navigation.js`, `js/seguimiento.js`, `js/reportes.js`
