(function () {
  // "pedidos" es el autoservicio exclusivo de Cliente (roles.js): ningun
  // otro rol lo tiene entre sus modulos. Sin esta guarda, cargar() de mas
  // abajo pedia GET /mis-cargas igual para cualquier sesion (Administrador,
  // Coordinador, Mantenimiento, Conductor, Supervisor) apenas cargaba la
  // pagina - 403 silencioso (el backend ya lo rechaza) pero una peticion de
  // red y un error en consola de mas en cada login que no fuera Cliente,
  // para una seccion que ademas queda oculta por navigation.js igual.
  // Mismo patron ya usado en js/dashboard.js para el caso inverso.
  if (trailersysGetSession()?.role !== "cliente") return;
  const $ = (id) => document.getElementById(id);
  const badge = { Pendiente: "badge-warning", Asignada: "badge-info", "En Tránsito": "badge-neutral", Entregada: "badge-success", Cancelada: "badge-danger" };
  let pedidos = [], viajes = {}, detalleActual = null, cargaRecepcionId = null, unidadAnterior = "kg", pedidoEditandoId = null;
  // Estado del mapa en vivo del detalle (ver abrirDetalle/actualizarMarcadorPedido):
  // vive fuera de abrirDetalle() porque iniciarSeguimientoEnVivo() sigue
  // corriendo cada 10s despues de que esa funcion ya termino.
  let pedidoMapaInstance = null, pedidoVehiculoMarker = null, pedidoActualizacionIntervalId = null;
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const peso = (kg) => `${Number(kg || 0).toLocaleString("es-EC")} kg / ${(Number(kg || 0) * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  const labelNovedad = (v) => ({ COMPLETO: "Completo", INCOMPLETO: "Incompleto", DANADO: "Dañado", INCORRECTO: "Incorrecto", OTRO: "Otro" }[v] || v || "—");
  // ABIERTO/EN_REVISION/RESUELTO tal cual llegan del backend no son texto
  // presentable ("en_revision" con guion bajo, literal) - se muestran en
  // minúscula para calzar en la frase "Reclamo {estado}".
  const labelEstadoReclamo = (v) => ({ ABIERTO: "abierto", EN_REVISION: "en revisión", RESUELTO: "resuelto" }[v] || String(v || "").toLowerCase());

  function actualizarKpis() {
    $("pedidoKpiActivos").textContent = pedidos.filter((p) => p.estado !== "Entregada" && p.estado !== "Cancelada").length;
    $("pedidoKpiTransito").textContent = pedidos.filter((p) => p.estado === "En Tránsito").length;
    $("pedidoKpiEntregados").textContent = pedidos.filter((p) => p.estado === "Entregada").length;
    $("pedidoKpiReclamos").textContent = Object.values(viajes).filter((v) => v?.estadoReclamoCliente === "ABIERTO").length;
    const novedades = [];
    Object.values(viajes).filter(Boolean).forEach((v) => {
      if (v.estadoReclamoCliente === "ABIERTO") novedades.push(`El reclamo del viaje #${v.id} está siendo revisado.`);
      else if (v.respuestaReclamoCliente) novedades.push(`Tu reclamo del viaje #${v.id} tiene una respuesta.`);
      else if (v.entregaConfirmada && !v.entregaConfirmadaCliente) novedades.push(`El viaje #${v.id} llegó a destino. Confirma cómo recibiste la carga.`);
    });
    $("pedidoNotifications").hidden = novedades.length === 0;
    $("pedidoNotifications").innerHTML = novedades.length ? `<h3><i class="bi bi-bell"></i> Notificaciones</h3>${novedades.map((n) => `<p>${esc(n)}</p>`).join("")}` : "";
  }

  // "Verificada" es un estado derivado (no viaja como tal desde el
  // backend): el cliente ya reviso la carga y no dejo un reclamo sin
  // resolver. El viaje puede seguir "En Curso" en ese momento -recien lo
  // finaliza Coordinador/Administrador despues- asi que se muestra en vez
  // del badge crudo de c.estado (que hasta entonces sigue diciendo "En
  // Tránsito"), para que el cliente vea de una vez que ya quedo conforme.
  const estaVerificada = (v) => Boolean(v?.entregaConfirmadaCliente) && (!v.estadoReclamoCliente || v.estadoReclamoCliente === "RESUELTO");

  function tarjeta(c) {
    const v = viajes[c.id];
    const reclamo = v?.estadoReclamoCliente ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Reclamo ${esc(labelEstadoReclamo(v.estadoReclamoCliente))}</span>` : "";
    const confirmar = v && v.entregaConfirmada && !v.entregaConfirmadaCliente ? `<button class="btn btn-primary" data-action="recibir" data-id="${c.id}"><i class="bi bi-clipboard-check"></i> Confirmar recepción</button>` : "";
    const estadoBadge = estaVerificada(v)
      ? `<span class="badge badge-success"><i class="bi bi-check-circle-fill"></i> Verificada</span>`
      : `<span class="badge ${badge[c.estado] || "badge-neutral"}">${esc(c.estado)}</span>`;
    // Editar/cancelar solo mientras el pedido sigue "Pendiente": una vez
    // que Operación le asigna un viaje, el backend ya rechaza ambas
    // acciones (ver PedidoClienteService.miCargaPendiente), asi que ni se
    // ofrecen los botones.
    const gestion = c.estado === "Pendiente"
      ? `<button class="btn btn-ghost" data-action="editar" data-id="${c.id}"><i class="bi bi-pencil"></i> Editar</button><button class="btn btn-ghost" data-action="cancelar" data-id="${c.id}"><i class="bi bi-x-circle"></i> Cancelar pedido</button>`
      : "";
    return `<article class="card item-card pedido-card"><div class="item-banner"><i class="bi bi-box-seam"></i><div class="item-banner-title"><div class="item-title">Pedido #${c.id} · ${esc(c.descripcion)}</div><div class="item-subtitle">${esc(c.tipo)}</div></div></div><div class="item-body"><div class="item-route"><i class="bi bi-geo-alt"></i><span>${esc(c.origen)}</span><i class="bi bi-arrow-right"></i><span>${esc(c.destino)}</span></div><div class="item-meta">${estadoBadge}<span><i class="bi bi-box-seam"></i>${peso(c.peso)}</span><span><i class="bi bi-truck"></i>${esc(v?.estado || "Sin asignar")}</span></div><div class="pedido-card-actions">${reclamo}<button class="btn btn-ghost" data-action="detalle" data-id="${c.id}"><i class="bi bi-eye"></i> Ver detalle y guía</button>${confirmar}${gestion}</div></div></article>`;
  }

  function filtrar() {
    const q = $("pedidoBuscar").value.trim().toLowerCase(), estado = $("pedidoEstadoFiltro").value, fecha = $("pedidoFechaFiltro").value;
    const visibles = pedidos.filter((c) => { const v = viajes[c.id], fechaPedido = (c.fechaCreacion || v?.fechaSalida || "").slice(0, 10); return (!estado || (estado === "RECLAMO" ? Boolean(v?.estadoReclamoCliente) : c.estado === estado)) && (!fecha || fechaPedido === fecha) && `${c.id} ${c.descripcion} ${c.origen} ${c.destino}`.toLowerCase().includes(q); });
    $("pedidoGrid").innerHTML = visibles.map(tarjeta).join("");
    $("pedidoResultsCount").textContent = `${visibles.length} de ${pedidos.length} pedidos`;
    $("pedidoGrid").hidden = visibles.length === 0; $("pedidoEmptyState").hidden = visibles.length !== 0;
    $("pedidoEmptyTitle").textContent = pedidos.length ? "No hay resultados para estos filtros" : "Todavía no tienes pedidos";
  }

  async function cargar() {
    try {
      pedidos = await trailersysApiRequest("GET", "/mis-cargas"); viajes = {};
      await Promise.all(pedidos.filter((c) => c.estado !== "Pendiente").map(async (c) => { try { viajes[c.id] = await trailersysApiRequest("GET", `/mis-cargas/${c.id}/viaje`); } catch { viajes[c.id] = null; } }));
      actualizarKpis(); filtrar();
    } catch (e) { $("pedidoGrid").hidden = true; $("pedidoEmptyState").hidden = false; $("pedidoEmptyTitle").textContent = "No se pudieron cargar tus pedidos"; $("pedidoEmptyText").textContent = e.message; }
  }

  function lineaTiempo(d) {
    const items = [{ titulo: "Pedido creado", detalle: `Carga #${d.carga.id} registrada${d.carga.fechaCreacion ? ` · ${trailersysFormatDateTime(d.carga.fechaCreacion)}` : ""}` }];
    if (d.viaje) items.push({ titulo: "Viaje asignado", detalle: `Viaje #${d.viaje.id} · ${d.viaje.vehiculoPlaca}` });
    [...(d.eventos || [])].reverse().forEach((e) => items.push({ titulo: e.evento, detalle: `${e.ubicacion} · ${trailersysFormatDateTime(e.fechaHora)}` }));
    if (d.viaje?.estado === "Finalizado") items.push({ titulo: "Entrega registrada", detalle: "El viaje fue finalizado" });
    if (d.viaje?.entregaConfirmadaCliente) items.push({ titulo: "Recepción confirmada", detalle: labelNovedad(d.viaje.novedadRecepcionCliente) });
    return items.map((x) => `<div class="pedido-timeline-item"><span></span><div><strong>${esc(x.titulo)}</strong><p>${esc(x.detalle)}</p></div></div>`).join("");
  }

  // Perezoso (no "const ... = L.divIcon(...)" al nivel del modulo): a
  // diferencia de js/seguimiento.js, aca <script src="js/pedidos.js"> carga
  // ANTES que js/vendor/leaflet.js (ver app.html) - evaluar L.divIcon() de
  // una vez en cuanto se define este archivo rompia con "L is not defined",
  // porque en ese momento Leaflet todavia no existe. Para cuando esto se
  // llama de verdad (abrirDetalle, ya adentro del "if (v?.ruta && window.L)")
  // Leaflet ya esta cargado.
  let vehiculoIconCache = null;
  const vehiculoIcon = () => vehiculoIconCache || (vehiculoIconCache = L.divIcon({ className: "vehicle-marker", html: '<i class="bi bi-truck"></i>', iconSize: [30, 30], iconAnchor: [15, 15] }));
  const formatHaceTiempo = (fechaHoraIso) => { const f = new Date(fechaHoraIso); if (Number.isNaN(f.getTime())) return "hace un momento"; const min = Math.max(0, (Date.now() - f.getTime()) / 60000); return min < 1 ? "hace un momento" : `hace ${trailersysFormatDuration(min)}`; };

  // Distancia euclidiana simple entre dos puntos [lat,lng] - no hace falta
  // una proyeccion geodesica exacta solo para encontrar cual punto de la
  // ruta ya calculada queda mas cerca de una ubicacion reportada a mano.
  // Misma logica que indicePathMasCercano/calcularPosicionSimulada de
  // js/seguimiento.js (mapa en vivo de Administrador/Coordinador): se
  // duplica aca en vez de compartirse porque ese archivo esta envuelto en
  // su propia IIFE (nada de lo suyo queda accesible desde afuera) y ademas
  // Cliente no tiene acceso a ese modulo ni a su API (ver
  // PedidoClienteController.ubicacionReportada, que es la version acotada
  // al propio pedido que este archivo si puede usar).
  function indicePathMasCercano(path, lat, lng) {
    let mejorIndice = 0, mejorDistancia = Infinity;
    path.forEach((punto, indice) => { const d = Math.hypot(punto[0] - lat, punto[1] - lng); if (d < mejorDistancia) { mejorDistancia = d; mejorIndice = indice; } });
    return mejorIndice;
  }

  function calcularPosicionSimulada(viaje, ubicacionReportada) {
    // ViajeResponse.ruta.path llega como [{lat,lng}, ...] (ver PuntoRuta.java) -
    // Leaflet acepta ese formato tal cual para dibujar la polilinea (por eso
    // el mapa estatico de mas abajo lo usa sin tocarlo), pero la aritmetica de
    // aca abajo (punto[0]/punto[1], p1[0]-p2[0]...) necesita pares [lat,lng].
    // Sin este mapeo, "punto[0]"/"punto[1]" salian undefined y el marcador se
    // intentaba poner en (NaN, NaN) - Leaflet lo rechaza y el marcador nunca
    // aparecia.
    // La inmensa mayoria de los viajes reales de esta base (los generados en
    // volumen, no los creados a mano desde Viajes con "Calcular ruta") tienen
    // origen/destino/distancia/duracion pero NUNCA un "path" con la forma
    // real de la carretera - se ve al consultar la base en vivo (ruta_path
    // null, el resto de columnas de ruta con datos). Exigir un path real
    // aca dejaba a esos viajes con la linea recta ya dibujada en el mapa
    // estatico de abajo, pero SIN marcador movil nunca (calcularPosicionSimulada
    // devolvia null siempre) - el "seguimiento en vivo" simplemente no
    // aparecia para casi ningun pedido real. De respaldo, sin un path
    // calculado, se simula sobre la linea recta origen-destino (2 puntos) en
    // vez de no simular nada.
    const pathRaw = viaje.ruta?.path?.length >= 2 ? viaje.ruta.path
      : (viaje.ruta?.origenLat != null && viaje.ruta?.destinoLat != null)
        ? [{ lat: viaje.ruta.origenLat, lng: viaje.ruta.origenLng }, { lat: viaje.ruta.destinoLat, lng: viaje.ruta.destinoLng }]
        : null;
    if (!pathRaw || pathRaw.length < 2 || !viaje.fechaSalida || !viaje.ruta.duracionMin) return null;
    const path = pathRaw.map((p) => Array.isArray(p) ? p : [p.lat, p.lng]);
    const salida = new Date(viaje.fechaSalida).getTime();
    if (Number.isNaN(salida)) return null;
    const duracionMs = viaje.ruta.duracionMin * 60000;
    if (duracionMs <= 0) return null;

    let progreso;
    if (ubicacionReportada) {
      const indiceReportado = indicePathMasCercano(path, ubicacionReportada.lat, ubicacionReportada.lng);
      const fraccionReal = indiceReportado / (path.length - 1);
      const reportadoEn = new Date(ubicacionReportada.fechaHora).getTime();
      const avanceDesdeElReporte = Number.isNaN(reportadoEn) ? 0 : (Date.now() - reportadoEn) / duracionMs;
      progreso = Math.min(1, Math.max(0, fraccionReal + avanceDesdeElReporte));
    } else {
      progreso = Math.min(1, Math.max(0, (Date.now() - salida) / duracionMs));
    }

    const posicionExacta = progreso * (path.length - 1);
    const indiceBase = Math.floor(posicionExacta);
    const indiceSiguiente = Math.min(path.length - 1, indiceBase + 1);
    const fraccionLocal = posicionExacta - indiceBase;
    const p1 = path[indiceBase], p2 = path[indiceSiguiente];
    return [p1[0] + (p2[0] - p1[0]) * fraccionLocal, p1[1] + (p2[1] - p1[1]) * fraccionLocal];
  }

  // Se re-consulta el viaje en cada tick (no solo la ubicacion reportada):
  // si mientras el detalle esta abierto el viaje termina (Finalizado/
  // Cancelado), el marcador debe desaparecer y el refresco detenerse solo,
  // sin que el cliente tenga que cerrar y volver a abrir el detalle.
  async function actualizarMarcadorPedido(id) {
    if (!pedidoMapaInstance) return;
    let viaje;
    try { viaje = await trailersysApiRequest("GET", `/mis-cargas/${id}/viaje`); }
    catch { viaje = null; }

    if (!viaje || viaje.estado !== "En Curso") {
      detenerSeguimientoEnVivo();
      if (pedidoVehiculoMarker) { pedidoMapaInstance.removeLayer(pedidoVehiculoMarker); pedidoVehiculoMarker = null; }
      return;
    }

    let ubicacionReportada = null;
    try {
      const evento = await trailersysApiRequest("GET", `/mis-cargas/${id}/ubicacion-reportada`);
      if (evento) ubicacionReportada = { lat: evento.latitudReportada, lng: evento.longitudReportada, fechaHora: evento.fechaHora };
    } catch { ubicacionReportada = null; }

    const posicion = calcularPosicionSimulada(viaje, ubicacionReportada);
    if (!posicion) return;
    const popupTexto = ubicacionReportada
      ? `Última ubicación reportada por el conductor ${formatHaceTiempo(ubicacionReportada.fechaHora)}`
      : "Posición estimada (simulada)";

    if (pedidoVehiculoMarker) { pedidoVehiculoMarker.setLatLng(posicion); pedidoVehiculoMarker.setPopupContent(popupTexto); }
    else { pedidoVehiculoMarker = L.marker(posicion, { icon: vehiculoIcon() }).addTo(pedidoMapaInstance).bindPopup(popupTexto); }
  }

  function detenerSeguimientoEnVivo() {
    if (pedidoActualizacionIntervalId) { clearInterval(pedidoActualizacionIntervalId); pedidoActualizacionIntervalId = null; }
  }

  function destruirMapaPedido() {
    detenerSeguimientoEnVivo();
    if (pedidoMapaInstance) { pedidoMapaInstance.remove(); pedidoMapaInstance = null; }
    pedidoVehiculoMarker = null;
  }

  // Mientras el pedido no tiene una ruta que mostrar (Pendiente, o ya
  // Asignada/En Tránsito pero sin ruta calculada todavia), el espacio grande
  // del mapa mostraba solo un icono y una frase - se sentia vacio para el
  // lugar mas grande del detalle. En su lugar, un progreso de 4 pasos
  // (deriva directo de c.estado, sin depender de si ya hay viaje) le da al
  // cliente la misma idea que un "seguimiento" real: en que parte del
  // proceso esta su pedido ahora mismo, no solo "todavia no hay mapa".
  function renderProgreso(c) {
    if (c.estado === "Cancelada") {
      return `<div class="pedido-progreso-cancelado"><i class="bi bi-x-circle"></i><strong>Pedido cancelado</strong><p>Este pedido fue cancelado y no continuará su proceso.</p></div>`;
    }
    const pasos = [
      { titulo: "Pedido creado", icono: "bi-file-earmark-text", estado: "hecho",
        detalle: c.fechaCreacion ? trailersysFormatDateTime(c.fechaCreacion) : "Tu pedido quedó registrado." },
      { titulo: "Viaje asignado", icono: "bi-clipboard-check",
        estado: c.estado === "Pendiente" ? "activo" : "hecho",
        detalle: c.estado === "Pendiente" ? "Un coordinador está buscando vehículo y conductor disponibles." : "Vehículo y conductor ya asignados." },
      { titulo: "En camino", icono: "bi-truck",
        estado: c.estado === "Pendiente" ? "pendiente" : c.estado === "Asignada" ? "activo" : "hecho",
        detalle: c.estado === "En Tránsito" || c.estado === "Entregada" ? "Tu carga va en camino." : "Todavía no ha salido." },
      { titulo: "Entregado", icono: "bi-box-seam",
        estado: c.estado === "Entregada" ? "hecho" : "pendiente",
        detalle: c.estado === "Entregada" ? "Tu carga llegó a destino." : "Aquí verás la confirmación de entrega." },
    ];
    return `<div class="pedido-progreso">${pasos.map((p) => `<div class="pedido-progreso-item ${p.estado}"><div class="pedido-progreso-icono"><i class="bi ${p.estado === "hecho" ? "bi-check-lg" : p.icono}"></i></div><div><strong>${esc(p.titulo)}</strong><p>${esc(p.detalle)}</p></div></div>`).join("")}</div>`;
  }

  async function abrirDetalle(id) {
    // Por si quedo un mapa/intervalo de un detalle anterior sin cerrar bien.
    destruirMapaPedido();
    $("pedidoDetalleContenido").innerHTML = `<p>Cargando detalle...</p>`; trailersysOpenModal($("pedidoDetalleOverlay"));
    try {
      const d = await trailersysApiRequest("GET", `/mis-cargas/${id}/detalle`); detalleActual = d; const c = d.carga, v = d.viaje;
      const estadoBadgeDetalle = estaVerificada(v)
        ? `<span class="badge badge-success"><i class="bi bi-check-circle-fill"></i> Verificada</span>`
        : `<span class="badge ${badge[c.estado] || "badge-neutral"}">${esc(c.estado)}</span>`;
      // "Llegada estimada" se calcula sobre el propio objeto Date (no se
      // vuelve a serializar a ISO): fechaSalida llega sin zona horaria
      // (hora local tal cual se guardo) y Date ya la interpreta como local,
      // asi que sumarle la duracion y pasar el Date resultante directo a
      // trailersysFormatDateTime mantiene esa misma hora local sin que un
      // viaje por UTC de por medio la corra.
      const fechaLlegadaEstimada = v?.fechaSalida && v?.ruta?.duracionMin
        ? new Date(new Date(v.fechaSalida).getTime() + v.ruta.duracionMin * 60000)
        : null;
      // Todo lo "de lectura" (cabecera, datos, reclamo) va en la columna
      // angosta de la izquierda; el mapa ocupa el resto del ancho del modal,
      // grande, en vez de una franja delgada apretada entre bloques de
      // texto como antes. Mismo criterio que ya usa el mapa en vivo de
      // Administrador/Coordinador (css/seguimiento.css, "seguimiento-layout"),
      // aca aplicado siempre (no solo si el viaje esta En Curso: incluso una
      // ruta estatica se ve mejor grande que angosta).
      // Sin viaje todavia, mostrar 5 campos "Por asignar/definir/calcular"
      // (vehiculo, conductor, salida, distancia, llegada) es puro relleno -
      // ese estado ya lo cuenta el progreso del mapa (renderProgreso). En su
      // lugar, si el cliente dejo una observacion al crear el pedido, es lo
      // unico realmente suyo que todavia no se mostraba en ningun lado del
      // detalle.
      const camposViaje = v
        ? `<div><small>Vehículo</small><strong>${esc(v.vehiculoPlaca || "Por asignar")}</strong></div><div><small>Conductor</small><strong>${esc(v.conductorNombres || "Por asignar")}</strong></div><div><small>Salida programada</small><strong>${v.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "Por definir"}</strong></div><div><small>Distancia / duración</small><strong>${v.ruta ? `${Number(v.ruta.distanciaKm).toFixed(1)} km · ${Math.round(v.ruta.duracionMin)} min` : "Por calcular"}</strong></div><div><small>Llegada estimada</small><strong>${fechaLlegadaEstimada ? trailersysFormatDateTime(fechaLlegadaEstimada) : "Por calcular"}</strong></div>`
        : c.observaciones ? `<div><small>Observaciones</small><strong>${esc(c.observaciones)}</strong></div>` : "";
      $("pedidoDetalleContenido").innerHTML = `<div class="pedido-detail-layout"><div class="pedido-detail-layout-info"><div class="pedido-detail-head"><div><span class="eyebrow">GUÍA TS-${String(c.id).padStart(6, "0")}</span><h2>${esc(c.descripcion)}</h2><p>${esc(c.origen)} → ${esc(c.destino)}</p></div>${estadoBadgeDetalle}</div><div class="pedido-detail-grid"><div><small>Cliente</small><strong>${esc(c.clienteNombre)}</strong></div><div><small>Tipo y peso</small><strong>${esc(c.tipo)} · ${peso(c.peso)}</strong></div>${camposViaje}</div>${v?.estadoReclamoCliente ? `<div class="alert alert-danger"><i class="bi bi-exclamation-triangle"></i><div><strong>Reclamo ${esc(labelEstadoReclamo(v.estadoReclamoCliente))}: ${esc(labelNovedad(v.novedadRecepcionCliente))}</strong><p>${esc(v.observacionConfirmacionCliente)}</p>${v.respuestaReclamoCliente ? `<p><b>Respuesta:</b> ${esc(v.respuestaReclamoCliente)}</p>` : ""}</div></div>` : ""}</div><div class="pedido-detail-layout-map"></div></div><h3>Historial del viaje</h3><div class="pedido-timeline">${lineaTiempo(d)}</div>`;

      const columnaInfo = $("pedidoDetalleContenido").querySelector(".pedido-detail-layout-info");
      const columnaMapa = $("pedidoDetalleContenido").querySelector(".pedido-detail-layout-map");

      if (v?.evidenciaRecepcionCliente) {
        const evidencia = document.createElement("img"); evidencia.className = "pedido-evidencia";
        evidencia.src = v.evidenciaRecepcionCliente; evidencia.alt = "Evidencia del reclamo";
        columnaInfo.appendChild(evidencia);
      }

      if (v?.ruta && window.L) {
        const mapa = document.createElement("div"); mapa.className = "pedido-detail-map";
        columnaMapa.appendChild(mapa);
        setTimeout(async () => {
          // Igual que renderMapa() en js/seguimiento.js: la inmensa mayoria
          // de los viajes reales no tienen un path guardado (ver comentario
          // en calcularPosicionSimulada), asi que sin esto el cliente veia
          // una linea recta desde el primer momento mientras que el mismo
          // viaje, visto por Administrador/Coordinador, ya mostraba la
          // carretera real - se pide esa misma ruta real por OSRM al vuelo
          // antes de dibujar el mapa. Si falla (sin internet, servidor OSRM
          // caido) v.ruta.path sigue vacio y calcularPosicionSimulada ya
          // sabe simular igual sobre la linea recta origen-destino.
          if (!v.ruta.path?.length) {
            const rutaCalculada = await trailersysGetRoute(
              { lat: v.ruta.origenLat, lng: v.ruta.origenLng },
              { lat: v.ruta.destinoLat, lng: v.ruta.destinoLng }
            );
            if (rutaCalculada) v.ruta.path = rutaCalculada.path;
          }
          pedidoMapaInstance = L.map(mapa).setView([v.ruta.origenLat, v.ruta.origenLng], 7);
          L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { attribution: "© OpenStreetMap" }).addTo(pedidoMapaInstance);
          const puntos = v.ruta.path?.length ? v.ruta.path : [[v.ruta.origenLat, v.ruta.origenLng], [v.ruta.destinoLat, v.ruta.destinoLng]];
          const linea = L.polyline(puntos, { color: "#2563eb", weight: 5 }).addTo(pedidoMapaInstance); pedidoMapaInstance.fitBounds(linea.getBounds(), { padding: [25, 25] });
          L.marker(puntos[0]).addTo(pedidoMapaInstance).bindPopup("Origen"); L.marker(puntos[puntos.length - 1]).addTo(pedidoMapaInstance).bindPopup("Destino");
          // Solo un viaje En Curso tiene sentido "seguir": Programado
          // todavia no salio y Finalizado/Cancelado ya no se mueve. Se
          // refresca cada 10s, mismo intervalo que el mapa en vivo de
          // Administrador/Coordinador (js/seguimiento.js).
          if (v.estado === "En Curso") {
            actualizarMarcadorPedido(id);
            pedidoActualizacionIntervalId = setInterval(() => actualizarMarcadorPedido(id), 10000);
          }
          // El contenedor nace con ancho 0 (todavia detras de la animacion
          // de apertura del modal): sin este invalidateSize() los tiles de
          // Leaflet quedan calculados para ese ancho equivocado y el mapa se
          // ve cortado/gris hasta el primer resize de la ventana.
          setTimeout(() => pedidoMapaInstance?.invalidateSize(), 200);
        }, 50);
      } else {
        columnaMapa.innerHTML = `<div class="pedido-detail-map"><div class="pedido-progreso-wrap">${renderProgreso(c)}</div></div>`;
      }
    } catch (e) { $("pedidoDetalleContenido").innerHTML = `<div class="alert alert-danger">${esc(e.message)}</div>`; }
  }

  function imprimirGuia() {
    if (!detalleActual) return;
    // window.open puede devolver null si el navegador bloquea la ventana
    // emergente (bloqueador de pop-ups activo, o el permiso no se concedio
    // para este sitio): sin esta comprobacion, w.document.write revienta
    // con un error silencioso y el usuario se queda sin poder imprimir ni
    // saber por que.
    const w = window.open("", "_blank", "width=900,height=700");
    if (!w) {
      alert("El navegador bloqueó la ventana de impresión. Permite las ventanas emergentes para este sitio e inténtalo de nuevo.");
      return;
    }
    w.document.write(`<!doctype html><html><head><title>Guía TS-${detalleActual.carga.id}</title><link rel="stylesheet" href="css/variables.css"><link rel="stylesheet" href="css/base.css"><link rel="stylesheet" href="css/components.css"><link rel="stylesheet" href="css/pedidos.css"><style>body{padding:32px}.pedido-detail{max-width:900px;margin:auto}</style></head><body><div class="pedido-detail"><h1>TrailerSys · Guía de transporte</h1>${$("pedidoDetalleContenido").innerHTML}<p>Documento generado el ${new Date().toLocaleString("es-EC")}</p></div><script>window.onload=()=>window.print()<\/script></body></html>`); w.document.close();
  }

  // Top 3 combinaciones origen->destino de los pedidos ya hechos por el
  // cliente (cargar() ya trajo todos en "pedidos", sin paginar), ordenadas
  // por cuantas veces las repitio y, en empate, por la mas reciente -
  // pensado para el caso comun de un cliente que envia carga siempre entre
  // las mismas dos ciudades y tiene que rearmar los mismos dos selects (65
  // ciudades cada uno) cada vez que hace un pedido nuevo.
  function rutasFrecuentes() {
    const porRuta = new Map();
    pedidos.forEach((c) => {
      if (!c.origen || !c.destino) return;
      const clave = `${c.origen}→${c.destino}`;
      const previa = porRuta.get(clave);
      const fecha = c.fechaCreacion || "";
      if (previa) { previa.count++; if (fecha > previa.fecha) previa.fecha = fecha; }
      else porRuta.set(clave, { origen: c.origen, destino: c.destino, count: 1, fecha });
    });
    return [...porRuta.values()].sort((a, b) => b.count - a.count || b.fecha.localeCompare(a.fecha)).slice(0, 3);
  }

  function renderRutasFrecuentes() {
    const rutas = rutasFrecuentes();
    $("pedidoRutasFrecuentes").hidden = rutas.length === 0;
    $("pedidoRutasFrecuentesList").innerHTML = rutas.map((r, i) =>
      `<button type="button" class="pedido-ruta-chip" data-indice="${i}"><i class="bi bi-signpost-split"></i>${esc(r.origen)} → ${esc(r.destino)}</button>`).join("");
    $("pedidoRutasFrecuentesList").dataset.rutas = JSON.stringify(rutas);
  }

  $("pedidoRutasFrecuentesList").onclick = (e) => {
    const b = e.target.closest("button[data-indice]"); if (!b) return;
    const ruta = JSON.parse($("pedidoRutasFrecuentesList").dataset.rutas || "[]")[Number(b.dataset.indice)];
    if (!ruta) return;
    asegurarOpcionLugar($("pedidoOrigen"), ruta.origen);
    asegurarOpcionLugar($("pedidoDestino"), ruta.destino);
    $("pedidoOrigen").value = ruta.origen;
    $("pedidoDestino").value = ruta.destino;
  };

  const modal = $("pedidoModalOverlay"), form = $("pedidoForm");
  $("btnNuevoPedido").onclick = () => {
    form.reset(); unidadAnterior = "kg"; pedidoEditandoId = null;
    $("pedidoModalTitulo").textContent = "Hacer un pedido";
    $("pedidoSubmitTexto").textContent = "Enviar pedido";
    // Reinicia el select al catalogo completo: puede haber quedado con una
    // opcion "extra" (ver asegurarOpcionLugar) de una edicion anterior.
    trailersysPoblarLugaresEcuador($("pedidoOrigen"));
    trailersysPoblarLugaresEcuador($("pedidoDestino"));
    renderRutasFrecuentes();
    trailersysOpenModal(modal);
  };
  $("pedidoModalClose").onclick = $("pedidoCancelar").onclick = () => trailersysCloseModal(modal);

  // Origen/Destino son un <select> con las 65 ciudades del catalogo
  // compartido (ecuador-locations.js) - el origen/destino real de un
  // pedido no siempre es exactamente el texto de una de esas 65 opciones
  // (por ejemplo si el pedido lo creo/edito antes un Administrador desde
  // Cargas, donde el mismo problema ya se corrigio). Sin esto, el select
  // quedaba en blanco al editar un pedido asi.
  function asegurarOpcionLugar(select, valor) {
    if (!valor || [...select.options].some((o) => o.value === valor)) return;
    const opcion = document.createElement("option");
    opcion.value = valor;
    opcion.textContent = valor;
    select.appendChild(opcion);
  }

  // Reutiliza el mismo modal/formulario de "Hacer un pedido": precarga los
  // valores actuales y cambia el envio a PUT en vez de POST (ver
  // form.onsubmit). Solo se llega aca para un pedido todavia "Pendiente"
  // (unico boton que la muestra, ver tarjeta()).
  function abrirEdicion(c) {
    form.reset();
    pedidoEditandoId = c.id;
    $("pedidoModalTitulo").textContent = `Editar pedido #${c.id}`;
    $("pedidoSubmitTexto").textContent = "Guardar cambios";
    // Las rutas frecuentes son solo un atajo para armar un pedido nuevo
    // desde cero - editando uno ya existente los selects ya se precargan
    // con su origen/destino actual, asi que no aportan nada aca.
    $("pedidoRutasFrecuentes").hidden = true;
    $("pedidoDescripcion").value = c.descripcion || "";
    $("pedidoTipo").value = c.tipo || "";
    $("pedidoPesoUnidad").value = "kg"; unidadAnterior = "kg";
    $("pedidoPeso").value = c.peso ?? "";
    // Reinicia el select al catalogo completo: puede haber quedado con una
    // opcion "extra" (ver asegurarOpcionLugar) de una edicion anterior.
    trailersysPoblarLugaresEcuador($("pedidoOrigen"));
    trailersysPoblarLugaresEcuador($("pedidoDestino"));
    asegurarOpcionLugar($("pedidoOrigen"), c.origen);
    asegurarOpcionLugar($("pedidoDestino"), c.destino);
    $("pedidoOrigen").value = c.origen || "";
    $("pedidoDestino").value = c.destino || "";
    $("pedidoObservaciones").value = c.observaciones || "";
    trailersysOpenModal(modal);
  }

  async function cancelarPedido(id) {
    if (!window.confirm("¿Cancelar este pedido? Quedará registrado como \"Cancelada\" y ya no podrás editarlo.")) return;
    try { await trailersysApiRequest("DELETE", `/mis-cargas/${id}`); await cargar(); }
    catch (err) { alert(err.message || "No se pudo cancelar el pedido."); }
  }
  $("pedidoPesoUnidad").onchange = () => { const input = $("pedidoPeso"), valor = Number(input.value); if (input.value) { const kg = unidadAnterior === "lb" ? valor / 2.2046226218 : valor; input.value = ($("pedidoPesoUnidad").value === "lb" ? kg * 2.2046226218 : kg).toFixed(2); } unidadAnterior = $("pedidoPesoUnidad").value; };
  // pesoTexto en vez de leer el peso ya convertido a Number directamente:
  // Number("") es 0, asi que dejar el campo vacio pasaba silenciosamente
  // como un pedido de "0 kg" en vez de pedir el dato. Con este sentinel
  // (igual que en cargas.js/mantenimientos.js) un campo vacio se detecta
  // como tal en vez de convertirse en un numero valido por accidente.
  form.onsubmit = async (e) => { e.preventDefault(); const pesoTexto = $("pedidoPeso").value; const valor = Number(pesoTexto), kg = $("pedidoPesoUnidad").value === "lb" ? valor / 2.2046226218 : valor; const data = { descripcion: $("pedidoDescripcion").value.trim(), tipo: $("pedidoTipo").value.trim(), peso: pesoTexto === "" ? "" : Math.round(kg), origen: $("pedidoOrigen").value, destino: $("pedidoDestino").value, observaciones: $("pedidoObservaciones").value.trim() }; if (!data.descripcion || !data.tipo || !data.origen || !data.destino || data.peso === "" || Number.isNaN(data.peso) || data.peso < 0) return alert("Completa los campos obligatorios."); try { if (pedidoEditandoId) await trailersysApiRequest("PUT", `/mis-cargas/${pedidoEditandoId}`, data); else await trailersysApiRequest("POST", "/mis-cargas", data); trailersysCloseModal(modal); await cargar(); } catch (err) { alert(err.message); } };
  $("pedidoGrid").onclick = (e) => {
    const b = e.target.closest("button[data-action]"); if (!b) return;
    const id = b.dataset.id;
    if (b.dataset.action === "detalle") abrirDetalle(id);
    else if (b.dataset.action === "editar") { const c = pedidos.find((p) => String(p.id) === id); if (c) abrirEdicion(c); }
    else if (b.dataset.action === "cancelar") cancelarPedido(id);
    else { cargaRecepcionId = id; $("pedidoRecepcionForm").reset(); trailersysOpenModal($("pedidoRecepcionOverlay")); }
  };
  $("pedidoDetalleClose").onclick = $("pedidoDetalleAceptar").onclick = () => { trailersysCloseModal($("pedidoDetalleOverlay")); destruirMapaPedido(); }; $("pedidoImprimirGuia").onclick = imprimirGuia;
  $("pedidoRecepcionClose").onclick = $("pedidoRecepcionCancelar").onclick = () => trailersysCloseModal($("pedidoRecepcionOverlay"));
  $("pedidoRecepcionForm").onsubmit = async (e) => { e.preventDefault(); const tipo = $("pedidoRecepcionTipo").value, obs = $("pedidoRecepcionObservacion").value.trim(), file = $("pedidoRecepcionEvidencia").files[0]; if (tipo !== "COMPLETO" && !obs) return alert("Describe el problema para registrar el reclamo."); if (file && file.size > 4 * 1024 * 1024) return alert("La imagen supera 4 MB."); const evidencia = file ? await new Promise((resolve, reject) => { const r = new FileReader(); r.onload = () => resolve(r.result); r.onerror = reject; r.readAsDataURL(file); }) : null; try { await trailersysApiRequest("POST", `/mis-cargas/${cargaRecepcionId}/confirmar-recepcion`, { observacion: obs, novedad: tipo, evidencia }); trailersysCloseModal($("pedidoRecepcionOverlay")); await cargar(); } catch (err) { alert(err.message); } };
  $("pedidoBuscar").oninput = filtrar; $("pedidoEstadoFiltro").onchange = filtrar; $("pedidoFechaFiltro").onchange = filtrar; cargar();
})();
