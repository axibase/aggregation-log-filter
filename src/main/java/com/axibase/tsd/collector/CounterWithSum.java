package com.axibase.tsd.collector;

/**
 * @author Nikolay Malevanny.
 */
public class CounterWithSum extends Counter {
    private long sum;

    public CounterWithSum(long value, int zeroRepeats) {
        super(value, zeroRepeats);
    }

    public long getSum() {
        return sum;
    }

    @Override
    public void clean() {
        sum += value;
        super.clean();
    }
}
