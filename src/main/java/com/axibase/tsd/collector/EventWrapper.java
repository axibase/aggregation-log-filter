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

/**
 * @author Nikolay Malevanny.
 */
public class EventWrapper<E> {
    private final E event;
    private final int lines;
    private final String message;

    public EventWrapper(E event, int lines, String message) {
        this.event = event;
        this.lines = lines;
        this.message = message;
    }

    public E getEvent() {
        return event;
    }

    public int getLines() {
        return lines;
    }

    public String getMessage() {
        return message;
    }
}
