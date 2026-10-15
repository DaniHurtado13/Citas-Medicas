-- Opcionales en la base: los doctores registrados antes de esta regla no tienen certificado.
-- La obligatoriedad para los doctores nuevos la aplica DoctorService.
ALTER TABLE doctores
    ADD COLUMN certificado_archivo VARCHAR(255) UNIQUE,
    ADD COLUMN certificado_nombre_original VARCHAR(255);
