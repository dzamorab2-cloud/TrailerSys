/**
 * Dashboard (Fase 11): indicadores reales a partir de las mismas APIs que
 * usan los demas modulos. No hay endpoint propio; cada tarjeta se arma con
 * datos que el rol activo ya tiene permiso de consultar (TRAILERSYS_ROLES),
 * asi que si el backend devuelve 403 para una coleccion esa tarjeta
 * simplemente se omite en vez de romper el resto del panel.
 */
(function () {
  const statsRow = document.getElementById("dashboardStats");
  const alertasPanel = document.getElementById("dashboardAlertasPanel");
  const alertasContainer = document.getElementById("dashboardAlertas");
  const viajesPanel = document.getElementById("dashboardViajesPanel");
  const viajesContainer = document.getElementById("dashboardViajes");

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
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

  async function safeFetch(path) {
    try {
      return await trailersysApiRequest("GET", path);
    } catch {
      return null;
    }
  }

  async function render() {
    const session = trailersysGetSession();
    const roleInfo = TRAILERSYS_ROLES[session?.role];
    const modules = roleInfo ? roleInfo.modules : [];

    const cards = [];
    let viajes = null;

    if (modules.includes("vehiculos")) {
      const vehiculos = await safeFetch("/vehiculos");
      if (vehiculos) {
        const disponibles = vehiculos.filter((v) => v.estado === "Disponible").length;
        cards.push(statCard("bi-truck", vehiculos.length, "Vehículos en la flota"));
        cards.push(statCard("bi-check-circle", disponibles, "Vehículos disponibles"));
      }
    }

    if (modules.includes("conductores")) {
      const conductores = await safeFetch("/conductores");
      if (conductores) {
        const activos = conductores.filter((c) => c.estado === "Disponible" || c.estado === "En Ruta").length;
        cards.push(statCard("bi-person-badge", conductores.length, "Conductores registrados"));
        cards.push(statCard("bi-person-check", activos, "Conductores activos"));
      }
    }

    if (modules.includes("viajes")) {
      viajes = await safeFetch("/viajes");
      if (viajes) {
        const enCurso = viajes.filter((v) => v.estado === "En Curso").length;
        cards.push(statCard("bi-signpost-split", enCurso, "Viajes en curso"));
      }
    }

    if (modules.includes("mantenimientos")) {
      const mantenimientos = await safeFetch("/mantenimientos");
      if (mantenimientos) {
        const vencidos = mantenimientos.filter((m) => m.proximoServicioVencido).length;
        cards.push(statCard("bi-tools", vencidos, "Servicios vencidos"));
      }
    }

    statsRow.innerHTML = cards.length
      ? cards.join("")
      : `<div class="dashboard-empty">No hay indicadores disponibles para tu rol.</div>`;

    // --- Alertas operativas ---
    if (modules.includes("seguimiento")) {
      alertasPanel.hidden = false;
      const alertas = await safeFetch("/seguimiento/alertas");
      if (!alertas) {
        alertasContainer.innerHTML = `<div class="dashboard-empty">No se pudieron cargar las alertas.</div>`;
      } else if (!alertas.length) {
        alertasContainer.innerHTML = `<div class="alerts-empty"><i class="bi bi-check-circle"></i>No hay alertas activas. Todo está en orden.</div>`;
      } else {
        alertasContainer.innerHTML = alertas
          .slice(0, 5)
          .map((a) => `<div class="alert-item level-${a.nivel}"><i class="bi ${a.icono}"></i><div class="alert-text">${escapeHtml(a.texto)}</div></div>`)
          .join("");
      }
    } else {
      alertasPanel.hidden = true;
    }

    // --- Proximos viajes ---
    if (modules.includes("viajes")) {
      viajesPanel.hidden = false;
      if (!viajes) {
        viajesContainer.innerHTML = `<div class="dashboard-empty">No se pudieron cargar los viajes.</div>`;
      } else {
        const proximos = viajes
          .filter((v) => v.estado === "Programado")
          .sort((a, b) => (a.fechaSalida < b.fechaSalida ? -1 : 1))
          .slice(0, 5);

        viajesContainer.innerHTML = proximos.length
          ? proximos
              .map(
                (v) => `
            <div class="dashboard-trip">
              <div>
                <div class="dashboard-trip-route">${escapeHtml(v.origen)} → ${escapeHtml(v.destino)}</div>
                <div class="dashboard-trip-meta">${escapeHtml(v.vehiculoPlaca)} · ${escapeHtml(v.conductorNombres)}</div>
              </div>
              <div class="dashboard-trip-when">${v.fechaSalida ? trailersysFormatDateTime(v.fechaSalida) : "—"}</div>
            </div>`
              )
              .join("")
          : `<div class="dashboard-empty">No hay viajes programados.</div>`;
      }
    } else {
      viajesPanel.hidden = true;
    }
  }

  render();
})();
