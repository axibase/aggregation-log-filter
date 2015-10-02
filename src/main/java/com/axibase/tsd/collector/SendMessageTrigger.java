/*
 * Copyright 2015 Axibase Corporation or its affiliates. All Rights Reserved.
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

import java.lang.IllegalStateException; /**
 * @author Nikolay Malevanny.
 */
public class SendMessageTrigger<E> {
    private volatile long counter;
    private int every = 0;
    private int stackTraceLines = 0;

    public SendMessageTrigger() {
    }

    public boolean onEvent(E event) {
        if (every <=0) {
            throw new IllegalStateException("Low every value to process event: " + event);
        }
        return ++counter % every == 0;
    }

    public void setEvery(int every) {
        this.every = every;
    }

    public int getEvery() {
        return every;
    }

    public void setStackTraceLines(int stackTraceLines) {
        this.stackTraceLines = stackTraceLines;
    }

    public int getStackTraceLines() {
        return stackTraceLines;
    }
}
