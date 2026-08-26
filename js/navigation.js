(function () {
  const session = trailersysGetSession();
  if (!session) {
    window.location.href = "index.html";
    return;
  }

  const roleInfo = TRAILERSYS_ROLES[session.role] || TRAILERSYS_ROLES.administrador;
  const navLinks = Array.from(document.querySelectorAll(".nav-link"));
  const modules = Array.from(document.querySelectorAll(".module"));
  const topbarTitle = document.getElementById("topbarTitle");

  // --- Info de usuario en el topbar ---
  document.getElementById("userName").textContent = session.username;
  document.getElementById("userRole").textContent = roleInfo.label;
  document.getElementById("userAvatar").textContent = session.username
    .trim()
    .slice(0, 2)
    .toUpperCase();

  // --- Control de acceso por rol: ocultar modulos no permitidos ---
  navLinks.forEach((link) => {
    const moduleName = link.dataset.module;
    if (!roleInfo.modules.includes(moduleName)) {
      link.hidden = true;
    }
  });

  // Ocultar tambien el titulo de una seccion si quedo sin items visibles.
  document.querySelectorAll(".nav-section-title").forEach((title) => {
    const list = title.nextElementSibling;
    if (!list) return;
    const hasVisibleLink = Array.from(list.querySelectorAll(".nav-link")).some((link) => !link.hidden);
    title.hidden = !hasVisibleLink;
  });

  // --- Navegacion modular: solo el modulo seleccionado esta visible ---
  function activateModule(moduleName) {
    if (!roleInfo.modules.includes(moduleName)) {
      moduleName = roleInfo.modules[0];
    }

    modules.forEach((section) => {
      section.hidden = section.dataset.module !== moduleName;
    });

    navLinks.forEach((link) => {
      link.classList.toggle("active", link.dataset.module === moduleName);
    });

    const activeLink = navLinks.find((link) => link.dataset.module === moduleName);
    if (activeLink) {
      topbarTitle.textContent = activeLink.querySelector(".nav-label").textContent;
    }

    window.dispatchEvent(new CustomEvent("trailersys:module-activated", {
      detail: { module: moduleName },
    }));
  }

  navLinks.forEach((link) => {
    link.addEventListener("click", () => activateModule(link.dataset.module));
  });

  activateModule("dashboard");

  // --- Colapsar/expandir sidebar (en escritorio) o abrir/cerrar (en movil) ---
  const appShell = document.getElementById("appShell");
  document.getElementById("sidebarToggle").addEventListener("click", () => {
    appShell.classList.toggle("sidebar-toggled");
  });
  document.getElementById("sidebarBackdrop").addEventListener("click", () => {
    appShell.classList.remove("sidebar-toggled");
  });

  // --- Cerrar sesion ---
  document.getElementById("logoutBtn").addEventListener("click", () => {
    trailersysClearSession();
    window.location.href = "index.html";
  });
})();
