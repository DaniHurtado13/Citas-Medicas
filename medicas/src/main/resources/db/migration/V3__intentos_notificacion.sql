-- Las notificaciones existentes ya tuvieron su primer intento de envío
ALTER TABLE notificaciones
    ADD COLUMN intentos INTEGER NOT NULL DEFAULT 1;

-- Desde ahora el valor lo asigna siempre la aplicación
ALTER TABLE notificaciones
    ALTER COLUMN intentos DROP DEFAULT;
