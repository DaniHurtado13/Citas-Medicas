package com.citas.medicas.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "pacientes")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JoinColumn(name = "usuario_id", unique = true, nullable = false)
    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String telefono;

    @Column(nullable = false)
    private LocalDate fechaNacimiento;

}
