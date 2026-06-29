package com.grupo13.transportes.service;

import com.grupo13.transportes.exception.AlmacenamientoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Almacenamiento FINAL de objetos en Amazon S3.
 * El bucket se mantiene privado; los objetos se organizan por fecha y transportista.
 */
@Slf4j
@Service
public class S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /** Sube (o reemplaza) un objeto desde un archivo del EFS hacia S3. */
    public void subirDesdeArchivo(String key, String rutaArchivoEfs) {
        try {
            Path archivo = Paths.get(rutaArchivoEfs);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/pdf")
                            .build(),
                    RequestBody.fromFile(archivo));
            log.info("Objeto subido a S3: s3://{}/{}", bucket, key);
        } catch (S3Exception e) {
            throw new AlmacenamientoException("Error subiendo objeto a S3: " + key, e);
        }
    }

    /** Descarga los bytes de un objeto de S3. */
    public byte[] descargar(String key) {
        try {
            ResponseBytes<GetObjectResponse> obj = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return obj.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new AlmacenamientoException("El objeto no existe en S3: " + key, e);
        } catch (S3Exception e) {
            throw new AlmacenamientoException("Error descargando objeto de S3: " + key, e);
        }
    }

    /** Elimina un objeto de S3. */
    public void eliminar(String key) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("Objeto eliminado de S3: s3://{}/{}", bucket, key);
        } catch (S3Exception e) {
            throw new AlmacenamientoException("Error eliminando objeto de S3: " + key, e);
        }
    }

    public String getBucket() {
        return bucket;
    }
}
