package com.citas.medicas.service;

import com.citas.medicas.dto.request.HorarioRequest;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.HorarioAtencion;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.exception.FechaInvalidaException;
import com.citas.medicas.exception.HorarioSolapadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.HorarioAtencionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalTime;
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
class HorarioServiceTest {

    @Mock
    private HorarioAtencionRepository horarioRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private HorarioService horarioService;

    private final UUID usuarioId = UUID.randomUUID();
    private final Doctor doctor = new Doctor();

    @BeforeEach
    void setUp() {
        doctor.setId(UUID.randomUUID());
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));
        when(horarioRepository.save(any(HorarioAtencion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private HorarioAtencion horario(Doctor dueno, DayOfWeek dia, String inicio, String fin) {
        HorarioAtencion horario = new HorarioAtencion();
        horario.setId(UUID.randomUUID());
        horario.setDoctor(dueno);
        horario.setDiaSemana(dia);
        horario.setHoraInicio(LocalTime.parse(inicio));
        horario.setHoraFin(LocalTime.parse(fin));
        return horario;
    }

    private HorarioRequest lunes(String inicio, String fin) {
        return new HorarioRequest(DayOfWeek.MONDAY, LocalTime.parse(inicio), LocalTime.parse(fin));
    }

    @Test
    void listarDevuelveLosHorariosOrdenadosPorDiaYHora() {
        HorarioAtencion viernes = horario(doctor, DayOfWeek.FRIDAY, "08:00", "12:00");
        HorarioAtencion lunesTarde = horario(doctor, DayOfWeek.MONDAY, "14:00", "18:00");
        HorarioAtencion lunesManana = horario(doctor, DayOfWeek.MONDAY, "08:00", "12:00");
        when(horarioRepository.findByDoctorId(doctor.getId())).thenReturn(List.of(viernes, lunesTarde, lunesManana));

        List<HorarioAtencion> horarios = horarioService.listarMios(usuarioId);

        assertEquals(List.of(lunesManana, lunesTarde, viernes), horarios);
    }

    @Test
    void crearBloqueQueNoSeCruzaLoGuarda() {
        when(horarioRepository.findByDoctorIdAndDiaSemana(doctor.getId(), DayOfWeek.MONDAY))
                .thenReturn(List.of(horario(doctor, DayOfWeek.MONDAY, "08:00", "12:00")));

        HorarioAtencion creado = horarioService.crear(usuarioId, lunes("12:00", "16:00"));

        assertEquals(LocalTime.parse("12:00"), creado.getHoraInicio());
        assertEquals(doctor, creado.getDoctor());
    }

    @Test
    void crearBloqueQueSeCruzaLanzaExcepcion() {
        when(horarioRepository.findByDoctorIdAndDiaSemana(doctor.getId(), DayOfWeek.MONDAY))
                .thenReturn(List.of(horario(doctor, DayOfWeek.MONDAY, "08:00", "12:00")));

        assertThrows(HorarioSolapadoException.class, () -> horarioService.crear(usuarioId, lunes("10:00", "14:00")));
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void crearConInicioDespuesDelFinLanzaExcepcion() {
        assertThrows(FechaInvalidaException.class, () -> horarioService.crear(usuarioId, lunes("15:00", "14:00")));
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void editarUnBloqueNoChocaConsigoMismo() {
        HorarioAtencion existente = horario(doctor, DayOfWeek.MONDAY, "08:00", "12:00");
        when(horarioRepository.findById(existente.getId())).thenReturn(Optional.of(existente));
        when(horarioRepository.findByDoctorIdAndDiaSemana(doctor.getId(), DayOfWeek.MONDAY)).thenReturn(List.of(existente));

        HorarioAtencion editado = horarioService.actualizar(usuarioId, existente.getId(), lunes("09:00", "13:00"));

        assertEquals(LocalTime.parse("09:00"), editado.getHoraInicio());
        assertEquals(LocalTime.parse("13:00"), editado.getHoraFin());
    }

    @Test
    void editarHorarioDeOtroDoctorLanzaExcepcion() {
        Doctor otroDoctor = new Doctor();
        otroDoctor.setId(UUID.randomUUID());
        HorarioAtencion ajeno = horario(otroDoctor, DayOfWeek.MONDAY, "08:00", "12:00");
        when(horarioRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertThrows(AccesoDenegadoException.class,
                () -> horarioService.actualizar(usuarioId, ajeno.getId(), lunes("09:00", "13:00")));
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void eliminarBorraElHorarioPropio() {
        HorarioAtencion propio = horario(doctor, DayOfWeek.MONDAY, "08:00", "12:00");
        when(horarioRepository.findById(propio.getId())).thenReturn(Optional.of(propio));

        horarioService.eliminar(usuarioId, propio.getId());

        verify(horarioRepository).delete(propio);
    }

    @Test
    void eliminarHorarioInexistenteLanzaExcepcion() {
        UUID inexistente = UUID.randomUUID();
        when(horarioRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> horarioService.eliminar(usuarioId, inexistente));
    }
}
