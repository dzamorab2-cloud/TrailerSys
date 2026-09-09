(function(){
 const session=trailersysGetSession(); if(session?.role!=="administrador")return;
 const panel=document.getElementById("adminUsuarios"),body=document.getElementById("usuariosBody"),search=document.getElementById("usuarioBuscar"),roleFilter=document.getElementById("usuarioRolFiltro"),modal=document.getElementById("usuarioModalOverlay"),form=document.getElementById("usuarioForm"); let users=[],auditPage=0,auditTimer,auditCache=[];
 const cambiosOverlay=document.getElementById("auditoriaCambiosModalOverlay"),cambiosTitle=document.getElementById("auditoriaCambiosTitle"),cambiosContent=document.getElementById("auditoriaCambiosContent");
 const rolSelect=document.getElementById("usuarioRol"),fieldCliente=document.getElementById("fieldUsuarioCliente"),fieldConductor=document.getElementById("fieldUsuarioConductor");
 const inputCliente=document.getElementById("usuarioCliente"),inputClienteBuscar=document.getElementById("usuarioClienteBuscar"),resultadosCliente=document.getElementById("usuarioClienteResultados");
 const inputConductor=document.getElementById("usuarioConductor"),inputConductorBuscar=document.getElementById("usuarioConductorBuscar"),resultadosConductor=document.getElementById("usuarioConductorResultados");
 // El type="email" nativo del input solo exige un "@" (acepta "a@b" sin
 // dominio real); esta regex es la misma que usan Conductores y Clientes,
 // para que "correo valido" signifique lo mismo en toda la app.
 const EMAIL_REGEX=/^[^\s@]+@[^\s@]+\.[^\s@]+$/;
 panel.hidden=false;
 const esc=(v)=>String(v??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
 const roleLabel=(r)=>({ADMINISTRADOR:"Administrador",COORDINADOR:"Coordinador",MANTENIMIENTO:"Mantenimiento",CONDUCTOR:"Conductor",SUPERVISOR:"Supervisor",CLIENTE:"Cliente"}[r]||r);
 function toast(message,error=false){let el=document.querySelector(".ui-toast");if(!el){el=document.createElement("div");el.className="ui-toast";document.body.appendChild(el);}el.textContent=message;el.classList.toggle("error",error);el.classList.add("show");clearTimeout(el._timer);el._timer=setTimeout(()=>el.classList.remove("show"),2800);}
 async function loadUsers(){body.innerHTML='<tr class="loading-row"><td colspan="5">Cargando usuarios…</td></tr>';try{users=await trailersysApiRequest("GET","/usuarios");renderUsers();}catch(e){body.innerHTML=`<tr class="loading-row"><td colspan="5">${esc(e.message)}</td></tr>`;}}
 function renderUsers(){const q=search.value.trim().toLowerCase(),role=roleFilter.value,filtered=users.filter(u=>(!role||u.rol===role)&&(!q||`${u.username} ${u.nombre} ${u.correo||""}`.toLowerCase().includes(q)));body.innerHTML=filtered.length?filtered.map(u=>`<tr><td><div class="table-primary">@${esc(u.username)}</div><div class="table-secondary">${esc(u.correo||"Sin correo")}</div></td><td>${esc(u.nombre)}</td><td><span class="badge badge-neutral">${roleLabel(u.rol)}</span></td><td><span class="badge ${u.activo?"badge-success":"badge-danger"}">${u.activo?"Activo":"Inactivo"}</span></td><td><div class="table-actions"><button class="icon-btn" data-edit-user="${u.id}" title="Editar usuario" aria-label="Editar ${esc(u.username)}"><i class="bi bi-pencil"></i></button><button class="icon-btn danger" data-delete-user="${u.id}" title="Eliminar usuario" aria-label="Eliminar ${esc(u.username)}"><i class="bi bi-trash3"></i></button></div></td></tr>`).join(""):'<tr class="loading-row"><td colspan="5">No hay usuarios que coincidan.</td></tr>';}
 // Cliente/Conductor pueden tener decenas de miles de filas reales: un
 // <select> con una lista fija es inutil ahi (y precargarla entera, lento).
 // Igual que en Viajes/Mantenimientos/Conductores, "Cliente asociado" y
 // "Conductor asociado" buscan con autocompletado contra el backend en vez
 // de precargarse (ver trailersysAutocomplete en ui-helpers.js).
 //
 // Ademas, filtran del lado del cliente a quien YA tiene una cuenta de
 // usuario (el backend igual lo rechaza al guardar, ver
 // UsuarioService.validarClienteDisponible/validarConductorDisponible) -
 // salvo el propio vinculo del usuario que se esta editando, que debe
 // seguir apareciendo aunque no se toque. editandoId se actualiza cada vez
 // que se abre el modal.
 let editandoId=null;
 const sinCuentaAjena=(campo)=>(item)=>!users.some(u=>u[campo]!=null&&String(u[campo])===String(item.id)&&u.id!==editandoId);
 const clienteAutocomplete=trailersysAutocomplete({
  input:inputClienteBuscar,hidden:inputCliente,resultados:resultadosCliente,recurso:"clientes",
  etiqueta:c=>c.nombre,detalle:c=>c.identificacion,filtro:sinCuentaAjena("clienteId"),
 });
 const conductorAutocomplete=trailersysAutocomplete({
  input:inputConductorBuscar,hidden:inputConductor,resultados:resultadosConductor,recurso:"conductores",
  etiqueta:c=>c.nombres,detalle:c=>c.identificacion,filtro:sinCuentaAjena("conductorId"),
 });
 function actualizarVisibilidadCliente(){fieldCliente.hidden=rolSelect.value!=="CLIENTE";fieldConductor.hidden=rolSelect.value!=="CONDUCTOR";}
 rolSelect.onchange=actualizarVisibilidadCliente;
 function openUser(u=null){form.reset();editandoId=u?.id??null;document.getElementById("usuarioId").value=u?.id||"";document.getElementById("usuarioModalTitle").textContent=u?"Editar usuario":"Nuevo usuario";document.getElementById("usuarioPasswordHint").textContent=u?"(opcional)":"*";clienteAutocomplete.ocultar();conductorAutocomplete.ocultar();if(u){document.getElementById("usuarioUsername").value=u.username;document.getElementById("usuarioNombre").value=u.nombre;document.getElementById("usuarioCorreo").value=u.correo||"";document.getElementById("usuarioRol").value=u.rol;document.getElementById("usuarioActivo").checked=u.activo;
  // clienteNombre/conductorNombres ya vienen denormalizados en el usuario
  // (igual que vehiculoPlaca/conductorNombres en Viaje), asi que no hace
  // falta pedir aparte /clientes/{id} ni /conductores/{id} para mostrar la
  // seleccion actual.
  inputCliente.value=u.clienteId||"";inputClienteBuscar.value=u.clienteNombre||"";inputConductor.value=u.conductorId||"";inputConductorBuscar.value=u.conductorNombres||"";
 }else{inputCliente.value="";inputClienteBuscar.value="";inputConductor.value="";inputConductorBuscar.value="";}actualizarVisibilidadCliente();trailersysOpenModal(modal);}
 const closeUser=()=>trailersysCloseModal(modal); document.getElementById("btnNuevoUsuario").onclick=()=>openUser();document.getElementById("usuarioModalClose").onclick=closeUser;document.getElementById("usuarioCancelar").onclick=closeUser;modal.onclick=e=>{if(e.target===modal)closeUser();};search.oninput=renderUsers;roleFilter.onchange=renderUsers;
 body.onclick=async e=>{const edit=e.target.closest("[data-edit-user]"),remove=e.target.closest("[data-delete-user]");if(edit){const u=users.find(x=>x.id===Number(edit.dataset.editUser));openUser(u);}if(remove){const u=users.find(x=>x.id===Number(remove.dataset.deleteUser));trailersysConfirm({title:"Eliminar usuario",text:`Se eliminará la cuenta @${u.username}. Esta acción quedará auditada.`,onAccept:async()=>{try{await trailersysApiRequest("DELETE",`/usuarios/${u.id}`);toast("Usuario eliminado.");loadUsers();}catch(err){toast(err.message,true);}}});}};
 form.onsubmit=async e=>{e.preventDefault();if(!form.reportValidity())return;const id=document.getElementById("usuarioId").value,password=document.getElementById("usuarioPassword").value,rol=document.getElementById("usuarioRol").value;if(!id&&!password){toast("La contraseña es obligatoria.",true);return;}if(password&&password.length<8){toast("La contraseña debe tener al menos 8 caracteres.",true);return;}if(rol==="CLIENTE"&&!inputCliente.value){toast("Selecciona el cliente asociado a este usuario.",true);return;}if(rol==="CONDUCTOR"&&!inputConductor.value){toast("Selecciona el conductor asociado a este usuario.",true);return;}const correo=document.getElementById("usuarioCorreo").value.trim();if(correo&&!EMAIL_REGEX.test(correo)){toast("Ingresa un correo válido.",true);return;}const payload={username:document.getElementById("usuarioUsername").value.trim(),password:password||null,nombre:document.getElementById("usuarioNombre").value.trim(),correo:correo||null,rol,activo:document.getElementById("usuarioActivo").checked,clienteId:rol==="CLIENTE"?Number(inputCliente.value):null,conductorId:rol==="CONDUCTOR"?Number(inputConductor.value):null};try{await trailersysApiRequest(id?"PUT":"POST",id?`/usuarios/${id}`:"/usuarios",payload);closeUser();toast(id?"Usuario actualizado.":"Usuario creado.");loadUsers();}catch(err){toast(err.message,true);}};
 // Antes, "Ver cambios" en un UPDATE mostraba solo datosNuevos (el estado
 // actual del registro) - eso es indistinguible de mirar la tabla en vivo,
 // y el boton pierde su sentido: no hay forma de saber que campo cambio ni
 // cual era su valor anterior, aunque el backend ya guarda ambos lados. Se
 // arma un diff campo por campo (solo los que cambiaron) en una tabla con
 // columnas "Antes"/"Despues" bien separadas y con color (rojo/verde, igual
 // criterio que las badges de INSERT/DELETE). En INSERT/DELETE no hay
 // "antes" o "despues" con que comparar, asi que se mantiene el volcado
 // JSON completo de ese unico lado.
 //
 // Este detalle se abre en un modal (igual patron que "Historial del viaje")
 // en vez del <details>/<summary> inline que tenia antes: la columna
 // "Detalle" es angosta (una de seis en la tabla), y ahi dentro una tabla de
 // 3 columnas quedaba tan comprimida que en la practica solo se alcanzaba a
 // leer el valor "Despues" - el "Antes" tecnicamente estaba pero era
 // ilegible. El modal le da el ancho que la comparacion necesita.
 const fmtValor=(v)=>v===undefined?"—":v===null?"null":typeof v==="object"?JSON.stringify(v):String(v);
 function formatCambios(a){
  if(a.operacion==="UPDATE"&&a.datosAnteriores&&a.datosNuevos){
   try{
    const antes=JSON.parse(a.datosAnteriores),despues=JSON.parse(a.datosNuevos);
    const campos=[...new Set([...Object.keys(antes),...Object.keys(despues)])].filter(k=>JSON.stringify(antes[k])!==JSON.stringify(despues[k]));
    if(!campos.length)return '<p class="audit-sin-cambios">Sin cambios en los campos registrados.</p>';
    const filas=campos.map(k=>`<tr><td class="audit-campo">${esc(k)}</td><td class="audit-antes">${esc(fmtValor(antes[k]))}</td><td class="audit-despues">${esc(fmtValor(despues[k]))}</td></tr>`).join("");
    return `<table class="audit-diff-table"><thead><tr><th>Campo</th><th>Antes</th><th>Después</th></tr></thead><tbody>${filas}</tbody></table>`;
   }catch{ /* JSON invalido o inesperado: cae al volcado crudo de abajo. */ }
  }
  const etiqueta=a.operacion==="DELETE"?"Datos eliminados":a.operacion==="INSERT"?"Datos registrados":"Detalle";
  const valor=a.datosNuevos||a.datosAnteriores;
  return `<pre class="audit-json">${valor?`${esc(etiqueta)}:\n${esc(valor)}`:"Sin detalle"}</pre>`;
 }
 async function loadAudit(page=0){auditPage=page;const auditBody=document.getElementById("auditoriaBody");auditBody.innerHTML='<tr class="loading-row"><td colspan="6">Cargando auditoría…</td></tr>';const p=new URLSearchParams({page:String(page),size:"25"}),t=document.getElementById("auditoriaTabla").value,o=document.getElementById("auditoriaOperacion").value,q=document.getElementById("auditoriaBuscar").value.trim();if(t)p.set("tabla",t);if(o)p.set("operacion",o);if(q)p.set("search",q);try{const d=await trailersysApiRequest("GET",`/auditoria?${p}`);auditCache=d.content;auditBody.innerHTML=d.content.length?d.content.map(a=>`<tr><td>${trailersysFormatDateTime(a.fechaHora)}</td><td><div class="table-primary">${esc(a.usuarioApp||a.usuarioBd)}</div><div class="table-secondary">${esc(a.usuarioBd)}</div></td><td><span class="badge ${a.operacion==='DELETE'?'badge-danger':a.operacion==='INSERT'?'badge-success':'badge-warning'}">${a.operacion}</span></td><td>${esc(a.tabla)}</td><td>#${esc(a.registroId||'—')}</td><td><button type="button" class="audit-ver-cambios" data-audit-id="${a.id}">Ver cambios</button></td></tr>`).join(""):'<tr class="loading-row"><td colspan="6">No hay operaciones registradas.</td></tr>';renderPager(d);}catch(e){auditBody.innerHTML=`<tr class="loading-row"><td colspan="6">${esc(e.message)}</td></tr>`;}}
 function renderPager(d){document.getElementById("auditoriaPager").innerHTML=`<span>${d.totalElements.toLocaleString("es-EC")} operaciones · Página ${d.page+1} de ${Math.max(1,d.totalPages)}</span><div class="pagination-actions"><button class="btn btn-ghost" id="auditPrev" ${d.page===0?'disabled':''}>Anterior</button><button class="btn btn-ghost" id="auditNext" ${d.page+1>=d.totalPages?'disabled':''}>Siguiente</button></div>`;document.getElementById("auditPrev").onclick=()=>loadAudit(auditPage-1);document.getElementById("auditNext").onclick=()=>loadAudit(auditPage+1);}
 const closeCambios=()=>trailersysCloseModal(cambiosOverlay);
 document.getElementById("auditoriaCambiosClose").onclick=closeCambios;
 document.getElementById("auditoriaCambiosCerrarBtn").onclick=closeCambios;
 cambiosOverlay.onclick=e=>{if(e.target===cambiosOverlay)closeCambios();};
 document.getElementById("auditoriaBody").onclick=e=>{const btn=e.target.closest("[data-audit-id]");if(!btn)return;const a=auditCache.find(x=>String(x.id)===btn.dataset.auditId);if(!a)return;cambiosTitle.textContent=`${a.operacion} en ${a.tabla} #${a.registroId||"—"}`;cambiosContent.innerHTML=formatCambios(a);trailersysOpenModal(cambiosOverlay);};
 document.getElementById("auditoriaTabla").onchange=()=>loadAudit(0);document.getElementById("auditoriaOperacion").onchange=()=>loadAudit(0);document.getElementById("auditoriaBuscar").oninput=()=>{clearTimeout(auditTimer);auditTimer=setTimeout(()=>loadAudit(0),350);};loadUsers();loadAudit();
})();
