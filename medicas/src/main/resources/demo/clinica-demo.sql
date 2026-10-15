-- Clínica de demostración del portafolio. Lo ejecuta ReinicioDemo (solo con citas.demo.habilitada=true)
-- al arrancar y cada noche, en una sola transacción: borra todo menos los administradores reales y
-- vuelve a crear la clínica de ejemplo. Las fechas se calculan desde hoy para que la demo siempre se vea al día.

DELETE FROM notificaciones;
DELETE FROM citas;
DELETE FROM bloqueos_agenda;
DELETE FROM horarios_atencion;
DELETE FROM tokens_verificacion;
DELETE FROM pacientes;
DELETE FROM doctores;
DELETE FROM especialidades;
DELETE FROM usuarios WHERE rol <> 'ADMIN' OR demo;

-- n-ésimo día hábil (lunes a viernes) contando desde hoy en Bogotá, a la hora dada: 1 es el próximo, -1 el anterior
CREATE OR REPLACE FUNCTION pg_temp.dia_habil(n INT, hora TIME) RETURNS TIMESTAMP AS $$
    SELECT dia + hora
    FROM (SELECT (now() AT TIME ZONE 'America/Bogota')::date + paso * sign(n)::int AS dia,
                 row_number() OVER (ORDER BY paso) AS orden
          FROM generate_series(1, abs(n) * 2 + 3) AS paso
          WHERE extract(ISODOW FROM (now() AT TIME ZONE 'America/Bogota')::date + paso * sign(n)::int) <= 5) AS habiles
    WHERE orden = abs(n)
$$ LANGUAGE sql;

INSERT INTO especialidades (id, nombre, descripcion) VALUES
    ('11111111-1111-1111-1111-111111111101', 'Medicina General', 'Consulta médica general, chequeos y certificados'),
    ('11111111-1111-1111-1111-111111111102', 'Pediatría', 'Atención de niños y adolescentes'),
    ('11111111-1111-1111-1111-111111111103', 'Dermatología', 'Piel, cabello y uñas'),
    ('11111111-1111-1111-1111-111111111104', 'Cardiología', 'Corazón y presión arterial');

-- Ninguna cuenta de demo tiene una contraseña conocida: se entra con POST /api/auth/demo (los botones del login),
-- así que nadie puede cambiarla. Los usuarios 201, 202 y 203 son los de esos botones (AuthService.CUENTAS_DEMO).
INSERT INTO usuarios (id, tipo_documento, numero_documento, email, password, rol, activo, email_verificado, demo) VALUES
    ('22222222-2222-2222-2222-222222222201', 'CC', '9000000001', 'laura.gomez@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'DOCTOR', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222202', 'CC', '9000000002', 'carlos.perez@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222203', 'CC', '9000000003', 'admin.demo@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'ADMIN', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222211', 'CC', '9000000011', 'andres.rojas@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'DOCTOR', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222212', 'CC', '9000000012', 'sofia.martinez@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'DOCTOR', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222213', 'CC', '9000000013', 'julian.herrera@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'DOCTOR', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222221', 'CC', '9000000021', 'maria.lopez@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222222', 'CC', '9000000022', 'juan.torres@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222223', 'CC', '9000000023', 'valentina.rios@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222224', 'CC', '9000000024', 'santiago.castro@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222225', 'CC', '9000000025', 'daniela.moreno@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222226', 'TI', '9000000026', 'camilo.vargas@example.com',
     '$2a$10$HZ.3M3W7FmDO3aD0ZDUjf.8Ac7wdff9OYsDxQN/juvkAfrudLUIeS', 'PACIENTE', TRUE, TRUE, TRUE);

INSERT INTO doctores (id, usuario_id, especialidad_id, nombre, duracion_consulta_min, consultorio) VALUES
    ('33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201',
     '11111111-1111-1111-1111-111111111101', 'Laura Gómez', 30, 'Consultorio 101'),
    ('33333333-3333-3333-3333-333333333302', '22222222-2222-2222-2222-222222222211',
     '11111111-1111-1111-1111-111111111102', 'Andrés Rojas', 20, 'Consultorio 204'),
    ('33333333-3333-3333-3333-333333333303', '22222222-2222-2222-2222-222222222212',
     '11111111-1111-1111-1111-111111111103', 'Sofía Martínez', 30, 'Consultorio 305'),
    ('33333333-3333-3333-3333-333333333304', '22222222-2222-2222-2222-222222222213',
     '11111111-1111-1111-1111-111111111104', 'Julián Herrera', 40, 'Consultorio 410');

INSERT INTO pacientes (id, usuario_id, nombre, telefono, fecha_nacimiento) VALUES
    ('44444444-4444-4444-4444-444444444401', '22222222-2222-2222-2222-222222222202', 'Carlos Pérez', '3001234567', '1990-05-15'),
    ('44444444-4444-4444-4444-444444444402', '22222222-2222-2222-2222-222222222221', 'María López', '3104567890', '1985-11-02'),
    ('44444444-4444-4444-4444-444444444403', '22222222-2222-2222-2222-222222222222', 'Juan Torres', '3157894561', '1978-03-21'),
    ('44444444-4444-4444-4444-444444444404', '22222222-2222-2222-2222-222222222223', 'Valentina Ríos', '3206543210', '1999-08-09'),
    ('44444444-4444-4444-4444-444444444405', '22222222-2222-2222-2222-222222222224', 'Santiago Castro', '3012223344', '1965-01-30'),
    ('44444444-4444-4444-4444-444444444406', '22222222-2222-2222-2222-222222222225', 'Daniela Moreno', '3168889900', '1993-12-14'),
    ('44444444-4444-4444-4444-444444444407', '22222222-2222-2222-2222-222222222226', 'Camilo Vargas', '3045556677', '2010-06-25');

INSERT INTO horarios_atencion (id, doctor_id, dia_semana, hora_inicio, hora_fin)
SELECT gen_random_uuid(), h.doctor_id::uuid, d.dia, h.inicio::time, h.fin::time
FROM (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'), ('FRIDAY')) AS d (dia)
CROSS JOIN (VALUES
    ('33333333-3333-3333-3333-333333333301', '08:00', '12:00'),
    ('33333333-3333-3333-3333-333333333301', '14:00', '17:00'),
    ('33333333-3333-3333-3333-333333333302', '08:00', '12:00'),
    ('33333333-3333-3333-3333-333333333303', '14:00', '18:00')) AS h (doctor_id, inicio, fin);

INSERT INTO horarios_atencion (id, doctor_id, dia_semana, hora_inicio, hora_fin)
SELECT gen_random_uuid(), '33333333-3333-3333-3333-333333333304', d.dia, '08:00', '12:00'
FROM (VALUES ('MONDAY'), ('WEDNESDAY'), ('FRIDAY')) AS d (dia);

INSERT INTO bloqueos_agenda (id, doctor_id, fecha_inicio, fecha_fin, motivo) VALUES
    (gen_random_uuid(), '33333333-3333-3333-3333-333333333301',
     pg_temp.dia_habil(7, '00:00'), pg_temp.dia_habil(7, '23:59'), 'Congreso de medicina familiar');

-- La agenda de Laura Gómez (la doctora de demo) tiene citas por cerrar, por confirmar y confirmadas.
-- Carlos Pérez (el paciente de demo) tiene historial y una cita próxima, y aún puede reservar con Laura.
INSERT INTO citas (id, paciente_id, doctor_id, fecha_hora, estado, motivo_consulta, version)
SELECT gen_random_uuid(), c.paciente_id::uuid, c.doctor_id::uuid, pg_temp.dia_habil(c.dia, c.hora::time), c.estado, c.motivo, 0
FROM (VALUES
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333301', -15, '09:00', 'COMPLETADA', 'Chequeo general anual'),
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333301',  -4, '10:30', 'CANCELADA',  'Dolor de garganta'),
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333303',  -6, '15:00', 'NO_ASISTIO', 'Revisión de un lunar'),
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333303',   2, '16:00', 'CONFIRMADA', 'Revisión de un lunar'),
    ('44444444-4444-4444-4444-444444444407', '33333333-3333-3333-3333-333333333301',  -5, '10:00', 'COMPLETADA', 'Certificado médico para el colegio'),
    ('44444444-4444-4444-4444-444444444406', '33333333-3333-3333-3333-333333333301',  -3, '15:00', 'CANCELADA',  'Dolor de espalda'),
    ('44444444-4444-4444-4444-444444444402', '33333333-3333-3333-3333-333333333301',  -2, '08:00', 'COMPLETADA', 'Dolor de cabeza frecuente'),
    ('44444444-4444-4444-4444-444444444403', '33333333-3333-3333-3333-333333333301',  -2, '08:30', 'NO_ASISTIO', 'Control de exámenes'),
    ('44444444-4444-4444-4444-444444444404', '33333333-3333-3333-3333-333333333301',  -1, '09:00', 'COMPLETADA', 'Gripe y fiebre'),
    ('44444444-4444-4444-4444-444444444405', '33333333-3333-3333-3333-333333333301',  -1, '14:00', 'CONFIRMADA', 'Dolor lumbar'),
    ('44444444-4444-4444-4444-444444444406', '33333333-3333-3333-3333-333333333301',   1, '08:30', 'CONFIRMADA', 'Revisión de exámenes de sangre'),
    ('44444444-4444-4444-4444-444444444402', '33333333-3333-3333-3333-333333333301',   1, '10:00', 'PENDIENTE',  'Seguimiento del dolor de cabeza'),
    ('44444444-4444-4444-4444-444444444403', '33333333-3333-3333-3333-333333333301',   1, '14:30', 'PENDIENTE',  'Control de exámenes'),
    ('44444444-4444-4444-4444-444444444404', '33333333-3333-3333-3333-333333333301',   2, '09:00', 'CONFIRMADA', 'Control después de la gripe'),
    ('44444444-4444-4444-4444-444444444407', '33333333-3333-3333-3333-333333333301',   3, '11:00', 'PENDIENTE',  'Resultados de laboratorio'))
    AS c (paciente_id, doctor_id, dia, hora, estado, motivo);
