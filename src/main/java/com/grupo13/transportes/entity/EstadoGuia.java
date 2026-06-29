package com.grupo13.transportes.entity;

/**
 * Estados del ciclo de vida de una guia de despacho.
 * GENERADA     -> PDF creado y guardado en EFS, aun no subida a S3.
 * SUBIDA_S3    -> PDF disponible en el bucket S3 (almacenamiento final).
 * ACTUALIZADA  -> PDF regenerado y reemplazado en S3.
 * ELIMINADA    -> Objeto eliminado de S3; el registro queda como historico.
 */
public enum EstadoGuia {
    GENERADA,
    SUBIDA_S3,
    ACTUALIZADA,
    ELIMINADA
}
