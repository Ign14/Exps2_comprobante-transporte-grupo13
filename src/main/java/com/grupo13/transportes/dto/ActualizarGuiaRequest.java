package com.grupo13.transportes.dto;

/**
 * Datos para modificar/actualizar una guia. Todos los campos son opcionales:
 * solo se actualizan los que vengan informados. Al actualizar se regenera el
 * PDF y se reemplaza el objeto en S3.
 */
public record ActualizarGuiaRequest(
        String transportista,
        String rutTransportista,
        String cliente,
        String direccionOrigen,
        String direccionDestino
) {
}
