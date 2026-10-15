package com.citas.medicas.controller;

import com.citas.medicas.dto.request.BloqueoRequest;
import com.citas.medicas.dto.response.BloqueoResponse;
import com.citas.medicas.service.BloqueoService;
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

@Tag(name = "Bloqueos de agenda",
        description = "Periodos en que un doctor no atiende (vacaciones, ausencias). Solo doctores, cada uno los suyos.")
@RestController
@RequestMapping("/api/bloqueos")
@PreAuthorize("hasRole('DOCTOR')")
public class BloqueoController {

    private final BloqueoService bloqueoService;

    public BloqueoController(BloqueoService bloqueoService) {
        this.bloqueoService = bloqueoService;
    }

    @Operation(summary = "Ver mis bloqueos vigentes", description = "Bloqueos que no han terminado, ordenados por fecha.")
    @GetMapping
    public ResponseEntity<List<BloqueoResponse>> listarVigentes(@AuthenticationPrincipal UUID usuarioId) {
        List<BloqueoResponse> bloqueos = bloqueoService.listarVigentes(usuarioId).stream()
                .map(BloqueoResponse::desde)
                .toList();
        return ResponseEntity.ok(bloqueos);
    }

    @Operation(summary = "Bloquear un periodo",
            description = "No se permite si hay citas PENDIENTES o CONFIRMADAS en ese rango: primero hay que cancelarlas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bloqueo creado"),
            @ApiResponse(responseCode = "400", description = "Rango invertido o que ya terminó"),
            @ApiResponse(responseCode = "409", description = "Hay citas activas en el rango")
    })
    @PostMapping
    public ResponseEntity<BloqueoResponse> crear(@AuthenticationPrincipal UUID usuarioId,
                                                 @Valid @RequestBody BloqueoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BloqueoResponse.desde(bloqueoService.crear(usuarioId, request)));
    }

    @Operation(summary = "Modificar un bloqueo", description = "Solo bloqueos propios. Aplican las mismas reglas que al crear.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloqueo actualizado"),
            @ApiResponse(responseCode = "400", description = "Rango invertido o que ya terminó"),
            @ApiResponse(responseCode = "403", description = "El bloqueo no es tuyo"),
            @ApiResponse(responseCode = "404", description = "El bloqueo no existe"),
            @ApiResponse(responseCode = "409", description = "Hay citas activas en el rango")
    })
    @PutMapping("/{id}")
    public ResponseEntity<BloqueoResponse> actualizar(@AuthenticationPrincipal UUID usuarioId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody BloqueoRequest request) {
        return ResponseEntity.ok(BloqueoResponse.desde(bloqueoService.actualizar(usuarioId, id, request)));
    }

    @Operation(summary = "Eliminar un bloqueo", description = "Solo bloqueos propios. Ese periodo vuelve a quedar disponible.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bloqueo eliminado"),
            @ApiResponse(responseCode = "403", description = "El bloqueo no es tuyo"),
            @ApiResponse(responseCode = "404", description = "El bloqueo no existe")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal UUID usuarioId, @PathVariable UUID id) {
        bloqueoService.eliminar(usuarioId, id);
        return ResponseEntity.noContent().build();
    }
}
