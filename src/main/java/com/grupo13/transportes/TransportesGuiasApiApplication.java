package com.grupo13.transportes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicacion Cloud Native.
 * Sistema de Gestion de Pedidos y Generacion de Guias de Despacho.
 * Semana 3 - Desarrollo Cloud Native (CDY2204) - Grupo 13.
 */
@SpringBootApplication
public class TransportesGuiasApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportesGuiasApiApplication.class, args);
    }
}
