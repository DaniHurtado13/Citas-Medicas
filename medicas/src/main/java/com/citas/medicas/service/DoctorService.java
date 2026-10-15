package com.citas.medicas.service;

import com.citas.medicas.almacenamiento.AlmacenamientoService;
import com.citas.medicas.almacenamiento.ArchivoDescarga;
import com.citas.medicas.almacenamiento.ArchivoValidado;
import com.citas.medicas.dto.request.ActualizarDoctorRequest;
import com.citas.medicas.dto.request.CrearDoctorRequest;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final EspecialidadRepository especialidadRepository;
    private final UsuarioService usuarioService;
    private final AlmacenamientoService almacenamientoService;
    private final CitaService citaService;

    public DoctorService(DoctorRepository doctorRepository,
                         EspecialidadRepository especialidadRepository,
                         UsuarioService usuarioService,
                         AlmacenamientoService almacenamientoService,
                         CitaService citaService) {
        this.doctorRepository = doctorRepository;
        this.especialidadRepository = especialidadRepository;
        this.usuarioService = usuarioService;
        this.almacenamientoService = almacenamientoService;
        this.citaService = citaService;
    }

    public record Desactivacion(Doctor doctor, int citasCanceladas) {
    }

    @Transactional(readOnly = true)
    public Page<Doctor> listar(UUID especialidadId, boolean incluirInactivos, Pageable pageable) {
        if (incluirInactivos) {
            return especialidadId == null
                    ? doctorRepository.findAllBy(pageable)
                    : doctorRepository.findByEspecialidadId(especialidadId, pageable);
        }
        return especialidadId == null
                ? doctorRepository.findByUsuarioActivoTrue(pageable)
                : doctorRepository.findByEspecialidadIdAndUsuarioActivoTrue(especialidadId, pageable);
    }

    /**
     * El doctor deja de poder iniciar sesión, desaparece de los listados y no recibe reservas.
     * Sus citas futuras se cancelan y cada paciente recibe un aviso. El historial se conserva.
     */
    @Transactional
    public Desactivacion desactivar(UUID id) {
        Doctor doctor = buscar(id);

        int citasCanceladas = citaService.cancelarCitasFuturasDelDoctor(doctor.getId());
        doctor.getUsuario().setActivo(false);
        // Así, al reactivarlo, los tokens de antes no vuelven a servir
        doctor.getUsuario().invalidarSesiones();

        return new Desactivacion(doctor, citasCanceladas);
    }

    @Transactional
    public Doctor activar(UUID id) {
        Doctor doctor = buscar(id);
        doctor.getUsuario().setActivo(true);
        return doctor;
    }

    @Transactional(readOnly = true)
    public Doctor obtener(UUID id) {
        return buscar(id);
    }

    @Transactional
    public Doctor crear(CrearDoctorRequest request) {
        // Se valida el archivo antes de tocar la base: si es inválido, no hay nada que deshacer
        ArchivoValidado certificado = almacenamientoService.validar(request.certificado());
        Especialidad especialidad = buscarEspecialidad(request.especialidadId());

        Usuario usuario = usuarioService.crearUsuario(request.tipoDocumento(), request.numeroDocumento(),
                request.email(), request.passwordProvisional(), Rol.DOCTOR);

        Doctor doctor = new Doctor();
        doctor.setUsuario(usuario);
        doctor.setEspecialidad(especialidad);
        doctor.setNombre(request.nombre());
        doctor.setDuracionConsultaMin(request.duracionConsultaMin());
        doctor.setConsultorio(request.consultorio());
        doctor.setCertificadoArchivo(certificado.nombreAlmacenado());
        doctor.setCertificadoNombreOriginal(certificado.nombreOriginal());

        // saveAndFlush ejecuta el INSERT ahora: un error de la base aparece antes de escribir en disco
        doctorRepository.saveAndFlush(doctor);
        almacenamientoService.escribir(certificado);

        return doctor;
    }

    @Transactional(readOnly = true)
    public ArchivoDescarga obtenerCertificado(UUID id) {
        Doctor doctor = buscar(id);
        if (doctor.getCertificadoArchivo() == null) {
            throw new RecursoNoEncontradoException("Este doctor no tiene certificado registrado");
        }

        return new ArchivoDescarga(
                almacenamientoService.cargar(doctor.getCertificadoArchivo()),
                doctor.getCertificadoNombreOriginal(),
                almacenamientoService.tipoContenido(doctor.getCertificadoArchivo()));
    }

    @Transactional
    public Doctor actualizar(UUID id, ActualizarDoctorRequest request) {
        Doctor doctor = buscar(id);
        Especialidad especialidad = buscarEspecialidad(request.especialidadId());

        doctor.setNombre(request.nombre());
        doctor.setEspecialidad(especialidad);
        doctor.setDuracionConsultaMin(request.duracionConsultaMin());
        doctor.setConsultorio(request.consultorio());

        return doctorRepository.save(doctor);
    }

    private Doctor buscar(UUID id) {
        return doctorRepository.findConEspecialidadById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));
    }

    private Especialidad buscarEspecialidad(UUID especialidadId) {
        return especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada"));
    }
}
