(function () {
  const TIPOS = ["Preventivo", "Correctivo"];

  // Caches del ultimo listado cargado desde la API.
  let mantenimientosCache = [];
  let currentPage = 0;
  let pageMeta = null;
  let vehiculosCache = [];

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoMantenimiento");
  const grid = document.getElementById("mantenimientoGrid");
  const emptyState = document.getElementById("mantenimientoEmptyState");
  const emptyTitle = document.getElementById("mantenimientoEmptyTitle");
  const emptyText = document.getElementById("mantenimientoEmptyText");
  const resultsCount = document.getElementById("mantenimientoResultsCount");

  const inputBuscar = document.getElementById("mantenimientoBuscar");
  const filtroVehiculo = document.getElementById("mantenimientoFiltroVehiculo");
  const filtroTipo = document.getElementById("mantenimientoFiltroTipo");

  const modalOverlay = document.getElementById("mantenimientoModalOverlay");
  const modalTitle = document.getElementById("mantenimientoModalTitle");
  const form = document.getElementById("mantenimientoForm");
  const btnCerrarModal = document.getElementById("mantenimientoModalClose");
  const btnCancelar = document.getElementById("mantenimientoCancelar");

  const inputId = document.getElementById("mantenimientoId");
  const inputVehiculo = document.getElementById("mantenimientoVehiculo");
  const inputVehiculoBuscar = document.getElementById("mantenimientoVehiculoBuscar");
  const resultadosVehiculo = document.getElementById("mantenimientoVehiculoResultados");
  const selectTipo = document.getElementById("mantenimientoTipo");
  const inputFecha = document.getElementById("mantenimientoFecha");
  const inputKilometraje = document.getElementById("mantenimientoKilometraje");
  const inputCosto = document.getElementById("mantenimientoCosto");
  const inputProximoServicio = document.getElementById("mantenimientoProximoServicio");
  const inputDescripcion = document.getElementById("mantenimientoDescripcion");

  const tabs = document.getElementById("maintenanceTabs");
  const listadoView = document.getElementById("mantenimientoListadoView");
  const calendarioView = document.getElementById("mantenimientoCalendarioView");
  const reportesView = document.getElementById("mantenimientoReportesView");
  const calendar = document.getElementById("maintenanceCalendar");
  const calendarTitle = document.getElementById("calendarTitle");
  let calendarDate = new Date();
  let calendarMode = "month";
  const evidenciaOverlay = document.getElementById("evidenciaModalOverlay");
  const evidenciaForm = document.getElementById("evidenciaForm");
  const evidenciaList = document.getElementById("evidenciaList");

  let session = null;

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

  function formatCosto(value) {
    return `$ ${Number(value).toLocaleString("es-EC", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function sumarUnMes(fechaTexto) {
    if (!fechaTexto) return "";
    const [anio, mes, dia] = fechaTexto.split("-").map(Number);
    const inicioMesDestino = new Date(Date.UTC(anio, mes, 1));
    const ultimoDia = new Date(Date.UTC(inicioMesDestino.getUTCFullYear(), inicioMesDestino.getUTCMonth() + 1, 0)).getUTCDate();
    const fecha = new Date(Date.UTC(inicioMesDestino.getUTCFullYear(), inicioMesDestino.getUTCMonth(), Math.min(dia, ultimoDia)));
    return fecha.toISOString().slice(0, 10);
  }

  function fechaLocalHoy() {
    const hoy = new Date();
    const dos = (valor) => String(valor).padStart(2, "0");
    return `${hoy.getFullYear()}-${dos(hoy.getMonth() + 1)}-${dos(hoy.getDate())}`;
  }

  function estadoProximoServicio(fechaTexto) {
    if (!fechaTexto) return { clase: "badge-neutral", sufijo: "" };
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    const fecha = new Date(`${fechaTexto}T00:00:00`);
    const dias = Math.ceil((fecha - hoy) / 86400000);
    if (dias < 0) return { clase: "badge-danger", sufijo: ` (vencido hace ${Math.abs(dias)} día${Math.abs(dias) === 1 ? "" : "s"})` };
    if (dias === 0) return { clase: "badge-warning", sufijo: " (corresponde hoy)" };
    if (dias <= 7) return { clase: "badge-warning", sufijo: ` (faltan ${dias} días)` };
    return { clase: "badge-info", sufijo: ` (faltan ${dias} días)` };
  }

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldMantenimientoVehiculo", "fieldMantenimientoFecha", "fieldMantenimientoKilometraje",
      "fieldMantenimientoCosto", "fieldMantenimientoDescripcion"]
      .forEach((id) => setFieldError(id, ""));
  }

  // Buscador con autocompletado para el campo "Vehiculo" del formulario: con
  // decenas de miles de vehiculos reales, un <select> con una lista fija es
  // inutil (ver trailersysAutocomplete en ui-helpers.js). El filtro de la
  // lista (mas abajo) es un caso aparte y sigue usando el <select> con
  // vehiculosCache, que ya trae como maximo 100 vehiculos.
  const vehiculoAutocomplete = trailersysAutocomplete({
    input: inputVehiculoBuscar,
    hidden: inputVehiculo,
    resultados: resultadosVehiculo,
    recurso: "vehiculos",
    etiqueta: (v) => `${v.placa} · ${v.marca} ${v.modelo}`,
    detalle: (v) => v.estado,
  });

  // --- Selects relacionados ---
  async function refreshVehiculosCache() {
    try {
      vehiculosCache = (await trailersysPagedRequest("vehiculos", 0, 100)).content;
    } catch {
      vehiculosCache = [];
    }
  }

  function fillVehiculoSelect(select, placeholder) {
    const current = select.value;
    select.innerHTML = `<option value="">${placeholder}</option>`;
    vehiculosCache.forEach((v) => {
      const option = document.createElement("option");
      option.value = v.id;
      option.textContent = `${v.placa} · ${v.marca} ${v.modelo}`;
      select.appendChild(option);
    });
    if (vehiculosCache.some((v) => String(v.id) === current)) select.value = current;
  }

  function renderCard(mantenimiento, canManage) {
    const estadoProximo = estadoProximoServicio(mantenimiento.proximoServicio);

    const actions = canManage
      ? `<div class="maintenance-card-actions" aria-label="Acciones del mantenimiento">
          <button type="button" class="maintenance-action" data-action="evidencias" data-id="${mantenimiento.id}" title="Documentos y evidencias"><i class="bi bi-paperclip"></i><span>Evidencias</span></button>
          <button type="button" class="maintenance-action" data-action="editar" data-id="${mantenimiento.id}" title="Editar mantenimiento">
            <i class="bi bi-pencil"></i><span>Editar</span>
          </button>
          <button type="button" class="maintenance-action danger" data-action="eliminar" data-id="${mantenimiento.id}" title="Eliminar mantenimiento">
            <i class="bi bi-trash3"></i><span>Eliminar</span>
          </button>
        </div>`
      : "";

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-tools"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(mantenimiento.tipo)} · ${escapeHtml(mantenimiento.vehiculoPlaca)}</div>
            <div class="item-subtitle">${escapeHtml(mantenimiento.fecha)}</div>
          </div>
        </div>
        ${actions}
        <div class="item-body">
          <p class="item-observations">${escapeHtml(mantenimiento.descripcion)}</p>
          <div class="item-meta">
            <span><i class="bi bi-speedometer2"></i>${Number(mantenimiento.kilometraje).toLocaleString("es-EC")} km</span>
            <span><i class="bi bi-cash-coin"></i>${formatCosto(mantenimiento.costo)}</span>
          </div>
          <div class="item-meta">
            ${mantenimiento.proximoServicio
              ? `<span class="badge ${estadoProximo.clase}"><i class="bi bi-calendar-check"></i> Próximo mantenimiento: ${escapeHtml(mantenimiento.proximoServicio)}${estadoProximo.sufijo}</span>`
              : `<span class="badge badge-neutral">Sin próximo servicio definido</span>`}
          </div>
        </div>
      </article>`;
  }

  async function render() {
    const canManage = trailersysCanManage(session, "mantenimientos");
    btnNuevo.hidden = !canManage;

    await refreshVehiculosCache();
    fillVehiculoSelect(filtroVehiculo, "Todos los vehículos");

    let mantenimientos;
    try {
      pageMeta = await trailersysPagedRequest("mantenimientos", currentPage, 24, {
        search: inputBuscar.value.trim(),
        vehiculoId: filtroVehiculo.value,
        tipo: filtroTipo.value,
      });
      mantenimientos = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los mantenimientos";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    mantenimientosCache = mantenimientos;

    const filtrados = mantenimientos;

    filtrados.sort((a, b) => (a.fecha < b.fecha ? 1 : -1));

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (mantenimientos.length === 0) {
        emptyTitle.textContent = "Todavía no hay mantenimientos registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo mantenimiento" para registrar el primero.'
          : "Cuando se registren mantenimientos, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún mantenimiento coincide con la búsqueda o los filtros aplicados.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${Number(pageMeta.totalElements).toLocaleString("es-EC")} mantenimiento${pageMeta.totalElements === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((m) => renderCard(m, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  async function openForm(mantenimiento) {
    clearFieldErrors();
    form.reset();
    selectTipo.value = "Preventivo";
    vehiculoAutocomplete.ocultar();

    if (mantenimiento) {
      modalTitle.textContent = "Editar mantenimiento";
      inputId.value = mantenimiento.id;
      // El vehiculo ya viene denormalizado en el mantenimiento
      // (vehiculoPlaca), asi que no hace falta otra peticion.
      inputVehiculo.value = mantenimiento.vehiculoId;
      inputVehiculoBuscar.value = mantenimiento.vehiculoPlaca || "";
      selectTipo.value = mantenimiento.tipo;
      inputFecha.value = mantenimiento.fecha;
      inputKilometraje.value = mantenimiento.kilometraje;
      inputCosto.value = mantenimiento.costo;
      inputProximoServicio.value = mantenimiento.proximoServicio || "";
      inputDescripcion.value = mantenimiento.descripcion;
    } else {
      modalTitle.textContent = "Nuevo mantenimiento";
      inputId.value = "";
      inputVehiculo.value = "";
      inputVehiculoBuscar.value = "";
      inputFecha.value = fechaLocalHoy();
      inputProximoServicio.value = sumarUnMes(inputFecha.value);
    }

    trailersysOpenModal(modalOverlay);
    inputVehiculoBuscar.focus();
  }

  function closeForm() {
    trailersysCloseModal(modalOverlay);
  }

  btnNuevo.addEventListener("click", () => openForm(null));
  btnCerrarModal.addEventListener("click", closeForm);
  btnCancelar.addEventListener("click", closeForm);
  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) closeForm();
  });

  function actualizarFechaPreventiva() {
    if (inputFecha.value) {
      inputProximoServicio.value = sumarUnMes(inputFecha.value);
    }
  }

  async function refreshAllMaintenanceViews() {
    await render();
    if (!calendarioView.hidden) await renderCalendar();
    if (!reportesView.hidden) await renderReports();
  }

  function notifyMaintenanceChanged(action, mantenimientoId) {
    window.dispatchEvent(new CustomEvent("trailersys:data-changed", {
      detail: { resource: "mantenimientos", action, id: mantenimientoId },
    }));
  }

  function apiHeaders() {
    const token = trailersysGetSession()?.token;
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  async function loadEvidencias(id) {
    const items = await trailersysApiRequest("GET", `/mantenimientos/${id}/evidencias`);
    evidenciaList.innerHTML = items.length ? items.map(e => `<div class="evidence-item"><i class="bi ${e.tipoContenido === "application/pdf" ? "bi-file-earmark-pdf" : "bi-image"}"></i><div class="evidence-info"><strong>${escapeHtml(e.nombre)}</strong><small>${escapeHtml(e.categoria.replaceAll("_", " "))} · ${(e.tamano / 1024).toLocaleString("es-EC",{maximumFractionDigits:1})} KB · ${trailersysFormatDateTime(e.fechaCarga)}</small></div><button class="icon-btn" data-evidence-download="${e.id}" title="Descargar"><i class="bi bi-download"></i></button><button class="icon-btn danger" data-evidence-delete="${e.id}" title="Eliminar"><i class="bi bi-trash3"></i></button></div>`).join("") : '<div class="dashboard-empty">Todavía no hay evidencias adjuntas.</div>';
  }

  async function openEvidencias(m) {
    document.getElementById("evidenciaMantenimientoId").value = m.id;
    document.getElementById("evidenciaMaintenanceLabel").textContent = `${m.tipo} · ${m.vehiculoPlaca} · ${m.fecha}`;
    evidenciaForm.reset(); trailersysOpenModal(evidenciaOverlay); await loadEvidencias(m.id);
  }

  async function downloadEvidence(mid,id) {
    const response = await fetch(`${TRAILERSYS_API_BASE_URL}/mantenimientos/${mid}/evidencias/${id}/archivo`,{headers:apiHeaders()});
    if(!response.ok) throw new Error("No se pudo descargar el archivo.");
    const blob=await response.blob(); const disposition=response.headers.get("content-disposition")||""; const name=/filename="([^"]+)"/.exec(disposition)?.[1]||"evidencia";
    const url=URL.createObjectURL(blob); const a=document.createElement("a");a.href=url;a.download=name;a.click();URL.revokeObjectURL(url);
  }

  evidenciaForm.addEventListener("submit",async event=>{event.preventDefault();const id=document.getElementById("evidenciaMantenimientoId").value;const file=document.getElementById("evidenciaArchivo").files[0];if(!file)return;if(file.size>10*1024*1024){alert("El archivo supera 10 MB.");return;}const fd=new FormData();fd.append("categoria",document.getElementById("evidenciaCategoria").value);fd.append("archivo",file);const btn=evidenciaForm.querySelector("button");btn.disabled=true;try{const response=await fetch(`${TRAILERSYS_API_BASE_URL}/mantenimientos/${id}/evidencias`,{method:"POST",headers:apiHeaders(),body:fd});if(!response.ok){const data=await response.json().catch(()=>null);throw new Error(data?.message||"No se pudo adjuntar.");}evidenciaForm.reset();await loadEvidencias(id);}catch(e){alert(e.message);}finally{btn.disabled=false;}});
  [document.getElementById("evidenciaModalClose"),document.getElementById("evidenciaCerrarBtn")].forEach(b=>b.addEventListener("click",()=>trailersysCloseModal(evidenciaOverlay)));
  evidenciaList.addEventListener("click",async event=>{const id=document.getElementById("evidenciaMantenimientoId").value;const download=event.target.closest("[data-evidence-download]");const del=event.target.closest("[data-evidence-delete]");try{if(download)await downloadEvidence(id,download.dataset.evidenceDownload);if(del){await trailersysApiRequest("DELETE",`/mantenimientos/${id}/evidencias/${del.dataset.evidenceDelete}`);await loadEvidencias(id);}}catch(e){alert(e.message);}});

  async function renderCalendar() {
    const year=calendarDate.getFullYear(),month=calendarDate.getMonth();
    let first=new Date(year,month,1),start,end;
    if(calendarMode==="day"){start=new Date(calendarDate);end=new Date(calendarDate);}
    else if(calendarMode==="week"){start=new Date(calendarDate);start.setDate(start.getDate()-start.getDay());end=new Date(start);end.setDate(end.getDate()+6);}
    else{start=new Date(year,month,1-first.getDay());end=new Date(start);end.setDate(end.getDate()+41);}
    const iso=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
    calendarTitle.textContent=calendarMode==="day"?new Intl.DateTimeFormat("es-EC",{dateStyle:"full"}).format(calendarDate):calendarMode==="week"?`${new Intl.DateTimeFormat("es-EC",{day:"numeric",month:"short"}).format(start)} – ${new Intl.DateTimeFormat("es-EC",{day:"numeric",month:"short",year:"numeric"}).format(end)}`:new Intl.DateTimeFormat("es-EC",{month:"long",year:"numeric"}).format(first);
    const events=await trailersysApiRequest("GET",`/mantenimientos/calendario?desde=${iso(start)}&hasta=${iso(end)}`);
    const byDate=Object.groupBy(events,e=>e.proximoServicio);
    calendar.className=`calendar-grid ${calendarMode}-view`;
    const names=["Dom","Lun","Mar","Mié","Jue","Vie","Sáb"];const heads=(calendarMode==="day"?[names[start.getDay()]]:names).map(x=>`<div class="calendar-head">${x}</div>`).join("");let days="";
    for(let d=new Date(start);d<=end;d.setDate(d.getDate()+1)){const key=iso(d);days+=`<div class="calendar-day ${d.getMonth()===month?"":"outside"}"><span class="calendar-number">${d.getDate()}</span>${(byDate[key]||[]).map(e=>`<span class="calendar-event ${key<fechaLocalHoy()?"overdue":""}" title="${escapeHtml(e.descripcion)}">${escapeHtml(e.vehiculoPlaca)} · ${escapeHtml(e.tipo)}</span>`).join("")}</div>`;}
    calendar.innerHTML=heads+days;
  }

  function stat(icon,value,label){return `<div class="stat-card"><div class="stat-card-icon"><i class="bi ${icon}"></i></div><div><div class="stat-card-value">${value}</div><div class="stat-card-label">${label}</div></div></div>`;}
  function bars(items,label,value,formatter=v=>v){const max=Math.max(1,...items.map(value));return items.map(x=>`<div class="metric-row"><span>${escapeHtml(label(x))}</span><div class="metric-bar"><span style="width:${value(x)*100/max}%"></span></div><strong>${formatter(value(x))}</strong></div>`).join("")||'<div class="dashboard-empty">Sin datos.</div>';}
  async function renderReports(){const d=await trailersysApiRequest("GET","/mantenimientos/reportes");const money=n=>formatCosto(n);document.getElementById("maintenanceStats").innerHTML=[stat("bi-tools",Number(d.total).toLocaleString("es-EC"),"Mantenimientos"),stat("bi-cash-coin",money(d.costoTotal),"Costo total"),stat("bi-receipt",money(d.costoPromedio),"Costo promedio"),stat("bi-exclamation-triangle",Number(d.vencidos).toLocaleString("es-EC"),"Servicios vencidos")].join("");document.getElementById("maintenanceCosts").innerHTML=bars(d.costosPorVehiculo,x=>x.placa,x=>x.costo,money)+bars(d.costosPorVehiculo,x=>`${x.placa} · días fuera`,x=>x.diasFueraServicio);document.getElementById("maintenanceFrequency").innerHTML=bars(d.mantenimientosFrecuentes,x=>x.tipo,x=>x.cantidad,v=>Number(v).toLocaleString("es-EC"));const fleet=[{name:"Disponibles",v:d.vehiculosDisponibles},{name:"Mantenimiento",v:d.vehiculosMantenimiento},{name:"Fuera de servicio",v:d.vehiculosFueraServicio}];document.getElementById("maintenanceAvailability").innerHTML=bars(fleet,x=>x.name,x=>x.v,v=>Number(v).toLocaleString("es-EC"));}

  tabs.addEventListener("click",async event=>{const btn=event.target.closest("[data-view]");if(!btn)return;tabs.querySelectorAll(".maintenance-tab").forEach(x=>x.classList.toggle("active",x===btn));listadoView.hidden=btn.dataset.view!=="listado";calendarioView.hidden=btn.dataset.view!=="calendario";reportesView.hidden=btn.dataset.view!=="reportes";try{if(btn.dataset.view==="calendario")await renderCalendar();if(btn.dataset.view==="reportes")await renderReports();}catch(e){alert(e.message);}});
  function moveCalendar(direction){if(calendarMode==="day")calendarDate.setDate(calendarDate.getDate()+direction);else if(calendarMode==="week")calendarDate.setDate(calendarDate.getDate()+7*direction);else calendarDate.setMonth(calendarDate.getMonth()+direction);renderCalendar();}
  document.getElementById("calendarPrev").addEventListener("click",()=>moveCalendar(-1));document.getElementById("calendarNext").addEventListener("click",()=>moveCalendar(1));document.getElementById("calendarToday").addEventListener("click",()=>{calendarDate=new Date();renderCalendar();});
  document.querySelectorAll("[data-calendar-mode]").forEach(button=>button.addEventListener("click",()=>{calendarMode=button.dataset.calendarMode;document.querySelectorAll("[data-calendar-mode]").forEach(x=>{x.classList.toggle("btn-primary",x===button);x.classList.toggle("btn-ghost",x!==button);});renderCalendar();}));

  inputFecha.addEventListener("change", actualizarFechaPreventiva);
  selectTipo.addEventListener("change", actualizarFechaPreventiva);

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.vehiculoId) fail("fieldMantenimientoVehiculo", "Selecciona un vehículo existente.");
    if (!data.fecha) fail("fieldMantenimientoFecha", "La fecha es obligatoria.");

    if (data.kilometraje === "" || Number.isNaN(data.kilometraje) || data.kilometraje < 0) {
      fail("fieldMantenimientoKilometraje", "Ingresa un kilometraje válido.");
    }

    if (data.costo === "" || Number.isNaN(data.costo) || data.costo < 0) {
      fail("fieldMantenimientoCosto", "Ingresa un costo válido.");
    }

    if (!data.descripcion) fail("fieldMantenimientoDescripcion", "La descripción es obligatoria.");

    if (data.fecha && data.proximoServicio && data.proximoServicio < data.fecha) {
      fail("fieldMantenimientoFecha", "El próximo servicio debe ser posterior a la fecha del mantenimiento.");
    }

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      vehiculoId: inputVehiculo.value ? Number(inputVehiculo.value) : null,
      tipo: TIPOS.includes(selectTipo.value) ? selectTipo.value : TIPOS[0],
      fecha: inputFecha.value,
      kilometraje: inputKilometraje.value === "" ? "" : Number(inputKilometraje.value),
      costo: inputCosto.value === "" ? "" : Number(inputCosto.value),
      proximoServicio: inputProximoServicio.value || null,
      descripcion: inputDescripcion.value.trim(),
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/mantenimientos/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/mantenimientos", data);
      }
      closeForm();
      await refreshAllMaintenanceViews();
      notifyMaintenanceChanged(id ? "updated" : "created", id);
    } catch (error) {
      alert(error.message || "No se pudo guardar el mantenimiento.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Acciones sobre las tarjetas (editar / eliminar) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const mantenimiento = mantenimientosCache.find((m) => String(m.id) === id);
    if (!mantenimiento) return;

    if (action === "editar") {
      openForm(mantenimiento);
    } else if (action === "evidencias") {
      openEvidencias(mantenimiento);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar mantenimiento",
        text: "¿Seguro que deseas eliminar este registro de mantenimiento? Esta acción no se puede deshacer.",
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/mantenimientos/${id}`);
            await refreshAllMaintenanceViews();
            notifyMaintenanceChanged("deleted", id);
          } catch (error) {
            alert(error.message || "No se pudo eliminar el mantenimiento.");
          }
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  let buscarTimer;
  inputBuscar.addEventListener("input", () => {
    clearTimeout(buscarTimer);
    buscarTimer = setTimeout(() => { currentPage = 0; render(); }, 300);
  });
  [filtroVehiculo, filtroTipo].forEach((el) => {
    el.addEventListener("change", () => { currentPage = 0; render(); });
  });

  window.addEventListener("trailersys:module-activated", (event) => {
    if (event.detail?.module === "mantenimientos") refreshAllMaintenanceViews().catch(() => {});
  });

  session = trailersysGetSession();
  render();
})();
