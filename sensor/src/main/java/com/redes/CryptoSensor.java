package com.redes;

import com.redes.dto.PriceResponse;
import com.redes.util.Config;
import com.redes.util.LogFormatter;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;

public class CryptoSensor {
    private static Config config = new Config("config.properties");

    private static final String HOST = config.getString("host");
    private static final String PASSWORD = config.getString("password");
    private static final int PORT = config.getInt("port");
    private static final String TOPIC = config.getString("topic");
    private static final String USERNAME = config.getString("username");
    private static final int INTERVAL = config.getInt("interval");
    private static final Set<String> SYMBOLS = Arrays.stream(config.getString("symbols").split(","))
            .map(String::trim)
            .collect(Collectors.toSet());

    private static final Logger logger = Logger.getLogger(CryptoSensor.class.getName());
    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new LogFormatter());
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
    }

    // Obtener el precio actual de una criptomoneda desde la API pública de Binance
    public double getCryptoPrice(String symbol) throws ConnectBinanceException {
        // Validar el símbolo
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new ConnectBinanceException("Símbolo inválido: " + symbol);
        }

        // Normalizar el símbolo
        String trimmed = symbol.trim().toUpperCase(Locale.ROOT);

        String url = String.format("https://api.binance.com/api/v3/ticker/price?symbol=%s", trimmed);
        try {
            // Petición HTTP
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Código de respuesta
            int status = response.statusCode();
            if (status != 200) {
                throw new ConnectBinanceException("Respuesta HTTP inválida: " + status);
            }

            // Respuesta no vacía
            String body = response.body();
            if (body == null || body.isEmpty()) {
                throw new ConnectBinanceException("Respuesta vacía: " + trimmed);
            }

            // Convertir JSON
            Gson gson = new Gson();
            PriceResponse priceResponse;
            try {
                priceResponse = gson.fromJson(body, PriceResponse.class);
            } catch (JsonSyntaxException e) {
                throw new ConnectBinanceException("Formato JSON inesperado en la respuesta: " + body, e);
            }

            // Validar el campo
            if (priceResponse == null || priceResponse.getPrice() == null) {
                throw new ConnectBinanceException("La respuesta no contiene el campo 'price': " + body);
            }

            double price = Double.parseDouble(priceResponse.getPrice());
            logger.log(Level.INFO, "{0} => {1}", new Object[] { trimmed, price });

            return price;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectBinanceException("Error conectando a Binance", e);

        } catch (NumberFormatException nfe) {
            throw new ConnectBinanceException("No se pudo convertir el precio para " + trimmed, nfe);
        }
    }

    public static void main(String[] args) {
        CryptoSensor sensor = new CryptoSensor();

        // Construir el cliente MQTT
        Mqtt3BlockingClient mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .serverHost(HOST)
                .serverPort(PORT)
                .sslWithDefaultConfig()
                .buildBlocking();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            // Conectarse al bróker MQTT
            mqttClient.connectWith()
                    .simpleAuth()
                    .username(USERNAME)
                    .password(PASSWORD.getBytes())
                    .applySimpleAuth()
                    .send();
            logger.log(Level.INFO,
                    "Conectado al broker MQTT en {0}:{1}",
                    new Object[] { HOST, String.valueOf(PORT) });

            // Tarea periódica: por cada símbolo llamar a Binance y publicar el precio
            Runnable task = () -> {
                for (String symbol : SYMBOLS) {
                    try {
                        double price = sensor.getCryptoPrice(symbol);
                        String message = String.format("{\"symbol\":\"%s\",\"price\":%.8f,\"timestamp\":%d}",
                                symbol, price, Instant.now().toEpochMilli());

                        // Publicar en el tópico
                        mqttClient.publishWith()
                                .topic(TOPIC)
                                .qos(MqttQos.AT_LEAST_ONCE)
                                .payload(message.getBytes())
                                .send();
                        logger.log(Level.INFO, "Publicado: {0}", message);
                    } catch (ConnectBinanceException e) {
                        logger.log(Level.SEVERE, "Error obteniendo precio para " + symbol, e);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "Error durante publicación MQTT para " + symbol, ex);
                    }
                }
            };

            // Arrancar la tarea para ejecutarse repetidamente
            scheduler.scheduleAtFixedRate(task, 0, INTERVAL, TimeUnit.MILLISECONDS);

            // Añadir un shutdown hook para cerrar conexiones al terminar la JVM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Deteniendo y desconectando MQTT...");
                try {
                    scheduler.shutdownNow();
                    if (mqttClient.getState().isConnected()) {
                        mqttClient.disconnect();
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error durante la desconexión", e);
                }
            }));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error en la conexión MQTT", e);
            try {
                scheduler.shutdownNow();
            } catch (Exception ignore) {
            }
        }
    }
}
