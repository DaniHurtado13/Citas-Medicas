-- Datos de prueba solo para el perfil dev. Contraseña de todos los usuarios: clave123

INSERT INTO especialidades (id, nombre, descripcion) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Medicina General', 'Consulta médica general')
ON CONFLICT (id) DO NOTHING;

INSERT INTO usuarios (id, tipo_documento, numero_documento, email, password, rol, activo, email_verificado) VALUES
    ('22222222-2222-2222-2222-222222222201', 'CC', '1000000001', 'doctor@citas.dev',
     '$2a$10$WvN/dFoVKWQ4AoBsysVJY..5q680uSKM3zp6z/jFrct2QQd1dnkWm', 'DOCTOR', TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222202', 'CC', '1000000002', 'paciente@citas.dev',
     '$2a$10$WvN/dFoVKWQ4AoBsysVJY..5q680uSKM3zp6z/jFrct2QQd1dnkWm', 'PACIENTE', TRUE, TRUE),
    ('22222222-2222-2222-2222-222222222203', 'CC', '1000000003', 'admin@citas.dev',
     '$2a$10$WvN/dFoVKWQ4AoBsysVJY..5q680uSKM3zp6z/jFrct2QQd1dnkWm', 'ADMIN', TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- Son las cuentas de los botones de demo del login (también en bases de dev creadas antes de V8)
UPDATE usuarios SET demo = TRUE
WHERE id IN ('22222222-2222-2222-2222-222222222201',
             '22222222-2222-2222-2222-222222222202',
             '22222222-2222-2222-2222-222222222203');

INSERT INTO doctores (id, usuario_id, especialidad_id, nombre, duracion_consulta_min, consultorio) VALUES
    ('33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201',
     '11111111-1111-1111-1111-111111111111', 'Laura Gómez', 30, 'Consultorio 101')
ON CONFLICT (id) DO NOTHING;

INSERT INTO pacientes (id, usuario_id, nombre, telefono, fecha_nacimiento) VALUES
    ('44444444-4444-4444-4444-444444444401', '22222222-2222-2222-2222-222222222202',
     'Carlos Pérez', '3001234567', '1990-05-15')
ON CONFLICT (id) DO NOTHING;

INSERT INTO horarios_atencion (id, doctor_id, dia_semana, hora_inicio, hora_fin) VALUES
    ('55555555-5555-5555-5555-555555555501', '33333333-3333-3333-3333-333333333301', 'MONDAY',    '08:00', '12:00'),
    ('55555555-5555-5555-5555-555555555502', '33333333-3333-3333-3333-333333333301', 'TUESDAY',   '08:00', '12:00'),
    ('55555555-5555-5555-5555-555555555503', '33333333-3333-3333-3333-333333333301', 'WEDNESDAY', '08:00', '12:00'),
    ('55555555-5555-5555-5555-555555555504', '33333333-3333-3333-3333-333333333301', 'THURSDAY',  '08:00', '12:00'),
    ('55555555-5555-5555-5555-555555555505', '33333333-3333-3333-3333-333333333301', 'FRIDAY',    '08:00', '12:00')
ON CONFLICT (id) DO NOTHING;
