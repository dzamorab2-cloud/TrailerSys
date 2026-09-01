(function(){
 // El conductor, el supervisor y mantenimiento tienen su propio Dashboard
 // personalizado (js/conductor-dashboard.js, js/supervisor-dashboard.js,
 // js/mantenimiento-dashboard.js); el cliente no tiene "dashboard" en su
 // lista de modulos y nunca llega a ver esta seccion (queda oculta toda la
 // sesion). Sin esta guarda, este IIFE pediria /dashboard/resumen igual
 // (403 silencioso para cliente, ya ocurria antes) y ademas inicializaria
 // un mapa de Leaflet real sobre un contenedor que jamas se muestra -
 // trabajo y peticiones de red de mas para una vista que ese rol no usa.
 if(["conductor","supervisor","mantenimiento","cliente"].includes(trailersysGetSession()?.role))return;
 const stats=document.getElementById("dashboardStats"),alerts=document.getElementById("dashboardAlertas"),trips=document.getElementById("dashboardViajes");
 const esc=v=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
 const card=(icon,value,label,tone="")=>`<div class="stat-card stat-card-action ${tone}"><div class="stat-card-icon"><i class="bi ${icon}"></i></div><div><div class="stat-card-value">${Number(value).toLocaleString("es-EC")}</div><div class="stat-card-label">${label}</div></div></div>`;
 async function render(){stats.innerHTML='<div class="dashboard-empty">Cargando indicadores…</div>';try{const d=await trailersysApiRequest("GET","/dashboard/resumen");stats.innerHTML=[card("bi-truck",d.vehiculos,"Vehículos"),card("bi-check-circle",d.vehiculosDisponibles,"Disponibles","success"),card("bi-person-badge",d.conductoresActivos,"Conductores activos"),card("bi-signpost-split",d.viajesEnCurso,"Viajes en curso","info"),card("bi-calendar2-check",d.viajesProgramados,"Programados"),card("bi-tools",d.mantenimientosVencidos,"Servicios vencidos",d.mantenimientosVencidos?"danger":""),card("bi-patch-check",d.entregasPendientes,"Entregas por validar",d.entregasPendientes?"warning":"")].join("");
  trailersysRenderProgressRing(document.getElementById("dashboardConductoresRing"),{valor:d.conductores>0?(d.conductoresActivos/d.conductores)*100:0,etiqueta:"Personal activo",color:"var(--color-info)"});
  const warnings=[];if(d.mantenimientosVencidos)warnings.push(`<div class="alert-item level-warning"><i class="bi bi-tools"></i><div class="alert-text"><strong>${d.mantenimientosVencidos.toLocaleString("es-EC")}</strong> servicios requieren atención.</div></div>`);if(d.entregasPendientes)warnings.push(`<div class="alert-item level-info"><i class="bi bi-patch-check"></i><div class="alert-text"><strong>${d.entregasPendientes.toLocaleString("es-EC")}</strong> entregas esperan validación.</div></div>`);alerts.innerHTML=warnings.length?warnings.join(""):'<div class="alerts-empty"><i class="bi bi-check-circle"></i>No hay alertas críticas.</div>';trips.innerHTML=d.proximosViajes.length?d.proximosViajes.map(v=>`<div class="dashboard-trip"><div><div class="dashboard-trip-route">${esc(v.origen)} → ${esc(v.destino)}</div><div class="dashboard-trip-meta">${esc(v.placa)} · ${esc(v.conductor)}</div></div><div class="dashboard-trip-when">${trailersysFormatDateTime(v.fechaSalida)}</div></div>`).join(""):'<div class="dashboard-empty">No hay viajes programados.</div>';}catch(e){stats.innerHTML=`<div class="dashboard-empty">${esc(e.message)}</div>`;}}
 async function renderTendencia(){const contenedor=document.getElementById("dashboardTendenciaChart");try{const d=await trailersysApiRequest("GET","/dashboard/tendencia");trailersysRenderAreaChart(contenedor,d.viajesPorDia.map(p=>({label:p.etiqueta,value:p.cantidad})),{color:"var(--color-primary)"});}catch(e){contenedor.innerHTML=`<div class="dashboard-empty">${esc(e.message)}</div>`;}}
 // "Flota disponible" necesita el desglose por estado (no solo
 // disponibles/total), asi que usa /dashboard/disponibilidad (la misma
 // fuente del Panel de disponibilidad de Administrador/Coordinador) en vez
 // de /dashboard/resumen.
 async function renderFlotaRing(){const contenedor=document.getElementById("dashboardFlotaRing");try{const d=await trailersysApiRequest("GET","/dashboard/disponibilidad");trailersysRenderMultiRing(contenedor,[["Disponible",d.vehiculosDisponibles,"var(--color-success)"],["En Ruta",d.vehiculosEnRuta,"var(--color-info)"],["Mantenimiento",d.vehiculosMantenimiento,"var(--color-warning)"],["Fuera de Servicio",d.vehiculosFueraServicio,"var(--color-danger)"]]);}catch(e){contenedor.innerHTML=`<div class="dashboard-empty">${esc(e.message)}</div>`;}}
 window.addEventListener("trailersys:data-changed",e=>{if(e.detail?.resource==="mantenimientos"){render();renderFlotaRing();}});
 window.addEventListener("trailersys:module-activated",e=>{if(e.detail?.module==="dashboard"){render();renderTendencia();renderFlotaRing();trailersysRenderEcuadorMap("dashboardEcuadorMap");}});
 render();
 renderTendencia();
 renderFlotaRing();
 trailersysRenderEcuadorMap("dashboardEcuadorMap");
})();

(function () {
  const container = document.getElementById("dashboardDisponibilidad");
  const panel = document.getElementById("dashboardDisponibilidadPanel");
  const session = trailersysGetSession();
  if (!["administrador", "coordinador"].includes(session?.role)) { panel.hidden = true; return; }
  const item = (icon, label, value, tone = "") => `<div class="availability-item ${tone}"><i class="bi ${icon}"></i><div><strong>${Number(value).toLocaleString("es-EC")}</strong><span>${label}</span></div></div>`;
  function renderAvailability() { trailersysApiRequest("GET", "/dashboard/disponibilidad").then((d) => {
    container.innerHTML = `
      <div class="availability-group"><h4>Vehículos</h4><div class="availability-items">
        ${item("bi-check-circle", "Disponibles", d.vehiculosDisponibles, "ok")}${item("bi-signpost", "En ruta", d.vehiculosEnRuta)}
        ${item("bi-tools", "En mantenimiento", d.vehiculosMantenimiento, "warning")}${item("bi-x-octagon", "Fuera de servicio", d.vehiculosFueraServicio, "danger")}
      </div></div>
      <div class="availability-group"><h4>Conductores</h4><div class="availability-items">
        ${item("bi-person-check", "Disponibles", d.conductoresDisponibles, "ok")}${item("bi-person-workspace", "En ruta", d.conductoresEnRuta)}
        ${item("bi-moon-stars", "En descanso", d.conductoresDescanso)}${item("bi-person-x", "Inactivos", d.conductoresInactivos, "danger")}
        ${item("bi-person-vcard", "Licencias vencidas", d.licenciasVencidas, "warning")}
      </div></div>`;
  }).catch((error) => { container.innerHTML = `<div class="dashboard-empty">${error.message}</div>`; }); }
  window.addEventListener("trailersys:data-changed",e=>{if(e.detail?.resource==="mantenimientos")renderAvailability();});
  window.addEventListener("trailersys:module-activated",e=>{if(e.detail?.module==="dashboard")renderAvailability();});
  renderAvailability();
})();
