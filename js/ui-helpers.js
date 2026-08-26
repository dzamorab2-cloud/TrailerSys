/**
 * Utilidades de UI compartidas entre modulos: apertura/cierre de modales
 * y un dialogo de confirmacion generico (reemplaza al confirm() nativo
 * para mantener la identidad visual del sistema).
 */
function trailersysOpenModal(overlayEl) {
  overlayEl.classList.add("open");
}

function trailersysCloseModal(overlayEl) {
  overlayEl.classList.remove("open");
}

const trailersysConfirm = (function () {
  const overlay = document.getElementById("confirmModalOverlay");
  const titleEl = document.getElementById("confirmModalTitle");
  const textEl = document.getElementById("confirmModalText");
  const acceptBtn = document.getElementById("confirmModalAccept");
  const cancelBtn = document.getElementById("confirmModalCancel");
  const closeBtn = document.getElementById("confirmModalClose");
  let pendingAccept = null;

  function close() {
    trailersysCloseModal(overlay);
    pendingAccept = null;
  }

  acceptBtn.addEventListener("click", () => {
    const accept = pendingAccept;
    close();
    if (accept) accept();
  });
  cancelBtn.addEventListener("click", close);
  closeBtn.addEventListener("click", close);
  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) close();
  });

  return function trailersysConfirm({ title, text, acceptLabel = "Eliminar", variant = "danger", onAccept }) {
    titleEl.textContent = title;
    textEl.textContent = text;
    acceptBtn.textContent = acceptLabel;
    acceptBtn.classList.toggle("btn-danger", variant === "danger");
    acceptBtn.classList.toggle("btn-primary", variant !== "danger");
    pendingAccept = onAccept;
    trailersysOpenModal(overlay);
  };
})();

const trailersysShowGuide = (function () {
  const overlay = document.getElementById("guiaModalOverlay");
  const titleEl = document.getElementById("guiaModalTitle");
  const contentEl = document.getElementById("guiaModalContent");
  const closeBtn = document.getElementById("guiaModalClose");
  const closeFooterBtn = document.getElementById("guiaModalCerrarBtn");
  const printBtn = document.getElementById("guiaModalImprimir");

  const escapeHtml = (value) => String(value ?? "—").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[char]));

  function close() { trailersysCloseModal(overlay); }
  closeBtn.addEventListener("click", close);
  closeFooterBtn.addEventListener("click", close);
  printBtn.addEventListener("click", () => window.print());
  overlay.addEventListener("click", (event) => { if (event.target === overlay) close(); });

  return function trailersysShowGuide({ tipo, codigo, id, estado, secciones }) {
    const prefijo = codigo || (tipo === "Viaje" ? "VIA" : "CAR");
    const numero = `GUIA-${prefijo}-${String(id).padStart(6, "0")}`;
    titleEl.textContent = `Guía de ${tipo.toLowerCase()} ${numero}`;
    contentEl.innerHTML = `
      <div class="guia-summary">
        <div><span>Número de guía</span><strong>${escapeHtml(numero)}</strong></div>
        <div><span>Fecha de emisión</span><strong>${escapeHtml(new Date().toLocaleString("es-EC"))}</strong></div>
        <div><span>Estado</span><strong>${escapeHtml(estado)}</strong></div>
      </div>
      ${secciones.map((seccion) => `
        <section class="guia-section">
          <h4><i class="bi ${escapeHtml(seccion.icono || "bi-list-check")}"></i>${escapeHtml(seccion.titulo)}</h4>
          <div class="guia-fields">
            ${seccion.campos.map(([etiqueta, valor]) => `
              <div class="guia-field"><span>${escapeHtml(etiqueta)}</span><strong>${escapeHtml(valor || "—")}</strong></div>
            `).join("")}
          </div>
        </section>
      `).join("")}
      <div class="guia-signatures">
        <div><span>Firma del responsable de despacho</span></div>
        <div><span>Firma del conductor</span></div>
        <div><span>Firma de recepción</span></div>
      </div>`;
    trailersysOpenModal(overlay);
  };
})();
