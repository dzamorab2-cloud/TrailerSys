package com.trailersys.backend.usuario;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.usuario.dto.UsuarioRequest;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
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
        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return usuario;
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
