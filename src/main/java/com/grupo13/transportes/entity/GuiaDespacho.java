package com.grupo13.transportes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa una guia de despacho.
 * El historial se persiste en base de datos (H2 por defecto, configurable),
 * mientras que el archivo PDF vive primero en EFS y luego en S3.
 */
@Entity
@Table(name = "guias_despacho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuiaDespacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numero de guia legible para el negocio (ej: GD-2026-000123). */
    @Column(nullable = false, unique = true, length = 40)
    private String numeroGuia;

    @Column(nullable = false, length = 120)
    private String transportista;

    @Column(length = 20)
    private String rutTransportista;

    @Column(length = 120)
    private String cliente;

    @Column(length = 200)
    private String direccionOrigen;

    @Column(length = 200)
    private String direccionDestino;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoGuia estado;

    /** Ruta del PDF dentro de EFS (ej: /app/efs/guias/2026-06-08/transportista-x/GD-...pdf). */
    @Column(length = 300)
    private String efsPath;

    /** Key del objeto en S3 (ej: guias/2026-06-08/transportista-x/GD-...pdf). */
    @Column(length = 300)
    private String s3Key;

    /** Usuario que creo la guia (para historial / auditoria). */
    @Column(length = 80)
    private String creadoPor;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.fecha == null) {
            this.fecha = LocalDate.now();
        }
        if (this.estado == null) {
            this.estado = EstadoGuia.GENERADA;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
