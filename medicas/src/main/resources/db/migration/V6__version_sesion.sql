-- Viaja dentro del JWT. Al subir, todos los tokens emitidos antes dejan de servir.
-- El 0 es solo el punto de partida del contador, por eso conserva el valor por defecto.
ALTER TABLE usuarios
    ADD COLUMN version_sesion INTEGER NOT NULL DEFAULT 0;
