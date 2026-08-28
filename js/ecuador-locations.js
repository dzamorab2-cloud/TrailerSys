/** Catálogo compartido de ciudades y puntos logísticos del Ecuador. */
const TRAILERSYS_LUGARES_ECUADOR = {
  "Azuay": ["Cuenca", "Gualaceo", "Paute"],
  "Bolívar": ["Guaranda", "San Miguel de Bolívar"],
  "Cañar": ["Azogues", "La Troncal"],
  "Carchi": ["Tulcán", "San Gabriel"],
  "Chimborazo": ["Riobamba", "Alausí"],
  "Cotopaxi": ["Latacunga", "La Maná"],
  "El Oro": ["Machala", "Huaquillas", "Santa Rosa", "Puerto Bolívar"],
  "Esmeraldas": ["Esmeraldas", "Quinindé", "San Lorenzo"],
  "Galápagos": ["Puerto Baquerizo Moreno", "Puerto Ayora"],
  "Guayas": ["Guayaquil", "Durán", "Daule", "Milagro", "Samborondón", "Playas"],
  "Imbabura": ["Ibarra", "Otavalo", "Cotacachi"],
  "Loja": ["Loja", "Catamayo", "Macará"],
  "Los Ríos": ["Babahoyo", "Quevedo", "Ventanas"],
  "Manabí": ["Portoviejo", "Manta", "Chone", "Jipijapa", "Bahía de Caráquez"],
  "Morona Santiago": ["Macas", "Gualaquiza"],
  "Napo": ["Tena", "El Chaco"],
  "Orellana": ["Puerto Francisco de Orellana", "La Joya de los Sachas"],
  "Pastaza": ["Puyo", "Mera"],
  "Pichincha": ["Quito", "Cayambe", "Machachi", "Sangolquí"],
  "Santa Elena": ["Santa Elena", "La Libertad", "Salinas"],
  "Santo Domingo de los Tsáchilas": ["Santo Domingo"],
  "Sucumbíos": ["Nueva Loja", "Shushufindi"],
  "Tungurahua": ["Ambato", "Baños de Agua Santa"],
  "Zamora Chinchipe": ["Zamora", "Yantzaza"]
};

function trailersysPoblarLugaresEcuador(select) {
  if (!select) return;
  const valorActual = select.value;
  select.innerHTML = '<option value="">Selecciona un lugar del Ecuador</option>';
  Object.entries(TRAILERSYS_LUGARES_ECUADOR).forEach(([provincia, lugares]) => {
    const grupo = document.createElement("optgroup");
    grupo.label = provincia;
    lugares.forEach((lugar) => {
      const opcion = document.createElement("option");
      opcion.value = lugar;
      opcion.textContent = lugar;
      grupo.appendChild(opcion);
    });
    select.appendChild(grupo);
  });
  if (valorActual) select.value = valorActual;
}

["cargaOrigen", "cargaDestino", "viajeOrigen", "viajeDestino", "pedidoOrigen", "pedidoDestino"]
  .forEach((id) => trailersysPoblarLugaresEcuador(document.getElementById(id)));

function trailersysLugarParaGeocodificar(lugar) {
  const texto = String(lugar || "").trim();
  return /ecuador/i.test(texto) ? texto : `${texto}, Ecuador`;
}
