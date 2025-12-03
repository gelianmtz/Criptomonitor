package com.proyredes;

import java.util.Map;

public class MonitorConfiguration {
    private Map<String, ButtonConfiguration> buttons;
    private int alarmDuration;

    public MonitorConfiguration(Map<String, ButtonConfiguration> buttons, int alarmDuration) {
        this.buttons = buttons;
        this.alarmDuration = alarmDuration;
    }

    public Map<String, ButtonConfiguration> getButtons() {
        return buttons;
    }

    public void setButtons(Map<String, ButtonConfiguration> buttons) {
        this.buttons = buttons;
    }

    public int getAlarmDuration() {
        return alarmDuration;
    }

    public void setAlarmDuration(int alarmDuration) {
        this.alarmDuration = alarmDuration;
    }

    public static class ButtonConfiguration {
        private String currency;
        private double threshold;

        public ButtonConfiguration(String currency, double threshold) {
            this.currency = currency;
            this.threshold = threshold;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public double getThreshold() {
            return threshold;
        }

        public void setThreshold(double threshold) {
            this.threshold = threshold;
        }
    }
}
