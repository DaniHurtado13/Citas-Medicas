package com.citas.medicas.service;

import com.citas.medicas.dto.request.EspecialidadRequest;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoEnUsoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EspecialidadService {

    private final EspecialidadRepository especialidadRepository;
    private final DoctorRepository doctorRepository;

    public EspecialidadService(EspecialidadRepository especialidadRepository,
                               DoctorRepository doctorRepository) {
        this.especialidadRepository = especialidadRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public List<Especialidad> listar() {
        return especialidadRepository.findAll(Sort.by("nombre"));
    }

    @Transactional(readOnly = true)
    public Especialidad obtener(UUID id) {
        return buscar(id);
    }

    @Transactional
    public Especialidad crear(EspecialidadRequest request) {
        if (especialidadRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw new RecursoDuplicadoException("Ya existe una especialidad con ese nombre");
        }

        Especialidad especialidad = new Especialidad();
        especialidad.setNombre(request.nombre());
        especialidad.setDescripcion(request.descripcion());
        return guardar(especialidad);
    }

    @Transactional
    public Especialidad actualizar(UUID id, EspecialidadRequest request) {
        Especialidad especialidad = buscar(id);

        if (especialidadRepository.existsByNombreIgnoreCaseAndIdNot(request.nombre(), id)) {
            throw new RecursoDuplicadoException("Ya existe una especialidad con ese nombre");
        }

        especialidad.setNombre(request.nombre());
        especialidad.setDescripcion(request.descripcion());
        return guardar(especialidad);
    }

    @Transactional
    public void eliminar(UUID id) {
        Especialidad especialidad = buscar(id);

        if (doctorRepository.existsByEspecialidadId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar: hay doctores con esta especialidad");
        }

        especialidadRepository.delete(especialidad);
    }

    private Especialidad buscar(UUID id) {
        return especialidadRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada"));
    }

    private Especialidad guardar(Especialidad especialidad) {
        try {
            return especialidadRepository.saveAndFlush(especialidad);
        } catch (DataIntegrityViolationException ex) {
            throw new RecursoDuplicadoException("Ya existe una especialidad con ese nombre");
        }
    }
}
