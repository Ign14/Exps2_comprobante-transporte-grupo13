package com.grupo13.transportes.exception;

/** Se lanza cuando un usuario no tiene permisos para la operacion (ej: descarga). */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
