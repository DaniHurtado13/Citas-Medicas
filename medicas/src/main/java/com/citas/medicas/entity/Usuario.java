package com.citas.medicas.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    @Column(unique = true, nullable = false)
    private String numeroDocumento;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false, length = 60)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Rol rol;

    @Column(nullable = false)
    private Boolean activo;

    @Column(nullable = false)
    private Boolean emailVerificado;

    @Column(nullable = false)
    private Integer versionSesion = 0;

    @Column(nullable = false)
    private Boolean demo = false;

    public void invalidarSesiones() {
        versionSesion++;
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
