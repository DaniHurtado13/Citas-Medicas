package com.citas.medicas.controller;

import com.citas.medicas.dto.request.EspecialidadRequest;
import com.citas.medicas.dto.response.EspecialidadResponse;
import com.citas.medicas.service.EspecialidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Especialidades", description = "Catálogo de especialidades médicas")
@RestController
@RequestMapping("/api/especialidades")
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    public EspecialidadController(EspecialidadService especialidadService) {
        this.especialidadService = especialidadService;
    }

    @Operation(summary = "Listar especialidades",
            description = "Cualquier usuario autenticado. Ordenadas por nombre.")
    @GetMapping
    public ResponseEntity<List<EspecialidadResponse>> listar() {
        List<EspecialidadResponse> especialidades = especialidadService.listar().stream()
                .map(EspecialidadResponse::desde)
                .toList();
        return ResponseEntity.ok(especialidades);
    }

    @Operation(summary = "Ver una especialidad", description = "Cualquier usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Especialidad encontrada"),
            @ApiResponse(responseCode = "404", description = "La especialidad no existe")
    })
    @GetMapping("/{id}")
    public ResponseEntity<EspecialidadResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(EspecialidadResponse.desde(especialidadService.obtener(id)));
    }

    @Operation(summary = "Crear una especialidad",
            description = "Solo admin. El nombre no se puede repetir, sin importar mayúsculas.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Especialidad creada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una especialidad con ese nombre")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EspecialidadResponse> crear(@Valid @RequestBody EspecialidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EspecialidadResponse.desde(especialidadService.crear(request)));
    }

    @Operation(summary = "Actualizar una especialidad", description = "Solo admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Especialidad actualizada"),
            @ApiResponse(responseCode = "404", description = "La especialidad no existe"),
            @ApiResponse(responseCode = "409", description = "Ya existe otra especialidad con ese nombre")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<EspecialidadResponse> actualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody EspecialidadRequest request) {
        return ResponseEntity.ok(EspecialidadResponse.desde(especialidadService.actualizar(id, request)));
    }

    @Operation(summary = "Eliminar una especialidad",
            description = "Solo admin. No se puede eliminar si tiene doctores asignados.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Especialidad eliminada"),
            @ApiResponse(responseCode = "404", description = "La especialidad no existe"),
            @ApiResponse(responseCode = "409", description = "Hay doctores con esta especialidad")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        especialidadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
