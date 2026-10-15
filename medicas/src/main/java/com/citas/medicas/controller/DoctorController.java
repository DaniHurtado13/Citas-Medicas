package com.citas.medicas.controller;

import com.citas.medicas.almacenamiento.ArchivoDescarga;
import com.citas.medicas.dto.request.ActualizarDoctorRequest;
import com.citas.medicas.dto.request.CrearDoctorRequest;
import com.citas.medicas.dto.response.DesactivacionDoctorResponse;
import com.citas.medicas.dto.response.DoctorResponse;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.dto.response.PaginaResponse;
import com.citas.medicas.service.AutorizacionService;
import com.citas.medicas.service.DisponibilidadService;
import com.citas.medicas.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Doctores", description = "Listado, administración y disponibilidad de los doctores")
@RestController
@RequestMapping("/api/doctores")
public class DoctorController {

    private final DisponibilidadService disponibilidadService;
    private final AutorizacionService autorizacionService;
    private final DoctorService doctorService;

    public DoctorController(DisponibilidadService disponibilidadService,
                            AutorizacionService autorizacionService,
                            DoctorService doctorService) {
        this.disponibilidadService = disponibilidadService;
        this.autorizacionService = autorizacionService;
        this.doctorService = doctorService;
    }

    @Operation(summary = "Listar doctores",
            description = "Cualquier usuario que este autenticado. Por defecto solo los doctores activos. " +
                    "Paginado; se puede filtrar por especialidad y ordenar por nombre, duracionConsultaMin o consultorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de doctores"),
            @ApiResponse(responseCode = "403", description = "Se pidieron los inactivos sin ser admin")
    })
    @GetMapping
    public ResponseEntity<PaginaResponse<DoctorResponse>> listar(
            Authentication autenticacion,
            @Parameter(description = "Opcional: solo los doctores de esta especialidad")
            @RequestParam(required = false) UUID especialidadId,
            @Parameter(description = "Solo admin: incluir también a los doctores desactivados")
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            @ParameterObject @PageableDefault(size = 20, sort = "nombre") Pageable pageable) {

        if (incluirInactivos && !esAdmin(autenticacion)) {
            throw new AccesoDenegadoException("Solo el admin puede ver los doctores inactivos");
        }
        ValidadorOrdenamiento.validar(pageable, List.of("nombre", "duracionConsultaMin", "consultorio"));
        return ResponseEntity.ok(PaginaResponse.desde(
                doctorService.listar(especialidadId, incluirInactivos, pageable).map(DoctorResponse::desde)));
    }

    private boolean esAdmin(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMIN"));
    }

    @Operation(summary = "Desactivar un doctor",
            description = "Solo admin. El doctor no puede iniciar sesión, desaparece de los listados y no recibe "
                    + "reservas. Sus citas futuras (pendientes o confirmadas) se cancelan y cada paciente recibe un "
                    + "correo. Las citas pasadas se conservan.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctor desactivado, con la cantidad de citas canceladas"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<DesactivacionDoctorResponse> desactivar(@PathVariable UUID id) {
        DoctorService.Desactivacion resultado = doctorService.desactivar(id);
        return ResponseEntity.ok(new DesactivacionDoctorResponse(
                DoctorResponse.desde(resultado.doctor()), resultado.citasCanceladas()));
    }

    @Operation(summary = "Reactivar un doctor",
            description = "Solo admin. Vuelve a poder iniciar sesión, aparece en los listados y recibe reservas. "
                    + "Las citas que se cancelaron al desactivarlo no se restauran.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctor activo"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<DoctorResponse> activar(@PathVariable UUID id) {
        return ResponseEntity.ok(DoctorResponse.desde(doctorService.activar(id)));
    }

    @Operation(summary = "Ver un doctor", description = "Cualquier usuario autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctor encontrado"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(DoctorResponse.desde(doctorService.obtener(id)));
    }

    @Operation(summary = "Crear un doctor",
            description = "Solo admin. Crea también su cuenta de usuario con rol DOCTOR y una contraseña provisional, "
                    + "que el doctor debe cambiar después. Exige adjuntar su título o certificado profesional "
                    + "(PDF, JPG o PNG, máximo 5 MB); se envía como multipart/form-data.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Doctor creado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, o certificado ausente o con formato no permitido"),
            @ApiResponse(responseCode = "404", description = "La especialidad no existe"),
            @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese email o documento"),
            @ApiResponse(responseCode = "413", description = "El certificado supera el tamaño máximo")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DoctorResponse> crear(@Valid @ModelAttribute CrearDoctorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(DoctorResponse.desde(doctorService.crear(request)));
    }

    @Operation(summary = "Ver el certificado de un doctor",
            description = "Solo admin. Devuelve el archivo para mostrarlo en el navegador (Content-Disposition: inline).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "El archivo del certificado"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe o no tiene certificado registrado")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/certificado")
    public ResponseEntity<Resource> verCertificado(@PathVariable UUID id) {
        ArchivoDescarga certificado = doctorService.obtenerCertificado(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(certificado.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(certificado.nombre(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(certificado.contenido());
    }

    @Operation(summary = "Actualizar un doctor",
            description = "Solo admin. Cambia nombre, especialidad, duración de consulta y consultorio.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctor actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "El doctor o la especialidad no existen")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponse> actualizar(@PathVariable UUID id,
                                                     @Valid @RequestBody ActualizarDoctorRequest request) {
        return ResponseEntity.ok(DoctorResponse.desde(doctorService.actualizar(id, request)));
    }

    @Operation(summary = "Consultar disponibilidad",
            description = "Horarios libres de un doctor en una fecha: su horario de atención menos bloqueos y citas "
                    + "tomadas. Pacientes y admin pueden ver la de cualquier doctor; un doctor, solo la suya.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de horarios libres"),
            @ApiResponse(responseCode = "403", description = "Un doctor intentó ver la agenda de otro"),
            @ApiResponse(responseCode = "404", description = "El doctor no existe")
    })
    @PreAuthorize("hasAnyRole('PACIENTE', 'DOCTOR', 'ADMIN')")
    @GetMapping("/{doctorId}/disponibilidad")
    public ResponseEntity<List<LocalDateTime>> consultarDisponibilidad(
            @AuthenticationPrincipal UUID usuarioId,
            @PathVariable UUID doctorId,
            @Parameter(description = "Fecha a consultar, formato AAAA-MM-DD", example = "2026-10-05")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        autorizacionService.validarAccesoAAgenda(usuarioId, doctorId);
        return ResponseEntity.ok(disponibilidadService.calcularDisponibilidad(doctorId, fecha));
    }
}
