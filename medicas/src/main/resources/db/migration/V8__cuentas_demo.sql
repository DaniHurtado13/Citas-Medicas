-- Cuentas de demostración del portafolio: se entra a ellas sin contraseña (POST /api/auth/demo),
-- no reciben correos y en producción se recrean cada noche. Las cuentas reales siempre quedan en FALSE.
ALTER TABLE usuarios
    ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
