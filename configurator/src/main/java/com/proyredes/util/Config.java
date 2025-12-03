package com.proyredes.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private Properties properties = new Properties();

    public Config(String filename) {
        try (InputStream input = new FileInputStream(String.format("src/main/resources/%s", filename))) {
            properties.load(input);
        } catch (Exception ex) {
            System.err.format("Error al cargar configuración: %s.", ex.getMessage());
        }
    }

    public String getString(String key) {
        return properties.getProperty(key);
    }

    public int getInt(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public String[] getArray(String key) {
        return properties.getProperty(key).split(",");
    }
}
