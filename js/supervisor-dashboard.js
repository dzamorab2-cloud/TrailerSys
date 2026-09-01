(function () {
  const session = trailersysGetSession();
  if (session?.role !== "supervisor") return;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  // Se muestra la vista personalizada y se oculta la generica (la de
  // Administrador/Coordinador/Mantenimiento) - ambas viven bajo el mismo
  // #module-dashboard, ver app.html.
  $("dashboardGenericView").hidden = true;
  $("dashboardSupervisorView").hidden = false;

  function initials(name) {
    return String(name || "").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  }

  // --- Perfil (cuenta propia, GET /api/auth/me - el Supervisor no tiene
  // un registro propio como Conductor/Cliente, solo su Usuario) ---
  function renderPerfil(me) {
    $("supervisorDashboardSaludo").textContent = `Hola, ${me.nombre.split(" ")[0]}`;
    $("supervisorPerfilNombre").textContent = me.nombre;
    $("supervisorPerfilUsername").textContent = `@${me.username}`;
    $("supervisorPerfilCorreo").textContent = me.correo || "Sin correo registrado";

    const fotoPreview = $("supervisorPerfilFotoPreview");
    fotoPreview.innerHTML = me.foto
      ? `<img src="${esc(me.foto)}" alt="Foto de ${esc(me.nombre)}" />`
      : `<span class="photo-preview-initials">${esc(initials(me.nombre))}</span>`;
  }

  $("supervisorPerfilFoto").addEventListener("change", async () => {
    const input = $("supervisorPerfilFoto");
    const file = input.files[0];
    if (!file) return;
    if (file.size > MAX_PHOTO_BYTES) {
      alert("La imagen es muy grande. El tamaño máximo permitido es 3 MB.");
      input.value = "";
      return;
    }
    const dataUrl = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
    try {
      const actualizado = await trailersysApiRequest("PUT", "/auth/me/foto", { foto: dataUrl });
      $("supervisorPerfilFotoPreview").innerHTML = `<img src="${esc(actualizado.foto)}" alt="Foto de perfil" />`;
    } catch (error) {
      alert(error.message || "No se pudo actualizar la foto.");
    } finally {
      input.value = "";
    }
  });

  // --- Graficas CSS (sin libreria), con los mismos datos que ya trae el
  // Panel de disponibilidad de Administrador/Coordinador (GET
  // /dashboard/disponibilidad, ahora tambien permitido para Supervisor). ---
  function renderDonut(donutId, legendId, datos, colores) {
    const total = datos.reduce((sum, [, cantidad]) => sum + cantidad, 0);
    const donut = $(donutId);
    const legend = $(legendId);

    if (total === 0) {
      donut.style.background = "var(--color-border, #e5e5e5)";
      legend.innerHTML = '<p class="dashboard-empty">Sin datos todavía.</p>';
      return;
    }

    let acumulado = 0;
    const stops = datos
      .filter(([, cantidad]) => cantidad > 0)
      .map(([estado, cantidad]) => {
        const inicio = (acumulado / total) * 360;
        acumulado += cantidad;
        const fin = (acumulado / total) * 360;
        return `${colores[estado]} ${inicio}deg ${fin}deg`;
      });
    donut.style.background = `conic-gradient(${stops.join(", ")})`;
    legend.innerHTML = datos.map(([estado, cantidad]) => `
      <div class="dashboard-donut-legend-item">
        <span class="dashboard-donut-dot" style="background:${colores[estado]}"></span>
        <span>${esc(estado)}</span>
        <strong>${cantidad}</strong>
      </div>`).join("");
  }

  const COLOR_VEHICULOS = {
    Disponible: "var(--color-success)",
    "En Ruta": "var(--color-info)",
    Mantenimiento: "var(--color-warning)",
    "Fuera de Servicio": "var(--color-danger)",
  };
  const COLOR_CONDUCTORES = {
    Disponible: "var(--color-success)",
    "En Ruta": "var(--color-info)",
    Descanso: "var(--color-warning)",
    Inactivo: "var(--color-danger)",
  };

  async function cargar() {
    try {
      const [me, resumen, disponibilidad] = await Promise.all([
        trailersysApiRequest("GET", "/auth/me"),
        trailersysApiRequest("GET", "/dashboard/resumen"),
        trailersysApiRequest("GET", "/dashboard/disponibilidad"),
      ]);

      renderPerfil(me);
      $("supervisorPerfilEntregasPendientes").textContent = `${Number(resumen.entregasPendientes).toLocaleString("es-EC")} entregas por validar`;
      $("supervisorPerfilLicenciasVencidas").textContent = `${Number(disponibilidad.licenciasVencidas).toLocaleString("es-EC")} licencias vencidas`;

      renderDonut("supervisorVehiculosDonut", "supervisorVehiculosLegend", [
        ["Disponible", disponibilidad.vehiculosDisponibles],
        ["En Ruta", disponibilidad.vehiculosEnRuta],
        ["Mantenimiento", disponibilidad.vehiculosMantenimiento],
        ["Fuera de Servicio", disponibilidad.vehiculosFueraServicio],
      ], COLOR_VEHICULOS);

      renderDonut("supervisorConductoresDonut", "supervisorConductoresLegend", [
        ["Disponible", disponibilidad.conductoresDisponibles],
        ["En Ruta", disponibilidad.conductoresEnRuta],
        ["Descanso", disponibilidad.conductoresDescanso],
        ["Inactivo", disponibilidad.conductoresInactivos],
      ], COLOR_CONDUCTORES);

      const totalVehiculos = disponibilidad.vehiculosDisponibles + disponibilidad.vehiculosEnRuta
        + disponibilidad.vehiculosMantenimiento + disponibilidad.vehiculosFueraServicio;
      const totalConductores = disponibilidad.conductoresDisponibles + disponibilidad.conductoresEnRuta
        + disponibilidad.conductoresDescanso + disponibilidad.conductoresInactivos;

      trailersysRenderProgressRing($("supervisorFlotaRing"), {
        valor: totalVehiculos > 0 ? (disponibilidad.vehiculosDisponibles / totalVehiculos) * 100 : 0,
        etiqueta: "Vehículos disponibles",
        color: "var(--color-success)",
      });
      trailersysRenderProgressRing($("supervisorLicenciasRing"), {
        valor: totalConductores > 0 ? ((totalConductores - disponibilidad.licenciasVencidas) / totalConductores) * 100 : 0,
        etiqueta: "Conductores al día",
        color: "var(--color-info)",
      });

      const tendencia = await trailersysApiRequest("GET", "/dashboard/tendencia");
      trailersysRenderAreaChart($("supervisorTendenciaChart"),
        tendencia.viajesPorDia.map((p) => ({ label: p.etiqueta, value: p.cantidad })),
        { color: "var(--color-primary)" });
    } catch (error) {
      $("supervisorPerfilNombre").textContent = error.message || "No se pudo cargar tu perfil.";
    }
  }

  trailersysRenderEcuadorMap("supervisorEcuadorMap");
  cargar();
  window.addEventListener("trailersys:module-activated", (e) => { if (e.detail?.module === "dashboard") { cargar(); trailersysRenderEcuadorMap("supervisorEcuadorMap"); } });
})();
