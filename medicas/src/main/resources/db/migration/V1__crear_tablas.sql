CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    tipo_documento VARCHAR(255) NOT NULL,
    numero_documento VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(60)  NOT NULL,
    rol VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL
);

CREATE TABLE pacientes (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios (id),
    nombre VARCHAR(255) NOT NULL,
    telefono VARCHAR(10) NOT NULL,
    fecha_nacimiento DATE NOT NULL
);

CREATE TABLE especialidades (
    id UUID PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL UNIQUE,
    descripcion VARCHAR(500) NOT NULL
);

CREATE TABLE doctores (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios (id),
    especialidad_id UUID NOT NULL REFERENCES especialidades (id),
    nombre VARCHAR(255) NOT NULL,
    duracion_consulta_min INTEGER NOT NULL,
    consultorio VARCHAR(255) NOT NULL
);

CREATE TABLE horarios_atencion (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctores (id),
    dia_semana VARCHAR(255) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL
);

CREATE TABLE bloqueos_agenda (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctores (id),
    fecha_inicio TIMESTAMP NOT NULL,
    fecha_fin TIMESTAMP NOT NULL,
    motivo VARCHAR(255)
);

CREATE TABLE citas (
    id UUID PRIMARY KEY,
    paciente_id UUID NOT NULL REFERENCES pacientes (id),
    doctor_id UUID NOT NULL REFERENCES doctores (id),
    cita_origen_id  UUID UNIQUE REFERENCES citas (id),
    fecha_hora TIMESTAMP NOT NULL,
    estado VARCHAR(255) NOT NULL,
    motivo_consulta VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL
);

CREATE TABLE notificaciones (
    id UUID PRIMARY KEY,
    cita_id UUID NOT NULL REFERENCES citas (id),
    tipo VARCHAR(255) NOT NULL,
    estado VARCHAR(255) NOT NULL,
    fecha_programada TIMESTAMP NOT NULL
);
