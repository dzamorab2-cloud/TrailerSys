(function () {
  const tabsContainer = document.getElementById("reportTabs");
  const filtersContainer = document.getElementById("reportFilters");
  const statsRow = document.getElementById("reportStats");
  const tableHead = document.getElementById("reportTableHead");
  const tableBody = document.getElementById("reportTableBody");
  const tableWrap = document.getElementById("reportTableWrap");
  const emptyState = document.getElementById("reportEmptyState");
  const emptyTitle = document.getElementById("reportEmptyTitle");
  const emptyText = document.getElementById("reportEmptyText");
  const printHeader = document.getElementById("reportPrintHeader");
  const scopeNote = document.getElementById("reportScopeNote");
  const btnExportarCsv = document.getElementById("btnExportarCsv");
  const btnImprimir = document.getElementById("btnImprimirReporte");

  const TAB_LABELS = {
    vehiculos: "Vehículos",
    conductores: "Conductores",
    cargas: "Cargas",
    viajes: "Viajes",
    mantenimientos: "Mantenimientos",
    clientes: "Clientes",
  };

  // Espeja los @PreAuthorize de GET /api/paginas/* en CatalogoPageController
  // (unica fuente real de verdad del permiso). De los roles con acceso al
  // modulo "reportes" (administrador y supervisor, ver roles.js), supervisor
  // no tiene permiso de backend para conductores/mantenimientos/clientes:
  // esas pestañas quedaban visibles pero siempre devolvian 403 ("No tienes
  // permiso para ver este reporte") apenas se hacia clic - un callejon sin
  // salida en vez de simplemente no ofrecer una pestaña que ese rol nunca
  // podra usar.
  const TAB_ROLES = {
    vehiculos: ["administrador", "coordinador", "mantenimiento", "supervisor"],
    conductores: ["administrador", "coordinador"],
    cargas: ["administrador", "coordinador"],
    viajes: ["administrador", "coordinador", "conductor", "supervisor"],
    mantenimientos: ["administrador", "mantenimiento"],
    clientes: ["administrador", "coordinador"],
  };

  let currentTab = "vehiculos";
  let currentExport = { headers: [], rows: [], filenamePrefix: "reporte" };

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

  // OJO: no usar new Date().toISOString().slice(0,10) aqui. toISOString()
  // convierte a UTC, así que en Ecuador (UTC-5) cualquier hora desde las
  // 19:00 en adelante ya cae en el dia UTC siguiente: el filtro "Hoy"
  // terminaba mostrando una fecha que todavia no llegaba localmente.
  function todayIso() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  // Estado de filtros por pestaña. Se mantiene en variables aparte (en vez
  // de leerse siempre del DOM) porque el contenedor de filtros se
  // reconstruye con innerHTML en cada cambio de pestaña: antes, elegir un
  // estado disparaba el "change", pero el propio handler reconstruia el
  // <select> desde cero (con "" como unico valor marcado "selected") justo
  // antes de leer su valor, así que ningún filtro llegaba a aplicarse
  // realmente. Viajes y Mantenimientos arrancan acotados a "hoy": son
  // catálogos de decenas o cientos de miles de filas, y sin un rango de
  // fecha razonable el reporte (y lo que se imprime/exporta) termina
  // mostrando una muestra arbitraria de 100 registros como si fuera todo.
  const filtros = {
    vehiculos: { estado: "" },
    conductores: { estado: "" },
    cargas: { estado: "" },
    viajes: { estado: "", desde: todayIso(), hasta: todayIso() },
    mantenimientos: { vehiculoId: "", tipo: "", desde: todayIso(), hasta: todayIso() },
    clientes: { estado: "" },
  };

  function formatCosto(value) {
    return `$ ${Number(value).toLocaleString("es-EC", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function statCard(icon, value, label) {
    return `
      <div class="stat-card">
        <div class="stat-card-icon"><i class="bi ${icon}"></i></div>
        <div>
          <div class="stat-card-value">${value}</div>
          <div class="stat-card-label">${label}</div>
        </div>
      </div>`;
  }

  function selectOption(value, label, current) {
    return `<option value="${value}" ${value === current ? "selected" : ""}>${label}</option>`;
  }

  // Un input de fecha vacio no debe filtrar nada: si el usuario borra el
  // campo (o nunca lo toca), "" se manda tal cual y trailersysPagedRequest
  // ya omite los parametros vacios.
  function dateRangeInputsHtml(desde, hasta) {
    return `
      <input type="date" id="reportFiltroDesde" class="select-pill" value="${desde}" title="Desde" />
      <input type="date" id="reportFiltroHasta" class="select-pill" value="${hasta}" title="Hasta" />
      <button type="button" class="btn btn-ghost" id="reportFiltroHoy"><i class="bi bi-calendar-day"></i> Hoy</button>
      <button type="button" class="btn btn-ghost" id="reportFiltroVerTodo"><i class="bi bi-x-circle"></i> Ver todo</button>`;
  }

  function bindDateRangeEvents(filtro, onChange) {
    document.getElementById("reportFiltroDesde").addEventListener("change", (event) => {
      filtro.desde = event.target.value;
      onChange(false);
    });
    document.getElementById("reportFiltroHasta").addEventListener("change", (event) => {
      filtro.hasta = event.target.value;
      onChange(false);
    });
    document.getElementById("reportFiltroHoy").addEventListener("click", () => {
      filtro.desde = todayIso();
      filtro.hasta = todayIso();
      onChange(true);
    });
    document.getElementById("reportFiltroVerTodo").addEventListener("click", () => {
      filtro.desde = "";
      filtro.hasta = "";
      onChange(true);
    });
  }

  function descripcionRangoFecha(desde, hasta) {
    if (!desde && !hasta) return "";
    if (desde && desde === hasta) return `Fecha: ${desde}`;
    return `Del ${desde || "inicio"} al ${hasta || "hoy"}`;
  }

  // --- Tabla generica ---
  function renderTable(headers, rows) {
    tableHead.innerHTML = `<tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr>`;

    if (rows.length === 0) {
      tableWrap.hidden = true;
      emptyState.hidden = false;
      // Vuelve al mensaje generico por si la pestaña venia de mostrar el
      // error de permisos de mostrarErrorPermiso() en una consulta anterior.
      emptyTitle.textContent = "Sin datos para mostrar";
      emptyText.textContent = "Ningún registro coincide con el filtro aplicado.";
      tableBody.innerHTML = "";
      return;
    }

    tableWrap.hidden = false;
    emptyState.hidden = true;
    tableBody.innerHTML = rows
      .map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join("")}</tr>`)
      .join("");
  }

  // Antes, si el backend devolvia 403 (rol sin permiso para este catalogo,
  // ej. Supervisor viendo Conductores) el error se tragaba en silencio y el
  // reporte quedaba en "0 total / sin datos", indistinguible de un catalogo
  // realmente vacio. Ahora se muestra con claridad que fue un problema de
  // permisos (o de conexion) y no que no hay registros.
  function mostrarErrorPermiso(error) {
    statsRow.innerHTML = "";
    scopeNote.hidden = true;
    printHeader.innerHTML = "";
    renderTable([], []);
    emptyTitle.textContent = "No se pudo cargar este reporte";
    emptyText.textContent = error?.status === 403
      ? "No tienes permiso para ver este reporte."
      : (error?.message || "Ocurrió un error al conectar con el servidor.");
    setExportData([], [], "reporte");
  }

  function setExportData(headers, rows, filenamePrefix) {
    currentExport = { headers, rows, filenamePrefix };
    btnExportarCsv.disabled = rows.length === 0;
  }

  // Dejar claro cuando la tabla (y por lo tanto la impresión/CSV) no
  // alcanza a cubrir todos los registros que coinciden con el filtro: con
  // catálogos de decenas de miles de filas, mostrar "100" sin más contexto
  // se leía como si esa fuera la cantidad real. Acotando por fecha (o por
  // estado) el total casi siempre cabe entero; cuando no, al menos queda
  // documentado en pantalla y en el propio documento impreso.
  function updateScopeNote(totalElements, mostrados) {
    if (totalElements > mostrados) {
      scopeNote.hidden = false;
      scopeNote.textContent = `Mostrando los ${mostrados.toLocaleString("es-EC")} registros más recientes de un total de ${totalElements.toLocaleString("es-EC")} que coinciden con el filtro. Acota por fecha para ver el reporte completo.`;
    } else {
      scopeNote.hidden = true;
      scopeNote.textContent = "";
    }
  }

  function updatePrintHeader(detalleFiltro) {
    printHeader.innerHTML = `
      <div class="print-kicker"><span class="print-brand-mark">TS</span> TrailerSys · Reporte operativo</div>
      <div class="print-title">Reporte de ${TAB_LABELS[currentTab]}</div>
      <div class="print-meta">Generado el ${trailersysFormatDateTime(new Date())}${detalleFiltro ? ` · ${escapeHtml(detalleFiltro)}` : ""}</div>`;
  }

  // --- Reporte: Vehiculos ---
  async function renderVehiculosReport({ rebuildFilters = false } = {}) {
    const f = filtros.vehiculos;
    if (rebuildFilters) {
      filtersContainer.innerHTML = `
        <select id="reportFiltroEstado" class="select-pill">
          ${selectOption("", "Todos los estados", f.estado)}
          ${["Disponible", "En Ruta", "Mantenimiento", "Fuera de Servicio"].map((e) => selectOption(e, e, f.estado)).join("")}
        </select>`;
      document.getElementById("reportFiltroEstado").addEventListener("change", (event) => {
        f.estado = event.target.value;
        renderVehiculosReport();
      });
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("vehiculos", 0, 100, { estado: f.estado });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const vehiculos = pagina.content;

    const counts = { Disponible: 0, "En Ruta": 0, Mantenimiento: 0, "Fuera de Servicio": 0 };
    vehiculos.forEach((v) => {
      if (counts[v.estado] !== undefined) counts[v.estado] += 1;
    });

    statsRow.innerHTML = [
      statCard("bi-truck", pagina.totalElements.toLocaleString("es-EC"), "Total vehículos"),
      statCard("bi-check-circle", counts.Disponible, "Disponibles"),
      statCard("bi-signpost", counts["En Ruta"], "En ruta"),
      statCard("bi-tools", counts.Mantenimiento + counts["Fuera de Servicio"], "En mantenimiento / fuera de servicio"),
    ].join("");

    const headers = ["Placa", "Marca", "Modelo", "Tipo", "Año", "Estado", "Kilometraje", "Capacidad"];
    const rows = vehiculos.map((v) => [
      escapeHtml(v.placa),
      escapeHtml(v.marca),
      escapeHtml(v.modelo),
      escapeHtml(v.tipo),
      v.anio,
      escapeHtml(v.estado),
      `${Number(v.kilometraje).toLocaleString("es-EC")} km`,
      `${Number(v.capacidad).toLocaleString("es-EC")} kg`,
    ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-vehiculos");
    updateScopeNote(pagina.totalElements, vehiculos.length);
    updatePrintHeader(f.estado ? `Estado: ${f.estado}` : "");
  }

  // --- Reporte: Conductores ---
  async function renderConductoresReport({ rebuildFilters = false } = {}) {
    const f = filtros.conductores;
    if (rebuildFilters) {
      filtersContainer.innerHTML = `
        <select id="reportFiltroEstado" class="select-pill">
          ${selectOption("", "Todos los estados", f.estado)}
          ${["Disponible", "En Ruta", "Descanso", "Inactivo"].map((e) => selectOption(e, e, f.estado)).join("")}
        </select>`;
      document.getElementById("reportFiltroEstado").addEventListener("change", (event) => {
        f.estado = event.target.value;
        renderConductoresReport();
      });
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("conductores", 0, 100, { estado: f.estado });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const conductores = pagina.content;

    const en30Dias = new Date();
    en30Dias.setDate(en30Dias.getDate() + 30);
    const en30DiasIso = en30Dias.toISOString().slice(0, 10);

    const activos = conductores.filter((c) => c.estado === "Disponible" || c.estado === "En Ruta").length;
    const vencidas = conductores.filter((c) => c.licenciaVencida).length;
    const porVencer = conductores.filter(
      (c) => !c.licenciaVencida && c.licenciaVencimiento && c.licenciaVencimiento <= en30DiasIso
    ).length;

    statsRow.innerHTML = [
      statCard("bi-person-badge", pagina.totalElements.toLocaleString("es-EC"), "Total conductores"),
      statCard("bi-check-circle", activos, "Activos (disponible / en ruta)"),
      statCard("bi-exclamation-triangle", vencidas, "Licencias vencidas"),
      statCard("bi-alarm", porVencer, "Licencias por vencer (30 días)"),
    ].join("");

    const headers = ["Nombres", "Identificación", "Teléfono", "Licencia", "Categoría", "Vencimiento", "Estado", "Vehículo asignado"];
    const rows = conductores.map((c) => [
      escapeHtml(c.nombres),
      escapeHtml(c.identificacion),
      escapeHtml(c.telefono),
      escapeHtml(c.licenciaNumero),
      escapeHtml(c.licenciaCategoria),
      escapeHtml(c.licenciaVencimiento),
      escapeHtml(c.estado),
      c.vehiculoPlaca ? escapeHtml(c.vehiculoPlaca) : "—",
    ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-conductores");
    updateScopeNote(pagina.totalElements, conductores.length);
    updatePrintHeader(f.estado ? `Estado: ${f.estado}` : "");
  }

  // --- Reporte: Cargas ---
  async function renderCargasReport({ rebuildFilters = false } = {}) {
    const f = filtros.cargas;
    if (rebuildFilters) {
      filtersContainer.innerHTML = `
        <select id="reportFiltroEstado" class="select-pill">
          ${selectOption("", "Todos los estados", f.estado)}
          ${["Pendiente", "Asignada", "En Tránsito", "Entregada", "Cancelada"].map((e) => selectOption(e, e, f.estado)).join("")}
        </select>`;
      document.getElementById("reportFiltroEstado").addEventListener("change", (event) => {
        f.estado = event.target.value;
        renderCargasReport();
      });
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("cargas", 0, 100, { estado: f.estado });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const cargas = pagina.content;

    const counts = { Pendiente: 0, Asignada: 0, "En Tránsito": 0, Entregada: 0, Cancelada: 0 };
    cargas.forEach((c) => {
      if (counts[c.estado] !== undefined) counts[c.estado] += 1;
    });

    statsRow.innerHTML = [
      statCard("bi-box-seam", pagina.totalElements.toLocaleString("es-EC"), "Total cargas"),
      statCard("bi-exclamation-circle", counts.Pendiente, "Sin viaje asignado"),
      statCard("bi-signpost", counts["En Tránsito"], "En tránsito"),
      statCard("bi-check-circle", counts.Entregada, "Entregadas"),
      statCard("bi-x-circle", counts.Cancelada, "Canceladas"),
    ].join("");

    const headers = ["Descripción", "Cliente", "Tipo", "Peso", "Origen", "Destino", "Estado"];
    const rows = cargas.map((c) => [
      escapeHtml(c.descripcion),
      escapeHtml(c.clienteNombre),
      escapeHtml(c.tipo),
      `${Number(c.peso).toLocaleString("es-EC")} kg`,
      escapeHtml(c.origen),
      escapeHtml(c.destino),
      escapeHtml(c.estado),
    ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-cargas");
    updateScopeNote(pagina.totalElements, cargas.length);
    updatePrintHeader(f.estado ? `Estado: ${f.estado}` : "");
  }

  // --- Reporte: Viajes ---
  async function renderViajesReport({ rebuildFilters = false } = {}) {
    const f = filtros.viajes;
    if (rebuildFilters) {
      filtersContainer.innerHTML = `
        <select id="reportFiltroEstado" class="select-pill">
          ${selectOption("", "Todos los estados", f.estado)}
          ${["Programado", "En Curso", "Finalizado", "Cancelado"].map((e) => selectOption(e, e, f.estado)).join("")}
        </select>
        ${dateRangeInputsHtml(f.desde, f.hasta)}`;
      document.getElementById("reportFiltroEstado").addEventListener("change", (event) => {
        f.estado = event.target.value;
        renderViajesReport();
      });
      bindDateRangeEvents(f, (rebuild) => renderViajesReport({ rebuildFilters: rebuild }));
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("viajes", 0, 100, { estado: f.estado, desde: f.desde, hasta: f.hasta });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const viajes = pagina.content;

    const counts = { Programado: 0, "En Curso": 0, Finalizado: 0, Cancelado: 0 };
    let kmTotales = 0;
    viajes.forEach((v) => {
      if (counts[v.estado] !== undefined) counts[v.estado] += 1;
      if (v.ruta) kmTotales += v.ruta.distanciaKm;
    });

    statsRow.innerHTML = [
      statCard("bi-signpost-split", pagina.totalElements.toLocaleString("es-EC"), "Total viajes"),
      statCard("bi-hourglass-split", counts.Programado, "Programados"),
      statCard("bi-arrow-repeat", counts["En Curso"], "En curso"),
      statCard("bi-flag", counts.Finalizado, "Finalizados"),
      statCard("bi-map", `${kmTotales.toFixed(1)} km`, "Distancia total de rutas"),
    ].join("");

    const headers = ["Origen", "Destino", "Vehículo", "Conductor", "Cliente", "Estado", "Distancia", "Salida", "Entrega confirmada", "Validada por supervisor"];
    const rows = viajes.map((v) => [
      escapeHtml(v.origen),
      escapeHtml(v.destino),
      escapeHtml(v.vehiculoPlaca),
      escapeHtml(v.conductorNombres),
      escapeHtml(v.clienteNombre),
      escapeHtml(v.estado),
      v.ruta ? `${v.ruta.distanciaKm.toFixed(1)} km` : "—",
      v.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "—",
      v.entregaConfirmada
        ? `${trailersysFormatDateTime(v.fechaEntregaConfirmada)} (${escapeHtml(v.confirmadoPor || "—")})`
        : "—",
      v.entregaValidada
        ? `${trailersysFormatDateTime(v.fechaValidacionEntrega)} (${escapeHtml(v.validadoPor || "—")})`
        : "—",
    ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-viajes");
    updateScopeNote(pagina.totalElements, viajes.length);
    updatePrintHeader([descripcionRangoFecha(f.desde, f.hasta), f.estado ? `Estado: ${f.estado}` : ""].filter(Boolean).join(" · "));
  }

  // --- Reporte: Mantenimientos ---
  async function renderMantenimientosReport({ rebuildFilters = false } = {}) {
    const f = filtros.mantenimientos;
    if (rebuildFilters) {
      let vehiculos;
      try {
        vehiculos = (await trailersysPagedRequest("vehiculos", 0, 100)).content;
      } catch {
        vehiculos = [];
      }
      filtersContainer.innerHTML = `
        <select id="reportFiltroVehiculo" class="select-pill">
          ${selectOption("", "Todos los vehículos", f.vehiculoId)}
          ${vehiculos.map((v) => selectOption(v.id, `${v.placa} · ${v.marca} ${v.modelo}`, f.vehiculoId)).join("")}
        </select>
        <select id="reportFiltroTipo" class="select-pill">
          ${selectOption("", "Todos los tipos", f.tipo)}
          ${selectOption("Preventivo", "Preventivo", f.tipo)}
          ${selectOption("Correctivo", "Correctivo", f.tipo)}
        </select>
        ${dateRangeInputsHtml(f.desde, f.hasta)}`;
      document.getElementById("reportFiltroVehiculo").addEventListener("change", (event) => {
        f.vehiculoId = event.target.value;
        renderMantenimientosReport();
      });
      document.getElementById("reportFiltroTipo").addEventListener("change", (event) => {
        f.tipo = event.target.value;
        renderMantenimientosReport();
      });
      bindDateRangeEvents(f, (rebuild) => renderMantenimientosReport({ rebuildFilters: rebuild }));
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("mantenimientos", 0, 100, {
        vehiculoId: f.vehiculoId, tipo: f.tipo, desde: f.desde, hasta: f.hasta,
      });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const mantenimientos = pagina.content;

    const preventivos = mantenimientos.filter((m) => m.tipo === "Preventivo").length;
    const correctivos = mantenimientos.filter((m) => m.tipo === "Correctivo").length;
    const vencidos = mantenimientos.filter((m) => m.proximoServicioVencido).length;
    const costoTotal = mantenimientos.reduce((sum, m) => sum + Number(m.costo || 0), 0);

    statsRow.innerHTML = [
      statCard("bi-tools", pagina.totalElements.toLocaleString("es-EC"), "Total registros"),
      statCard("bi-cash-coin", formatCosto(costoTotal), "Costo total"),
      statCard("bi-arrow-repeat", preventivos, "Preventivos"),
      statCard("bi-exclamation-triangle", correctivos, "Correctivos"),
      statCard("bi-calendar-x", vencidos, "Próximos servicios vencidos"),
    ].join("");

    const headers = ["Vehículo", "Tipo", "Fecha", "Kilometraje", "Costo", "Próximo servicio", "Descripción"];
    const rows = mantenimientos
      .slice()
      .sort((a, b) => (a.fecha < b.fecha ? 1 : -1))
      .map((m) => [
        escapeHtml(m.vehiculoPlaca),
        escapeHtml(m.tipo),
        escapeHtml(m.fecha),
        `${Number(m.kilometraje).toLocaleString("es-EC")} km`,
        formatCosto(m.costo),
        m.proximoServicio ? escapeHtml(m.proximoServicio) : "—",
        escapeHtml(m.descripcion),
      ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-mantenimientos");
    updateScopeNote(pagina.totalElements, mantenimientos.length);
    updatePrintHeader([descripcionRangoFecha(f.desde, f.hasta), f.tipo ? `Tipo: ${f.tipo}` : ""].filter(Boolean).join(" · "));
  }

  // --- Reporte: Clientes ---
  async function renderClientesReport({ rebuildFilters = false } = {}) {
    const f = filtros.clientes;
    if (rebuildFilters) {
      filtersContainer.innerHTML = `
        <select id="reportFiltroEstado" class="select-pill">
          ${selectOption("", "Todos los estados", f.estado)}
          ${["Activo", "Inactivo"].map((e) => selectOption(e, e, f.estado)).join("")}
        </select>`;
      document.getElementById("reportFiltroEstado").addEventListener("change", (event) => {
        f.estado = event.target.value;
        renderClientesReport();
      });
    }

    let pagina;
    try {
      pagina = await trailersysPagedRequest("clientes", 0, 100, { estado: f.estado });
    } catch (error) {
      mostrarErrorPermiso(error);
      return;
    }
    const clientes = pagina.content;

    const activos = clientes.filter((c) => c.estado === "Activo").length;
    const inactivos = clientes.filter((c) => c.estado === "Inactivo").length;
    const conCorreo = clientes.filter((c) => c.correo).length;

    statsRow.innerHTML = [
      statCard("bi-building", pagina.totalElements.toLocaleString("es-EC"), "Total clientes"),
      statCard("bi-check-circle", activos, "Activos"),
      statCard("bi-x-circle", inactivos, "Inactivos"),
      statCard("bi-envelope", conCorreo, "Con correo registrado"),
    ].join("");

    const headers = ["Nombre", "Identificación", "Estado", "Teléfono", "Correo", "Dirección", "Servicios"];
    const rows = clientes.map((c) => [
      escapeHtml(c.nombre),
      escapeHtml(c.identificacion),
      escapeHtml(c.estado),
      escapeHtml(c.telefono),
      c.correo ? escapeHtml(c.correo) : "—",
      c.direccion ? escapeHtml(c.direccion) : "—",
      c.servicios ? escapeHtml(c.servicios) : "—",
    ]);

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-clientes");
    updateScopeNote(pagina.totalElements, clientes.length);
    updatePrintHeader(f.estado ? `Estado: ${f.estado}` : "");
  }

  const RENDERERS = {
    vehiculos: renderVehiculosReport,
    conductores: renderConductoresReport,
    cargas: renderCargasReport,
    viajes: renderViajesReport,
    mantenimientos: renderMantenimientosReport,
    clientes: renderClientesReport,
  };

  async function switchTab(tab) {
    currentTab = tab;
    document.querySelectorAll(".report-tab").forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === tab));
    await RENDERERS[tab]({ rebuildFilters: true });
  }

  tabsContainer.addEventListener("click", (event) => {
    const button = event.target.closest(".report-tab");
    if (!button) return;
    switchTab(button.dataset.tab);
  });

  // --- Exportar CSV ---
  // Separador ";" en vez de ",": con la configuracion regional en español
  // (Ecuador y la mayoria de Latinoamerica usan la coma como separador
  // decimal), Excel espera punto y coma entre columnas. Si el archivo trae
  // comas, Excel no reconoce columnas y mete cada fila entera en una sola
  // celda: eso es lo que se veia "muy desorganizado" al abrirlo.
  const CSV_SEP = ";";

  function csvEscape(value) {
    let str = String(value ?? "").replace(/<[^>]*>/g, "");
    // Neutraliza inyeccion de formulas: Excel/Sheets interpretan como
    // formula cualquier celda que empiece con =, +, - o @ al abrir el CSV.
    if (/^[=+\-@]/.test(str)) str = `'${str}`;
    if (str.includes('"') || str.includes(CSV_SEP) || str.includes("\n")) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  }

  btnExportarCsv.addEventListener("click", () => {
    const { headers, rows, filenamePrefix } = currentExport;
    if (!rows.length) return;

    const lines = [headers, ...rows].map((row) => row.map(csvEscape).join(CSV_SEP)).join("\r\n");
    const bom = String.fromCharCode(0xfeff);
    // La linea "sep=;" le dice a Excel explicitamente que separador usar,
    // sin depender de adivinar la configuracion regional de quien lo abre.
    const blob = new Blob([`${bom}sep=${CSV_SEP}\r\n${lines}`], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${filenamePrefix}-${todayIso()}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  });

  // --- Imprimir / PDF ---
  btnImprimir.addEventListener("click", () => window.print());

  window.addEventListener("trailersys:data-changed", (event) => {
    if (event.detail?.resource === "mantenimientos" && currentTab === "mantenimientos") {
      renderMantenimientosReport();
    }
  });
  window.addEventListener("trailersys:module-activated", (event) => {
    if (event.detail?.module === "reportes") RENDERERS[currentTab]();
  });

  const session = trailersysGetSession();
  const tabsPermitidas = Object.keys(TAB_LABELS).filter((tab) => TAB_ROLES[tab].includes(session?.role));
  document.querySelectorAll(".report-tab").forEach((btn) => {
    if (!tabsPermitidas.includes(btn.dataset.tab)) btn.hidden = true;
  });

  // Este script se carga siempre (junto con el resto de modulos) aunque el
  // rol actual no tenga "reportes" en sus modulos (roles.js) y nunca vaya a
  // ver esta pantalla - en ese caso tabsPermitidas queda vacio y no hay
  // nada que inicializar aqui.
  if (tabsPermitidas.length > 0) {
    switchTab(tabsPermitidas.includes("vehiculos") ? "vehiculos" : tabsPermitidas[0]);
  }
})();
