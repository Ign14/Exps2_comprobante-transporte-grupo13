package com.grupo13.transportes.dto;

import com.grupo13.transportes.entity.EstadoGuia;
import com.grupo13.transportes.entity.GuiaDespacho;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vista de salida de una guia (evita exponer la entidad JPA directamente).
 */
public record GuiaResponse(
        Long id,
        String numeroGuia,
        String transportista,
        String rutTransportista,
        String cliente,
        String direccionOrigen,
        String direccionDestino,
        LocalDate fecha,
        EstadoGuia estado,
        String efsPath,
        String s3Key,
        String creadoPor,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static GuiaResponse from(GuiaDespacho g) {
        return new GuiaResponse(
                g.getId(),
                g.getNumeroGuia(),
                g.getTransportista(),
                g.getRutTransportista(),
                g.getCliente(),
                g.getDireccionOrigen(),
                g.getDireccionDestino(),
                g.getFecha(),
                g.getEstado(),
                g.getEfsPath(),
                g.getS3Key(),
                g.getCreadoPor(),
                g.getCreatedAt(),
                g.getUpdatedAt()
        );
    }
}
