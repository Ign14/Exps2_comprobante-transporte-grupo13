package com.grupo13.transportes.exception;

/** Errores de almacenamiento (EFS o S3) encapsulados como excepcion de dominio. */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public AlmacenamientoException(String mensaje) {
        super(mensaje);
    }
}
