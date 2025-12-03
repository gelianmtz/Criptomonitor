package com.proyredes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BinanceValidator {
    private static Set<String> validSymbolsCache = null;

    public static List<String> getInvalidSymbols(String ...symbols) {
        List<String> invalidSymbols = new ArrayList<>();
        try {
            if (validSymbolsCache == null) {
                validSymbolsCache = loadValidSymbols();
            }

            for (String symbol : symbols) {
                if (!validSymbolsCache.contains(symbol.toUpperCase())) {
                    invalidSymbols.add(symbol);
                }
            }
        } catch (Exception ex) {
            invalidSymbols.add(String.format("ERROR: %s", ex.getMessage()));
        }
        return invalidSymbols;
    }

    public static void refreshSymbols() throws Exception {
        validSymbolsCache = loadValidSymbols();
    }

    private static Set<String> loadValidSymbols() throws Exception {
        URL url = new URI("https://api.binance.com/api/v3/exchangeInfo").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            JsonArray jsonSymbols = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("symbols");

            Set<String> validSymbols = new HashSet<>();
            for (JsonElement element : jsonSymbols) {
                validSymbols.add(element.getAsJsonObject().get("symbol").getAsString().toUpperCase());
            }
            return validSymbols;
        }
    }
}
