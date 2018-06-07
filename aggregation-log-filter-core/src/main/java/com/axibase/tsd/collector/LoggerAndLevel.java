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

public class LoggerAndLevel<L> {
    private final L level;
    private final String logger;

    public LoggerAndLevel(L level, String logger) {
        if (level == null) {
            throw new IllegalArgumentException("Level is null");
        }
        this.level = level;
        this.logger = logger;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LoggerAndLevel key = (LoggerAndLevel) o;

        if (level != null ? !level.equals(key.level) : key.level != null) return false;
        return !(logger != null ? !logger.equals(key.logger) : key.logger != null);
    }

    @Override
    public int hashCode() {
        int result = level != null ? level.hashCode() : 0;
        result = 31 * result + (logger != null ? logger.hashCode() : 0);
        return result;
    }

    public L getLevel() {
        return level;
    }

    public String getLogger() {
        return logger;
    }
}
