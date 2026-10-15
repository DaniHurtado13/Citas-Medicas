package com.citas.medicas.service;

import com.citas.medicas.dto.request.EspecialidadRequest;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoEnUsoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EspecialidadServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private EspecialidadService especialidadService;

    private final UUID id = UUID.randomUUID();
    private final EspecialidadRequest request = new EspecialidadRequest("Cardiología", "Corazón y sistema circulatorio");

    private Especialidad existente() {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(id);
        especialidad.setNombre("Pediatría");
        especialidad.setDescripcion("Niños");
        return especialidad;
    }

    @Test
    void crearGuardaLaEspecialidad() {
        when(especialidadRepository.saveAndFlush(any(Especialidad.class))).thenAnswer(inv -> inv.getArgument(0));

        Especialidad creada = especialidadService.crear(request);

        assertEquals("Cardiología", creada.getNombre());
        assertEquals("Corazón y sistema circulatorio", creada.getDescripcion());
    }

    @Test
    void crearConNombreRepetidoLanzaExcepcion() {
        when(especialidadRepository.existsByNombreIgnoreCase("Cardiología")).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class, () -> especialidadService.crear(request));
        verify(especialidadRepository, never()).saveAndFlush(any());
    }

    @Test
    void crearSimultaneoConMismoNombreLanzaExcepcion() {
        when(especialidadRepository.saveAndFlush(any(Especialidad.class)))
                .thenThrow(new DataIntegrityViolationException("Violacion de UNIQUE"));

        assertThrows(RecursoDuplicadoException.class, () -> especialidadService.crear(request));
    }

    @Test
    void actualizarCambiaNombreYDescripcion() {
        Especialidad especialidad = existente();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(especialidad));
        when(especialidadRepository.saveAndFlush(especialidad)).thenReturn(especialidad);

        Especialidad actualizada = especialidadService.actualizar(id, request);

        assertEquals("Cardiología", actualizada.getNombre());
        assertEquals("Corazón y sistema circulatorio", actualizada.getDescripcion());
    }

    @Test
    void actualizarConNombreDeOtraEspecialidadLanzaExcepcion() {
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(existente()));
        when(especialidadRepository.existsByNombreIgnoreCaseAndIdNot("Cardiología", id)).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class, () -> especialidadService.actualizar(id, request));
        verify(especialidadRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizarInexistenteLanzaExcepcion() {
        when(especialidadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> especialidadService.actualizar(id, request));
    }

    @Test
    void eliminarSinDoctoresLaBorra() {
        Especialidad especialidad = existente();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(especialidad));
        when(doctorRepository.existsByEspecialidadId(id)).thenReturn(false);

        especialidadService.eliminar(id);

        verify(especialidadRepository).delete(especialidad);
    }

    @Test
    void eliminarConDoctoresLanzaExcepcion() {
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(existente()));
        when(doctorRepository.existsByEspecialidadId(id)).thenReturn(true);

        assertThrows(RecursoEnUsoException.class, () -> especialidadService.eliminar(id));
        verify(especialidadRepository, never()).delete(any());
    }
}
