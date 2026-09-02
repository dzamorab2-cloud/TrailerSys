package com.trailersys.backend.usuario;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.usuario.dto.UsuarioRequest;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final ClienteRepository clienteRepository;
    private final ConductorRepository conductorRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, ClienteRepository clienteRepository,
                           ConductorRepository conductorRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.conductorRepository = conductorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listar() {
        return repository.findAll();
    }

    public Usuario obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }

    @Transactional
    public Usuario crear(UsuarioRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario.");
        }
        validarUsernameDisponible(request.username(), null);
        Usuario usuario = new Usuario(request.username().trim(), passwordEncoder.encode(request.password()),
                request.nombre().trim(), normalizar(request.correo()), request.rol());
        usuario.setActivo(request.activo());
        usuario.setCliente(resolverClienteSiAplica(request.rol(), request.clienteId(), null));
        usuario.setConductor(resolverConductorSiAplica(request.rol(), request.conductorId(), null));
        return repository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Long id, UsuarioRequest request) {
        Usuario usuario = obtener(id);
        validarUsernameDisponible(request.username(), id);
        impedirUltimoAdministrador(usuario, request.rol(), request.activo());
        usuario.setUsername(request.username().trim());
        usuario.setNombre(request.nombre().trim());
        usuario.setCorreo(normalizar(request.correo()));
        usuario.setRol(request.rol());
        usuario.setActivo(request.activo());
        usuario.setCliente(resolverClienteSiAplica(request.rol(), request.clienteId(), id));
        usuario.setConductor(resolverConductorSiAplica(request.rol(), request.conductorId(), id));
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
            // Si la cuenta estaba bloqueada por intentos fallidos (ver
            // AuthController.login()), una contraseña nueva de parte de un
            // Administrador la desbloquea de una vez - si no, quien la
            // resetea se queda sin poder entrar hasta que venza el bloqueo,
            // aun con la contraseña correcta.
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
        }
        return usuario;
    }

    /**
     * Solo los usuarios con rol CLIENTE quedan vinculados a un Cliente: para
     * cualquier otro rol se ignora el clienteId del request (se normaliza a
     * null) en vez de dejar un vinculo inconsistente con personal interno.
     * idActual es el id del propio usuario cuando se esta editando (null al
     * crear) - sin excluirlo, editar un usuario sin tocar su cliente se
     * rechazaria a si mismo como "ya vinculado".
     */
    private Cliente resolverClienteSiAplica(Rol rol, Long clienteId, Long idActual) {
        if (rol != Rol.CLIENTE) {
            return null;
        }
        if (clienteId == null) {
            throw new IllegalArgumentException("Selecciona el cliente asociado a este usuario.");
        }
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + clienteId));
        validarClienteDisponible(clienteId, idActual);
        return cliente;
    }

    /**
     * Mismo criterio que resolverClienteSiAplica(), para el rol CONDUCTOR: es
     * lo que permite que el autoservicio del conductor (Dashboard, "Mis
     * viajes") sepa a que Conductor pertenece la sesion, sin confiar en un
     * conductorId que venga de otra parte.
     */
    private Conductor resolverConductorSiAplica(Rol rol, Long conductorId, Long idActual) {
        if (rol != Rol.CONDUCTOR) {
            return null;
        }
        if (conductorId == null) {
            throw new IllegalArgumentException("Selecciona el conductor asociado a este usuario.");
        }
        Conductor conductor = conductorRepository.findById(conductorId)
                .orElseThrow(() -> new ResourceNotFoundException("Conductor no encontrado: " + conductorId));
        validarConductorDisponible(conductorId, idActual);
        return conductor;
    }

    /**
     * Sin este chequeo, el selector "Cliente asociado" (y su equivalente de
     * Conductor) no impedia elegir a alguien que YA tiene una cuenta -
     * quedaban dos usuarios distintos representando a la misma persona/
     * empresa, y el autoservicio de esa cuenta (Mis pedidos, Mis viajes)
     * quedaba ambiguo sobre cual de las dos cuentas es "la real".
     */
    private void validarClienteDisponible(Long clienteId, Long idActual) {
        repository.findByClienteId(clienteId)
                .filter(u -> !u.getId().equals(idActual))
                .ifPresent(u -> {
                    throw new ConflictException(
                            "Ese cliente ya tiene una cuenta de usuario (\"" + u.getUsername() + "\").");
                });
    }

    private void validarConductorDisponible(Long conductorId, Long idActual) {
        repository.findByConductorId(conductorId)
                .filter(u -> !u.getId().equals(idActual))
                .ifPresent(u -> {
                    throw new ConflictException(
                            "Ese conductor ya tiene una cuenta de usuario (\"" + u.getUsername() + "\").");
                });
    }

    @Transactional
    public void eliminar(Long id, String usuarioActual) {
        Usuario usuario = obtener(id);
        if (usuario.getUsername().equalsIgnoreCase(usuarioActual)) {
            throw new ConflictException("No puedes eliminar tu propia cuenta.");
        }
        impedirUltimoAdministrador(usuario, usuario.getRol(), false);
        repository.delete(usuario);
    }

    private void validarUsernameDisponible(String username, Long idActual) {
        repository.findByUsernameIgnoreCase(username.trim())
                .filter(u -> !u.getId().equals(idActual))
                .ifPresent(u -> { throw new ConflictException("El nombre de usuario ya existe."); });
    }

    private void impedirUltimoAdministrador(Usuario actual, Rol nuevoRol, boolean activo) {
        if (actual.getRol() == Rol.ADMINISTRADOR && actual.isActivo()
                && (nuevoRol != Rol.ADMINISTRADOR || !activo)
                && repository.countByRolAndActivoTrue(Rol.ADMINISTRADOR) <= 1) {
            throw new ConflictException("Debe existir al menos un administrador activo.");
        }
    }

    private String normalizar(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
