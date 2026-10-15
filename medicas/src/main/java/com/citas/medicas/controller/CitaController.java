package com.citas.medicas.controller;

import com.citas.medicas.dto.request.ReprogramarCitaRequest;
import com.citas.medicas.dto.request.ReservarCitaRequest;
import com.citas.medicas.dto.response.CitaResponse;
import com.citas.medicas.dto.response.PaginaResponse;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.service.CitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Citas", description = "Reservar, confirmar, cancelar, reprogramar, completar y consultar citas")
@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final CitaService citaService;

    public CitaController(CitaService citaService) {
        this.citaService = citaService;
    }

    @Operation(summary = "Reservar una cita",
            description = "Solo pacientes. El paciente sale del token. La hora debe estar en la disponibilidad del doctor. "
                    + "La cita nace en estado PENDIENTE. Un paciente puede tener como máximo 3 citas activas "
                    + "(pendientes o confirmadas, futuras) y solo una con cada doctor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cita reservada"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o fecha en el pasado"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe"),
            @ApiResponse(responseCode = "409", description = "El horario no está disponible, lo tomó otro paciente, "
                    + "o el paciente alcanzó su límite de citas activas")
    })
    @PreAuthorize("hasRole('PACIENTE')")
    @PostMapping
    public ResponseEntity<CitaResponse> reservarCita(@AuthenticationPrincipal UUID usuarioId,
                                                     @Valid @RequestBody ReservarCitaRequest request) {
        Cita cita = citaService.reservarCita(
                usuarioId,
                request.doctorId(),
                request.fechaHora(),
                request.motivoConsulta()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(CitaResponse.desde(cita));
    }



    @Operation(summary = "Confirmar una cita",
            description = "Solo el doctor de la cita. Pasa de PENDIENTE a CONFIRMADA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita confirmada"),
            @ApiResponse(responseCode = "403", description = "La cita no pertenece a tu agenda"),
            @ApiResponse(responseCode = "409", description = "La cita no está PENDIENTE")
    })
    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<CitaResponse> confirmarCita(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UUID usuarioId) {
        Cita citaConfirmada = citaService.confirmarCita(id, usuarioId);
        return ResponseEntity.ok(CitaResponse.desde(citaConfirmada));
    }



    @Operation(summary = "Marcar una cita como completada",
            description = "Solo el doctor de la cita, y solo cuando su hora ya pasó. Pasa de CONFIRMADA a COMPLETADA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita completada"),
            @ApiResponse(responseCode = "403", description = "La cita no pertenece a tu agenda"),
            @ApiResponse(responseCode = "409", description = "La cita no está CONFIRMADA o aún no ha ocurrido")
    })
    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/{id}/completar")
    public ResponseEntity<CitaResponse> completarCita(@PathVariable UUID id,
                                                      @AuthenticationPrincipal UUID usuarioId) {
        Cita citaCompletada = citaService.completarCita(id, usuarioId);
        return ResponseEntity.ok(CitaResponse.desde(citaCompletada));
    }



    @Operation(summary = "Marcar que el paciente no asistió",
            description = "Solo el doctor de la cita, y solo cuando su hora ya pasó. Pasa de CONFIRMADA a NO_ASISTIO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita marcada como no asistida"),
            @ApiResponse(responseCode = "403", description = "La cita no pertenece a tu agenda"),
            @ApiResponse(responseCode = "409", description = "La cita no está CONFIRMADA o aún no ha ocurrido")
    })
    @PreAuthorize("hasRole('DOCTOR')")
    @PatchMapping("/{id}/no-asistio")
    public ResponseEntity<CitaResponse> marcarNoAsistio(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UUID usuarioId) {
        Cita citaNoAsistida = citaService.marcarNoAsistio(id, usuarioId);
        return ResponseEntity.ok(CitaResponse.desde(citaNoAsistida));
    }



    @Operation(summary = "Cancelar una cita",
            description = "El paciente de la cita puede cancelarla si está CONFIRMADA y faltan al menos las horas mínimas "
                    + "de anticipación. El doctor de la cita puede cancelarla en cualquier momento, incluso si está PENDIENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cita cancelada"),
            @ApiResponse(responseCode = "403", description = "La cita no te pertenece"),
            @ApiResponse(responseCode = "409", description = "Estado no cancelable o fuera de la ventana de cancelación")
    })
    @PreAuthorize("hasAnyRole('PACIENTE', 'DOCTOR')")
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarCita(@PathVariable UUID id,
                                             @AuthenticationPrincipal UUID usuarioId) {
        citaService.cancelarCita(id, usuarioId);
        return ResponseEntity.noContent().build();
    }



    @Operation(summary = "Reprogramar una cita",
            description = "Solo el paciente de la cita. Cancela la original y crea una nueva enlazada a ella, en estado "
                    + "PENDIENTE. Aplica la ventana de cancelación y la disponibilidad del doctor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cita nueva creada; devuelve la nueva"),
            @ApiResponse(responseCode = "400", description = "Fecha inválida o en el pasado"),
            @ApiResponse(responseCode = "403", description = "La cita no te pertenece"),
            @ApiResponse(responseCode = "409", description = "Estado no válido, fuera de la ventana u horario no disponible")
    })
    @PreAuthorize("hasRole('PACIENTE')")
    @PostMapping("/{id}/reprogramacion")
    public ResponseEntity<CitaResponse> reprogramarCita(@PathVariable UUID id,
                                                        @AuthenticationPrincipal UUID usuarioId,
                                                        @Valid @RequestBody ReprogramarCitaRequest request) {
        Cita nuevaCita = citaService.reprogramarCita(id, request.nuevaFechaHora(), usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CitaResponse.desde(nuevaCita));
    }



    @Operation(summary = "Ver mis citas",
            description = "Solo pacientes. Historial paginado de las citas del paciente del token. "
                    + "Se puede ordenar por fechaHora o estado; por defecto, de la más reciente a la más antigua.")
    @PreAuthorize("hasRole('PACIENTE')")
    @GetMapping("/mias")
    public ResponseEntity<PaginaResponse<CitaResponse>> listarMisCitas(
            @AuthenticationPrincipal UUID usuarioId,
            @ParameterObject
            @PageableDefault(size = 10, sort = "fechaHora", direction = Sort.Direction.DESC) Pageable pageable) {

        ValidadorOrdenamiento.validar(pageable, List.of("fechaHora", "estado"));
        Page<CitaResponse> pagina = citaService.listarCitasPaciente(usuarioId, pageable)
                .map(CitaResponse::desde);
        return ResponseEntity.ok(PaginaResponse.desde(pagina));
    }



    @Operation(summary = "Ver mi agenda",
            description = "Solo doctores. Citas no canceladas del doctor del token en un rango de fechas (máximo 31 días), "
                    + "ordenadas por hora.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Citas del rango"),
            @ApiResponse(responseCode = "400", description = "Rango invertido o de más de 31 días")
    })
    @PreAuthorize("hasRole('DOCTOR')")
    @GetMapping("/agenda")
    public ResponseEntity<List<CitaResponse>> consultarAgenda(
            @AuthenticationPrincipal UUID usuarioId,
            @Parameter(description = "Primer día, formato AAAA-MM-DD", example = "2026-10-05")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día, formato AAAA-MM-DD", example = "2026-10-09")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        List<CitaResponse> agenda = citaService.listarAgendaDoctor(usuarioId, desde, hasta).stream()
                .map(CitaResponse::desde)
                .toList();
        return ResponseEntity.ok(agenda);
    }
}
