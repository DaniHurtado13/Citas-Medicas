package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Doctor;

import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String nombre,
        UUID especialidadId,
        String especialidadNombre,
        Integer duracionConsultaMin,
        String consultorio,
        boolean activo
) {

    public static DoctorResponse desde(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getNombre(),
                doctor.getEspecialidad().getId(),
                doctor.getEspecialidad().getNombre(),
                doctor.getDuracionConsultaMin(),
                doctor.getConsultorio(),
                doctor.getUsuario().getActivo()
        );
    }
}
