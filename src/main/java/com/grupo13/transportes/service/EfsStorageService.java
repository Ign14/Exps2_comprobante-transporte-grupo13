package com.grupo13.transportes.service;

import com.grupo13.transportes.exception.AlmacenamientoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Almacenamiento TEMPORAL en EFS.
 *
 * EFS se monta en la EC2 en /mnt/efs y dentro del contenedor en /app/efs
 * (variable EFS_MOUNT_PATH). Toda guia se escribe PRIMERO aqui antes de subir
 * a S3, cumpliendo el requisito de "almacenamiento temporal en EFS".
 *
 * La sub-ruta relativa se organiza igual que en S3: guias/<fecha>/<transportista>/<archivo>.
 */
@Slf4j
@Service
public class EfsStorageService {

    @Value("${efs.mount-path:/app/efs}")
    private String efsMountPath;

    /**
     * Guarda los bytes del PDF en EFS en la ruta relativa indicada.
     * @return la ruta absoluta del archivo dentro de EFS.
     */
    public String guardar(String rutaRelativa, byte[] contenido) {
        Path destino = Paths.get(efsMountPath, rutaRelativa);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            log.info("Guia guardada temporalmente en EFS: {}", destino);
            return destino.toString();
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo escribir la guia en EFS: " + destino, e);
        }
    }

    public byte[] leer(String rutaAbsoluta) {
        try {
            return Files.readAllBytes(Paths.get(rutaAbsoluta));
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo leer la guia desde EFS: " + rutaAbsoluta, e);
        }
    }

    public void eliminar(String rutaAbsoluta) {
        try {
            if (rutaAbsoluta != null) {
                boolean borrado = Files.deleteIfExists(Paths.get(rutaAbsoluta));
                log.info("Eliminacion en EFS ({}): {}", borrado, rutaAbsoluta);
            }
        } catch (IOException e) {
            // No es critico para el flujo: se registra y continua.
            log.warn("No se pudo eliminar el archivo temporal de EFS: {}", rutaAbsoluta, e);
        }
    }

    public String getEfsMountPath() {
        return efsMountPath;
    }
}
