CREATE UNIQUE INDEX uk_cita_doctor_fecha_activa
    ON citas (doctor_id, fecha_hora)
    WHERE estado <> 'CANCELADA';
