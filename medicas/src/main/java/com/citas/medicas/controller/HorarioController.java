package com.citas.medicas.controller;

import com.citas.medicas.dto.request.HorarioRequest;
import com.citas.medicas.dto.response.HorarioResponse;
import com.citas.medicas.service.HorarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Horarios de atención",
        description = "Horario semanal de cada doctor. Solo doctores, y cada uno administra el suyo.")
@RestController
@RequestMapping("/api/horarios")
@PreAuthorize("hasRole('DOCTOR')")
public class HorarioController {

    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @Operation(summary = "Ver mi horario semanal", description = "Bloques ordenados de lunes a domingo y por hora.")
    @GetMapping
    public ResponseEntity<List<HorarioResponse>> listarMios(@AuthenticationPrincipal UUID usuarioId) {
        List<HorarioResponse> horarios = horarioService.listarMios(usuarioId).stream()
                .map(HorarioResponse::desde)
                .toList();
        return ResponseEntity.ok(horarios);
    }

    @Operation(summary = "Agregar un bloque de horario",
            description = "Ejemplo: MONDAY de 08:00 a 12:00. No puede cruzarse con otro bloque del mismo día; "
                    + "bloques que solo se tocan (8–12 y 12–16) sí se permiten.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bloque creado"),
            @ApiResponse(responseCode = "400", description = "La hora de inicio no es anterior a la de fin"),
            @ApiResponse(responseCode = "409", description = "Se cruza con otro bloque del mismo día")
    })
    @PostMapping
    public ResponseEntity<HorarioResponse> crear(@AuthenticationPrincipal UUID usuarioId,
                                                 @Valid @RequestBody HorarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(HorarioResponse.desde(horarioService.crear(usuarioId, request)));
    }

    @Operation(summary = "Modificar un bloque de horario", description = "Solo bloques propios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloque actualizado"),
            @ApiResponse(responseCode = "400", description = "La hora de inicio no es anterior a la de fin"),
            @ApiResponse(responseCode = "403", description = "El bloque no es tuyo"),
            @ApiResponse(responseCode = "404", description = "El bloque no existe"),
            @ApiResponse(responseCode = "409", description = "Se cruza con otro bloque del mismo día")
    })
    @PutMapping("/{id}")
    public ResponseEntity<HorarioResponse> actualizar(@AuthenticationPrincipal UUID usuarioId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody HorarioRequest request) {
        return ResponseEntity.ok(HorarioResponse.desde(horarioService.actualizar(usuarioId, id, request)));
    }

    @Operation(summary = "Eliminar un bloque de horario",
            description = "Solo bloques propios. Las citas ya reservadas en ese horario no se afectan.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bloque eliminado"),
            @ApiResponse(responseCode = "403", description = "El bloque no es tuyo"),
            @ApiResponse(responseCode = "404", description = "El bloque no existe")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
        horarioService.eliminar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
