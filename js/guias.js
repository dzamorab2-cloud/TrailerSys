(function () {
  const session = trailersysGetSession();
  if (!["administrador", "coordinador"].includes(session?.role)) return;

  const body = document.getElementById("guiasBody");
  const pager = document.getElementById("guiasPager");
  const stats = document.getElementById("guiaStats");
  const search = document.getElementById("guiaBuscar");
  const tipo = document.getElementById("guiaTipoFiltro");
  const estado = document.getElementById("guiaEstadoFiltro");
  let currentPage = 0;
  let cache = [];
  let debounceTimer;

  const escapeHtml = (value) => String(value ?? "—").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[char]));
  const peso = (kg) => {
    const kilos = Number(kg) || 0;
    return `${kilos.toLocaleString("es-EC")} kg / ${(kilos * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  };

  function renderRows() {
    if (!cache.length) {
      body.innerHTML = '<tr class="loading-row"><td colspan="7">No existen guías que coincidan con los filtros.</td></tr>';
      return;
    }
    body.innerHTML = cache.map((guia) => `
      <tr>
        <td><div class="table-primary">${escapeHtml(guia.numero)}</div>${guia.fecha ? `<div class="table-secondary">${trailersysFormatDateTime(guia.fecha)}</div>` : ""}</td>
        <td><span class="badge ${guia.tipo === "VIAJE" ? "badge-info" : "badge-neutral"}">${guia.tipo === "VIAJE" ? "Viaje" : "Carga"}</span></td>
        <td><div class="table-primary">${escapeHtml(guia.descripcion)}</div><div class="table-secondary">${escapeHtml(guia.cliente)}</div></td>
        <td><div>${escapeHtml(guia.conductor || "Pendiente de asignar")}</div><div class="table-secondary">${escapeHtml(guia.placa || "Sin vehículo")}</div></td>
        <td>${escapeHtml(guia.origen)}<br><span class="table-secondary">a ${escapeHtml(guia.destino)}</span></td>
        <td><span class="badge badge-neutral">${escapeHtml(guia.estado)}</span></td>
        <td><button class="icon-btn" data-guia-id="${guia.referenciaId}" title="Ver e imprimir guía"><i class="bi bi-file-earmark-text"></i></button></td>
      </tr>`).join("");
  }

  async function load() {
    body.innerHTML = '<tr class="loading-row"><td colspan="7">Cargando guías…</td></tr>';
    const query = new URLSearchParams({ page: currentPage, size: 24, search: search.value.trim(), tipo: tipo.value, estado: estado.value });
    try {
      const data = await trailersysApiRequest("GET", `/guias?${query}`);
      cache = data.content;
      renderRows();
      stats.innerHTML = `
        <div class="stat-card card"><div class="stat-icon"><i class="bi bi-files"></i></div><div><div class="stat-value">${Number(data.totalElements).toLocaleString("es-EC")}</div><div class="stat-label">Guías encontradas</div></div></div>
        <div class="stat-card card"><div class="stat-icon"><i class="bi bi-file-earmark-text"></i></div><div><div class="stat-value">${cache.length}</div><div class="stat-label">Mostradas en esta página</div></div></div>`;
      pager.innerHTML = `<span>${Number(data.totalElements).toLocaleString("es-EC")} registros · Página ${data.number + 1} de ${Math.max(1, data.totalPages)}</span>
        <div class="pagination-actions"><button class="btn btn-ghost" data-page="prev" ${data.first ? "disabled" : ""}>Anterior</button><button class="btn btn-ghost" data-page="next" ${data.last ? "disabled" : ""}>Siguiente</button></div>`;
      pager.querySelector('[data-page="prev"]').onclick = () => { currentPage--; load(); };
      pager.querySelector('[data-page="next"]').onclick = () => { currentPage++; load(); };
    } catch (error) {
      cache = [];
      body.innerHTML = `<tr class="loading-row"><td colspan="7">${escapeHtml(error.message || "No se pudieron cargar las guías.")}</td></tr>`;
    }
  }

  // Los datos de conductor/vehiculo/carga vienen ya incluidos en el propio
  // ViajeResponse (el backend los denormaliza ahi, ver el comentario en
  // ViajeResponse.java) en vez de pedirse por separado a /conductores/{id},
  // /vehiculos/{id} y /cargas/{id}. Mismo fix ya aplicado en
  // js/viajes.js#showViajeGuide(): aunque en este modulo (solo
  // Administrador/Coordinador) esos 3 endpoints nunca devuelven 403, seguian
  // siendo 2-3 peticiones de red completamente innecesarias por cada guia
  // abierta, con datos que ya habian llegado en la primera.
  async function showViaje(id) {
    const viaje = await trailersysApiRequest("GET", `/viajes/${id}`);
    trailersysShowGuide({ tipo: "Viaje", id: viaje.id, estado: viaje.estado, secciones: [
      { titulo: "Conductor", icono: "bi-person-badge", campos: [["Nombre", viaje.conductorNombres], ["Identificación", viaje.conductorIdentificacion], ["Teléfono", viaje.conductorTelefono], ["Licencia", viaje.conductorLicenciaNumero], ["Categoría", viaje.conductorLicenciaCategoria], ["Vencimiento", viaje.conductorLicenciaVencimiento]] },
      { titulo: "Vehículo", icono: "bi-truck", campos: [["Placa", viaje.vehiculoPlaca], ["Marca", viaje.vehiculoMarca], ["Modelo", viaje.vehiculoModelo], ["Tipo", viaje.vehiculoTipo], ["Año", viaje.vehiculoAnio], ["Color", viaje.vehiculoColor], ["Capacidad", viaje.vehiculoCapacidad != null ? peso(viaje.vehiculoCapacidad) : "—"]] },
      { titulo: "Carga transportada", icono: "bi-box-seam", campos: [["Mercancía", viaje.cargaDescripcion || "Sin carga asociada"], ["Tipo", viaje.cargaTipo], ["Peso", viaje.cargaPeso != null ? peso(viaje.cargaPeso) : "—"], ["Cliente", viaje.clienteNombre]] },
      { titulo: "Ruta y despacho", icono: "bi-signpost-split", campos: [["Origen", viaje.origen], ["Destino", viaje.destino], ["Salida", trailersysFormatDateTime(viaje.fechaSalida)], ["Distancia", viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km` : "Sin ruta"], ["Duración", viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "Sin ruta"], ["Observaciones", viaje.observaciones || "Sin observaciones"]] }
    ] });
  }

  async function showCarga(id) {
    const carga = await trailersysApiRequest("GET", `/cargas/${id}`);
    // Sigue haciendo falta este fetch (una Carga no sabe su propio viaje),
    // pero ya no uno aparte a /conductores/{id} y /vehiculos/{id}: el
    // ViajeResponse que devuelve trae esos datos denormalizados tal cual.
    const viaje = await trailersysApiRequest("GET", `/viajes/por-carga/${id}`).catch(() => null);
    trailersysShowGuide({ tipo: "Carga", id: carga.id, estado: carga.estado, secciones: [
      { titulo: "Datos de la carga", icono: "bi-box-seam", campos: [["Mercancía", carga.descripcion], ["Tipo", carga.tipo], ["Peso", peso(carga.peso)], ["Observaciones", carga.observaciones || "Sin observaciones"]] },
      { titulo: "Cliente y recorrido", icono: "bi-building", campos: [["Cliente", carga.clienteNombre], ["Origen", carga.origen], ["Destino", carga.destino]] },
      { titulo: "Transporte asignado", icono: "bi-truck", campos: [["Conductor", viaje?.conductorNombres || "Pendiente de asignar"], ["Identificación", viaje?.conductorIdentificacion], ["Licencia", viaje?.conductorLicenciaNumero], ["Categoría", viaje?.conductorLicenciaCategoria], ["Vehículo", viaje ? `${viaje.vehiculoMarca} ${viaje.vehiculoModelo}` : "Pendiente de asignar"], ["Placa", viaje?.vehiculoPlaca], ["Capacidad", viaje?.vehiculoCapacidad != null ? peso(viaje.vehiculoCapacidad) : "—"]] }
    ] });
  }

  body.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-guia-id]");
    if (!button) return;
    const guia = cache.find((item) => String(item.referenciaId) === button.dataset.guiaId);
    if (!guia) return;
    button.disabled = true;
    try { await (guia.tipo === "VIAJE" ? showViaje(guia.referenciaId) : showCarga(guia.referenciaId)); }
    catch (error) { alert(error.message || "No se pudo abrir la guía."); }
    finally { button.disabled = false; }
  });

  search.addEventListener("input", () => { clearTimeout(debounceTimer); debounceTimer = setTimeout(() => { currentPage = 0; load(); }, 300); });
  [tipo, estado].forEach((control) => control.addEventListener("change", () => { currentPage = 0; load(); }));
  load();
})();
