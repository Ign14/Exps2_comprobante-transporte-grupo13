package com.grupo13.transportes;

import com.grupo13.transportes.entity.EstadoGuia;
import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.service.PdfGuiaService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que el servicio de PDF genere un archivo valido (cabecera %PDF)
 * sin requerir AWS ni base de datos.
 */
class PdfGuiaServiceTest {

    @Test
    void generaPdfValido() {
        PdfGuiaService pdf = new PdfGuiaService();
        GuiaDespacho guia = GuiaDespacho.builder()
                .id(1L)
                .numeroGuia("GD-2026-000001")
                .transportista("Transportista X")
                .cliente("Cliente Demo")
                .fecha(LocalDate.now())
                .estado(EstadoGuia.GENERADA)
                .build();

        byte[] bytes = pdf.generarPdf(guia);

        assertNotNull(bytes);
        assertTrue(bytes.length > 100, "El PDF deberia tener contenido");
        // Los PDF inician con la firma %PDF
        String firma = new String(bytes, 0, 4);
        assertEquals("%PDF", firma);
    }
}
