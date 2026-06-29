package com.grupo13.transportes.exception;

/** Se lanza cuando no existe una guia con el id solicitado. */
public class GuiaNoEncontradaException extends RuntimeException {
    public GuiaNoEncontradaException(Long id) {
        super("Guia de despacho no encontrada: " + id);
    }
}
