package com.grupo13.transportes.repository;

import com.grupo13.transportes.entity.GuiaDespacho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para el historial de guias.
 * Provee la consulta de historial filtrando por transportista y/o fecha.
 */
@Repository
public interface GuiaDespachoRepository extends JpaRepository<GuiaDespacho, Long> {

    Optional<GuiaDespacho> findByNumeroGuia(String numeroGuia);

    /**
     * Historial flexible: ambos filtros son opcionales.
     * Si transportista es null trae todos; si fecha es null no filtra por fecha.
     */
    @Query("""
            SELECT g FROM GuiaDespacho g
            WHERE (:transportista IS NULL OR LOWER(g.transportista) = LOWER(:transportista))
              AND (:fecha IS NULL OR g.fecha = :fecha)
            ORDER BY g.createdAt DESC
            """)
    List<GuiaDespacho> buscarHistorial(@Param("transportista") String transportista,
                                       @Param("fecha") LocalDate fecha);
}
