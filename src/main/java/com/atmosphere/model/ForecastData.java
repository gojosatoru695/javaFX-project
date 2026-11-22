
package com.atmosphere.model;

/**
 * Aggregated weather forecast for one local calendar day.
 */
public class ForecastData {

    private String date;
    private String dayName;
    private String description;
    private String icon;

    private double tempHigh;
    private double tempLow;
    private int rainChance;

    public ForecastData() {
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDayName() {
        return dayName;
    }

    public void setDayName(String dayName) {
        this.dayName = dayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public double getTempHigh() {
        return tempHigh;
    }

    public void setTempHigh(double tempHigh) {
        this.tempHigh = tempHigh;
    }

    public double getTempLow() {
        return tempLow;
    }

    public void setTempLow(double tempLow) {
        this.tempLow = tempLow;
    }

    public int getRainChance() {
        return rainChance;
    }

    public void setRainChance(int rainChance) {
        this.rainChance = rainChance;
    }
}


