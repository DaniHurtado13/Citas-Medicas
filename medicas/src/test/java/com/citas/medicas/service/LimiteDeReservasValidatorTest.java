package com.citas.medicas.service;

import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.LimiteDeReservasException;
import com.citas.medicas.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimiteDeReservasValidatorTest {

    private static final int MAXIMO = 3;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private CitaRepository citaRepository;

    @Captor
    private ArgumentCaptor<Collection<EstadoCita>> estadosCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> desdeCaptor;

    private LimiteDeReservasValidator validator;

    private final UUID pacienteId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new LimiteDeReservasValidator(citaRepository, MAXIMO, RELOJ);
    }

    private void tieneCitaConElDoctor(boolean tiene) {
        when(citaRepository.existsByPacienteIdAndDoctorIdAndEstadoInAndFechaHoraAfter(
                eq(pacienteId), eq(doctorId), any(), any())).thenReturn(tiene);
    }

    private void citasActivas(long cantidad) {
        when(citaRepository.countByPacienteIdAndEstadoInAndFechaHoraAfter(eq(pacienteId), any(), any()))
                .thenReturn(cantidad);
    }

    @Test
    void conMenosDelMaximoYSinCitaConEseDoctorPermite() {
        tieneCitaConElDoctor(false);
        citasActivas(MAXIMO - 1);

        assertDoesNotThrow(() -> validator.validar(pacienteId, doctorId));
    }

    @Test
    void alcanzarElMaximoRechaza() {
        tieneCitaConElDoctor(false);
        citasActivas(MAXIMO);

        LimiteDeReservasException ex = assertThrows(LimiteDeReservasException.class,
                () -> validator.validar(pacienteId, doctorId));
        assertTrue(ex.getMessage().contains("3 citas activas"));
    }

    @Test
    void unaCitaActivaConElMismoDoctorRechazaAunqueHayaCupoTotal() {
        tieneCitaConElDoctor(true);

        LimiteDeReservasException ex = assertThrows(LimiteDeReservasException.class,
                () -> validator.validar(pacienteId, doctorId));
        assertTrue(ex.getMessage().contains("este doctor"));
        verify(citaRepository, never()).countByPacienteIdAndEstadoInAndFechaHoraAfter(any(), any(), any());
    }

    @Test
    void soloCuentanLasCitasPendientesOConfirmadasFuturas() {
        tieneCitaConElDoctor(false);
        citasActivas(0);

        validator.validar(pacienteId, doctorId);

        verify(citaRepository).countByPacienteIdAndEstadoInAndFechaHoraAfter(
                eq(pacienteId), estadosCaptor.capture(), desdeCaptor.capture());
        assertEquals(List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA), List.copyOf(estadosCaptor.getValue()));
        // "desde" es el momento de la validación: las citas que ya pasaron no cuentan
        assertEquals(LocalDateTime.of(2026, 3, 2, 10, 0), desdeCaptor.getValue());
    }
}
