package com.proyredes;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.proyredes.util.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.swing.JOptionPane.*;

public class ConfiguratorGUI extends JFrame {
    private static Config config = new Config("config.properties");

    private static final String HOST = config.getString("host");
    private static final String PASSWORD = config.getString("password");
    private static final int PORT = config.getInt("port");
    private static final String TOPIC = config.getString("topic");
    private static final String USERNAME = config.getString("username");

    private JTextField symbolTxt1, symbolTxt2, symbolTxt3, thresholdTxt1, thresholdTxt2, thresholdTxt3, alarmDurationTxt;

    private static final Logger logger = Logger.getLogger(ConfiguratorGUI.class.getName());
    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new LogFormatter());
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
    }

    public ConfiguratorGUI() {
        setTitle("Configurador del monitor de criptomonedas");
        setSize(400, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        // Disposición
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        symbolTxt1 = new JTextField();
        symbolTxt2 = new JTextField();
        symbolTxt3 = new JTextField();
        thresholdTxt1 = new JTextField();
        thresholdTxt2 = new JTextField();
        thresholdTxt3 = new JTextField();
        alarmDurationTxt = new JTextField();

        // Botón 1
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("BOTÓN 1"), gbc);
        gbc.gridx = 1;
        add(createLabeledComponent("Moneda:", symbolTxt1), gbc);
        gbc.gridx = 2;
        add(createLabeledComponent("Valor límite:", thresholdTxt1), gbc);

        // Botón 2
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("BOTÓN 2"), gbc);
        gbc.gridx = 1;
        add(createLabeledComponent("Moneda:", symbolTxt2), gbc);
        gbc.gridx = 2;
        add(createLabeledComponent("Valor límite:", thresholdTxt2), gbc);

        // Botón 3
        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("BOTÓN 3"), gbc);
        gbc.gridx = 1;
        add(createLabeledComponent("Moneda:", symbolTxt3), gbc);
        gbc.gridx = 2;
        add(createLabeledComponent("Valor límite:", thresholdTxt3), gbc);

        // Alarma
        gbc.gridx = 1; gbc.gridy = 3;
        add(createLabeledComponent("Duración de alarma:", alarmDurationTxt), gbc);

        // Botón de enviar
        JButton sendBtn = new JButton("Aceptar");
        sendBtn.setBackground(new Color(0, 120, 255));
        sendBtn.setForeground(Color.WHITE);
        gbc.gridx = 2;
        add(sendBtn, gbc);
        sendBtn.addActionListener(e -> sendConfiguration());

        setVisible(true);
    }

    private JPanel createLabeledComponent(String labelText, JComponent component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(labelText));
        panel.add(component);
        return panel;
    }

    private void sendConfiguration() {
        String symbol1 = symbolTxt1.getText();
        String symbol2 = symbolTxt2.getText();
        String symbol3 = symbolTxt3.getText();

        // Validar que las monedas no estén vacías
        if (symbol1.isEmpty() || symbol2.isEmpty() || symbol3.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Debe asignarse una moneda a cada botón.",
                "Datos incorrectos",
                WARNING_MESSAGE
            );
            return;
        }

        // Validar que las monedas sean diferentes
        if (symbol1.equals(symbol2) || symbol2.equals(symbol3) || symbol3.equals(symbol1)) {
            JOptionPane.showMessageDialog(
                this,
                "Las monedas asignadas a los botones deben ser diferentes.",
                "Datos incorrectos",
                WARNING_MESSAGE
            );
            return;
        }

        // Validar que las monedas existan
        List<String> invalidSymbols = BinanceValidator.getInvalidSymbols(symbol1, symbol2, symbol3);
        if (!invalidSymbols.isEmpty()) {
            if (invalidSymbols.get(0).startsWith("ERROR:")) {
                JOptionPane.showMessageDialog(
                    this,
                    String.format("Error al validar símbolos: %s.", invalidSymbols.get(0).substring(6)),
                    "Error",
                    ERROR_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    String.format("Los siguientes símbolos no son válidos: %s.", String.join(", ", invalidSymbols)),
                    "Datos incorrectos",
                    WARNING_MESSAGE
                );
            }
            return;
        }

        try {
            double threshold1 = Double.parseDouble(thresholdTxt1.getText());
            double threshold2 = Double.parseDouble(thresholdTxt2.getText());
            double threshold3 = Double.parseDouble(thresholdTxt3.getText());
            int alarmDuration = Integer.parseInt(alarmDurationTxt.getText());

            if (threshold1 <= 0 || threshold2 <= 0 || threshold3 <= 0 || alarmDuration <= 0) {
                JOptionPane.showMessageDialog(
                    this,
                    "Los valores límite y la duración de la alarma deben ser mayores que cero.",
                    "Datos incorrectos",
                    WARNING_MESSAGE
                );
                return;
            }

            // Crear el cliente MQTT
            Mqtt5BlockingClient mqttClient = MqttClient.builder()
                .useMqttVersion5()
                .serverHost(HOST)
                .serverPort(PORT)
                .sslWithDefaultConfig()
                .buildBlocking();

            // Conectarse a HiveMQ Cloud
            mqttClient.connectWith()
                .simpleAuth()
                .username(USERNAME)
                .password(UTF_8.encode(PASSWORD))
                .applySimpleAuth()
                .send();

            publish(mqttClient, "button1/symbol", symbol1);
            publish(mqttClient, "button1/threshold", String.valueOf(threshold1));
            publish(mqttClient, "button2/symbol", symbol2);
            publish(mqttClient, "button2/threshold", String.valueOf(threshold2));
            publish(mqttClient, "button3/symbol", symbol3);
            publish(mqttClient, "button3/threshold", String.valueOf(threshold3));
            publish(mqttClient, "alarmDuration", String.valueOf(alarmDuration));

            // Desconectarse
            mqttClient.disconnect();

            JOptionPane.showMessageDialog(
                this,
                "Configuración enviada con éxito.",
                "Configuración correcta",
                INFORMATION_MESSAGE
            );
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                this,"Ingresa valores numéricos válidos.",
                "Datos incorrectos",
                WARNING_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Error",
                ERROR_MESSAGE
            );
        }
    }

    private Mqtt5PublishResult publish(Mqtt5BlockingClient mqttClient, String topic, String message) {
        String full_topic = String.format("%s/%s", TOPIC, topic);
        Mqtt5PublishResult result = mqttClient.publishWith()
            .topic(full_topic)
            .payload(message.getBytes(UTF_8))
            .send();
        logger.info(String.format("Publicado: %s -> %s.", full_topic, message));
        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ConfiguratorGUI::new);
    }
}
