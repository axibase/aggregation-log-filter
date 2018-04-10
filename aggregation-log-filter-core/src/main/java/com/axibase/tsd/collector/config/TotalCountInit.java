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

package com.axibase.tsd.collector.config;

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
