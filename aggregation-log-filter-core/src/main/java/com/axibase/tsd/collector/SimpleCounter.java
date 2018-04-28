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

import java.lang.Long;import java.lang.Override;import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SimpleCounter<L> implements EventCounter<L> {
    private Map<L, Long> map = new HashMap<L, Long>();

    @Override
    public long updateAndGetDiff(L key, long cnt) {
        Long old = map.put(key, cnt);
        return old == null ? cnt : (cnt - old);
    }

    @Override
    public Set<Map.Entry<L, Long>> values() {
        return map.entrySet();
    }
}
