(function () {
  const $ = (id) => document.getElementById(id);
  const badge = { Pendiente: "badge-warning", Asignada: "badge-info", "En Tránsito": "badge-neutral", Entregada: "badge-success" };
  let pedidos = [], viajes = {}, detalleActual = null, cargaRecepcionId = null, unidadAnterior = "kg", pedidoEditandoId = null;
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const peso = (kg) => `${Number(kg || 0).toLocaleString("es-EC")} kg / ${(Number(kg || 0) * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  const labelNovedad = (v) => ({ COMPLETO: "Completo", INCOMPLETO: "Incompleto", DANADO: "Dañado", INCORRECTO: "Incorrecto", OTRO: "Otro" }[v] || v || "—");

  function actualizarKpis() {
    $("pedidoKpiActivos").textContent = pedidos.filter((p) => p.estado !== "Entregada").length;
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
    const reclamo = v?.estadoReclamoCliente ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Reclamo ${esc(v.estadoReclamoCliente.toLowerCase())}</span>` : "";
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
    const items = [{ titulo: "Pedido creado", detalle: `Carga #${d.carga.id} registrada` }];
    if (d.viaje) items.push({ titulo: "Viaje asignado", detalle: `Viaje #${d.viaje.id} · ${d.viaje.vehiculoPlaca}` });
    [...(d.eventos || [])].reverse().forEach((e) => items.push({ titulo: e.evento, detalle: `${e.ubicacion} · ${trailersysFormatDateTime(e.fechaHora)}` }));
    if (d.viaje?.estado === "Finalizado") items.push({ titulo: "Entrega registrada", detalle: "El viaje fue finalizado" });
    if (d.viaje?.entregaConfirmadaCliente) items.push({ titulo: "Recepción confirmada", detalle: labelNovedad(d.viaje.novedadRecepcionCliente) });
    return items.map((x) => `<div class="pedido-timeline-item"><span></span><div><strong>${esc(x.titulo)}</strong><p>${esc(x.detalle)}</p></div></div>`).join("");
  }

  async function abrirDetalle(id) {
    $("pedidoDetalleContenido").innerHTML = `<p>Cargando detalle...</p>`; trailersysOpenModal($("pedidoDetalleOverlay"));
    try {
      const d = await trailersysApiRequest("GET", `/mis-cargas/${id}/detalle`); detalleActual = d; const c = d.carga, v = d.viaje;
      const estadoBadgeDetalle = estaVerificada(v)
        ? `<span class="badge badge-success"><i class="bi bi-check-circle-fill"></i> Verificada</span>`
        : `<span class="badge ${badge[c.estado] || "badge-neutral"}">${esc(c.estado)}</span>`;
      $("pedidoDetalleContenido").innerHTML = `<div class="pedido-detail-head"><div><span class="eyebrow">GUÍA TS-${String(c.id).padStart(6, "0")}</span><h2>${esc(c.descripcion)}</h2><p>${esc(c.origen)} → ${esc(c.destino)}</p></div>${estadoBadgeDetalle}</div><div class="pedido-detail-grid"><div><small>Cliente</small><strong>${esc(c.clienteNombre)}</strong></div><div><small>Tipo y peso</small><strong>${esc(c.tipo)} · ${peso(c.peso)}</strong></div><div><small>Vehículo</small><strong>${esc(v?.vehiculoPlaca || "Por asignar")}</strong></div><div><small>Conductor</small><strong>${esc(v?.conductorNombres || "Por asignar")}</strong></div><div><small>Salida programada</small><strong>${v?.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "Por definir"}</strong></div><div><small>Distancia / duración</small><strong>${v?.ruta ? `${Number(v.ruta.distanciaKm).toFixed(1)} km · ${Math.round(v.ruta.duracionMin)} min` : "Por calcular"}</strong></div></div>${v?.estadoReclamoCliente ? `<div class="alert alert-danger"><i class="bi bi-exclamation-triangle"></i><div><strong>Reclamo ${esc(v.estadoReclamoCliente.toLowerCase())}: ${esc(labelNovedad(v.novedadRecepcionCliente))}</strong><p>${esc(v.observacionConfirmacionCliente)}</p>${v.respuestaReclamoCliente ? `<p><b>Respuesta:</b> ${esc(v.respuestaReclamoCliente)}</p>` : ""}</div></div>` : ""}<h3>Historial del viaje</h3><div class="pedido-timeline">${lineaTiempo(d)}</div>`;
      if (v?.evidenciaRecepcionCliente) {
        const evidencia = document.createElement("img"); evidencia.className = "pedido-evidencia";
        evidencia.src = v.evidenciaRecepcionCliente; evidencia.alt = "Evidencia del reclamo";
        $("pedidoDetalleContenido").querySelector("h3")?.before(evidencia);
      }
      if (v?.ruta && window.L) {
        const mapa = document.createElement("div"); mapa.className = "pedido-detail-map";
        $("pedidoDetalleContenido").querySelector("h3")?.before(mapa);
        setTimeout(() => {
          const m = L.map(mapa).setView([v.ruta.origenLat, v.ruta.origenLng], 7);
          L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { attribution: "© OpenStreetMap" }).addTo(m);
          const puntos = v.ruta.path?.length ? v.ruta.path : [[v.ruta.origenLat, v.ruta.origenLng], [v.ruta.destinoLat, v.ruta.destinoLng]];
          const linea = L.polyline(puntos, { color: "#2563eb", weight: 5 }).addTo(m); m.fitBounds(linea.getBounds(), { padding: [25, 25] });
          L.marker(puntos[0]).addTo(m).bindPopup("Origen"); L.marker(puntos[puntos.length - 1]).addTo(m).bindPopup("Destino");
        }, 50);
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

  const modal = $("pedidoModalOverlay"), form = $("pedidoForm");
  $("btnNuevoPedido").onclick = () => {
    form.reset(); unidadAnterior = "kg"; pedidoEditandoId = null;
    $("pedidoModalTitulo").textContent = "Hacer un pedido";
    $("pedidoSubmitTexto").textContent = "Enviar pedido";
    trailersysOpenModal(modal);
  };
  $("pedidoModalClose").onclick = $("pedidoCancelar").onclick = () => trailersysCloseModal(modal);

  // Reutiliza el mismo modal/formulario de "Hacer un pedido": precarga los
  // valores actuales y cambia el envio a PUT en vez de POST (ver
  // form.onsubmit). Solo se llega aca para un pedido todavia "Pendiente"
  // (unico boton que la muestra, ver tarjeta()).
  function abrirEdicion(c) {
    form.reset();
    pedidoEditandoId = c.id;
    $("pedidoModalTitulo").textContent = `Editar pedido #${c.id}`;
    $("pedidoSubmitTexto").textContent = "Guardar cambios";
    $("pedidoDescripcion").value = c.descripcion || "";
    $("pedidoTipo").value = c.tipo || "";
    $("pedidoPesoUnidad").value = "kg"; unidadAnterior = "kg";
    $("pedidoPeso").value = c.peso ?? "";
    $("pedidoOrigen").value = c.origen || "";
    $("pedidoDestino").value = c.destino || "";
    $("pedidoObservaciones").value = c.observaciones || "";
    trailersysOpenModal(modal);
  }

  async function cancelarPedido(id) {
    if (!window.confirm("¿Cancelar este pedido? Esta acción no se puede deshacer.")) return;
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
  $("pedidoDetalleClose").onclick = $("pedidoDetalleAceptar").onclick = () => trailersysCloseModal($("pedidoDetalleOverlay")); $("pedidoImprimirGuia").onclick = imprimirGuia;
  $("pedidoRecepcionClose").onclick = $("pedidoRecepcionCancelar").onclick = () => trailersysCloseModal($("pedidoRecepcionOverlay"));
  $("pedidoRecepcionForm").onsubmit = async (e) => { e.preventDefault(); const tipo = $("pedidoRecepcionTipo").value, obs = $("pedidoRecepcionObservacion").value.trim(), file = $("pedidoRecepcionEvidencia").files[0]; if (tipo !== "COMPLETO" && !obs) return alert("Describe el problema para registrar el reclamo."); if (file && file.size > 4 * 1024 * 1024) return alert("La imagen supera 4 MB."); const evidencia = file ? await new Promise((resolve, reject) => { const r = new FileReader(); r.onload = () => resolve(r.result); r.onerror = reject; r.readAsDataURL(file); }) : null; try { await trailersysApiRequest("POST", `/mis-cargas/${cargaRecepcionId}/confirmar-recepcion`, { observacion: obs, novedad: tipo, evidencia }); trailersysCloseModal($("pedidoRecepcionOverlay")); await cargar(); } catch (err) { alert(err.message); } };
  $("pedidoBuscar").oninput = filtrar; $("pedidoEstadoFiltro").onchange = filtrar; $("pedidoFechaFiltro").onchange = filtrar; cargar();
})();
