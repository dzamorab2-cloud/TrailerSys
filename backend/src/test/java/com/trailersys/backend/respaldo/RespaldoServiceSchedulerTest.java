package com.trailersys.backend.respaldo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ejecutarProgramado() (el @Scheduled que revisa cada minuto si toca correr
 * un respaldo automatico) no se puede probar de punta a punta contra H2: el
 * camino real termina en pg_dump o en una consulta a "auditoria" (no existe
 * en H2) - mismo criterio ya aceptado en este proyecto para AuditoriaController.
 * Pero la logica de "hoy corresponde segun la frecuencia" y "ya se ejecuto
 * hoy, no dispares de nuevo" es pura y se puede aislar con un spy que
 * intercepta crearIncremental(...) antes de que llegue a tocar pg_dump o
 * auditoria - eso es lo que prueba esta clase.
 *
 * Esto cubre directamente la regresion detectada en verificacion en vivo: un
 * respaldo programado se estaba disparando en CADA tick del scheduler (cada
 * 60s) en vez de una sola vez al dia, porque el "ya se ejecuto hoy" se
 * marcaba solo en el objeto en memoria y nunca se guardaba en la base
 * (faltaba el configuracionRepository.save(...) explicito).
 */
class RespaldoServiceSchedulerTest {

    private RespaldoRepository respaldoRepository = Mockito.mock(RespaldoRepository.class);
    private ConfiguracionRespaldoRepository configuracionRepository = Mockito.mock(ConfiguracionRespaldoRepository.class);
    private JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);

    private RespaldoService nuevoServicio() {
        RespaldoService real = new RespaldoService(respaldoRepository, configuracionRepository, jdbc, new ObjectMapper(),
                "./target/backups-test", "pg_dump", "localhost", "5432", "trailersys", "postgres", "postgres");
        RespaldoService spyService = spy(real);
        // Intercepta crearIncremental(...) para que el spy nunca llegue a tocar
        // pg_dump ni a consultar "auditoria" (no existe en H2) - lo unico que
        // esta prueba verifica es la logica pura de "hoy corresponde" y
        // "ya se guardo que se ejecuto hoy", no el respaldo en si.
        doReturn(null).when(spyService).crearIncremental(anyString());
        return spyService;
    }

    private ConfiguracionRespaldo configuracion(boolean activo, FrecuenciaRespaldo frecuencia, LocalTime hora,
            DayOfWeek diaSemana, Integer diaMes, LocalDate ultimaEjecucion) {
        ConfiguracionRespaldo c = new ConfiguracionRespaldo();
        c.setActivo(activo);
        c.setFrecuencia(frecuencia);
        c.setHoraProgramada(hora);
        c.setDiaSemana(diaSemana);
        c.setDiaMes(diaMes);
        c.setUltimaEjecucionProgramada(ultimaEjecucion);
        return c;
    }

    @Test
    void noDisparaSiLaConfiguracionEstaInactiva() {
        ConfiguracionRespaldo c = configuracion(false, FrecuenciaRespaldo.DIARIO, LocalTime.MIN, null, null, null);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    void noDisparaDosVecesElMismoDia() {
        LocalDate hoy = LocalDate.now();
        ConfiguracionRespaldo c = configuracion(true, FrecuenciaRespaldo.DIARIO, LocalTime.MIN, null, null, hoy);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    void semanalNoDisparaUnDiaQueNoEsElConfigurado() {
        DayOfWeek otroDia = LocalDate.now().getDayOfWeek().plus(1);
        ConfiguracionRespaldo c = configuracion(true, FrecuenciaRespaldo.SEMANAL, LocalTime.MIN, otroDia, null, null);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    void mensualNoDisparaUnDiaQueNoEsElConfigurado() {
        int otroDia = LocalDate.now().getDayOfMonth() == 1 ? 2 : 1;
        ConfiguracionRespaldo c = configuracion(true, FrecuenciaRespaldo.MENSUAL, LocalTime.MIN, null, otroDia, null);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, never()).save(any());
    }

    @Test
    void diarioDisparaYGuardaLaFechaDeEjecucionCuandoCorresponde() {
        // Regresion directa del bug encontrado en vivo: si esto no queda
        // guardado en la base, el proximo tick (60s despues) vuelve a disparar.
        ConfiguracionRespaldo c = configuracion(true, FrecuenciaRespaldo.DIARIO, LocalTime.MIN, null, null, null);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, times(1)).save(c);
        org.junit.jupiter.api.Assertions.assertEquals(LocalDate.now(), c.getUltimaEjecucionProgramada());
    }

    @Test
    void mensualCorrigeAlUltimoDiaDelMesSiElDiaConfiguradoNoExisteEsteMes() {
        LocalDate hoy = LocalDate.now();
        // Si hoy es el ultimo dia del mes, un dia configurado que no existe
        // este mes (ej. 31 en un mes de 30 dias) debe correr HOY.
        int diaConfigurado = 31;
        boolean hoyEsUltimoDiaDelMes = hoy.getDayOfMonth() == hoy.lengthOfMonth();
        org.junit.jupiter.api.Assumptions.assumeTrue(hoyEsUltimoDiaDelMes && hoy.lengthOfMonth() < 31,
                "Esta prueba solo aplica corriendo en el ultimo dia de un mes con menos de 31 dias.");
        ConfiguracionRespaldo c = configuracion(true, FrecuenciaRespaldo.MENSUAL, LocalTime.MIN, null, diaConfigurado, null);
        when(configuracionRepository.findAll()).thenReturn(java.util.List.of(c));
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RespaldoService service = nuevoServicio();

        service.ejecutarProgramado();

        verify(configuracionRepository, times(1)).save(c);
    }
}
