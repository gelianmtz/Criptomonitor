package com.redes;

public class ConnectBinanceException extends Exception {
    public ConnectBinanceException(String message) {
        super(message);
    }

    public ConnectBinanceException(Throwable cause) {
        super(cause);
    }

    public ConnectBinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
