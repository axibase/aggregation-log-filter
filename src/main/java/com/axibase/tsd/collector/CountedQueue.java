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

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Nikolay Malevanny.
 */
public class CountedQueue<E> extends ConcurrentLinkedQueue<E> {
    private volatile int count;

    @Override
    public boolean offer(E e) {
        boolean offer = super.offer(e);
        if (offer) {
            count++;
        }
        return offer;
    }

    @Override
    public E poll() {
        E value = super.poll();
        if (count > 0) {
            count--;
        }
        return value;
    }

    public void clearCount() {
        count = 0;
    }

    public int getCount() {
        return count;
    }
}
