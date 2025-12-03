#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>

#include "credentials.h"

// Pines
#define PB1 0 // D3
#define PB2 2 // D4
#define PB3 14 // D5
#define LEDV 12 // D6
#define LEDB 13 // D7
#define LEDR 15 // D8
#define BUZ 16 // D0

// MQTT
WiFiClientSecure espClient;
PubSubClient mqttClient(espClient);

// LCD
LiquidCrystal_I2C lcd(0x27, 16, 2);

struct ButtonConfig {
    String symbol = "BTCUSDT";
    float threshold = 999999.0;
} buttonConfigs[3];

String selectedSymbol = "BTCUSDT";
float alertThreshold = 999999.0;
float lastPrice = 0.0;
int alarmDuration = 1000;

unsigned long lastWiFiAttempt = 0;
unsigned long lastMQTTAttempt = 0;

const unsigned long WiFiRetry = 30000;  // 30s
const unsigned long MQTTRetry = 10000;  // 10s

//------------------------------------------

void scanI2C() {
    Serial.println("\nScanning I2C...");
    for (byte i = 8; i < 120; i++) {
        Wire.beginTransmission(i);
        if (Wire.endTransmission() == 0) {
            Serial.print("Found I2C: 0x");
            Serial.println(i, HEX);
        }
    }
}

void initLCD() {
    lcd.init();
    lcd.backlight();
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Iniciando...");
}

void scanNetworks() {
    Serial.println("Scanning Wi-Fi...");
    int n = WiFi.scanNetworks();
    for (int i = 0; i < n; ++i) {
        Serial.print(i + 1);
        Serial.print(": ");
        Serial.println(WiFi.SSID(i));
    }
}

//------------------------------------------
// WIFI
//------------------------------------------

void connectToWiFi() {
    Serial.println("Connecting to WiFi...");
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    int attempts = 10;
    while (WiFi.status() != WL_CONNECTED && attempts-- > 0) {
        delay(300);
        Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.print("\nWiFi OK. IP: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println("\nWiFi FAIL.");
    }
}

//------------------------------------------
// MQTT
//------------------------------------------

int getButtonIndexFromTopic(const String &topic) {
    if (topic.indexOf("button1") != -1) return 0;
    if (topic.indexOf("button2") != -1) return 1;
    if (topic.indexOf("button3") != -1) return 2;
    return -1;
}

void mqttCallback(char *topic, byte *payload, unsigned int length) {
    String topicStr = String(topic);
    String received;

    for (unsigned int i = 0; i < length; i++) {
        received += (char)payload[i];
    }

    Serial.printf("MQTT: %s -> %s\n", topic, received.c_str());

    if (topicStr.startsWith(MQTT_TOPIC_CRYPTO)) {
        float price = received.toFloat();

        if (topicStr.endsWith(selectedSymbol)) {
            lcd.clear();
            lcd.setCursor(0, 0);
            lcd.print(selectedSymbol);

            lcd.setCursor(0, 1);
            lcd.print(price, 2);

            if (price > alertThreshold) {
                digitalWrite(BUZ, HIGH);
                delay(alarmDuration);
                digitalWrite(BUZ, LOW);
            }

            if (lastPrice == 0) {
                digitalWrite(LEDB, HIGH);
            } else if (price > lastPrice) {
                digitalWrite(LEDV, HIGH);
                digitalWrite(LEDR, LOW);
                digitalWrite(LEDB, LOW);
            } else if (price < lastPrice) {
                digitalWrite(LEDV, LOW);
                digitalWrite(LEDR, HIGH);
                digitalWrite(LEDB, LOW);
            } else {
                digitalWrite(LEDV, LOW);
                digitalWrite(LEDR, LOW);
                digitalWrite(LEDB, HIGH);
            }

            lastPrice = price;
        }
    }

    else if (topicStr.startsWith(MQTT_TOPIC_CONFIG)) {
        if (topicStr == MQTT_TOPIC_CONFIG + "/alarmDuration") {
            alarmDuration = received.toInt();
        } else {
            int idx = getButtonIndexFromTopic(topicStr);
            if (idx >= 0) {
                if (topicStr.endsWith("/symbol")) {
                    buttonConfigs[idx].symbol = received;
                } else if (topicStr.endsWith("/threshold")) {
                    buttonConfigs[idx].threshold = received.toFloat();
                }
            }
        }
    }
}

void connectToMQTTBroker() {
    if (WiFi.status() != WL_CONNECTED) return;

    Serial.println("Connecting to MQTT...");

    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);

    String clientID = "esp8266-" + String(WiFi.macAddress());

    if (mqttClient.connect(clientID.c_str(), MQTT_USER, MQTT_PASS)) {
        Serial.println("MQTT connected.");

        for (int i = 0; i < 3; i++) {
            mqttClient.subscribe((MQTT_TOPIC_CRYPTO + "/" + buttonConfigs[i].symbol).c_str());
        }

        for (int i = 1; i <= 3; i++) {
            mqttClient.subscribe((MQTT_TOPIC_CONFIG + "/button" + String(i) + "/symbol").c_str());
            mqttClient.subscribe((MQTT_TOPIC_CONFIG + "/button" + String(i) + "/threshold").c_str());
        }

        mqttClient.subscribe((MQTT_TOPIC_CONFIG + "/alarmDuration").c_str());
    } else {
        Serial.print("MQTT FAIL rc=");
        Serial.println(mqttClient.state());
    }
}

//------------------------------------------
// CAMBIO DE SÍMBOLO
//------------------------------------------

void updateCurrentSymbol(int button) {
    selectedSymbol = buttonConfigs[button].symbol;
    alertThreshold = buttonConfigs[button].threshold;

    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print(selectedSymbol);

    mqttClient.subscribe((MQTT_TOPIC_CRYPTO + "/" + selectedSymbol).c_str());
}

//------------------------------------------
// SETUP
//------------------------------------------

void setup() {
    Wire.begin();
    Serial.begin(74880);
    delay(800);

    pinMode(PB1, INPUT_PULLUP);
    pinMode(PB2, INPUT_PULLUP);
    pinMode(PB3, INPUT_PULLUP);

    pinMode(LEDV, OUTPUT);
    pinMode(LEDB, OUTPUT);
    pinMode(LEDR, OUTPUT);

    pinMode(BUZ, OUTPUT);
    digitalWrite(BUZ, LOW);

    scanI2C();
    initLCD();

    scanNetworks();
    connectToWiFi();

    if (WiFi.status() == WL_CONNECTED) {
        espClient.setInsecure();
        connectToMQTTBroker();
    }
}

//------------------------------------------
// LOOP
//------------------------------------------

void loop() {
    unsigned long now = millis();

    // WIFI AUTO RECONNECT
    if (WiFi.status() != WL_CONNECTED) {
        if (now - lastWiFiAttempt > WiFiRetry) {
            lastWiFiAttempt = now;
            connectToWiFi();
        }
        return; // sin Wi-Fi
    }

    // MQTT AUTO RECONNECT
    if (!mqttClient.connected()) {
        if (now - lastMQTTAttempt > MQTTRetry) {
            lastMQTTAttempt = now;
            connectToMQTTBroker();
        }
        return;
    }

    mqttClient.loop();

    // BOTONES
    if (!digitalRead(PB1)) updateCurrentSymbol(0);
    if (!digitalRead(PB2)) updateCurrentSymbol(1);
    if (!digitalRead(PB3)) updateCurrentSymbol(2);

    delay(150);
}
