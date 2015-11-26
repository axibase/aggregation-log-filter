package com.axibase.tsd.collector;

/**
 * @author Nikolay Malevanny.
 */
public class Counter {
    protected long value;
    protected int zeroRepeats;

    public Counter(long value, int zeroRepeats) {
        this.value = value;
        setZeroRepeats(zeroRepeats);
    }

    void increment() {
        value++;
    }

    public void add(long value) {
        this.value += value;
    }

    public void decrementZeroRepeats() {
        zeroRepeats--;
    }

    public void setZeroRepeats(int zeroRepeats) {
        this.zeroRepeats = zeroRepeats;
    }

    public void clean() {
        value = 0;
    }

    public long getValue() {
        return value;
    }

    public int getZeroRepeats() {
        return zeroRepeats;
    }
}
