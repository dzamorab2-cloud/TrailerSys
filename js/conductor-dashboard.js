(function () {
  const session = trailersysGetSession();
  if (session?.role !== "conductor") return;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  // Se muestra la vista personalizada y se oculta la generica (la de
  // Administrador/Coordinador/etc.) - ambas viven bajo el mismo
  // #module-dashboard, ver app.html.
  $("dashboardGenericView").hidden = true;
  const view = $("dashboardConductorView");
  view.hidden = false;

  function initials(name) {
    return String(name || "").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
  }

  // --- Perfil + vehiculo asignado ---
  function renderPerfil(p) {
    $("conductorDashboardSaludo").textContent = `Hola, ${p.nombres.split(" ")[0]}`;
    $("conductorPerfilNombre").textContent = p.nombres;
    $("conductorPerfilEstado").textContent = p.estado;
    $("conductorPerfilEstado").className = `badge ${{ Disponible: "badge-success", "En Ruta": "badge-info", Descanso: "badge-warning", Inactivo: "badge-neutral" }[p.estado] || "badge-neutral"}`;
    $("conductorPerfilTelefono").textContent = p.telefono || "—";
    $("conductorPerfilEdad").textContent = p.edad != null ? `${p.edad} años` : "Edad no registrada";
    $("conductorPerfilLicencia").textContent = `${p.licenciaNumero || "—"} · ${p.licenciaCategoria || "—"}`;
    $("conductorPerfilVencimiento").textContent = p.licenciaVencimiento
      ? `Vence ${p.licenciaVencimiento}${p.licenciaVencida ? " (vencida)" : ""}`
      : "Sin vencimiento registrado";

    const fotoPreview = $("conductorPerfilFotoPreview");
    fotoPreview.innerHTML = p.foto
      ? `<img src="${esc(p.foto)}" alt="Foto de ${esc(p.nombres)}" />`
      : `<span class="photo-preview-initials">${esc(initials(p.nombres))}</span>`;

    const vehiculoFoto = $("conductorVehiculoFotoPreview");
    if (p.vehiculoId) {
      $("conductorVehiculoNombre").textContent = `${p.vehiculoMarca} ${p.vehiculoModelo}`;
      $("conductorVehiculoEstado").hidden = false;
      $("conductorVehiculoEstado").textContent = p.vehiculoEstado;
      $("conductorVehiculoEstado").className = `badge ${{ Disponible: "badge-success", "En Ruta": "badge-info", Mantenimiento: "badge-warning", "Fuera de Servicio": "badge-danger" }[p.vehiculoEstado] || "badge-neutral"}`;
      $("conductorVehiculoPlaca").textContent = p.vehiculoPlaca || "—";
      $("conductorVehiculoAnio").textContent = p.vehiculoAnio || "—";
      $("conductorVehiculoColor").textContent = p.vehiculoColor || "—";
      $("conductorVehiculoCapacidad").textContent = p.vehiculoCapacidad != null ? `${Number(p.vehiculoCapacidad).toLocaleString("es-EC")} kg` : "—";
      vehiculoFoto.innerHTML = p.vehiculoFoto ? `<img src="${esc(p.vehiculoFoto)}" alt="Foto del vehículo" />` : `<i class="bi bi-truck"></i>`;
    } else {
      $("conductorVehiculoNombre").textContent = "Sin vehículo asignado";
      $("conductorVehiculoEstado").hidden = true;
      ["conductorVehiculoPlaca", "conductorVehiculoAnio", "conductorVehiculoColor", "conductorVehiculoCapacidad"].forEach((id) => { $(id).textContent = "—"; });
      vehiculoFoto.innerHTML = `<i class="bi bi-truck"></i>`;
    }
  }

  $("conductorPerfilFoto").addEventListener("change", async () => {
    const input = $("conductorPerfilFoto");
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
      const actualizado = await trailersysApiRequest("PUT", "/mis-viajes/perfil/foto", { foto: dataUrl });
      $("conductorPerfilFotoPreview").innerHTML = `<img src="${esc(actualizado.foto)}" alt="Foto de perfil" />`;
    } catch (error) {
      alert(error.message || "No se pudo actualizar la foto.");
    } finally {
      input.value = "";
    }
  });

  // --- Alerta de viaje asignado ---
  function renderAlerta(viaje) {
    const banner = $("conductorTripAlert");
    if (!viaje) { banner.hidden = true; return; }
    banner.hidden = false;
    const cuando = viaje.estado === "En Curso" ? "en curso ahora mismo" : `programado para ${trailersysFormatDateTime(viaje.fechaSalida)}`;
    $("conductorTripAlertTitle").textContent = viaje.estado === "En Curso" ? "Tienes un viaje en curso" : "Tienes un viaje asignado";
    $("conductorTripAlertText").textContent = `${viaje.origen} → ${viaje.destino} · ${cuando}`;
    $("conductorTripAlertBtn").onclick = () => {
      document.querySelector('.nav-link[data-module="mis-viajes"]')?.click();
      window.trailersysAbrirMiViaje?.(viaje.id);
    };
  }

  // --- Graficas CSS (sin libreria) ---
  const ESTADO_COLOR = {
    Programado: "var(--color-info)",
    "En Curso": "var(--color-warning)",
    Finalizado: "var(--color-success)",
    Cancelado: "var(--color-danger)",
  };

  function renderDonut(resumen) {
    const datos = [
      ["Programado", resumen.viajesProgramados],
      ["En Curso", resumen.viajesEnCurso],
      ["Finalizado", resumen.viajesFinalizados],
      ["Cancelado", resumen.viajesCancelados],
    ];
    const total = datos.reduce((sum, [, cantidad]) => sum + cantidad, 0);
    const donut = $("conductorDonutChart");
    const legend = $("conductorDonutLegend");

    if (total === 0) {
      donut.style.background = "var(--color-border, #e5e5e5)";
      legend.innerHTML = '<p class="dashboard-empty">Todavía no tienes viajes registrados.</p>';
      return;
    }

    let acumulado = 0;
    const stops = datos
      .filter(([, cantidad]) => cantidad > 0)
      .map(([estado, cantidad]) => {
        const inicio = (acumulado / total) * 360;
        acumulado += cantidad;
        const fin = (acumulado / total) * 360;
        return `${ESTADO_COLOR[estado]} ${inicio}deg ${fin}deg`;
      });
    donut.style.background = `conic-gradient(${stops.join(", ")})`;
    legend.innerHTML = datos.map(([estado, cantidad]) => `
      <div class="dashboard-donut-legend-item">
        <span class="dashboard-donut-dot" style="background:${ESTADO_COLOR[estado]}"></span>
        <span>${esc(estado)}</span>
        <strong>${cantidad}</strong>
      </div>`).join("");
  }

  function renderTendencia(viajesPorMes) {
    trailersysRenderAreaChart($("conductorBarChart"),
      viajesPorMes.map((m) => ({ label: m.mes, value: m.cantidad })),
      { color: "var(--color-primary)" });
  }

  function renderFinalizadosRing(resumen) {
    const pct = resumen.totalViajes > 0 ? (resumen.viajesFinalizados / resumen.totalViajes) * 100 : 0;
    trailersysRenderProgressRing($("conductorFinalizadosRing"),
      { valor: pct, etiqueta: "Viajes finalizados", color: "var(--color-success)" });
  }

  // --- Viajes recientes ---
  function renderRecientes(viajes) {
    const contenedor = $("conductorViajesRecientes");
    contenedor.innerHTML = viajes.length
      ? viajes.map((v) => `
        <div class="dashboard-trip">
          <div>
            <div class="dashboard-trip-route">${esc(v.origen)} → ${esc(v.destino)}</div>
            <div class="dashboard-trip-meta">${esc(v.vehiculoPlaca)} · ${esc(v.estado)}</div>
          </div>
          <div class="dashboard-trip-when">${trailersysFormatDateTime(v.fechaSalida)}</div>
        </div>`).join("")
      : '<div class="dashboard-empty">Todavía no tienes viajes registrados.</div>';
  }

  async function cargar() {
    try {
      const [perfil, resumen, activo, recientes] = await Promise.all([
        trailersysApiRequest("GET", "/mis-viajes/perfil"),
        trailersysApiRequest("GET", "/mis-viajes/resumen"),
        trailersysApiRequest("GET", "/mis-viajes/activo"),
        trailersysApiRequest("GET", "/mis-viajes?page=0&size=5"),
      ]);
      renderPerfil(perfil);
      renderAlerta(activo);
      renderDonut(resumen);
      renderTendencia(resumen.viajesPorMes);
      renderFinalizadosRing(resumen);
      renderRecientes(recientes.content);
    } catch (error) {
      $("conductorViajesRecientes").innerHTML = `<div class="dashboard-empty">${esc(error.message)}</div>`;
    }
  }

  trailersysRenderEcuadorMap("conductorEcuadorMap");
  cargar();
  window.addEventListener("trailersys:module-activated", (e) => { if (e.detail?.module === "dashboard") { cargar(); trailersysRenderEcuadorMap("conductorEcuadorMap"); } });
})();
