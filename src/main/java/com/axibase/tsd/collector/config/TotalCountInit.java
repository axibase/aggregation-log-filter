package com.axibase.tsd.collector.config;

/**
 * @author Nikolay Malevanny.
 */
public class TotalCountInit {
    private String level;
    private int value = -1;

    public TotalCountInit() {
    }

    public TotalCountInit(String level, int value) {
        this.level = level;
        this.value = value;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getLevel() {
        return level;
    }

    public int getValue() {
        return value;
    }
}
