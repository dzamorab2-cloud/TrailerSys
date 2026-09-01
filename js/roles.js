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
      "viajes", "seguimiento", "mantenimientos", "reportes", "guias", "reclamos", "auditoria", "configuracion",
    ],
    manage: [
      "vehiculos", "conductores", "clientes", "cargas",
      "viajes", "seguimiento", "mantenimientos", "reclamos", "configuracion",
    ],
  },
  coordinador: {
    label: "Coordinador / Operador",
    modules: ["dashboard", "vehiculos", "conductores", "cargas", "viajes", "seguimiento", "guias", "reclamos"],
    manage: ["vehiculos", "conductores", "cargas", "viajes", "seguimiento", "reclamos"],
  },
  mantenimiento: {
    label: "Responsable de Mantenimiento",
    modules: ["dashboard", "vehiculos", "mantenimientos"],
    manage: ["mantenimientos"],
  },
  // Autoservicio: el conductor tiene su propio Dashboard (su perfil, su
  // vehiculo asignado, sus graficas) y "Mis viajes" (su propio historial,
  // con mapa/guia/confirmar llegada) en vez del Dashboard generico de la
  // operacion y los modulos internos "Viajes"/"Seguimiento", que exponian
  // el resto de la flota y una bitacora manual que no le corresponden.
  conductor: {
    label: "Conductor",
    modules: ["dashboard", "mis-viajes"],
    manage: [],
  },
  supervisor: {
    label: "Supervisor / Consulta",
    // "seguimiento" en modules (no en manage): el supervisor consulta el
    // detalle de cada viaje ahi mismo para poder validar una entrega ya
    // confirmada por el conductor, aunque no gestiona eventos manuales.
    modules: ["dashboard", "vehiculos", "viajes", "seguimiento", "reportes"],
    manage: [],
  },
  // Autoservicio: el cliente solo ve "Mis pedidos" (crear pedidos = Cargas
  // Pendientes a su propio nombre, y confirmar la recepcion de las que ya
  // se entregaron). Nunca el resto de la operacion interna.
  cliente: {
    label: "Cliente",
    modules: ["pedidos", "configuracion"],
    manage: ["pedidos"],
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
