package com.citas.medicas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "tokens_verificacion")
public class TokenVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropositoToken proposito;

    @Column(nullable = false)
    private LocalDateTime expiraEn;
}
