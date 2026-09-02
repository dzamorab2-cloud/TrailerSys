package com.trailersys.backend.config;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Session;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * database/02_auditoria_indices.sql define un trigger (trailersys_registrar_
 * auditoria) que en cada INSERT/UPDATE/DELETE de las tablas auditadas graba
 * quien hizo el cambio leyendo la variable de sesion de Postgres
 * "trailersys.usuario" - pero nada en el backend la establecia, asi que la
 * columna usuario_app de /api/auditoria siempre salia null (solo quedaba
 * usuario_bd, que es "postgres" para todas las conexiones del pool, y la IP,
 * que en desarrollo siempre es 127.0.0.1: en la practica no se podia saber
 * QUIEN hizo cada cambio).
 *
 * Se reemplaza el JpaTransactionManager por defecto por uno que, apenas
 * arranca cada transaccion (justo despues de super.doBegin(), antes de que
 * el metodo @Transactional real corra ni una sola sentencia), fija esa
 * variable con set_config(...) sobre la MISMA conexion que esa transaccion
 * va a usar - set_config(nombre, valor, true) es el equivalente
 * parametrizado de "SET LOCAL": solo dura hasta que la transaccion termina
 * (COMMIT o ROLLBACK), asi que nunca se filtra hacia otra transaccion que
 * mas tarde reutilice la misma conexion fisica del pool.
 *
 * No se engancha por AOP (@Aspect) a proposito: el orden de precedencia
 * entre un @Aspect propio y el advisor de @Transactional no esta
 * garantizado sin configurarlo a mano, y una ejecucion antes de que la
 * transaccion arranque dejaria el mismo problema de origen. Sobreescribir
 * doBegin() es el punto exacto, sin ambiguedad, donde la conexion ya quedo
 * ligada al hilo actual pero el codigo de negocio todavia no corrio nada.
 */
@Configuration
public class AuditoriaTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new AuditoriaJpaTransactionManager(entityManagerFactory);
    }

    private static final class AuditoriaJpaTransactionManager extends JpaTransactionManager {

        AuditoriaJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
            super(entityManagerFactory);
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            super.doBegin(transaction, definition);
            marcarUsuarioActual();
        }

        private void marcarUsuarioActual() {
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(getEntityManagerFactory());
            if (holder == null) {
                return;
            }
            String usuario = usuarioAutenticadoOSistema();
            Session session = holder.getEntityManager().unwrap(Session.class);
            session.doWork(connection -> {
                try (var statement = connection.prepareStatement("SELECT set_config('trailersys.usuario', ?, true)")) {
                    statement.setString(1, usuario);
                    statement.execute();
                }
            });
        }

        /**
         * "sistema" para lo que corre sin una peticion HTTP detras (el
         * scheduler de ViajeSimulacionService: paradas/llegada automaticas,
         * viajes que arrancan solos al vencer su fecha de salida) - asi la
         * auditoria distingue eso de un usuario real en vez de dejarlo en
         * blanco sin explicacion.
         */
        private String usuarioAutenticadoOSistema() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
                return "sistema";
            }
            return auth.getName();
        }
    }
}
