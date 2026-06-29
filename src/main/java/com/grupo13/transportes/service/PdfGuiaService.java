package com.grupo13.transportes.service;

import com.grupo13.transportes.entity.GuiaDespacho;
import com.grupo13.transportes.exception.AlmacenamientoException;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Genera el archivo PDF representativo de una guia de despacho.
 * Devuelve los bytes; el guardado fisico lo hace EfsStorageService.
 */
@Service
public class PdfGuiaService {

    private static final Font TITULO = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
    private static final Font SUBTITULO = new Font(Font.FontFamily.HELVETICA, 11, Font.ITALIC, BaseColor.DARK_GRAY);
    private static final Font ETIQUETA = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
    private static final Font VALOR = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL);

    public byte[] generarPdf(GuiaDespacho g) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph("GUIA DE DESPACHO", TITULO);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph sub = new Paragraph("Empresa Transportista - Sistema Cloud Native", SUBTITULO);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(20);
            doc.add(sub);

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{1.2f, 2.8f});

            fila(tabla, "Numero de guia", g.getNumeroGuia());
            fila(tabla, "Transportista", g.getTransportista());
            fila(tabla, "RUT transportista", nvl(g.getRutTransportista()));
            fila(tabla, "Cliente", nvl(g.getCliente()));
            fila(tabla, "Direccion origen", nvl(g.getDireccionOrigen()));
            fila(tabla, "Direccion destino", nvl(g.getDireccionDestino()));
            fila(tabla, "Fecha", g.getFecha() != null
                    ? g.getFecha().format(DateTimeFormatter.ISO_LOCAL_DATE) : "");
            fila(tabla, "Estado", g.getEstado() != null ? g.getEstado().name() : "");
            fila(tabla, "Creado por", nvl(g.getCreadoPor()));

            doc.add(tabla);

            Paragraph pie = new Paragraph(
                    "\nDocumento generado automaticamente. Almacenado en EFS y respaldado en Amazon S3.",
                    SUBTITULO);
            pie.setSpacingBefore(30);
            doc.add(pie);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new AlmacenamientoException("Error generando el PDF de la guia", e);
        }
    }

    private void fila(PdfPTable t, String etiqueta, String valor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, ETIQUETA));
        c1.setBackgroundColor(new BaseColor(240, 240, 240));
        c1.setPadding(6);
        PdfPCell c2 = new PdfPCell(new Phrase(valor, VALOR));
        c2.setPadding(6);
        t.addCell(c1);
        t.addCell(c2);
    }

    private String nvl(String s) {
        return s == null ? "-" : s;
    }
}
