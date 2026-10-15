-- Las cuentas existentes se crearon antes de esta regla: quedan verificadas
ALTER TABLE usuarios
    ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;

-- Desde ahora el valor lo asigna siempre la aplicación
ALTER TABLE usuarios
    ALTER COLUMN email_verificado DROP DEFAULT;

-- Solo se guarda el hash SHA-256 del token: el token en claro viaja únicamente en el correo
CREATE TABLE tokens_verificacion (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES usuarios (id),
    hash VARCHAR(64) NOT NULL UNIQUE,
    proposito VARCHAR(255) NOT NULL,
    expira_en TIMESTAMP NOT NULL
);

CREATE INDEX idx_tokens_verificacion_usuario ON tokens_verificacion (usuario_id, proposito);
