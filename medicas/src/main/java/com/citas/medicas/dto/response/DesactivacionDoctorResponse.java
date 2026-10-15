package com.citas.medicas.dto.response;

public record DesactivacionDoctorResponse(
        DoctorResponse doctor,
        int citasCanceladas
) {
}
