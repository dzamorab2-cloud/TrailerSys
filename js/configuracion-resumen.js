/**
 * "Resumen del sistema" - exclusivo de administrador, junto a la tarjeta de
 * perfil en Configuración. Reutiliza endpoints que ya existen (no agrega
 * nada al backend): /usuarios para activos/total, /respaldos para el último
 * respaldo real, /respaldos/configuracion para describir cuándo corre el
 * próximo automático.
 */
(function () {
  const session = trailersysGetSession();
  if (session?.role !== "administrador") return;

  const card = document.getElementById("resumenSistemaCard");
  if (!card) return;
  card.hidden = false;

  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  const DIA_SEMANA_LABEL = {
    MONDAY: "lunes", TUESDAY: "martes", WEDNESDAY: "miércoles", THURSDAY: "jueves",
    FRIDAY: "viernes", SATURDAY: "sábado", SUNDAY: "domingo",
  };
  const ESTADO_RESPALDO_LABEL = { COMPLETADO: "Completado", EN_PROGRESO: "En progreso", FALLIDO: "Falló" };

  async function cargarUsuarios() {
    const el = document.getElementById("resumenUsuariosActivos");
    try {
      const usuarios = await trailersysApiRequest("GET", "/usuarios");
      const activos = usuarios.filter((u) => u.activo).length;
      el.textContent = `${activos} de ${usuarios.length}`;
    } catch {
      el.textContent = "No disponible";
    }
  }

  async function cargarUltimoRespaldo() {
    const el = document.getElementById("resumenUltimoRespaldo");
    try {
      const respaldos = await trailersysApiRequest("GET", "/respaldos");
      if (!respaldos.length) {
        el.textContent = "Todavía no hay respaldos";
        return;
      }
      const ultimo = respaldos[0];
      const estado = ESTADO_RESPALDO_LABEL[ultimo.estado] || ultimo.estado;
      el.innerHTML = `${esc(trailersysFormatDateTime(ultimo.fechaHora))} <span class="badge ${ultimo.estado === "FALLIDO" ? "badge-danger" : ultimo.estado === "EN_PROGRESO" ? "badge-warning" : "badge-success"}">${esc(estado)}</span>`;
    } catch {
      el.textContent = "No disponible";
    }
  }

  async function cargarProximoRespaldo() {
    const el = document.getElementById("resumenProximoRespaldo");
    try {
      const config = await trailersysApiRequest("GET", "/respaldos/configuracion");
      if (!config.activo) {
        el.textContent = "Desactivado";
        return;
      }
      const hora = config.horaProgramada?.slice(0, 5) || "--:--";
      const texto = config.frecuencia === "SEMANAL"
        ? `Cada ${DIA_SEMANA_LABEL[config.diaSemana] || config.diaSemana} a las ${hora}`
        : config.frecuencia === "MENSUAL"
          ? `Día ${config.diaMes} de cada mes a las ${hora}`
          : `Diario a las ${hora}`;
      el.textContent = texto;
    } catch {
      el.textContent = "No disponible";
    }
  }

  cargarUsuarios();
  cargarUltimoRespaldo();
  cargarProximoRespaldo();

  // Si se dispara un respaldo manual (o se guarda la configuración) desde la
  // otra pestaña, el resumen queda desactualizado hasta la próxima vez que
  // se entre a Configuración - se refresca también cuando se reabre la
  // pestaña de Respaldos, que es cuando es más probable que algo cambió.
  window.addEventListener("trailersys:configuracion-tab-activada", (event) => {
    if (event.detail?.vista !== "respaldos") return;
    cargarUltimoRespaldo();
    cargarProximoRespaldo();
  });
})();
