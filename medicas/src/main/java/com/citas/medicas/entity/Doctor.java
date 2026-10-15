package com.citas.medicas.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "doctores")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private Integer duracionConsultaMin;

    @Column(nullable = false)
    private String consultorio;

    @Column(unique = true)
    private String certificadoArchivo;

    private String certificadoNombreOriginal;
}
