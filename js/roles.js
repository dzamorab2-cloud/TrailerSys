/**
 * Catalogo de roles y permisos de modulo (seccion 6 del documento de proyecto).
 * Mientras no exista backend, esto es la unica fuente de verdad para el
 * control de acceso; al integrar PostgreSQL, el rol vendra del usuario
 * autenticado en vez de elegirse en el login.
 */
const TRAILERSYS_STORAGE_KEY = "trailersys_session";

const TRAILERSYS_ROLES = {
  administrador: {
    label: "Administrador",
    modules: [
      "dashboard", "vehiculos", "conductores", "clientes", "cargas",
      "viajes", "seguimiento", "mantenimientos", "reportes", "auditoria", "configuracion",
    ],
    manage: [
      "vehiculos", "conductores", "clientes", "cargas",
      "viajes", "seguimiento", "mantenimientos", "configuracion",
    ],
  },
  coordinador: {
    label: "Coordinador / Operador",
    modules: ["dashboard", "vehiculos", "conductores", "cargas", "viajes", "seguimiento"],
    manage: ["vehiculos", "conductores", "cargas", "viajes", "seguimiento"],
  },
  mantenimiento: {
    label: "Responsable de Mantenimiento",
    modules: ["dashboard", "vehiculos", "mantenimientos"],
    manage: ["mantenimientos"],
  },
  conductor: {
    label: "Conductor",
    modules: ["dashboard", "viajes", "seguimiento"],
    // El conductor no gestiona viajes, pero si puede registrar eventos
    // de seguimiento de su propia ruta (doc: "actualizar estados segun permisos").
    manage: ["seguimiento"],
  },
  supervisor: {
    label: "Supervisor / Consulta",
    // "seguimiento" en modules (no en manage): el supervisor consulta el
    // detalle de cada viaje ahi mismo para poder validar una entrega ya
    // confirmada por el conductor, aunque no gestiona eventos manuales.
    modules: ["dashboard", "vehiculos", "viajes", "seguimiento", "reportes"],
    manage: [],
  },
};

function trailersysCanManage(session, moduleName) {
  const roleInfo = TRAILERSYS_ROLES[session?.role];
  return Boolean(roleInfo && roleInfo.manage.includes(moduleName));
}

function trailersysGetSession() {
  const raw = sessionStorage.getItem(TRAILERSYS_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function trailersysSetSession(session) {
  sessionStorage.setItem(TRAILERSYS_STORAGE_KEY, JSON.stringify(session));
}

function trailersysClearSession() {
  sessionStorage.removeItem(TRAILERSYS_STORAGE_KEY);
}
