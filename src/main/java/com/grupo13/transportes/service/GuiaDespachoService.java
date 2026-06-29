package com.grupo13.transportes.service;

import com.grupo13.transportes.dto.ActualizarGuiaRequest;
import com.grupo13.transportes.dto.CrearGuiaRequest;
import com.grupo13.transportes.entity.EstadoGuia;
import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.exception.GuiaNoEncontradaException;
import com.grupo13.transportes.repository.GuiaDespachoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Orquesta el ciclo de vida de una guia:
 * genera PDF -> EFS (temporal) -> S3 (final) -> descarga -> actualizacion -> eliminacion.
 *
 * La autorizacion de acceso (roles) se aplica en la capa de seguridad
 * (SecurityConfig), no aqui.
 */
@Slf4j
@Service
public class GuiaDespachoService {

    private final GuiaDespachoRepository repository;
    private final PdfGuiaService pdfService;
    private final EfsStorageService efsService;
    private final S3StorageService s3Service;

    public GuiaDespachoService(GuiaDespachoRepository repository,
                               PdfGuiaService pdfService,
                               EfsStorageService efsService,
                               S3StorageService s3Service) {
        this.repository = repository;
        this.pdfService = pdfService;
        this.efsService = efsService;
        this.s3Service = s3Service;
    }

    /** 1. Crea la guia, genera el PDF y lo guarda TEMPORALMENTE en EFS. */
    @Transactional
    public GuiaDespacho crearGuia(CrearGuiaRequest req, String creadoPor) {
        GuiaDespacho guia = GuiaDespacho.builder()
                .numeroGuia(generarNumeroGuia())
                .transportista(req.transportista())
                .rutTransportista(req.rutTransportista())
                .cliente(req.cliente())
                .direccionOrigen(req.direccionOrigen())
                .direccionDestino(req.direccionDestino())
                .fecha(LocalDate.now())
                .estado(EstadoGuia.GENERADA)
                .creadoPor(StringUtils.hasText(creadoPor) ? creadoPor : "sistema")
                .build();

        guia = repository.save(guia);

        String rutaRelativa = construirRutaRelativa(guia);
        byte[] pdf = pdfService.generarPdf(guia);
        guia.setEfsPath(efsService.guardar(rutaRelativa, pdf));
        guia.setS3Key(rutaRelativa);
        return repository.save(guia);
    }

    /** 2. Sube la guia desde EFS hacia S3. */
    @Transactional
    public GuiaDespacho subirAS3(Long id) {
        GuiaDespacho guia = obtenerEntidad(id);
        if (!StringUtils.hasText(guia.getEfsPath())) {
            String rutaRelativa = construirRutaRelativa(guia);
            guia.setEfsPath(efsService.guardar(rutaRelativa, pdfService.generarPdf(guia)));
            guia.setS3Key(rutaRelativa);
        }
        s3Service.subirDesdeArchivo(guia.getS3Key(), guia.getEfsPath());
        guia.setEstado(EstadoGuia.SUBIDA_S3);
        return repository.save(guia);
    }

    /** 3. Descarga la guia desde S3 (autorizacion por rol en SecurityConfig). */
    public byte[] descargar(Long id) {
        GuiaDespacho guia = obtenerEntidad(id);
        return s3Service.descargar(guia.getS3Key());
    }

    /**
     * 4. Modifica la guia, regenera el PDF y lo reemplaza en EFS y S3.
     * Si al cambiar los datos (p.ej. transportista) cambia la ruta/key, se
     * elimina el objeto S3 y el archivo EFS ANTIGUOS para no dejar huerfanos.
     */
    @Transactional
    public GuiaDespacho actualizarGuia(Long id, ActualizarGuiaRequest req) {
        GuiaDespacho guia = obtenerEntidad(id);

        String oldS3Key = guia.getS3Key();
        String oldEfsPath = guia.getEfsPath();

        if (StringUtils.hasText(req.transportista())) guia.setTransportista(req.transportista());
        if (req.rutTransportista() != null) guia.setRutTransportista(req.rutTransportista());
        if (req.cliente() != null) guia.setCliente(req.cliente());
        if (req.direccionOrigen() != null) guia.setDireccionOrigen(req.direccionOrigen());
        if (req.direccionDestino() != null) guia.setDireccionDestino(req.direccionDestino());

        guia.setEstado(EstadoGuia.ACTUALIZADA);

        String newKey = construirRutaRelativa(guia);
        byte[] pdf = pdfService.generarPdf(guia);
        String newEfsPath = efsService.guardar(newKey, pdf);
        guia.setEfsPath(newEfsPath);
        guia.setS3Key(newKey);

        // Subir/reemplazar el objeto en la key actual.
        s3Service.subirDesdeArchivo(newKey, newEfsPath);

        // Limpieza de huerfanos si la key cambio (ej. cambio de transportista).
        if (oldS3Key != null && !Objects.equals(oldS3Key, newKey)) {
            log.info("La key cambio ({} -> {}); eliminando objeto S3 antiguo", oldS3Key, newKey);
            s3Service.eliminar(oldS3Key);
        }
        if (oldEfsPath != null && !Objects.equals(oldEfsPath, newEfsPath)) {
            efsService.eliminar(oldEfsPath);
        }
        return repository.save(guia);
    }

    /** 5. Elimina la guia de S3 y marca su estado ELIMINADA. */
    @Transactional
    public GuiaDespacho eliminarGuia(Long id) {
        GuiaDespacho guia = obtenerEntidad(id);
        if (StringUtils.hasText(guia.getS3Key())) {
            s3Service.eliminar(guia.getS3Key());
        }
        efsService.eliminar(guia.getEfsPath());
        guia.setEstado(EstadoGuia.ELIMINADA);
        return repository.save(guia);
    }

    /** 6. Historial filtrando por transportista y/o fecha. */
    public List<GuiaDespacho> consultarHistorial(String transportista, LocalDate fecha) {
        String t = StringUtils.hasText(transportista) ? transportista : null;
        return repository.buscarHistorial(t, fecha);
    }

    /** 7. Detalle de una guia. */
    public GuiaDespacho obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> new GuiaNoEncontradaException(id));
    }

    // ---------- utilidades ----------

    private String generarNumeroGuia() {
        long secuencia = repository.count() + 1;
        return String.format("GD-%d-%06d", LocalDate.now().getYear(), secuencia);
    }

    private String construirRutaRelativa(GuiaDespacho guia) {
        String fecha = guia.getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String transp = normalizar(guia.getTransportista());
        return String.format("guias/%s/%s/%s.pdf", fecha, transp, guia.getNumeroGuia());
    }

    private String normalizar(String s) {
        if (s == null) return "sin-transportista";
        return s.trim().toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9\\-]", "");
    }
}
