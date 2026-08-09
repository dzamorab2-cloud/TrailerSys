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
