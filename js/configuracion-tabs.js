/**
 * "Usuarios y acceso" y "Respaldos de la base de datos" (ambos exclusivos de
 * administrador, ver admin.js/respaldos.js) vivian como dos paneles enteros
 * uno debajo del otro en Configuración: con la tabla de usuarios larga, había
 * que bajar toda esa tabla para llegar a Respaldos (o al revés, con el
 * historial de respaldos largo). Se convierten en pestañas (mismo patrón
 * "maintenance-tabs" que ya usa Mantenimientos): un clic muestra SOLO ese
 * panel completo, sin tener que bajar por el otro para llegar a él.
 */
(function () {
  const session = trailersysGetSession();
  if (session?.role !== "administrador") return;

  const contenedor = document.getElementById("adminGestionTabs");
  const tabs = document.getElementById("configuracionTabs");
  const panelUsuarios = document.getElementById("adminUsuarios");
  const panelRespaldos = document.getElementById("adminRespaldos");
  if (!contenedor || !tabs || !panelUsuarios || !panelRespaldos) return;

  contenedor.hidden = false;

  function activar(vista) {
    tabs.querySelectorAll(".maintenance-tab").forEach((btn) => btn.classList.toggle("active", btn.dataset.view === vista));
    panelUsuarios.hidden = vista !== "usuarios";
    panelRespaldos.hidden = vista !== "respaldos";
    if (vista === "respaldos") {
      // Respaldos no carga sus datos hasta que se abre su pestaña por
      // primera vez (y se refresca cada vez que se vuelve a abrir, igual
      // que el Calendario de Mantenimientos) - evita pedir /respaldos si el
      // administrador nunca llega a abrir esa pestaña.
      window.dispatchEvent(new CustomEvent("trailersys:configuracion-tab-activada", { detail: { vista } }));
    }
  }

  tabs.addEventListener("click", (event) => {
    const boton = event.target.closest("[data-view]");
    if (!boton) return;
    activar(boton.dataset.view);
  });
})();
