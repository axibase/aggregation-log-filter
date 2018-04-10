/*
 * Copyright 2016 Axibase Corporation or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * https://www.axibase.com/atsd/axibase-apache-2.0.pdf
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.axibase.tsd.collector;

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
