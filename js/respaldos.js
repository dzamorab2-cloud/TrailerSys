/**
 * Respaldos de la base de datos - exclusivo del rol Administrador. Vive
 * dentro de la pantalla de Configuración, en su propia pestaña junto a
 * "Usuarios y acceso" (ver configuracion-tabs.js, que controla cuál de las
 * dos está visible - este archivo ya no decide su propia visibilidad).
 */
(function () {
  const session = trailersysGetSession();
  if (session?.role !== "administrador") return;

  const body = document.getElementById("respaldosBody");
  const configForm = document.getElementById("respaldoConfigForm");
  const frecuenciaInput = document.getElementById("respaldoFrecuencia");
  const horaInput = document.getElementById("respaldoHora");
  const diaSemanaInput = document.getElementById("respaldoDiaSemana");
  const diaMesInput = document.getElementById("respaldoDiaMes");
  const fieldDiaSemana = document.getElementById("fieldRespaldoDiaSemana");
  const fieldDiaMes = document.getElementById("fieldRespaldoDiaMes");
  const activoInput = document.getElementById("respaldoActivo");
  const btnCompleto = document.getElementById("btnRespaldoCompleto");
  const btnIncremental = document.getElementById("btnRespaldoIncremental");

  function actualizarVisibilidadFrecuencia() {
    fieldDiaSemana.hidden = frecuenciaInput.value !== "SEMANAL";
    fieldDiaMes.hidden = frecuenciaInput.value !== "MENSUAL";
  }
  frecuenciaInput.addEventListener("change", actualizarVisibilidadFrecuencia);

  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function toast(message, error = false) {
    let el = document.querySelector(".ui-toast");
    if (!el) { el = document.createElement("div"); el.className = "ui-toast"; document.body.appendChild(el); }
    el.textContent = message;
    el.classList.toggle("error", error);
    el.classList.add("show");
    clearTimeout(el._timer);
    el._timer = setTimeout(() => el.classList.remove("show"), 2800);
  }

  function apiHeaders() {
    const token = trailersysGetSession()?.token;
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  // Los tamaños de respaldo real pueden ir de unos KB (un incremental con
  // pocos cambios) a varios GB (un completo de una base grande) - un mismo
  // "1234" sin unidad no dice nada util en ninguno de los dos extremos.
  function formatBytes(bytes) {
    if (bytes == null) return "—";
    const unidades = ["B", "KB", "MB", "GB", "TB"];
    let valor = bytes, i = 0;
    while (valor >= 1024 && i < unidades.length - 1) { valor /= 1024; i++; }
    return `${valor.toFixed(i === 0 ? 0 : 1)} ${unidades[i]}`;
  }

  const TIPO_LABEL = { COMPLETO: "Completo", INCREMENTAL: "Incremental" };
  const ESTADO_BADGE = { COMPLETADO: "badge-success", EN_PROGRESO: "badge-warning", FALLIDO: "badge-danger" };
  const ESTADO_LABEL = { COMPLETADO: "Completado", EN_PROGRESO: "En progreso", FALLIDO: "Fallido" };

  async function cargarConfiguracion() {
    try {
      const c = await trailersysApiRequest("GET", "/respaldos/configuracion");
      frecuenciaInput.value = c.frecuencia || "DIARIO";
      horaInput.value = c.horaProgramada?.slice(0, 5) || "02:00";
      diaSemanaInput.value = c.diaSemana || "MONDAY";
      diaMesInput.value = c.diaMes || 1;
      activoInput.checked = !!c.activo;
      actualizarVisibilidadFrecuencia();
    } catch (e) {
      toast(e.message || "No se pudo cargar la configuración de respaldos.", true);
    }
  }

  configForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!configForm.reportValidity()) return;
    const frecuencia = frecuenciaInput.value;
    if (frecuencia === "MENSUAL") {
      const dia = Number(diaMesInput.value);
      if (!dia || dia < 1 || dia > 31) {
        toast("Ingresa un día del mes válido (1-31).", true);
        return;
      }
    }
    try {
      await trailersysApiRequest("PUT", "/respaldos/configuracion", {
        activo: activoInput.checked,
        frecuencia,
        horaProgramada: `${horaInput.value}:00`,
        diaSemana: frecuencia === "SEMANAL" ? diaSemanaInput.value : null,
        diaMes: frecuencia === "MENSUAL" ? Number(diaMesInput.value) : null,
      });
      toast("Configuración de respaldos guardada.");
    } catch (e) {
      toast(e.message || "No se pudo guardar la configuración.", true);
    }
  });

  async function cargarHistorial() {
    body.innerHTML = '<tr class="loading-row"><td colspan="7">Cargando respaldos…</td></tr>';
    try {
      const respaldos = await trailersysApiRequest("GET", "/respaldos");
      body.innerHTML = respaldos.length ? respaldos.map((r) => `
        <tr>
          <td>${trailersysFormatDateTime(r.fechaHora)}</td>
          <td><span class="badge badge-neutral">${TIPO_LABEL[r.tipo] || r.tipo}</span></td>
          <td>${formatBytes(r.tamanoBytes)}</td>
          <td>${r.registrosCapturados ?? "—"}</td>
          <td><span class="badge ${ESTADO_BADGE[r.estado] || "badge-neutral"}" title="${esc(r.mensajeError || "")}">${ESTADO_LABEL[r.estado] || r.estado}</span></td>
          <td>${esc(r.generadoPor)}</td>
          <td>${r.estado === "COMPLETADO" ? `<div class="table-actions"><button class="icon-btn" data-download-respaldo="${r.id}" title="Descargar" aria-label="Descargar respaldo #${r.id}"><i class="bi bi-download"></i></button></div>` : "—"}</td>
        </tr>`).join("") : '<tr class="loading-row"><td colspan="7">Todavía no se ha generado ningún respaldo.</td></tr>';
    } catch (e) {
      body.innerHTML = `<tr class="loading-row"><td colspan="7">${esc(e.message)}</td></tr>`;
    }
  }

  async function dispararRespaldo(tipo, boton) {
    boton.disabled = true;
    const textoOriginal = boton.innerHTML;
    boton.innerHTML = `<i class="bi bi-hourglass-split"></i>Generando…`;
    try {
      await trailersysApiRequest("POST", `/respaldos/${tipo}`);
      toast(tipo === "completo" ? "Respaldo completo generado." : "Respaldo incremental generado.");
      await cargarHistorial();
    } catch (e) {
      toast(e.message || "No se pudo generar el respaldo.", true);
    } finally {
      boton.disabled = false;
      boton.innerHTML = textoOriginal;
    }
  }

  btnCompleto.addEventListener("click", () => dispararRespaldo("completo", btnCompleto));
  btnIncremental.addEventListener("click", () => dispararRespaldo("incremental", btnIncremental));

  body.addEventListener("click", async (event) => {
    const download = event.target.closest("[data-download-respaldo]");
    if (!download) return;
    try {
      const id = download.dataset.downloadRespaldo;
      const response = await fetch(`${TRAILERSYS_API_BASE_URL}/respaldos/${id}/descargar`, { headers: apiHeaders() });
      if (!response.ok) throw new Error("No se pudo descargar el respaldo.");
      const blob = await response.blob();
      const disposition = response.headers.get("content-disposition") || "";
      const name = /filename="([^"]+)"/.exec(disposition)?.[1] || `respaldo-${id}`;
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url; a.download = name; a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast(e.message || "No se pudo descargar el respaldo.", true);
    }
  });

  // No se carga hasta que la pestaña "Respaldos de la base de datos" se abre
  // por primera vez (ver configuracion-tabs.js) - así el admin que nunca
  // llega a abrir esa pestaña no dispara /respaldos ni /respaldos/configuracion
  // en cada visita a Configuración. Se refresca cada vez que se reabre la
  // pestaña (mismo criterio que el Calendario de Mantenimientos).
  window.addEventListener("trailersys:configuracion-tab-activada", (event) => {
    if (event.detail?.vista !== "respaldos") return;
    cargarConfiguracion();
    cargarHistorial();
  });
})();
