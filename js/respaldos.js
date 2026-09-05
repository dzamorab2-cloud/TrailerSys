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
  const btnElegirCarpeta = document.getElementById("btnRespaldoElegirCarpeta");
  const carpetaInfo = document.getElementById("respaldoCarpetaInfo");

  // Subida aqui arriba (antes vivia mas abajo, junto a toast()) porque
  // actualizarCarpetaInfo() ya la necesita al llamarse de forma inmediata
  // unas lineas mas abajo, al terminar de evaluarse este modulo.
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // File System Access API (showDirectoryPicker): solo Chrome/Edge la
  // soportan hoy. El "directory handle" que devuelve no se puede persistir
  // entre recargas (limitacion normal de la API, no un descuido nuestro),
  // asi que vive solo en esta variable de modulo: si se recarga la pagina,
  // hay que volver a elegir la carpeta.
  let carpetaHandle = null;
  const soportaDirectoryPicker = typeof window.showDirectoryPicker === "function";

  function actualizarCarpetaInfo() {
    if (!soportaDirectoryPicker) {
      carpetaInfo.hidden = false;
      carpetaInfo.innerHTML = `<i class="bi bi-exclamation-circle"></i> Elegir carpeta no está disponible en este navegador (usa Chrome o Edge).`;
      return;
    }
    if (carpetaHandle) {
      carpetaInfo.hidden = false;
      carpetaInfo.innerHTML = `<i class="bi bi-folder-check"></i> Los respaldos se guardarán también en: <strong>${esc(carpetaHandle.name)}</strong>`;
    } else {
      carpetaInfo.hidden = true;
    }
  }

  if (!soportaDirectoryPicker) {
    btnElegirCarpeta.disabled = true;
  } else {
    btnElegirCarpeta.addEventListener("click", async () => {
      try {
        // "readwrite": sin este modo explicito el permiso queda solo de
        // lectura y getFileHandle(..., {create:true})/createWritable() del
        // paso b) fallarian con NotAllowedError al intentar escribir.
        carpetaHandle = await window.showDirectoryPicker({ mode: "readwrite" });
        actualizarCarpetaInfo();
      } catch (e) {
        // AbortError: el usuario cerro el selector sin elegir nada - no es un error real.
        if (e?.name !== "AbortError") toast(e.message || "No se pudo seleccionar la carpeta.", true);
      }
    });
  }
  actualizarCarpetaInfo();

  function actualizarVisibilidadFrecuencia() {
    fieldDiaSemana.hidden = frecuenciaInput.value !== "SEMANAL";
    fieldDiaMes.hidden = frecuenciaInput.value !== "MENSUAL";
  }
  frecuenciaInput.addEventListener("change", actualizarVisibilidadFrecuencia);

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

  // Escribe el respaldo recien generado dentro de la carpeta que el admin
  // eligio con "Elegir carpeta de guardado" (ver btnElegirCarpeta arriba),
  // ademas de la copia que el servidor ya guarda en su carpeta por defecto.
  // Reutiliza el mismo endpoint de descarga que ya usaba el boton manual de
  // la tabla, solo que en vez de bajarlo con <a download> lo escribe
  // directo en el directory handle.
  async function guardarEnCarpetaElegida(id) {
    try {
      const response = await fetch(`${TRAILERSYS_API_BASE_URL}/respaldos/${id}/descargar`, { headers: apiHeaders() });
      if (!response.ok) throw new Error("No se pudo obtener el respaldo para guardarlo en la carpeta elegida.");
      const disposition = response.headers.get("content-disposition") || "";
      const nombre = /filename="([^"]+)"/.exec(disposition)?.[1] || `respaldo-${id}`;
      const contenido = await response.arrayBuffer();

      const archivoHandle = await carpetaHandle.getFileHandle(nombre, { create: true });
      const escritor = await archivoHandle.createWritable();
      await escritor.write(contenido);
      await escritor.close();

      toast(`Respaldo guardado también en "${carpetaHandle.name}/${nombre}".`);
    } catch (e) {
      // No se revierte ni se le resta importancia al respaldo ya generado en
      // el servidor (eso ya paso bien) - solo se avisa que la copia local no
      // se pudo escribir, para que el admin no crea que si quedo guardada.
      toast(e.message || "El respaldo se generó, pero no se pudo guardar en la carpeta elegida.", true);
    }
  }

  async function dispararRespaldo(tipo, boton) {
    boton.disabled = true;
    const textoOriginal = boton.innerHTML;
    const etiquetaTipo = tipo === "completo" ? "completo" : "incremental";
    // El boton deja de decir solo "Generando…" (generico) para dejar clara
    // cual de las dos operaciones esta en curso, con un spinner animado
    // (misma clase .spinner que ya usa el modulo de Viajes) en vez del
    // icono estatico de reloj de arena que habia antes.
    boton.innerHTML = `<span class="spinner"></span>Generando respaldo ${etiquetaTipo}...`;
    toast(`Generando respaldo ${etiquetaTipo}…`);
    try {
      const respaldo = await trailersysApiRequest("POST", `/respaldos/${tipo}`);
      toast(tipo === "completo" ? "Respaldo completo generado." : "Respaldo incremental generado.");
      await cargarHistorial();
      // Si el admin ya eligio una carpeta local, se guarda ahi una copia
      // automatica; si no, el comportamiento sigue siendo el de siempre
      // (el respaldo queda en el servidor y se descarga a mano desde la
      // tabla, con el boton de descarga de cada fila).
      if (carpetaHandle && respaldo?.estado === "COMPLETADO" && respaldo?.id) {
        await guardarEnCarpetaElegida(respaldo.id);
      }
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
