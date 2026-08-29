(function () {
  const $ = (id) => document.getElementById(id);
  const badge = { Pendiente: "badge-warning", Asignada: "badge-info", "En Tránsito": "badge-neutral", Entregada: "badge-success" };
  let pedidos = [], viajes = {}, detalleActual = null, cargaRecepcionId = null, unidadAnterior = "kg";
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
      else if (v.estado === "Finalizado" && !v.entregaConfirmadaCliente) novedades.push(`El viaje #${v.id} fue entregado. Confirma cómo recibiste la carga.`);
    });
    $("pedidoNotifications").hidden = novedades.length === 0;
    $("pedidoNotifications").innerHTML = novedades.length ? `<h3><i class="bi bi-bell"></i> Notificaciones</h3>${novedades.map((n) => `<p>${esc(n)}</p>`).join("")}` : "";
  }

  function tarjeta(c) {
    const v = viajes[c.id];
    const reclamo = v?.estadoReclamoCliente ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Reclamo ${esc(v.estadoReclamoCliente.toLowerCase())}</span>` : "";
    const confirmar = c.estado === "Entregada" && v && !v.entregaConfirmadaCliente ? `<button class="btn btn-primary" data-action="recibir" data-id="${c.id}"><i class="bi bi-clipboard-check"></i> Confirmar recepción</button>` : "";
    return `<article class="card item-card pedido-card"><div class="item-banner"><i class="bi bi-box-seam"></i><div class="item-banner-title"><div class="item-title">Pedido #${c.id} · ${esc(c.descripcion)}</div><div class="item-subtitle">${esc(c.tipo)}</div></div></div><div class="item-body"><div class="item-route"><i class="bi bi-geo-alt"></i><span>${esc(c.origen)}</span><i class="bi bi-arrow-right"></i><span>${esc(c.destino)}</span></div><div class="item-meta"><span class="badge ${badge[c.estado] || "badge-neutral"}">${esc(c.estado)}</span><span><i class="bi bi-box-seam"></i>${peso(c.peso)}</span><span><i class="bi bi-truck"></i>${esc(v?.estado || "Sin asignar")}</span></div><div class="pedido-card-actions">${reclamo}<button class="btn btn-ghost" data-action="detalle" data-id="${c.id}"><i class="bi bi-eye"></i> Ver detalle y guía</button>${confirmar}</div></div></article>`;
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
      $("pedidoDetalleContenido").innerHTML = `<div class="pedido-detail-head"><div><span class="eyebrow">GUÍA TS-${String(c.id).padStart(6, "0")}</span><h2>${esc(c.descripcion)}</h2><p>${esc(c.origen)} → ${esc(c.destino)}</p></div><span class="badge ${badge[c.estado] || "badge-neutral"}">${esc(c.estado)}</span></div><div class="pedido-detail-grid"><div><small>Cliente</small><strong>${esc(c.clienteNombre)}</strong></div><div><small>Tipo y peso</small><strong>${esc(c.tipo)} · ${peso(c.peso)}</strong></div><div><small>Vehículo</small><strong>${esc(v?.vehiculoPlaca || "Por asignar")}</strong></div><div><small>Conductor</small><strong>${esc(v?.conductorNombres || "Por asignar")}</strong></div><div><small>Salida programada</small><strong>${v?.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "Por definir"}</strong></div><div><small>Distancia / duración</small><strong>${v?.ruta ? `${Number(v.ruta.distanciaKm).toFixed(1)} km · ${Math.round(v.ruta.duracionMin)} min` : "Por calcular"}</strong></div></div>${v?.estadoReclamoCliente ? `<div class="alert alert-danger"><i class="bi bi-exclamation-triangle"></i><div><strong>Reclamo ${esc(v.estadoReclamoCliente.toLowerCase())}: ${esc(labelNovedad(v.novedadRecepcionCliente))}</strong><p>${esc(v.observacionConfirmacionCliente)}</p>${v.respuestaReclamoCliente ? `<p><b>Respuesta:</b> ${esc(v.respuestaReclamoCliente)}</p>` : ""}</div></div>` : ""}<h3>Historial del viaje</h3><div class="pedido-timeline">${lineaTiempo(d)}</div>`;
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
    if (!detalleActual) return; const w = window.open("", "_blank", "width=900,height=700");
    w.document.write(`<!doctype html><html><head><title>Guía TS-${detalleActual.carga.id}</title><link rel="stylesheet" href="css/variables.css"><link rel="stylesheet" href="css/base.css"><link rel="stylesheet" href="css/components.css"><link rel="stylesheet" href="css/pedidos.css"><style>body{padding:32px}.pedido-detail{max-width:900px;margin:auto}</style></head><body><div class="pedido-detail"><h1>TrailerSys · Guía de transporte</h1>${$("pedidoDetalleContenido").innerHTML}<p>Documento generado el ${new Date().toLocaleString("es-EC")}</p></div><script>window.onload=()=>window.print()<\/script></body></html>`); w.document.close();
  }

  const modal = $("pedidoModalOverlay"), form = $("pedidoForm");
  $("btnNuevoPedido").onclick = () => { form.reset(); unidadAnterior = "kg"; trailersysOpenModal(modal); };
  $("pedidoModalClose").onclick = $("pedidoCancelar").onclick = () => trailersysCloseModal(modal);
  $("pedidoPesoUnidad").onchange = () => { const input = $("pedidoPeso"), valor = Number(input.value); if (input.value) { const kg = unidadAnterior === "lb" ? valor / 2.2046226218 : valor; input.value = ($("pedidoPesoUnidad").value === "lb" ? kg * 2.2046226218 : kg).toFixed(2); } unidadAnterior = $("pedidoPesoUnidad").value; };
  form.onsubmit = async (e) => { e.preventDefault(); const valor = Number($("pedidoPeso").value), kg = $("pedidoPesoUnidad").value === "lb" ? valor / 2.2046226218 : valor; const data = { descripcion: $("pedidoDescripcion").value.trim(), tipo: $("pedidoTipo").value.trim(), peso: Math.round(kg), origen: $("pedidoOrigen").value, destino: $("pedidoDestino").value, observaciones: $("pedidoObservaciones").value.trim() }; if (!data.descripcion || !data.tipo || !data.origen || !data.destino || !(data.peso >= 0)) return alert("Completa los campos obligatorios."); try { await trailersysApiRequest("POST", "/mis-cargas", data); trailersysCloseModal(modal); await cargar(); } catch (err) { alert(err.message); } };
  $("pedidoGrid").onclick = (e) => { const b = e.target.closest("button[data-action]"); if (!b) return; if (b.dataset.action === "detalle") abrirDetalle(b.dataset.id); else { cargaRecepcionId = b.dataset.id; $("pedidoRecepcionForm").reset(); trailersysOpenModal($("pedidoRecepcionOverlay")); } };
  $("pedidoDetalleClose").onclick = $("pedidoDetalleAceptar").onclick = () => trailersysCloseModal($("pedidoDetalleOverlay")); $("pedidoImprimirGuia").onclick = imprimirGuia;
  $("pedidoRecepcionClose").onclick = $("pedidoRecepcionCancelar").onclick = () => trailersysCloseModal($("pedidoRecepcionOverlay"));
  $("pedidoRecepcionForm").onsubmit = async (e) => { e.preventDefault(); const tipo = $("pedidoRecepcionTipo").value, obs = $("pedidoRecepcionObservacion").value.trim(), file = $("pedidoRecepcionEvidencia").files[0]; if (tipo !== "COMPLETO" && !obs) return alert("Describe el problema para registrar el reclamo."); if (file && file.size > 4 * 1024 * 1024) return alert("La imagen supera 4 MB."); const evidencia = file ? await new Promise((resolve, reject) => { const r = new FileReader(); r.onload = () => resolve(r.result); r.onerror = reject; r.readAsDataURL(file); }) : null; try { await trailersysApiRequest("POST", `/mis-cargas/${cargaRecepcionId}/confirmar-recepcion`, { observacion: obs, novedad: tipo, evidencia }); trailersysCloseModal($("pedidoRecepcionOverlay")); await cargar(); } catch (err) { alert(err.message); } };
  $("pedidoBuscar").oninput = filtrar; $("pedidoEstadoFiltro").onchange = filtrar; $("pedidoFechaFiltro").onchange = filtrar; cargar();
})();
