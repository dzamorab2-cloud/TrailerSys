package com.trailersys.backend.usuario;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRolAndActivoTrue(Rol rol);

    /** Para impedir que dos cuentas de usuario queden vinculadas al mismo Cliente/Conductor (ver UsuarioService). */
    Optional<Usuario> findByClienteId(Long clienteId);

    Optional<Usuario> findByConductorId(Long conductorId);
}
