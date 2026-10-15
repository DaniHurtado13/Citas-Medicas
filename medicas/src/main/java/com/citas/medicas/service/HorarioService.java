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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class HorarioService {

    private final HorarioAtencionRepository horarioRepository;
    private final DoctorRepository doctorRepository;

    public HorarioService(HorarioAtencionRepository horarioRepository, DoctorRepository doctorRepository) {
        this.horarioRepository = horarioRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public List<HorarioAtencion> listarMios(UUID usuarioId) {
        Doctor doctor = buscarDoctor(usuarioId);

        return horarioRepository.findByDoctorId(doctor.getId()).stream()
                .sorted(Comparator.comparing(HorarioAtencion::getDiaSemana)
                        .thenComparing(HorarioAtencion::getHoraInicio))
                .toList();
    }

    @Transactional
    public HorarioAtencion crear(UUID usuarioId, HorarioRequest request) {
        Doctor doctor = buscarDoctor(usuarioId);
        validarRango(request);
        validarSinSolapamiento(doctor, request, null);

        HorarioAtencion horario = new HorarioAtencion();
        horario.setDoctor(doctor);
        aplicar(horario, request);
        return horarioRepository.save(horario);
    }

    @Transactional
    public HorarioAtencion actualizar(UUID usuarioId, UUID horarioId, HorarioRequest request) {
        Doctor doctor = buscarDoctor(usuarioId);
        HorarioAtencion horario = buscarHorarioDelDoctor(horarioId, doctor);
        validarRango(request);
        validarSinSolapamiento(doctor, request, horarioId);

        aplicar(horario, request);
        return horarioRepository.save(horario);
    }

    @Transactional
    public void eliminar(UUID usuarioId, UUID horarioId) {
        Doctor doctor = buscarDoctor(usuarioId);
        HorarioAtencion horario = buscarHorarioDelDoctor(horarioId, doctor);
        horarioRepository.delete(horario);
    }

    private Doctor buscarDoctor(UUID usuarioId) {
        return doctorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));
    }

    private HorarioAtencion buscarHorarioDelDoctor(UUID horarioId, Doctor doctor) {
        HorarioAtencion horario = horarioRepository.findById(horarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Horario no encontrado"));

        if (!horario.getDoctor().getId().equals(doctor.getId())) {
            throw new AccesoDenegadoException("Este horario no es tuyo");
        }
        return horario;
    }

    private void validarRango(HorarioRequest request) {
        if (!request.horaInicio().isBefore(request.horaFin())) {
            throw new FechaInvalidaException("La hora de inicio debe ser anterior a la hora de fin");
        }
    }

    private void validarSinSolapamiento(Doctor doctor, HorarioRequest request, UUID horarioQueSeEdita) {
        boolean seSolapa = horarioRepository.findByDoctorIdAndDiaSemana(doctor.getId(), request.diaSemana()).stream()
                .filter(otro -> !otro.getId().equals(horarioQueSeEdita))
                .anyMatch(otro -> request.horaInicio().isBefore(otro.getHoraFin())
                        && otro.getHoraInicio().isBefore(request.horaFin()));

        if (seSolapa) {
            throw new HorarioSolapadoException("Este bloque se cruza con otro horario del mismo día");
        }
    }

    private void aplicar(HorarioAtencion horario, HorarioRequest request) {
        horario.setDiaSemana(request.diaSemana());
        horario.setHoraInicio(request.horaInicio());
        horario.setHoraFin(request.horaFin());
    }
}
