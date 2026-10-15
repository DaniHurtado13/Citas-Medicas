package com.citas.medicas.service;

import com.citas.medicas.dto.request.BloqueoRequest;
import com.citas.medicas.entity.BloqueoAgenda;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.exception.FechaInvalidaException;
import com.citas.medicas.exception.HorarioSolapadoException;
import com.citas.medicas.repository.BloqueoAgendaRepository;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloqueoServiceTest {

    @Mock
    private BloqueoAgendaRepository bloqueoRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private CitaRepository citaRepository;

    private BloqueoService bloqueoService;

    @BeforeEach
    void crearServicio() {
        bloqueoService = new BloqueoService(bloqueoRepository, doctorRepository, citaRepository, Clock.systemDefaultZone());
    }

    private final UUID usuarioId = UUID.randomUUID();
    private final Doctor doctor = new Doctor();
    private final LocalDateTime lunesOcho = LocalDateTime.now().plusDays(7).withHour(8).withMinute(0).withSecond(0).withNano(0);
    private final BloqueoRequest manana = new BloqueoRequest(lunesOcho, lunesOcho.plusHours(4), "Congreso");

    @BeforeEach
    void setUp() {
        doctor.setId(UUID.randomUUID());
        doctor.setDuracionConsultaMin(30);
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));
        when(bloqueoRepository.save(any(BloqueoAgenda.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void citasExistentes(Cita... citas) {
        when(citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNot(any(), any(), any(), any()))
                .thenReturn(List.of(citas));
    }

    private Cita cita(LocalDateTime fechaHora, EstadoCita estado) {
        Cita cita = new Cita();
        cita.setFechaHora(fechaHora);
        cita.setEstado(estado);
        return cita;
    }

    @Test
    void crearSinCitasEnElRangoLoGuarda() {
        citasExistentes();

        BloqueoAgenda bloqueo = bloqueoService.crear(usuarioId, manana);

        assertEquals(doctor, bloqueo.getDoctor());
        assertEquals("Congreso", bloqueo.getMotivo());
    }

    @Test
    void crearConUnaCitaConfirmadaDentroLanzaExcepcion() {
        citasExistentes(cita(lunesOcho.plusHours(1), EstadoCita.CONFIRMADA));

        assertThrows(HorarioSolapadoException.class, () -> bloqueoService.crear(usuarioId, manana));
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void citaQueEmpiezaAntesYTerminaDentroTambienCuenta() {
        citasExistentes(cita(lunesOcho.minusMinutes(15), EstadoCita.PENDIENTE));

        assertThrows(HorarioSolapadoException.class, () -> bloqueoService.crear(usuarioId, manana));
    }

    @Test
    void citaQueTerminaJustoCuandoEmpiezaElBloqueoNoCuenta() {
        citasExistentes(cita(lunesOcho.minusMinutes(30), EstadoCita.CONFIRMADA));

        BloqueoAgenda bloqueo = bloqueoService.crear(usuarioId, manana);

        assertEquals(lunesOcho, bloqueo.getFechaInicio());
    }

    @Test
    void citasYaCompletadasNoImpidenElBloqueo() {
        citasExistentes(cita(lunesOcho.plusHours(1), EstadoCita.COMPLETADA));

        BloqueoAgenda bloqueo = bloqueoService.crear(usuarioId, manana);

        assertEquals(lunesOcho, bloqueo.getFechaInicio());
    }

    @Test
    void crearConRangoInvertidoLanzaExcepcion() {
        BloqueoRequest invertido = new BloqueoRequest(lunesOcho.plusHours(4), lunesOcho, null);

        assertThrows(FechaInvalidaException.class, () -> bloqueoService.crear(usuarioId, invertido));
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearUnBloqueoQueYaTerminoLanzaExcepcion() {
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        BloqueoRequest pasado = new BloqueoRequest(ayer, ayer.plusHours(2), null);

        assertThrows(FechaInvalidaException.class, () -> bloqueoService.crear(usuarioId, pasado));
    }

    @Test
    void editarBloqueoDeOtroDoctorLanzaExcepcion() {
        Doctor otro = new Doctor();
        otro.setId(UUID.randomUUID());
        BloqueoAgenda ajeno = new BloqueoAgenda();
        ajeno.setId(UUID.randomUUID());
        ajeno.setDoctor(otro);
        when(bloqueoRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThrows(AccesoDenegadoException.class, () -> bloqueoService.actualizar(usuarioId, ajeno.getId(), manana));
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void eliminarBorraElBloqueoPropio() {
        BloqueoAgenda propio = new BloqueoAgenda();
        propio.setId(UUID.randomUUID());
        propio.setDoctor(doctor);
        when(bloqueoRepository.findById(propio.getId())).thenReturn(Optional.of(propio));

        bloqueoService.eliminar(usuarioId, propio.getId());

        verify(bloqueoRepository).delete(propio);
    }
}
