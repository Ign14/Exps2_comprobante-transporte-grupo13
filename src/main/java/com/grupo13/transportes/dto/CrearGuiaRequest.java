package com.grupo13.transportes.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Datos de entrada para crear una guia de despacho.
 * El numero de guia, fecha y estado los asigna el servidor.
 */
public record CrearGuiaRequest(
        @NotBlank(message = "El transportista es obligatorio")
        String transportista,

        String rutTransportista,

        @NotBlank(message = "El cliente es obligatorio")
        String cliente,

        String direccionOrigen,

        String direccionDestino
) {
}
