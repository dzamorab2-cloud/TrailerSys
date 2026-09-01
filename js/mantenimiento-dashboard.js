(function () {
  const session = trailersysGetSession();
  if (session?.role !== "mantenimiento") return;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  // Se muestra la vista personalizada y se oculta la generica (la de
  // Administrador/Coordinador) - ambas viven bajo el mismo #module-dashboard,
  // ver app.html.
  $("dashboardGenericView").hidden = true;
  $("dashboardMantenimientoView").hidden = false;

  function initials(name) {
    return String(name || "").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  }

  // --- Perfil (cuenta propia, GET /api/auth/me - Mantenimiento no tiene un
  // registro propio como Conductor/Cliente, solo su Usuario) ---
  function renderPerfil(me) {
    $("mantenimientoDashboardSaludo").textContent = `Hola, ${me.nombre.split(" ")[0]}`;
    $("mantenimientoPerfilNombre").textContent = me.nombre;
    $("mantenimientoPerfilUsername").textContent = `@${me.username}`;
    $("mantenimientoPerfilCorreo").textContent = me.correo || "Sin correo registrado";

    const fotoPreview = $("mantenimientoPerfilFotoPreview");
    fotoPreview.innerHTML = me.foto
      ? `<img src="${esc(me.foto)}" alt="Foto de ${esc(me.nombre)}" />`
      : `<span class="photo-preview-initials">${esc(initials(me.nombre))}</span>`;
  }

  $("mantenimientoPerfilFoto").addEventListener("change", async () => {
    const input = $("mantenimientoPerfilFoto");
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
      $("mantenimientoPerfilFotoPreview").innerHTML = `<img src="${esc(actualizado.foto)}" alt="Foto de perfil" />`;
    } catch (error) {
      alert(error.message || "No se pudo actualizar la foto.");
    } finally {
      input.value = "";
    }
  });

  // --- Donut CSS puro (sin libreria), mismos datos que ya trae la pestaña
  // "Reportes y estadísticas" del propio modulo Mantenimientos. ---
  function renderDonut(datos, colores) {
    const total = datos.reduce((sum, [, cantidad]) => sum + cantidad, 0);
    const donut = $("mantenimientoTipoDonut");
    const legend = $("mantenimientoTipoLegend");

    if (total === 0) {
      donut.style.background = "var(--color-border, #e5e5e5)";
      legend.innerHTML = '<p class="dashboard-empty">Todavía no hay mantenimientos registrados.</p>';
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

  const COLOR_TIPO = { Preventivo: "var(--color-success)", Correctivo: "var(--color-warning)" };

  // --- Top vehiculos por costo de mantenimiento ---
  function renderCostos(lista) {
    const contenedor = $("mantenimientoCostos");
    contenedor.innerHTML = lista.length
      ? lista.slice(0, 5).map((v) => `
        <div class="dashboard-trip">
          <div>
            <div class="dashboard-trip-route">${esc(v.placa)}</div>
            <div class="dashboard-trip-meta">${v.cantidad} servicio${v.cantidad === 1 ? "" : "s"}</div>
          </div>
          <div class="dashboard-trip-when">$${Number(v.costo).toLocaleString("es-EC", { maximumFractionDigits: 2 })}</div>
        </div>`).join("")
      : '<div class="dashboard-empty">Todavía no hay mantenimientos registrados.</div>';
  }

  async function cargar() {
    try {
      const [me, reporte, disponibilidad] = await Promise.all([
        trailersysApiRequest("GET", "/auth/me"),
        trailersysApiRequest("GET", "/mantenimientos/reportes"),
        trailersysApiRequest("GET", "/dashboard/disponibilidad"),
      ]);

      renderPerfil(me);
      $("mantenimientoPerfilVencidos").textContent = `${Number(reporte.vencidos).toLocaleString("es-EC")} servicios vencidos`;
      $("mantenimientoPerfilCosto").textContent = `$${Number(reporte.costoTotal).toLocaleString("es-EC", { maximumFractionDigits: 2 })} en total`;

      renderDonut([
        ["Preventivo", reporte.preventivos],
        ["Correctivo", reporte.correctivos],
      ], COLOR_TIPO);

      trailersysRenderMultiRing($("mantenimientoFlotaRing"), [
        ["Disponible", disponibilidad.vehiculosDisponibles, "var(--color-success)"],
        ["En Ruta", disponibilidad.vehiculosEnRuta, "var(--color-info)"],
        ["Mantenimiento", disponibilidad.vehiculosMantenimiento, "var(--color-warning)"],
        ["Fuera de Servicio", disponibilidad.vehiculosFueraServicio, "var(--color-danger)"],
      ]);

      trailersysRenderProgressRing($("mantenimientoAlDiaRing"), {
        valor: reporte.total > 0 ? ((reporte.total - reporte.vencidos) / reporte.total) * 100 : 0,
        etiqueta: "Del total de servicios",
        color: "var(--color-success)",
      });

      renderCostos(reporte.costosPorVehiculo);

      const tendencia = await trailersysApiRequest("GET", "/mantenimientos/reportes/tendencia");
      trailersysRenderAreaChart($("mantenimientoTendenciaChart"),
        tendencia.mantenimientosPorMes.map((p) => ({ label: p.etiqueta, value: p.cantidad })),
        { color: "var(--color-primary)" });
    } catch (error) {
      $("mantenimientoPerfilNombre").textContent = error.message || "No se pudo cargar tu perfil.";
    }
  }

  trailersysRenderEcuadorMap("mantenimientoEcuadorMap");
  cargar();
  window.addEventListener("trailersys:module-activated", (e) => { if (e.detail?.module === "dashboard") { cargar(); trailersysRenderEcuadorMap("mantenimientoEcuadorMap"); } });
  window.addEventListener("trailersys:data-changed", (e) => { if (e.detail?.resource === "mantenimientos") cargar(); });
})();
