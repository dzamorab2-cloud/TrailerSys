(function () {
  const tabsContainer = document.getElementById("reportTabs");
  const filtersContainer = document.getElementById("reportFilters");
  const statsRow = document.getElementById("reportStats");
  const tableHead = document.getElementById("reportTableHead");
  const tableBody = document.getElementById("reportTableBody");
  const tableWrap = document.getElementById("reportTableWrap");
  const emptyState = document.getElementById("reportEmptyState");
  const printHeader = document.getElementById("reportPrintHeader");
  const btnExportarCsv = document.getElementById("btnExportarCsv");
  const btnImprimir = document.getElementById("btnImprimirReporte");

  const TAB_LABELS = {
    vehiculos: "Vehículos",
    conductores: "Conductores",
    viajes: "Viajes",
    mantenimientos: "Mantenimientos",
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

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
  }

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

  // --- Tabla generica ---
  function renderTable(headers, rows) {
    tableHead.innerHTML = `<tr>${headers.map((h) => `<th>${h}</th>`).join("")}</tr>`;

    if (rows.length === 0) {
      tableWrap.hidden = true;
      emptyState.hidden = false;
      tableBody.innerHTML = "";
      return;
    }

    tableWrap.hidden = false;
    emptyState.hidden = true;
    tableBody.innerHTML = rows
      .map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join("")}</tr>`)
      .join("");
  }

  function setExportData(headers, rows, filenamePrefix) {
    currentExport = { headers, rows, filenamePrefix };
    btnExportarCsv.disabled = rows.length === 0;
  }

  function updatePrintHeader() {
    printHeader.innerHTML = `
      <div class="print-title">Reporte de ${TAB_LABELS[currentTab]}</div>
      <div class="print-meta">TrailerSys · Generado el ${trailersysFormatDateTime(new Date())}</div>`;
  }

  // --- Reporte: Vehiculos ---
  function renderVehiculosReport() {
    filtersContainer.innerHTML = `
      <select id="reportFiltroEstado" class="select-pill">
        ${selectOption("", "Todos los estados", "")}
        ${["Disponible", "En Ruta", "Mantenimiento", "Fuera de Servicio"].map((e) => selectOption(e, e, "")).join("")}
      </select>`;
    document.getElementById("reportFiltroEstado").addEventListener("change", renderVehiculosReport);
    const estado = document.getElementById("reportFiltroEstado").value;

    const vehiculos = trailersysList("vehiculos").filter((v) => !estado || v.estado === estado);

    const counts = { Disponible: 0, "En Ruta": 0, Mantenimiento: 0, "Fuera de Servicio": 0 };
    vehiculos.forEach((v) => {
      if (counts[v.estado] !== undefined) counts[v.estado] += 1;
    });

    statsRow.innerHTML = [
      statCard("bi-truck", vehiculos.length, "Total vehículos"),
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
    updatePrintHeader();
  }

  // --- Reporte: Conductores ---
  function renderConductoresReport() {
    filtersContainer.innerHTML = `
      <select id="reportFiltroEstado" class="select-pill">
        ${selectOption("", "Todos los estados", "")}
        ${["Disponible", "En Ruta", "Descanso", "Inactivo"].map((e) => selectOption(e, e, "")).join("")}
      </select>`;
    document.getElementById("reportFiltroEstado").addEventListener("change", renderConductoresReport);
    const estado = document.getElementById("reportFiltroEstado").value;

    const conductores = trailersysList("conductores").filter((c) => !estado || c.estado === estado);
    const vehiculos = trailersysList("vehiculos");
    const hoy = todayIso();
    const en30Dias = new Date();
    en30Dias.setDate(en30Dias.getDate() + 30);
    const en30DiasIso = en30Dias.toISOString().slice(0, 10);

    const activos = conductores.filter((c) => c.estado === "Disponible" || c.estado === "En Ruta").length;
    const vencidas = conductores.filter((c) => c.licenciaVencimiento && c.licenciaVencimiento < hoy).length;
    const porVencer = conductores.filter(
      (c) => c.licenciaVencimiento && c.licenciaVencimiento >= hoy && c.licenciaVencimiento <= en30DiasIso
    ).length;

    statsRow.innerHTML = [
      statCard("bi-person-badge", conductores.length, "Total conductores"),
      statCard("bi-check-circle", activos, "Activos (disponible / en ruta)"),
      statCard("bi-exclamation-triangle", vencidas, "Licencias vencidas"),
      statCard("bi-alarm", porVencer, "Licencias por vencer (30 días)"),
    ].join("");

    const headers = ["Nombres", "Identificación", "Teléfono", "Licencia", "Categoría", "Vencimiento", "Estado", "Vehículo asignado"];
    const rows = conductores.map((c) => {
      const vehiculo = vehiculos.find((v) => v.id === c.vehiculoId);
      return [
        escapeHtml(c.nombres),
        escapeHtml(c.identificacion),
        escapeHtml(c.telefono),
        escapeHtml(c.licenciaNumero),
        escapeHtml(c.licenciaCategoria),
        escapeHtml(c.licenciaVencimiento),
        escapeHtml(c.estado),
        vehiculo ? escapeHtml(vehiculo.placa) : "—",
      ];
    });

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-conductores");
    updatePrintHeader();
  }

  // --- Reporte: Viajes ---
  function renderViajesReport() {
    filtersContainer.innerHTML = `
      <select id="reportFiltroEstado" class="select-pill">
        ${selectOption("", "Todos los estados", "")}
        ${["Programado", "En Curso", "Finalizado", "Cancelado"].map((e) => selectOption(e, e, "")).join("")}
      </select>`;
    document.getElementById("reportFiltroEstado").addEventListener("change", renderViajesReport);
    const estado = document.getElementById("reportFiltroEstado").value;

    const viajes = trailersysList("viajes").filter((v) => !estado || v.estado === estado);
    const vehiculos = trailersysList("vehiculos");
    const conductores = trailersysList("conductores");
    const clientes = trailersysList("clientes");

    const counts = { Programado: 0, "En Curso": 0, Finalizado: 0, Cancelado: 0 };
    let kmTotales = 0;
    viajes.forEach((v) => {
      if (counts[v.estado] !== undefined) counts[v.estado] += 1;
      if (v.ruta) kmTotales += v.ruta.distanciaKm;
    });

    statsRow.innerHTML = [
      statCard("bi-signpost-split", viajes.length, "Total viajes"),
      statCard("bi-hourglass-split", counts.Programado, "Programados"),
      statCard("bi-arrow-repeat", counts["En Curso"], "En curso"),
      statCard("bi-flag", counts.Finalizado, "Finalizados"),
      statCard("bi-map", `${kmTotales.toFixed(1)} km`, "Distancia total de rutas"),
    ].join("");

    const headers = ["Origen", "Destino", "Vehículo", "Conductor", "Cliente", "Estado", "Distancia", "Salida"];
    const rows = viajes.map((v) => {
      const vehiculo = vehiculos.find((x) => x.id === v.vehiculoId);
      const conductor = conductores.find((x) => x.id === v.conductorId);
      const cliente = clientes.find((x) => x.id === v.clienteId);
      return [
        escapeHtml(v.origen),
        escapeHtml(v.destino),
        vehiculo ? escapeHtml(vehiculo.placa) : "—",
        conductor ? escapeHtml(conductor.nombres) : "—",
        cliente ? escapeHtml(cliente.nombre) : "—",
        escapeHtml(v.estado),
        v.ruta ? `${v.ruta.distanciaKm.toFixed(1)} km` : "—",
        v.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "—",
      ];
    });

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-viajes");
    updatePrintHeader();
  }

  // --- Reporte: Mantenimientos ---
  function renderMantenimientosReport() {
    const vehiculos = trailersysList("vehiculos");
    filtersContainer.innerHTML = `
      <select id="reportFiltroVehiculo" class="select-pill">
        ${selectOption("", "Todos los vehículos", "")}
        ${vehiculos.map((v) => selectOption(v.id, `${v.placa} · ${v.marca} ${v.modelo}`, "")).join("")}
      </select>
      <select id="reportFiltroTipo" class="select-pill">
        ${selectOption("", "Todos los tipos", "")}
        ${selectOption("Preventivo", "Preventivo", "")}
        ${selectOption("Correctivo", "Correctivo", "")}
      </select>`;
    document.getElementById("reportFiltroVehiculo").addEventListener("change", renderMantenimientosReport);
    document.getElementById("reportFiltroTipo").addEventListener("change", renderMantenimientosReport);
    const vehiculoId = document.getElementById("reportFiltroVehiculo").value;
    const tipo = document.getElementById("reportFiltroTipo").value;

    const mantenimientos = trailersysList("mantenimientos")
      .filter((m) => !vehiculoId || m.vehiculoId === vehiculoId)
      .filter((m) => !tipo || m.tipo === tipo)
      .sort((a, b) => (a.fecha < b.fecha ? 1 : -1));

    const hoy = todayIso();
    const preventivos = mantenimientos.filter((m) => m.tipo === "Preventivo").length;
    const correctivos = mantenimientos.filter((m) => m.tipo === "Correctivo").length;
    const vencidos = mantenimientos.filter((m) => m.proximoServicio && m.proximoServicio < hoy).length;
    const costoTotal = mantenimientos.reduce((sum, m) => sum + Number(m.costo || 0), 0);

    statsRow.innerHTML = [
      statCard("bi-tools", mantenimientos.length, "Total registros"),
      statCard("bi-cash-coin", formatCosto(costoTotal), "Costo total"),
      statCard("bi-arrow-repeat", preventivos, "Preventivos"),
      statCard("bi-exclamation-triangle", correctivos, "Correctivos"),
      statCard("bi-calendar-x", vencidos, "Próximos servicios vencidos"),
    ].join("");

    const headers = ["Vehículo", "Tipo", "Fecha", "Kilometraje", "Costo", "Próximo servicio", "Descripción"];
    const rows = mantenimientos.map((m) => {
      const vehiculo = vehiculos.find((v) => v.id === m.vehiculoId);
      return [
        vehiculo ? escapeHtml(vehiculo.placa) : "—",
        escapeHtml(m.tipo),
        escapeHtml(m.fecha),
        `${Number(m.kilometraje).toLocaleString("es-EC")} km`,
        formatCosto(m.costo),
        m.proximoServicio ? escapeHtml(m.proximoServicio) : "—",
        escapeHtml(m.descripcion),
      ];
    });

    renderTable(headers, rows);
    setExportData(headers, rows, "reporte-mantenimientos");
    updatePrintHeader();
  }

  const RENDERERS = {
    vehiculos: renderVehiculosReport,
    conductores: renderConductoresReport,
    viajes: renderViajesReport,
    mantenimientos: renderMantenimientosReport,
  };

  function switchTab(tab) {
    currentTab = tab;
    document.querySelectorAll(".report-tab").forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === tab));
    RENDERERS[tab]();
  }

  tabsContainer.addEventListener("click", (event) => {
    const button = event.target.closest(".report-tab");
    if (!button) return;
    switchTab(button.dataset.tab);
  });

  // --- Exportar CSV ---
  function csvEscape(value) {
    const str = String(value ?? "").replace(/<[^>]*>/g, "");
    if (/[",\n]/.test(str)) return `"${str.replace(/"/g, '""')}"`;
    return str;
  }

  btnExportarCsv.addEventListener("click", () => {
    const { headers, rows, filenamePrefix } = currentExport;
    if (!rows.length) return;

    const lines = [headers, ...rows].map((row) => row.map(csvEscape).join(",")).join("\r\n");
    const bom = String.fromCharCode(0xfeff);
    const blob = new Blob([bom + lines], { type: "text/csv;charset=utf-8;" });
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

  switchTab("vehiculos");
})();
