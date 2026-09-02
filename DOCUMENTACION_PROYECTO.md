# TrailerSys — Documento consolidado de verificación y validación

**Asignatura:** Verificación y Validación de Software
**Proyecto:** TrailerSys — Sistema de gestión de flota y transporte de carga
**Equipo (3 integrantes):**

| # | Integrante | Secciones que expone en el video |
|---|---|---|
| 1 | Diego Zamora | 1. Presentación del proyecto · 2. Técnica Personas |
| 2 | Jefferson Umaginga | 3. Historias de usuario · 4. Casos de prueba · 5. Pruebas funcionales |
| 3 | Fajardos Montes | 6. Pruebas de integración · 7. Automatización de pruebas · 8. Análisis de resultados · 9. Conclusiones |

> La información de este documento coincide con lo que se muestra en el video técnico.
> Toda evidencia proviene del repositorio del proyecto (`README.md`, `INFORME_CORRECCIONES.md`,
> `backend/src/test/`, `js/`, `database/`).

---

## 1. Descripción general del proyecto

### 1.1 Nombre y propósito

**TrailerSys** es un sistema de gestión de flota y transporte de carga por carretera. Centraliza
en una sola aplicación la administración de vehículos, conductores, clientes, cargas, viajes
(con cálculo de ruta y mapa), seguimiento de entregas y mantenimientos, con control de acceso
por rol.

### 1.2 Problema que resuelve

Una empresa transportista pequeña o mediana gestiona su operación con hojas de cálculo,
llamadas y mensajes. Eso produce:

- **Doble asignación**: el mismo vehículo o conductor termina asignado a dos viajes el mismo día.
- **Cargas "perdidas" entre estados**: nadie sabe con certeza si una carga está pendiente, en
  tránsito o entregada.
- **Cero trazabilidad de la ruta**: cuando el cliente pregunta "¿dónde va mi carga?", la oficina
  no tiene respuesta.
- **Sin historial de mantenimiento** confiable por unidad.

TrailerSys resuelve esto con un modelo de datos único, reglas de negocio que **impiden** la
doble asignación, sincronización automática de estados, una línea de tiempo de eventos por
viaje y un portal de autoservicio para el cliente.

### 1.3 Usuarios a los que está dirigido

| Rol | Descripción | Módulos que ve |
|---|---|---|
| **Administrador** | Configura el sistema, gestiona usuarios, revisa auditoría. | Todos |
| **Coordinador / Operador** | Usuario más activo. Crea cargas, arma viajes, asigna vehículo y conductor, atiende reclamos. | Dashboard, Vehículos, Conductores, Cargas, Viajes, Seguimiento, Guías, Reclamos |
| **Responsable de Mantenimiento** | Registra mantenimientos y evidencias por vehículo. | Dashboard, Vehículos, Mantenimientos |
| **Conductor** | Consulta su viaje y registra eventos de seguimiento de su ruta; confirma la llegada. | Dashboard, Viajes, Seguimiento |
| **Supervisor / Consulta** | Solo lectura; valida entregas ya confirmadas por el conductor. | Dashboard, Vehículos, Viajes, Seguimiento, Reportes |
| **Cliente** | Autoservicio: crea pedidos y confirma la recepción de su carga. No ve la operación interna. | Mis pedidos, Configuración |

### 1.4 Principales funcionalidades

1. **Catálogos** de vehículos, conductores y clientes, con estados operativos
   (Disponible, En Ruta, Mantenimiento, Fuera de Servicio…).
2. **Cargas y Viajes**: al armar un viaje se calcula la **ruta real** sobre OpenStreetMap /
   OSRM y se dibuja en un mapa Leaflet; el sistema **rechaza** asignar un vehículo o conductor
   que ya está en otro viaje activo, y **sincroniza automáticamente** los estados de vehículo,
   conductor y carga al crear, editar o cerrar el viaje.
3. **Seguimiento**: línea de tiempo de eventos por viaje, **alertas operativas** (licencia
   vencida, mantenimiento vencido, viaje en curso sin ruta, entrega pendiente de validación) y
   **confirmación + validación de entregas** (el conductor confirma la llegada, el supervisor
   la valida).
4. **Mantenimientos** con tipo (preventivo / correctivo), costo, kilometraje y evidencias.
5. **Reportes** con filtros por rango de fechas, indicadores, exportación a CSV e impresión / PDF,
   más **guías imprimibles** de viajes, mantenimientos y reclamos.
6. **Portal del cliente** (crear pedidos, confirmar recepción) y **gestión de reclamos**.
7. **Dashboard** con indicadores en vivo y disponibilidad de flota.
8. **Auditoría** de acciones y **Configuración** del perfil autenticado.

### 1.5 Tecnologías utilizadas

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.5, API REST, Spring Security con **JWT**, autorización por rol con `@PreAuthorize` en cada endpoint |
| Persistencia | Spring Data JPA / Hibernate sobre **PostgreSQL** |
| Frontend | HTML5, CSS3 y **JavaScript puro** (sin framework ni build step); consumo de la API con `fetch()` |
| Mapas y ruta | Leaflet + OpenStreetMap; cálculo de ruta con OSRM |
| Base de datos | PostgreSQL con scripts de roles/privilegios, auditoría, índices y respaldo/recuperación; validada con **carga de 1 000 000 de registros** (`database/`) |
| Pruebas | **JUnit 5** + **Spring Test / MockMvc**, base **H2 embebida**; **95 pruebas automatizadas** |
| Servidor de desarrollo | `dev/server.js` (Node), solo para servir el frontend estático |

### 1.6 Usuarios de prueba sembrados (`DataSeeder.java`)

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin1234` | Administrador |
| `coordinador` | `coordinador1234` | Coordinador / Operador |
| `mantenimiento` | `mantenimiento1234` | Responsable de Mantenimiento |
| `conductor` | `conductor1234` | Conductor (Luis Herrera) |
| `supervisor` | `supervisor1234` | Supervisor / Consulta |
| `cliente` | `cliente1234` | Cliente (vinculado a "Comercial Andina S.A.") |

**Datos de demostración sembrados:** vehículos `PBA-1234` (Freightliner Cascadia, Disponible) y
`PCD-5678` (Hino 300, En Ruta); conductores Luis Herrera (En Ruta) y Marcia Torres (Disponible);
clientes "Comercial Andina S.A." (Activo) y "Distribuidora El Roble" (Inactivo); cargas "Lote de
telas e insumos textiles" (Pendiente) y "Productos refrigerados para distribución" (En Tránsito);
un viaje Programado Guayaquil→Quito y uno En Curso Ambato→Riobamba.

---

## 2. Técnica Personas

Las Personas son arquetipos concretos de usuario, con nombre, contexto y frustraciones, que se
construyeron **antes** de definir los módulos. El diseño de TrailerSys partió de ellas: no se
partió de "qué pantallas hacer", sino de "qué necesita resolver esta persona real".

### 2.1 Persona A — Marcos Villacís · Coordinador de Operaciones

| Campo | Contenido |
|---|---|
| **A quién representa** | El operador que cada mañana arma los viajes del día en una transportista con ~20 unidades. 38 años, 10 años en logística. Trabaja en un computador de escritorio en la oficina y consulta el celular cuando baja a bodega. |
| **Características** | Trabaja bajo presión, con varias llamadas simultáneas. Conoce la flota de memoria, pero no confía en su memoria cuando hay muchos viajes abiertos. Metódico, odia rehacer trabajo. |
| **Necesidades** | Ver de un vistazo qué vehículos y conductores están libres. Armar un viaje en pocos pasos. Que el sistema **no lo deje** cometer un error de doble asignación. Poder responder al cliente dónde va su carga. |
| **Objetivos** | Cero unidades paradas por mala planificación. Que cada carga tenga siempre un responsable y un estado claro. Cerrar el día sin viajes "a medias". |
| **Problemas / dificultades** | Hoy usa Excel. Ya asignó dos veces el mismo camión el mismo día. Cuando reasigna un viaje, el vehículo anterior queda "ocupado" en su hoja y nadie lo libera. No tiene forma de responder "¿dónde va mi carga?". |
| **Cómo responde el sistema** | El módulo **Viajes** solo ofrece vehículos y conductores en estado *Disponible*. Si intenta asignar uno ya ocupado en un viaje activo, la API responde **409 Conflicto** y la interfaz lo bloquea. El **Dashboard** muestra disponibilidad en vivo. Al crear un viaje "En curso", vehículo y conductor pasan a *En Ruta* solos; al confirmarse la entrega vuelven a *Disponible*. Si el viaje se **reasigna**, el recurso anterior se libera automáticamente. El **Seguimiento** le da la respuesta para el cliente. |

**Cómo influyó en el diseño:** por Marcos se decidió que los estados de vehículo / conductor / carga
**no** se editen a mano, sino que el servicio de Viajes los **sincronice** en cada alta, edición o
cierre — incluida la liberación del recurso anterior en una reasignación. Esa regla es el núcleo
del sistema y es la que más pruebas automatizadas tiene (`ViajeControllerTest`, 23 casos).

### 2.2 Persona B — Luis Herrera · Conductor

| Campo | Contenido |
|---|---|
| **A quién representa** | El conductor en ruta. 45 años, celular de gama media, datos móviles limitados, a menudo con las manos ocupadas. |
| **Características** | Práctico. No quiere "aprender un sistema"; quiere abrir, tocar dos botones y seguir manejando. Desconfía de apps que le piden información que no le corresponde. |
| **Necesidades** | Ver **solo su viaje**, no todo el sistema. Registrar rápido "salí", "parada", "llegué", "novedad en la vía". Que quede constancia de que entregó. |
| **Objetivos** | Que nadie le reclame después una entrega que sí hizo. Que su palabra quede registrada con hora y lugar. |
| **Problemas / dificultades** | Las apps anteriores eran pesadas y mostraban datos de toda la empresa. Una vez registró una hora equivocada (futura) por error y no pudo corregirla. |
| **Cómo responde el sistema** | El rol **Conductor** solo ve Dashboard, Viajes y Seguimiento (`js/roles.js`). Puede **registrar eventos de seguimiento** de su propia ruta y **confirmar la llegada**, pero **no** puede crear viajes ni **validar** su propia entrega (esa validación la hace el Supervisor). El formulario y el backend **rechazan una fecha/hora futura** en un evento. |

**Cómo influyó en el diseño:** de Luis salió la **separación de responsabilidades**
confirmar (conductor) / validar (supervisor), la restricción de módulos del rol Conductor y la
validación de "fecha no futura" en eventos de seguimiento.

### 2.3 Persona C — Andrea Cedeño · Analista de Logística de "Comercial Andina S.A." (Cliente)

| Campo | Contenido |
|---|---|
| **A quién representa** | La contraparte en la empresa **cliente** que contrata los envíos. 30 años, ofimática fluida, coordina despachos semanales. |
| **Características** | Organizada, quiere autonomía. No quiere llamar a la transportista por cada pedido ni por cada consulta de estado. Le preocupa la confidencialidad: no quiere que otros clientes vean sus envíos. |
| **Necesidades** | Crear un pedido de transporte ella misma. Ver el estado de sus cargas. Confirmar que recibió la mercadería. Levantar un reclamo si algo llega mal. |
| **Objetivos** | Registrar el despacho semanal en minutos. Tener respaldo de la recepción. Que sus datos sean **solo suyos**. |
| **Problemas / dificultades** | Antes enviaba los pedidos por correo y no sabía si los habían leído. No tenía comprobante de recepción. |
| **Cómo responde el sistema** | El rol **Cliente** solo ve "Mis pedidos" y "Configuración". Sus pedidos se crean como **cargas Pendientes a su propio nombre**. Solo ve **sus** cargas (aislamiento verificado en `PedidoClienteControllerTest`). Puede **confirmar la recepción** de las cargas entregadas y abrir **reclamos**. |

**Cómo influyó en el diseño:** Andrea justificó todo el **portal de autoservicio del cliente** y,
sobre todo, la regla de **aislamiento entre clientes** (un cliente nunca consulta datos de otro),
que se probó explícitamente.

> Los roles **Supervisor** y **Responsable de Mantenimiento** se modelaron a partir de la matriz de
> permisos (`js/roles.js`) derivada de estas Personas; sus necesidades (consulta/validación y
> registro técnico, respectivamente) están cubiertas por las historias HU-04 y HU-07.

---

## 3. Historias de usuario y criterios de aceptación

Formato: *Como [rol] quiero [necesidad] para [beneficio]*. Cada historia tiene criterios de
aceptación verificables, que se convirtieron en casos de prueba (sección 4) y pruebas
automatizadas (secciones 6 y 7).

### HU-01 — Asignar un viaje a recursos disponibles

- **Rol:** Coordinador.
- **Necesidad:** armar un viaje asignando un vehículo y un conductor.
- **Beneficio:** evitar la doble asignación y las unidades paradas.
- **Criterios de aceptación:**
  1. El formulario de Viajes solo lista vehículos y conductores en estado *Disponible*.
  2. Si se intenta asignar un vehículo/conductor ya ocupado en un viaje activo, el sistema
     responde **409 Conflicto** y no crea el viaje.
  3. Al crear un viaje "En curso", el vehículo y el conductor pasan a *En Ruta*.
  4. Si el viaje incluye una carga, la carga pasa a *En Tránsito*.
- **Implementado en:** módulo **Viajes**. **Pruebas:** `crearViajeConVehiculoYaAsignadoAOtroViajeActivoDaConflicto`,
  `crearViajeConConductorYaAsignadoAOtroViajeActivoDaConflicto`,
  `crearViajeEnCursoSincronizaVehiculoYConductorAEnRuta`,
  `crearViajeEnCursoConCargaSincronizaEstadoATransito` (`ViajeControllerTest`).

### HU-02 — Reasignar un viaje sin dejar recursos bloqueados

- **Rol:** Coordinador.
- **Necesidad:** cambiar el vehículo, conductor o carga de un viaje ya creado.
- **Beneficio:** poder corregir la planificación sin "quemar" unidades.
- **Criterios de aceptación:**
  1. Al reasignar, el vehículo/conductor nuevo pasa a *En Ruta*.
  2. El vehículo/conductor anterior, si no está en otro viaje activo, vuelve a *Disponible*.
  3. La carga anterior, si nadie más la transporta, vuelve a *Pendiente* (salvo que ya esté
     *Entregada*).
- **Pruebas:** `actualizarViajeConOtroVehiculoYConductorLiberaLosAnteriores`,
  `actualizarViajeConOtraCargaLiberaLaCargaAnterior` (`ViajeControllerTest`).
  Corrige el **defecto #2** del `INFORME_CORRECCIONES.md`.

### HU-03 — Registrar eventos de seguimiento de mi ruta

- **Rol:** Conductor.
- **Necesidad:** registrar salida, paradas, incidencias y llegada de mi viaje.
- **Beneficio:** dejar constancia con hora y lugar de lo ocurrido en la ruta.
- **Criterios de aceptación:**
  1. El conductor solo ve y opera sobre sus propios viajes.
  2. Puede crear eventos de tipo Salida, Parada, Incidencia, Llegada.
  3. **No se permite** registrar un evento con fecha/hora **futura**
     (mensaje: "La fecha y hora no pueden ser futuras.").
  4. Un evento sobre un viaje inexistente devuelve **404**.
- **Pruebas:** `crearConsultarYEliminarEvento`, `crearEventoConViajeInexistenteDevuelveNoEncontrado`
  (`SeguimientoControllerTest`); validación de fecha futura en `js/seguimiento.js:569`.

### HU-04 — Confirmar la llegada (conductor) y validar la entrega (supervisor)

- **Rol:** Conductor y Supervisor.
- **Necesidad:** que la entrega tenga dos pasos: quien entrega la confirma, quien controla la valida.
- **Beneficio:** control cruzado; ninguna entrega la cierra una sola persona.
- **Criterios de aceptación:**
  1. Solo el Conductor puede **confirmar la llegada**; hacerlo finaliza el viaje.
  2. Confirmar dos veces, o confirmar un viaje que no está "En curso", devuelve **409**.
  3. Solo el Supervisor puede **validar la entrega**; validar sin confirmación previa devuelve **409**.
  4. El Conductor **no** puede validar; el Supervisor **no** puede confirmar la llegada.
  5. Al confirmarse la entrega, vehículo y conductor vuelven a *Disponible* y la carga a *Entregada*.
- **Pruebas:** `conductorConfirmaLlegadaFinalizaElViajeYQuedaRegistrado`,
  `supervisorNoPuedeConfirmarLlegada`, `confirmarLlegadaDosVecesDaConflicto`,
  `validarEntregaSinConfirmarDaConflicto`, `conductorNoPuedeValidarEntrega`,
  `confirmarEntregaSincronizaVehiculoYConductorADisponible` (`ViajeControllerTest`).

### HU-05 — Crear un pedido de transporte (autoservicio del cliente)

- **Rol:** Cliente.
- **Necesidad:** registrar yo mismo un pedido de envío.
- **Beneficio:** no depender de llamar o escribir a la oficina.
- **Criterios de aceptación:**
  1. El cliente solo ve "Mis pedidos".
  2. El pedido se crea como una **carga en estado Pendiente** a nombre del propio cliente.
  3. El cliente no puede elegir cliente destinatario ni tocar la operación interna.
- **Pruebas:** `PedidoClienteControllerTest` (6 casos).

### HU-06 — Consultar solo mis cargas (aislamiento entre clientes)

- **Rol:** Cliente.
- **Necesidad:** ver el estado de mis envíos y confirmar su recepción.
- **Beneficio:** trazabilidad y comprobante, con mis datos protegidos.
- **Criterios de aceptación:**
  1. El listado de pedidos devuelve **únicamente** las cargas del cliente autenticado.
  2. Un intento de consultar el detalle de una carga de otro cliente devuelve **403/404**.
  3. El cliente puede **confirmar la recepción** de una carga entregada.
- **Pruebas:** `PedidoClienteControllerTest`; sección "Cómo se garantiza el aislamiento entre
  clientes" del `INFORME_CORRECCIONES.md`.

### HU-07 — Registrar mantenimientos con evidencias

- **Rol:** Responsable de Mantenimiento.
- **Necesidad:** registrar cada intervención de un vehículo con su costo y evidencias.
- **Beneficio:** historial técnico confiable por unidad.
- **Criterios de aceptación:**
  1. El rol solo ve Dashboard, Vehículos y Mantenimientos.
  2. Se registra tipo (preventivo/correctivo), fecha, kilometraje, costo y próximo mantenimiento.
  3. Se pueden adjuntar evidencias al mantenimiento.
  4. Otros roles sin permiso reciben **403** al intentar gestionar mantenimientos.
- **Pruebas:** `MantenimientoControllerTest` (8 casos), `MantenimientoRepositoryTest` (3 casos).

### HU-08 — Gestionar usuarios con validación de datos

- **Rol:** Administrador.
- **Necesidad:** crear y editar cuentas de usuario.
- **Beneficio:** que las credenciales y los datos de contacto sean válidos desde el alta.
- **Criterios de aceptación:**
  1. Correo con formato inválido (p. ej. `a@b`) se rechaza ("Ingresa un correo válido.").
  2. Contraseña de menos de 8 caracteres se rechaza.
  3. Al **editar**, la contraseña es opcional; si se deja vacía, no se cambia.
  4. Un usuario con rol Cliente exige seleccionar el cliente asociado.
- **Implementado en:** módulo **Configuración → Usuarios** (`js/admin.js`).
  Corrige los **defectos #8 y #10** del `INFORME_CORRECCIONES.md` (formato de correo y validación
  de contraseña al editar).

### HU-09 — Buscar en todo el catálogo, no solo en la página visible

- **Rol:** Coordinador / Operador.
- **Necesidad:** buscar un vehículo, cliente, carga o viaje por texto o estado.
- **Beneficio:** encontrar cualquier registro, aunque haya miles.
- **Criterios de aceptación:**
  1. La búsqueda y el filtro de estado se resuelven en el **backend** (consulta JPQL paginada),
     no filtrando en memoria la página ya cargada.
  2. Buscar un valor que existe pero no está en las primeras 24 filas **sí** lo encuentra.
- **Pruebas:** `VehiculoRepositoryTest`, `ClienteRepositoryTest`, `CargaRepositoryTest`,
  `ViajeRepositoryTest`, `MantenimientoRepositoryTest`.
  Corrige el **defecto #1** del `INFORME_CORRECCIONES.md`.

### HU-10 — Ver la ruta y el mapa del viaje

- **Rol:** Coordinador / Supervisor / Conductor.
- **Necesidad:** ver el trazado, la distancia y la duración estimada del viaje.
- **Beneficio:** planificar tiempos y responder al cliente.
- **Criterios de aceptación:**
  1. Al crear un viaje con origen y destino, la respuesta incluye la ruta (distancia km,
     duración min y trazado).
  2. Si no se puede calcular la ruta, el viaje se crea igual con ruta nula (no se bloquea).
  3. El mapa muestra origen y destino con textos **escapados** (sin ejecución de HTML).
- **Pruebas:** `crearViajeConRutaDevuelveDatosDeRelacionesYRuta`,
  `crearViajeSinRutaDevuelveRutaNula` (`ViajeControllerTest`); defecto #6 (XSS en popups del mapa).

---

## 4. Casos de prueba

Estructura: **ID · Funcionalidad · Precondiciones · Datos · Pasos · Resultado esperado ·
Resultado obtenido · Estado.**

> Cobertura de los tres tipos exigidos:
> - **Caso exitoso:** CP-01, CP-04, CP-07.
> - **Caso con validación:** CP-05, CP-06, CP-10, CP-14.
> - **Caso que produce / permite identificar un error:** CP-02, CP-09, CP-13, CP-15
>   (defectos reales encontrados durante las pruebas, documentados en `INFORME_CORRECCIONES.md`).

### CP-01 — Inicio de sesión con credenciales válidas *(exitoso)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Autenticación / login |
| Precondiciones | Backend en ejecución; usuario `admin` sembrado |
| Datos | usuario: `admin` · contraseña: `admin1234` |
| Pasos | 1. Abrir `index.html`. 2. Ingresar usuario y contraseña. 3. Pulsar "Ingresar". |
| Resultado esperado | HTTP 200; la respuesta incluye `token` y `rol`; redirige a `app.html` y muestra el Dashboard. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `AuthControllerTest.loginConCredencialesValidasDevuelveToken` |

### CP-02 — Inicio de sesión con contraseña incorrecta *(identifica error, ya corregido)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Autenticación / manejo de error de credenciales |
| Precondiciones | Backend en ejecución |
| Datos | usuario: `admin` · contraseña: `incorrecta` |
| Pasos | 1. Abrir `index.html`. 2. Ingresar credenciales incorrectas. 3. Pulsar "Ingresar". |
| Resultado esperado | HTTP 401; se muestra el mensaje "Usuario o contraseña incorrectos." **sin recargar** la página; se puede reintentar. |
| Resultado obtenido (antes de corregir) | ❌ La página se recargaba sola y mostraba "Sesión expirada." — mensaje sin sentido en el login. |
| Resultado obtenido (tras corrección) | ✅ Igual al esperado. |
| Estado | ❌ → ✅ **Corregido** — defecto #3 del `INFORME_CORRECCIONES.md` |
| Prueba automatizada | `AuthControllerTest.loginConCredencialesInvalidasDevuelve401` |

### CP-03 — Inicio de sesión con campos vacíos *(validación)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Autenticación / validación de entrada |
| Precondiciones | Backend en ejecución |
| Datos | usuario: `""` · contraseña: `""` |
| Pasos | 1. Abrir `index.html`. 2. Dejar los campos vacíos. 3. Pulsar "Ingresar". |
| Resultado esperado | HTTP 400; no se intenta autenticar; el formulario indica campos obligatorios. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `AuthControllerTest.loginSinCamposDevuelve400` |

### CP-04 — Alta de vehículo con datos válidos *(exitoso)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Vehículos — crear |
| Precondiciones | Sesión como `admin` o `coordinador` |
| Datos | Placa `PXY-9090` · Marca `Volvo` · Modelo `FH` · Tipo `Tráiler` · Año `2022` · Color `Rojo` · Kilometraje `50000` · Capacidad `30000` |
| Pasos | 1. Vehículos → "Nuevo vehículo". 2. Llenar el formulario con los datos. 3. Guardar. |
| Resultado esperado | HTTP 201; el vehículo aparece en el listado con estado *Disponible*. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `VehiculoControllerTest` (crear/listar) |

### CP-05 — Alta de vehículo con año inválido *(validación)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Vehículos — validación de campo "Año" |
| Precondiciones | Sesión como `admin` |
| Datos | Año `1500` (y variante: `2030`) — el resto de campos válidos |
| Pasos | 1. Vehículos → "Nuevo vehículo". 2. Ingresar el año inválido. 3. Guardar. |
| Resultado esperado | No se guarda; mensaje "Ingresa un año entre 1980 y 2027." en el campo Año. |
| Resultado obtenido | Igual al esperado (corregido en commit `c0a51ea`). |
| Estado | ✅ **Aprobado** |
| Referencia | `js/vehiculos.js:380` |

### CP-06 — Alta de vehículo con capacidad negativa *(validación)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Vehículos — validación numérica de "Capacidad" y "Kilometraje" |
| Precondiciones | Sesión como `admin` |
| Datos | Capacidad `-5` · Kilometraje `-1` |
| Pasos | 1. Vehículos → "Nuevo vehículo". 2. Ingresar valores negativos. 3. Guardar. |
| Resultado esperado | No se guarda; mensaje de valor inválido en ambos campos. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Referencia | `js/vehiculos.js:384-388` |

### CP-07 — Crear viaje con vehículo disponible *(exitoso)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Viajes — crear y sincronizar estados |
| Precondiciones | Sesión como `coordinador`; vehículo `PBA-1234` y conductor Marcia Torres en *Disponible*; carga en *Pendiente* |
| Datos | Origen `Guayaquil` · Destino `Quito` · Vehículo `PBA-1234` · Conductor `Marcia Torres` · Carga `Lote de telas...` · Estado `En curso` |
| Pasos | 1. Viajes → "Nuevo viaje". 2. Seleccionar recursos (el desplegable solo muestra disponibles). 3. Guardar. 4. Abrir Vehículos y Cargas. |
| Resultado esperado | HTTP 201; el viaje se crea con ruta calculada; el vehículo y el conductor quedan *En Ruta*; la carga queda *En Tránsito*. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `crearViajeEnCursoSincronizaVehiculoYConductorAEnRuta`, `crearViajeEnCursoConCargaSincronizaEstadoATransito` |

### CP-08 — Crear viaje con vehículo ya asignado *(regla de negocio)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Viajes — impedir doble asignación |
| Precondiciones | Existe un viaje activo con el vehículo `PCD-5678` |
| Datos | Nuevo viaje usando el mismo vehículo `PCD-5678` |
| Pasos | 1. Viajes → "Nuevo viaje". 2. Forzar el uso del vehículo ocupado (vía API o quitando el filtro). 3. Guardar. |
| Resultado esperado | HTTP 409 Conflicto; no se crea el viaje; mensaje explicando que el vehículo está en otro viaje activo. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `crearViajeConVehiculoYaAsignadoAOtroViajeActivoDaConflicto` |

### CP-09 — Reasignar un viaje libera el recurso anterior *(identifica error, ya corregido)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Viajes — reasignación y liberación de recursos |
| Precondiciones | Viaje "Programado" con Vehículo A (queda *En Ruta*) y Vehículo B *Disponible* |
| Datos | Editar el viaje y cambiar Vehículo A → Vehículo B |
| Pasos | 1. Viajes → editar el viaje. 2. Cambiar el vehículo. 3. Guardar. 4. Abrir Vehículos. |
| Resultado esperado | B queda *En Ruta*; **A vuelve a *Disponible***. |
| Resultado obtenido (antes) | ❌ A quedaba *En Ruta* para siempre y ya no se podía asignar a ningún viaje nuevo. |
| Resultado obtenido (tras corrección) | ✅ Igual al esperado. |
| Estado | ❌ → ✅ **Corregido** — defecto #2 del `INFORME_CORRECCIONES.md` |
| Prueba automatizada | `actualizarViajeConOtroVehiculoYConductorLiberaLosAnteriores` |

### CP-10 — Evento de seguimiento con fecha futura *(validación)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Seguimiento — validación de fecha del evento |
| Precondiciones | Sesión como `conductor`; viaje "En curso" asignado |
| Datos | Fecha/hora del evento = mañana |
| Pasos | 1. Seguimiento → abrir el viaje. 2. "Registrar evento". 3. Poner fecha futura. 4. Guardar. |
| Resultado esperado | No se guarda; mensaje "La fecha y hora no pueden ser futuras." |
| Resultado obtenido | Igual al esperado (commit `a4a9c24`). |
| Estado | ✅ **Aprobado** |
| Referencia | `js/seguimiento.js:569` |

### CP-11 — El conductor no puede validar su propia entrega *(seguridad / rol)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Viajes — separación confirmar / validar |
| Precondiciones | Viaje con entrega ya confirmada por el conductor |
| Datos | Petición de "validar entrega" con token de rol Conductor |
| Pasos | 1. Como `conductor`, confirmar la llegada. 2. Intentar validar la entrega. |
| Resultado esperado | HTTP 403 Prohibido; la validación queda pendiente para el Supervisor. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `conductorNoPuedeValidarEntrega`, `supervisorNoPuedeConfirmarLlegada` |

### CP-12 — El cliente solo ve sus propias cargas *(aislamiento)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Portal del cliente — aislamiento entre clientes |
| Precondiciones | Cargas de al menos dos clientes distintos en la base |
| Datos | Sesión como `cliente` (vinculado a "Comercial Andina S.A.") |
| Pasos | 1. Login como `cliente`. 2. Abrir "Mis pedidos". 3. Intentar consultar por API el detalle de una carga de otro cliente. |
| Resultado esperado | El listado devuelve solo cargas de "Comercial Andina S.A."; el detalle ajeno devuelve 403/404. |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Prueba automatizada | `PedidoClienteControllerTest` |

### CP-13 — Búsqueda de un registro fuera de la primera página *(identifica error, ya corregido)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Búsqueda y filtro de estado en catálogos |
| Precondiciones | Más de 24 registros en el catálogo; el buscado no está en las primeras 24 filas |
| Datos | Texto o estado de un registro conocido "antiguo" |
| Pasos | 1. Abrir Vehículos (o Clientes / Cargas / Viajes). 2. Escribir el criterio en el buscador. |
| Resultado esperado | El registro aparece en los resultados. |
| Resultado obtenido (antes) | ❌ No aparecía: el filtro solo operaba sobre los 24 registros ya cargados en el navegador. |
| Resultado obtenido (tras corrección) | ✅ Aparece: la búsqueda ahora se resuelve en el backend. |
| Estado | ❌ → ✅ **Corregido** — defecto #1 del `INFORME_CORRECCIONES.md` |
| Prueba automatizada | `VehiculoRepositoryTest`, `ClienteRepositoryTest`, `CargaRepositoryTest`, `ViajeRepositoryTest`, `MantenimientoRepositoryTest` |

### CP-14 — Alta de usuario con correo mal formado *(validación)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Configuración → Usuarios — validación de correo |
| Precondiciones | Sesión como `admin` |
| Datos | Correo `a@b` (sin dominio) · contraseña `clave123` (≥ 8) |
| Pasos | 1. Configuración → Usuarios → "Nuevo usuario". 2. Ingresar el correo mal formado. 3. Guardar. |
| Resultado esperado | No se guarda; mensaje "Ingresa un correo válido." |
| Resultado obtenido | Igual al esperado. |
| Estado | ✅ **Aprobado** |
| Referencia | `js/admin.js` (`EMAIL_REGEX`); defecto #8 del `INFORME_CORRECCIONES.md` |

### CP-15 — Exportación CSV neutraliza inyección de fórmulas *(identifica error, ya corregido)*

| Campo | Contenido |
|---|---|
| Funcionalidad | Reportes — exportar CSV |
| Precondiciones | Un registro cuyo texto empiece con `=`, `+`, `-` o `@` (p. ej. observación `=1+1`) |
| Datos | Reporte con esa fila |
| Pasos | 1. Reportes → generar un reporte. 2. "Exportar CSV". 3. Abrir el archivo en Excel / Sheets. |
| Resultado esperado | La celda se muestra como texto literal; **no** se evalúa como fórmula. |
| Resultado obtenido (antes) | ❌ Excel/Sheets interpretaba la celda como fórmula. |
| Resultado obtenido (tras corrección) | ✅ La celda se prefija y queda inerte. |
| Estado | ❌ → ✅ **Corregido** — defecto #7 del `INFORME_CORRECCIONES.md` |
| Referencia | `js/reportes.js:523` (`csvEscape`) |

---

## 5. Pruebas funcionales

### 5.1 Qué son

Las pruebas funcionales verifican que **cada funcionalidad hace lo que exige el requerimiento**,
observando **entradas y salidas** desde afuera (caja negra): se envía un dato y se comprueba el
resultado, sin importar la implementación interna. En TrailerSys se automatizaron como pruebas de
controlador con **Spring MockMvc**: se levanta la API y se le hacen peticiones HTTP reales contra
una base **H2 embebida**.

### 5.2 Funcionalidades cubiertas

| Funcionalidad (requerimiento) | Clase de prueba | Casos representativos |
|---|---|---|
| Inicio de sesión y emisión de JWT | `AuthControllerTest` (3) | login válido → token + rol; login inválido → 401; campos vacíos → 400 |
| Vehículos: alta, listado, edición, validación | `VehiculoControllerTest` (4) | crear, listar, actualizar, no autorizado sin token |
| Conductores: reglas de alta y estado | `ConductorControllerTest` (9) | duplicado de licencia, estados, permisos por rol |
| Cargas: ciclo de estados | `CargaControllerTest` (5) | crear, listar, transición de estado, permisos |
| Portal del cliente | `PedidoClienteControllerTest` (6) | crear pedido como carga Pendiente propia; ver solo lo propio; confirmar recepción |
| Seguimiento: eventos y alertas | `SeguimientoControllerTest` (9) | crear/consultar/eliminar evento; viaje inexistente → 404; alertas de licencia vencida, mantenimiento vencido, viaje sin ruta, entrega pendiente; supervisor consulta pero no crea |
| Mantenimientos | `MantenimientoControllerTest` (8) | registrar, evidencias, permisos por rol |
| Dashboard: indicadores | `DashboardControllerTest` (2) | totales y disponibilidad |

### 5.3 Evidencia funcional en el sistema (demostración en vivo)

1. **Inicio de sesión:** login correcto con `admin/admin1234` → Dashboard; login con contraseña
   incorrecta → mensaje "Usuario o contraseña incorrectos." sin recargar (CP-01, CP-02).
2. **Creación de información:** crear un vehículo (CP-04) y un viaje (CP-07).
3. **Modificación de registros:** editar un cliente y ver el cambio en el listado.
4. **Eliminación de información:** eliminar un evento de seguimiento (queda auditado).
5. **Búsquedas:** buscar en Vehículos un registro que no está en la primera página → aparece (CP-13).
6. **Validación de formularios:** año inválido en Vehículos (CP-05); correo inválido en Usuarios
   (CP-14); fecha futura en Seguimiento (CP-10).
7. **Proceso propio del proyecto:** armar un viaje "En curso" y comprobar que vehículo, conductor
   y carga cambian de estado automáticamente (CP-07).

---

## 6. Pruebas de integración

### 6.1 Propósito

Las pruebas de integración verifican que **varios componentes funcionan juntos correctamente**:
no se prueba una función aislada, sino el recorrido completo
**Interfaz → API (controlador) → lógica de negocio (servicio) → base de datos → respuesta**.

### 6.2 Proceso elegido: "Crear / editar un viaje"

La clase `ViajeControllerTest` (**23 casos**) se ejecuta con `@SpringBootTest` +
`@AutoConfigureMockMvc`: levanta **todo el contexto de Spring** y una base **H2**, hace la
petición HTTP con MockMvc y verifica **tanto la respuesta JSON como el estado que quedó en la
base de datos** para varias entidades.

**Componentes que intervienen y cómo interactúan:**

| Paso | Componente | Qué hace |
|---|---|---|
| 1 | **Frontend** (`js/viajes.js`) | Envía `POST /api/viajes` con el token JWT en la cabecera. |
| 2 | `JwtAuthenticationFilter` + `SecurityConfig` | Valida el token y el rol; `@PreAuthorize` autoriza el endpoint. |
| 3 | `ViajeController` | Recibe el `ViajeRequest`, delega en el servicio. |
| 4 | `ViajeService` | Aplica reglas: recurso *Disponible*, no doble asignación, cálculo de ruta (OSRM), sincronización de estados y liberación de recursos anteriores. |
| 5 | `ViajeRepository`, `VehiculoRepository`, `ConductorRepository`, `CargaRepository` | Persisten los cambios en 4 tablas. |
| 6 | `ViajeController` → **Frontend** | Devuelve `ViajeResponse` en JSON (con datos denormalizados de las relaciones y la ruta). |

**Qué se verifica en la prueba:**

- `crearViajeConRutaDevuelveDatosDeRelacionesYRuta` — el JSON de salida trae placa del vehículo,
  nombre del conductor, nombre del cliente y la ruta (km, min, trazado).
- `crearViajeConVehiculoInexistenteDevuelveNoEncontrado` — 404 si el vehículo no existe.
- `crearViajeConCargaDeOtroClienteDaError` — no se puede montar en el viaje una carga de otro cliente.
- `crearViajeEnCursoSincronizaVehiculoYConductorAEnRuta` — tras el `POST`, **en la base** el
  vehículo y el conductor quedan *En Ruta*.
- `confirmarEntregaSincronizaVehiculoYConductorADisponible` — tras confirmar la entrega, **en la
  base** vuelven a *Disponible* y la carga a *Entregada*.
- `actualizarViajeConOtroVehiculoYConductorLiberaLosAnteriores` — tras el `PUT`, el recurso
  anterior vuelve a *Disponible* y el nuevo queda *En Ruta*.

Una sola prueba de integración recorre autenticación, autorización, controlador, servicio,
cuatro repositorios y la base de datos, y comprueba la coherencia de todo el conjunto.

### 6.3 Otras integraciones probadas

- **Seguimiento ↔ Viaje ↔ Vehículo ↔ Conductor ↔ Mantenimiento:**
  `alertasDetectanLicenciaVencidaYVehiculoEnMantenimiento`,
  `alertaDeEntregaPendienteApareceTrasConfirmarYDesapareceTrasValidar` (`SeguimientoControllerTest`).
- **Repositorio ↔ base de datos:** los `...RepositoryTest` (`@DataJpaTest`) insertan registros y
  verifican que las consultas JPQL de búsqueda paginada los encuentran por texto y por estado.

---

## 7. Automatización de pruebas

### 7.1 Herramienta y enfoque

La automatización tiene dos niveles:

1. **Backend (95 pruebas):** **JUnit 5 + Spring Test** sobre una base **H2 embebida** (no requiere
   PostgreSQL en ejecución). Se eligió esta stack porque el backend es Java / Spring Boot y
   MockMvc permite probar la API **de punta a punta** (HTTP real, seguridad real, base real) sin
   desplegar nada.
2. **Interfaz — end-to-end (E2E) con Playwright:** una suite en `e2e/` que abre un navegador
   real (Chromium), escribe en el formulario de login y comprueba el resultado como lo haría una
   persona. Cubre el inicio de sesión, que es la puerta de entrada del sistema y donde además se
   había detectado un defecto (defecto #3 del `INFORME_CORRECCIONES.md`).

### 7.2 Estructura de las pruebas

```
backend/src/test/java/com/trailersys/backend/
├── auth/          AuthControllerTest ................ 3
├── vehiculo/      VehiculoControllerTest ........... 4   VehiculoRepositoryTest .......... 2
├── conductor/     ConductorControllerTest ......... 9   ConductorRepositoryTest ........ 1
├── cliente/       ClienteControllerTest ........... 5   ClienteRepositoryTest .......... 2
├── carga/         CargaControllerTest ............. 5   CargaRepositoryTest ............ 3
├── viaje/         ViajeControllerTest ............ 23   ViajeRepositoryTest ........... 3   ViajeSimulacionServiceTest .... 7
├── seguimiento/   SeguimientoControllerTest ....... 9   SeguimientoEventoRepositoryTest  1
├── mantenimiento/ MantenimientoControllerTest ..... 8   MantenimientoRepositoryTest .... 3
├── dashboard/     DashboardControllerTest ......... 2
├── pedido/        PedidoClienteControllerTest ..... 6
└── TrailerSysBackendApplicationTests (carga de contexto) 1

e2e/                          # pruebas end-to-end (Playwright)
├── playwright.config.js      # arranca el frontend estático (:5173) y configura Chromium
├── package.json
└── tests/
    └── login.spec.js ........ 4   inicio de sesión desde el navegador real
```

Tipos de prueba:

- **`...ControllerTest`** — `@SpringBootTest` + MockMvc. Prueban la **API completa**: petición
  HTTP, JWT, autorización por rol, controlador, servicio, repositorio y base H2.
- **`...RepositoryTest`** — `@DataJpaTest`. Prueban las **consultas** contra la base (búsqueda
  paginada por texto y estado, unicidad, relaciones).
- **`ViajeSimulacionServiceTest`** — pruebas de **servicio** aisladas para la simulación
  automática del avance del viaje.
- **`e2e/tests/login.spec.js`** (Playwright) — pruebas **E2E** de interfaz: abren el navegador,
  usan el formulario real y verifican navegación, mensajes y sesión.

### 7.3 Qué se automatizó y por qué

| Área automatizada | Motivo |
|---|---|
| Login y JWT | Es la puerta de entrada: no puede romperse nunca. |
| Reglas de negocio de Viajes (23 casos) | Es el núcleo del sistema y lo más fácil de romper al tocar código. |
| Aislamiento del portal de clientes | Riesgo de privacidad: un cliente no puede ver datos de otro. |
| Separación confirmar / validar entrega | Control cruzado exigido por el requerimiento (Persona B). |
| Alertas operativas | Dependen de datos de 4 módulos; una regresión pasa desapercibida sin prueba. |
| Búsqueda paginada en repositorios | Nació de un bug real (defecto #1). |
| Sincronización y liberación de estados | Nació de un bug real (defecto #2). |
| Inicio de sesión desde el navegador (E2E, Playwright) | Es el flujo que todo usuario ejecuta y donde estuvo el defecto #3. |

### 7.4 Ejecución

**Backend (95 pruebas):**

```bash
cd backend
./mvnw test
```

**Resultado:** `Tests run: 95, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS`.

Para ver un fallo controlado durante el video, ejecutar un subconjunto tras romper una aserción a
propósito y luego revertir:

```bash
./mvnw test -Dtest=AuthControllerTest
```

**E2E de interfaz (Playwright):** requiere el **backend en :8080** y **PostgreSQL** en marcha
(ver `e2e/README.md`); el frontend en :5173 lo arranca Playwright solo.

```bash
cd e2e
npm install
npx playwright install chromium
npx playwright test
```

**Resultado esperado:** `4 passed`. El reporte HTML se abre con `npx playwright show-report`.

---

## 8. Evidencias de ejecución

Capturas y salidas a incluir en el informe y mostrar en el video (numeradas para referencia):

| # | Evidencia | Dónde se obtiene | Sección |
|---|---|---|---|
| E-01 | Login correcto → Dashboard | Navegador, `index.html` | 5, CP-01 |
| E-02 | Mensaje "Usuario o contraseña incorrectos." sin recarga | Navegador | 4, CP-02 |
| E-03 | Alta de vehículo válida en el listado | Módulo Vehículos | 4, CP-04 |
| E-04 | Mensaje "Ingresa un año entre 1980 y 2027." | Módulo Vehículos | 4, CP-05 |
| E-05 | Crear viaje "En curso" → vehículo/conductor/carga cambian de estado | Módulos Viajes, Vehículos, Cargas | 4, CP-07 · 6 |
| E-06 | Error 409 al reutilizar un vehículo ocupado | Módulo Viajes | 4, CP-08 |
| E-07 | Reasignar viaje → vehículo anterior vuelve a *Disponible* | Módulo Viajes / Vehículos | 4, CP-09 |
| E-08 | Mensaje "La fecha y hora no pueden ser futuras." | Módulo Seguimiento | 4, CP-10 |
| E-09 | Búsqueda encuentra un registro fuera de la primera página | Cualquier catálogo | 4, CP-13 |
| E-10 | Portal del cliente muestra solo cargas propias | Login `cliente` | 4, CP-12 |
| E-11 | Salida de consola `Tests run: 95 ... BUILD SUCCESS` | `./mvnw test` | 7 |
| E-12 | Salida de consola de una prueba fallida (rota a propósito) y luego verde | `./mvnw test -Dtest=...` | 7 |
| E-13 | Árbol de `backend/src/test/` en el IDE | IntelliJ / VS Code | 7 |
| E-14 | `INFORME_CORRECCIONES.md` abierto en las secciones 1, 2, 3 y 7 | Repositorio | 8, 10 |
| E-15 | Ejecución de `npx playwright test` con `4 passed` | Terminal, carpeta `e2e/` | 7 |
| E-16 | Reporte HTML de Playwright (`show-report`) con los 4 casos de login | Navegador | 7 |
| E-17 | Un caso de Playwright fallando (backend apagado o aserción rota) | Terminal `e2e/` | 7 |

> Guardar las capturas en `docs/evidencias/` con el nombre `E-01.png`, `E-02.png`, …

---

## 9. Resultados

### 9.1 Resultado de las pruebas automatizadas

| Métrica | Valor |
|---|---|
| Pruebas de backend (JUnit + MockMvc) | **95** — 95 aprobadas, 0 fallidas · `BUILD SUCCESS` |
| Pruebas E2E de interfaz (Playwright) | **4** — 4 aprobadas, 0 fallidas |
| Base usada | H2 embebida (backend) · PostgreSQL (E2E) |

### 9.2 Resultado por funcionalidad

| Funcionalidad | Casos | Estado |
|---|---|---|
| Inicio de sesión y JWT | CP-01, CP-02, CP-03 | ✅ Correcto |
| Vehículos (alta / validación) | CP-04, CP-05, CP-06 | ✅ Correcto |
| Viajes: asignación y no doble asignación | CP-07, CP-08 | ✅ Correcto |
| Viajes: reasignación y liberación de recursos | CP-09 | ✅ Correcto (tras corrección) |
| Seguimiento: eventos y validación de fecha | CP-10 | ✅ Correcto |
| Roles: confirmar / validar entrega | CP-11 | ✅ Correcto |
| Portal del cliente: aislamiento | CP-12 | ✅ Correcto |
| Búsqueda en catálogos | CP-13 | ✅ Correcto (tras corrección) |
| Usuarios: validación de correo | CP-14 | ✅ Correcto |
| Reportes: exportación CSV segura | CP-15 | ✅ Correcto (tras corrección) |

### 9.3 Errores encontrados durante la verificación

Documentados en `INFORME_CORRECCIONES.md` (12 hallazgos). Los más relevantes:

| # | Defecto | Tipo | Estado |
|---|---|---|---|
| 1 | La búsqueda y el filtro de estado solo operaban sobre los 24 registros de la página visible | Funcional (crítico con gran volumen) | Corregido + test |
| 2 | Al reasignar un viaje, el vehículo/conductor anterior quedaba *En Ruta* para siempre | Funcional / datos | Corregido + test |
| 3 | Un login con contraseña incorrecta se mostraba como "Sesión expirada" y recargaba la página | Usabilidad / manejo de error | Corregido + test |
| 4 | Un rol de sesión desconocido daba acceso de Administrador ("fail-open") | Seguridad | Corregido |
| 5 | XSS almacenado en el campo `foto` de Conductores y Vehículos | Seguridad | Corregido |
| 6 | XSS en los popups del mapa (origen/destino sin escapar) | Seguridad | Corregido |
| 7 | Inyección de fórmulas en la exportación CSV de Reportes | Seguridad | Corregido |
| 8 | El formulario de Usuarios no validaba el formato del correo | Validación | Corregido |
| 9 | La contraseña no se validaba bien al editar un usuario existente | Validación | Corregido |
| 10 | Reportes salían con una hoja en blanco al imprimir | Funcional / impresión | Corregido |

---

## 10. Análisis de resultados

- **Qué funcionó correctamente:** el flujo central del sistema —catálogos, armado de viajes con
  ruta y mapa, sincronización de estados, confirmación y validación de entregas, portal del
  cliente y reportes— pasa las 95 pruebas automatizadas y se comportó según los requerimientos en
  la prueba manual.

- **Qué errores fueron encontrados:** 12 defectos, varios **no visibles en una demostración
  "feliz"**: dos de comportamiento funcional (búsqueda que ignoraba la base completa; recursos que
  quedaban bloqueados tras una reasignación) y cuatro de **seguridad** (fail-open de roles, dos
  XSS almacenados, inyección de fórmulas en CSV). Sin pruebas sistemáticas se habrían entregado.

- **Qué validaciones fueron necesarias:** fecha no futura en eventos de seguimiento; rango de año
  y no negatividad de kilometraje/capacidad en Vehículos; formato de correo y longitud mínima de
  contraseña en Usuarios; identificación y teléfono en Conductores/Clientes; no permitir fecha
  futura ni doble confirmación de entrega; escape de HTML en todos los campos de texto que se
  renderizan (tarjetas, mapa, CSV).

- **Qué aspectos deberían mejorarse:**
  1. Los filtros de tipo/marca en Vehículos aún operan solo sobre la página cargada (limitación
     preexistente y menor, documentada).
  2. La automatización **end-to-end con Playwright** hoy solo cubre el **inicio de sesión**;
     falta extenderla al alta de un viaje y a la confirmación / validación de una entrega desde
     el navegador.
  3. Falta una **prueba de carga formal** más allá de la validación con 1 000 000 de registros en
     base.
  4. Convendría **pruebas de contrato** para las respuestas JSON de la API.

- **Qué importancia tuvo probar antes de dar el proyecto por terminado:** de los 12 defectos,
  cuatro eran de seguridad y dos hacían que una funcionalidad central **pareciera rota en uso
  real**. Ninguno aparecía en la ruta principal de una demo. Las pruebas —sobre todo las de
  integración, que revelan fallas en **cómo se coordinan los módulos**— fueron lo que permitió
  detectarlos, corregirlos y blindarlos con un test para que no reaparezcan.

---

## 11. Conclusiones

1. **Partir de Personas** (Marcos, Luis, Andrea) evitó construir módulos sin dueño y produjo
   decisiones de diseño concretas: sincronización automática de estados, separación
   confirmar/validar, aislamiento entre clientes y restricción de módulos por rol.

2. **Escribir criterios de aceptación verificables** hizo casi mecánico el paso
   *historia de usuario → caso de prueba → prueba automatizada*: cada criterio de HU-01…HU-10
   tiene su caso (CP-01…CP-15) y, en la mayoría, su test en `backend/src/test/`.

3. **Las pruebas de integración fueron las de mayor valor.** Los bugs importantes no estaban en
   una función aislada, sino en la coordinación entre viaje, vehículo, conductor y carga; solo
   una prueba que levanta el contexto completo y revisa la base los expone.

4. **La automatización dio confianza para seguir corrigiendo.** Cada corrección del
   `INFORME_CORRECCIONES.md` entró acompañada de su prueba; las 95 pruebas verdes son la red que
   permitió refactorizar sin miedo.

5. **¿Qué tan confiable consideramos el sistema hoy?** El **backend: alto** — 95 pruebas cubren
   autenticación, autorización, reglas de negocio y aislamiento de datos, y todos los defectos
   detectados están corregidos y cubiertos. La **interfaz: medio-alto** — funcional, validada
   manualmente y con una primera prueba E2E de login automatizada en Playwright; falta ampliar
   esa cobertura E2E a los demás flujos, y ese es el siguiente paso.

---

## Anexo A — Correspondencia video ↔ documento

| Bloque del video | Minuto aprox. | Expositor | Secciones de este documento | Qué se muestra en pantalla |
|---|---|---|---|---|
| 1. Presentación del proyecto | 0:00–2:30 | Diego Zamora | 1 | `README.md`, login, recorrido del menú por rol |
| 2. Técnica Personas | 2:30–5:00 | Diego Zamora | 2 | Fichas de Persona (este documento) |
| 3. Historias de usuario | 5:00–7:00 | Jefferson Umaginga | 3 | Tabla de HU; HU-01 demostrada en Viajes |
| 4. Casos de prueba | 7:00–9:30 | Jefferson Umaginga | 4 | Tabla de casos; CP-01, CP-05 y CP-02/CP-13 en vivo |
| 5. Pruebas funcionales | 9:30–11:30 | Jefferson Umaginga | 5 | Árbol de tests + demo de búsqueda / edición / validación |
| 6. Pruebas de integración | 11:30–13:30 | Fajardos Montes | 6 | `ViajeControllerTest`; viaje creado → estados cambian |
| 7. Automatización | 13:30–15:30 | Fajardos Montes | 7 | Estructura de tests; `./mvnw test` y `npx playwright test` en vivo |
| 8. Análisis de resultados | 15:30–17:00 | Fajardos Montes | 9, 10 | `INFORME_CORRECCIONES.md`; tabla de resultados |
| 9. Conclusiones | 17:00–18:00 | Fajardos Montes | 11 | Cierre (cámara) |

## Anexo B — Guion de la Parte 1 (Diego Zamora · 4–5 min)

### Bloque 1 — Presentación del proyecto (≈2:30)

> "Buenas. Somos el equipo de **TrailerSys**. En este video vamos a demostrar, sobre nuestro
> propio sistema, cómo aplicamos los conceptos de la asignatura: requerimientos, historias de
> usuario, técnica Personas, casos de prueba, pruebas funcionales, pruebas de integración y
> automatización.
>
> **TrailerSys es un sistema de gestión de flota y transporte de carga.** El problema que
> resuelve: una transportista mediana maneja hoy sus vehículos, conductores, clientes, cargas y
> viajes con hojas de cálculo y llamadas. Eso provoca **doble asignación** del mismo camión o
> conductor a dos viajes, cargas que se pierden entre estados y **ninguna trazabilidad** de la
> ruta. TrailerSys centraliza todo eso con control por rol y reglas que impiden esos errores.
>
> *(Mostrar la tabla de roles del README)*
> **¿Quién lo usa?** Cinco perfiles internos —Administrador, Coordinador, Responsable de
> Mantenimiento, Conductor y Supervisor— y uno externo, el **Cliente**, que entra a un portal de
> autoservicio para crear sus pedidos y confirmar la recepción, sin ver la operación interna.
>
> *(Login como `admin/admin1234`; recorrer el menú)*
> **Funcionalidades principales:** catálogos de vehículos, conductores y clientes con estados;
> **cargas y viajes** —al armar un viaje se calcula la ruta real sobre OpenStreetMap y se dibuja
> en un mapa; el sistema **rechaza** asignar un vehículo o conductor ya ocupado y **sincroniza
> solo** los estados—; **seguimiento** con línea de tiempo, alertas y confirmación/validación de
> entregas; **mantenimientos** con evidencias; **reportes** con filtros por fecha, CSV e
> impresión; y el **portal del cliente** con reclamos.
>
> **Tecnologías:** backend en **Java 21 + Spring Boot 3.5**, API REST con **JWT** y autorización
> por rol en cada endpoint, sobre **PostgreSQL**. Frontend en **HTML, CSS y JavaScript puro**,
> sin framework, consumiendo la API con `fetch`. La base de datos se validó con **un millón de
> registros**. Y tenemos **95 pruebas automatizadas** con JUnit y Spring MockMvc."

### Bloque 2 — Técnica Personas (≈2:00)

> "Para diseñar el sistema no partimos de pantallas, partimos de **Personas**: usuarios concretos
> con nombre, contexto y frustraciones. Desarrollamos tres; explico las dos que más marcaron el
> producto.
>
> *(Mostrar la ficha de Marcos)*
> **Marcos Villacís, Coordinador de Operaciones.** Representa al operador que cada mañana arma los
> viajes del día. Trabaja bajo presión, con muchas llamadas. **Necesita** ver de un vistazo qué
> está libre, armar un viaje rápido y que el sistema **no lo deje** hacer una doble asignación.
> Su **problema** hoy: usa Excel, ya asignó dos veces el mismo camión, y cuando reasigna un viaje
> el camión anterior queda 'ocupado' para siempre en su hoja. **El sistema le responde** ofreciendo
> solo recursos *Disponibles*, devolviendo un error de conflicto si intenta reutilizar uno ocupado,
> y **sincronizando los estados solo** —incluida la liberación del recurso anterior cuando se
> reasigna un viaje—. Ese análisis cambió una decisión de diseño concreta: los estados **no** se
> editan a mano, los maneja el sistema. Es la regla con más pruebas: 23 casos.
>
> *(Mostrar la ficha de Luis)*
> **Luis Herrera, Conductor.** Celular de gama media, datos limitados, manos ocupadas. **Necesita**
> ver **solo su viaje** y registrar rápido 'salí', 'llegué', 'novedad'. **Su objetivo**: que quede
> constancia de que entregó. **El sistema le responde** con un rol que solo ve Dashboard, Viajes y
> Seguimiento; puede registrar eventos y **confirmar la llegada**, pero **no** validar su propia
> entrega —eso lo hace el Supervisor— y **no** puede registrar una hora futura. De Luis salió la
> separación confirmar/validar y esa validación de fecha.
>
> La tercera Persona, **Andrea Cedeño**, del lado del cliente, justificó todo el **portal de
> autoservicio** y la regla de que un cliente **nunca** ve datos de otro.
>
> Con las Personas claras, el siguiente paso fue convertir sus necesidades en **historias de
> usuario** con criterios de aceptación. Con eso sigue Jefferson."

## Anexo C — Guion de la Parte 2 (Jefferson Umaginga · 5–6 min)

*Voz natural, en primera persona. Alternar entre la tabla del documento y el sistema en vivo.*

### Historias de usuario (≈2:00)

> "Gracias Diego. A mí me toca contar cómo pasamos de esas Personas a algo que después se pueda
> comprobar. Lo primero que hicimos fueron las historias de usuario.
>
> Cada historia la escribimos con el formato de siempre: como tal usuario, quiero hacer tal cosa,
> para tal beneficio. Y a cada una le pusimos sus criterios de aceptación, que al final son la
> lista de cosas que tienen que cumplirse para poder decir que esa historia ya está lista.
>
> *(mostrar la tabla de HU en pantalla)*
> Estas son las diez que trabajamos. Voy a explicar dos, que son las que más nos hicieron pensar.
>
> La primera es la del coordinador armando un viaje. La necesidad es poder asignarle a un viaje un
> vehículo y un conductor. El beneficio es que no se le cruce la programación y no queden camiones
> parados. Y los criterios que le pusimos fueron: que en el formulario solo salgan los vehículos y
> conductores que están libres; que si de alguna forma se intenta meter uno que ya está en otro
> viaje, el sistema no deje y avise; y que apenas el viaje arranca, el vehículo y el conductor
> pasen solos a estado en ruta.
>
> Esa historia la puedo mostrar funcionando ahorita.
> *(entrar como coordinador → Viajes → Nuevo viaje)*
> Abro la lista de vehículos y fíjense que solo aparecen los que están disponibles. El que ya está
> ocupado ni sale. Lleno el resto, guardo, y si me paso al módulo de Vehículos, el que acabo de
> usar ya cambió a en ruta y yo no toqué ese estado en ningún momento.
>
> La segunda historia es la del conductor registrando lo que pasa en su ruta: la salida, una
> parada, una novedad, la llegada. Ahí un criterio importante es que no lo deje poner una fecha
> que todavía no llega. Si intento registrar un evento con fecha de mañana, me sale el aviso de
> que la fecha no puede ser futura y no guarda."

### Casos de prueba (≈2:00)

> "Como ya teníamos los criterios de aceptación escritos, armar los casos de prueba fue casi
> pasarlos en limpio. Cada criterio se volvió un caso.
>
> Un caso nuestro tiene el identificador, qué funcionalidad prueba, las precondiciones, los datos
> que usamos, los pasos, el resultado que esperábamos, el que realmente nos salió, y el estado, si
> pasó o no.
>
> La actividad pide mostrar tres tipos, y los tenemos.
>
> Uno que sale bien: el CP-01, entrar al sistema con el usuario admin y su contraseña. Esperábamos
> llegar al panel y llegamos. *(hacerlo en vivo)*
>
> Uno de validación: el CP-05, intentar crear un vehículo con un año imposible. Puse 1500. El
> sistema no deja guardar y muestra el mensaje de que el año tiene que estar entre 1980 y 2027.
> *(hacerlo en vivo)*
>
> Y uno que nos sirvió para encontrar un error de verdad: el CP-02, entrar con la contraseña mal
> escrita. Nosotros esperábamos un mensaje que dijera que la clave estaba mal. Pero lo que pasaba
> era que la página se recargaba sola y por un segundo aparecía 'sesión expirada', que ahí no
> tiene sentido. Ese lo anotamos, lo corregimos, y le dejamos una prueba para que no vuelva a
> pasar. Está en el informe de correcciones, en el punto tres."

### Pruebas funcionales (≈2:00)

> "Con eso entro a las pruebas funcionales. Una prueba funcional lo que hace es: le doy una
> entrada al sistema y reviso que la salida sea la que pide el requerimiento. No me meto en cómo
> está programado por dentro, solo miro qué entra y qué sale.
>
> Nosotros estas pruebas las automatizamos en el backend con MockMvc, que lo que hace es levantar
> la API y mandarle peticiones de verdad, como si fuera el navegador, pero contra una base de
> datos de prueba.
>
> *(mostrar el árbol de tests en el IDE)*
> Aquí está. Hay una carpeta por cada módulo. Por ejemplo en la de auth están las de login: una
> para cuando entras bien, otra para cuando la clave está mal, otra para cuando mandas los campos
> vacíos. En la de vehículos están las de crear, listar y validar. Y así con cada módulo.
>
> Y aparte las probamos a mano en el sistema, que es lo que se ve en el video: crear un registro,
> editarlo, borrarlo, buscar y que los formularios validen. Por ejemplo la búsqueda.
> *(buscar en un catálogo un registro que no esté en la primera página)*
> Antes esto no encontraba nada, porque solo filtraba lo que ya estaba cargado en la pantalla.
> Ahora la búsqueda se hace en el servidor y sí encuentra en toda la base.
>
> Con eso le paso a Fajardos, que va a explicar las pruebas de integración y la automatización."

## Anexo D — Guion de la Parte 3 (Fajardos Montes · 5–6 min)

*Voz natural, en primera persona. Alternar entre el código de las pruebas y la terminal.*

### Pruebas de integración (≈2:00)

> "Listo, gracias Jefferson. Yo cierro con las pruebas de integración, la automatización y el
> análisis de todo lo que hicimos.
>
> Las pruebas de integración se diferencian de las funcionales en una cosa: acá no pruebo una
> parte sola, pruebo que varias partes funcionen bien juntas. O sea el recorrido completo: desde
> que se manda la petición, pasa por la seguridad, por la lógica, llega a la base de datos y
> vuelve la respuesta.
>
> El ejemplo que elegimos es crear un viaje, porque ahí se tocan cuatro tablas al mismo tiempo.
> *(abrir `ViajeControllerTest`)*
> Esta clase tiene 23 pruebas, y cada una levanta el sistema completo, no una parte suelta.
>
> Lo que pasa cuando se crea un viaje es esto: llega la petición con el token, el sistema revisa
> que el token sirva y que el rol tenga permiso; después la lógica de viajes revisa que el
> vehículo y el conductor estén libres, calcula la ruta, y recién ahí guarda. Y al guardar no
> solo crea el viaje: también cambia el estado del vehículo, del conductor y de la carga.
>
> Entonces en la prueba verificamos dos cosas: que la respuesta que vuelve esté bien armada, y
> que en la base de datos esos otros registros hayan quedado como debían. Una sola prueba termina
> revisando cinco tablas.
>
> Y acá también encontramos un error feo: cuando cambiabas el vehículo de un viaje que ya estaba
> creado, el vehículo viejo se quedaba pegado en en ruta para siempre y ya no lo podías volver a
> usar en ningún viaje. Lo arreglamos y le pusimos su prueba, es el punto dos del informe."

### Automatización (≈2:00)

> "Todo esto está automatizado. En el backend usamos JUnit, que es la herramienta estándar de
> pruebas en Java, más lo de Spring para levantar la API. Corre sobre una base de datos en
> memoria, así que no hace falta tener PostgreSQL prendido para las pruebas del backend. En total
> son 95 pruebas.
>
> *(mostrar la estructura de `backend/src/test/` y la carpeta `e2e/`)*
> Y aparte armamos una prueba end-to-end con Playwright. Esa sí abre un navegador de verdad y
> prueba el login como lo haría una persona: escribe el usuario, escribe la contraseña, le da a
> entrar y revisa que llegue al panel. También probamos entrando con la clave mal, para confirmar
> que sale el mensaje correcto y que la página ya no se recarga, que era justo el error de antes.
> Elegimos automatizar el login porque es lo que todo el mundo usa y porque ahí ya nos había
> aparecido un problema.
>
> Las corro.
> *(ejecutar `./mvnw test`)*
> Aquí sale: 95 pruebas, 0 fallos, build success.
> *(ejecutar `npx playwright test` en la carpeta `e2e/`)*
> Y las de Playwright: los cuatro casos de login pasan.
>
> Si quieren ver una fallando, antes de grabar rompí una a propósito y así se ve: sale en rojo,
> con el detalle de qué esperaba la prueba y qué le llegó. Ya la dejé arreglada."

### Análisis de resultados (≈1:30)

> "Haciendo el resumen de todo.
>
> Lo que funcionó bien fue el flujo principal: los catálogos, armar viajes con la ruta y el mapa,
> el cambio automático de estados, la confirmación y validación de entregas, y el portal del
> cliente. Todo eso pasa las pruebas.
>
> Errores encontramos doce en total, están todos en el informe de correcciones. Los que más nos
> preocuparon fueron dos que hacían que algo se viera roto en el uso normal, el buscador y lo del
> vehículo pegado, y cuatro de seguridad, entre esos dos que dejaban meter código en campos de
> texto.
>
> Validaciones nos tocó agregar varias: que no se pueda poner una fecha futura en un evento, que
> el año y el peso de un vehículo sean números válidos, que el correo tenga formato de correo, y
> que la contraseña tenga mínimo ocho caracteres.
>
> ¿Qué mejoraríamos? La prueba de Playwright por ahora solo cubre el login; habría que hacerla
> también para armar un viaje completo. Y falta una prueba de carga más formal.
>
> Y sobre por qué valió la pena probar antes de decir que el proyecto estaba terminado: casi
> ninguno de esos errores se veía en una demo normal, había que ir a buscarlos. Si no hacíamos
> las pruebas los entregábamos así, sin darnos cuenta."

### Conclusiones (≈1:00)

> "Para cerrar, tres cosas que nos llevamos de todo esto.
>
> Primero, arrancar desde las Personas nos ahorró trabajo, porque terminamos haciendo lo que el
> usuario de verdad necesitaba y no funciones de relleno.
>
> Segundo, las pruebas de integración fueron las que más errores encontraron, porque los
> problemas casi nunca estaban en una función suelta, sino en cómo se comunicaban los módulos
> entre ellos.
>
> Y tercero, tener las 95 pruebas corriendo nos dio la tranquilidad para seguir corrigiendo sin
> miedo a romper otra cosa.
>
> Si nos preguntan qué tan confiable es el sistema hoy, diríamos que el backend está bien cubierto
> y todos los errores que encontramos ya quedaron arreglados y con su prueba. La parte visual está
> probada, pero le falta más automatización, y eso es lo que seguiríamos haciendo. Gracias."
