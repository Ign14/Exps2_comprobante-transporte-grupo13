package com.grupo13.transportes.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configuracion del cliente S3 (AWS SDK v2).
 *
 * Las credenciales NUNCA se hardcodean: se leen de variables de entorno /
 * GitHub Secrets. En AWS Academy 2026 las credenciales son temporales e incluyen
 * AWS_SESSION_TOKEN, por lo que se usa AwsSessionCredentials cuando esta presente.
 *
 * Si no se entregan credenciales explicitas, se usa la cadena por defecto del SDK
 * (DefaultCredentialsProvider), util cuando la EC2 tiene un rol IAM asociado.
 */
@Slf4j
@Configuration
public class S3Config {

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey:}")
    private String secretAccessKey;

    @Value("${aws.sessionToken:}")
    private String sessionToken;

    @Bean
    public S3Client s3Client() {
        AwsCredentialsProvider provider;

        if (accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank()) {
            if (sessionToken != null && !sessionToken.isBlank()) {
                // AWS Academy / Learner Lab: credenciales temporales con session token.
                log.info("Inicializando S3Client con credenciales temporales (session token) en region {}", region);
                provider = StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken));
            } else {
                log.info("Inicializando S3Client con credenciales estaticas en region {}", region);
                provider = StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey));
            }
        } else {
            // Sin credenciales explicitas: usar rol IAM de la EC2 u otras fuentes estandar.
            log.info("Inicializando S3Client con DefaultCredentialsProvider en region {}", region);
            provider = DefaultCredentialsProvider.create();
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(provider)
                .build();
    }
}
