-- Ana@Correo.com y ana@correo.com son la misma cuenta: los emails se guardan en minúsculas y sin espacios.
-- Si dos cuentas existentes solo se diferencian en mayúsculas, el UPDATE choca con el UNIQUE y la migración
-- se detiene: decidir cuál conservar es tarea de una persona.
UPDATE usuarios
SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

-- Red de seguridad: si algún código olvida normalizar, la base rechaza el email en vez de crear un duplicado
ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_email_normalizado CHECK (email = lower(btrim(email)));
