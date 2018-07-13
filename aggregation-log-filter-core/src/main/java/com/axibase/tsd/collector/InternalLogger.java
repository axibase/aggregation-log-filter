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

public interface InternalLogger {
    void error(String message, Throwable exception);

    void error(String message);

    void warn(String message);

    void info(String message);

    void info(String message, Throwable exception);

    InternalLogger SYSTEM = new InternalLogger() {
        @Override
        public void error(String message, Throwable exception) {
            System.err.println("[ERROR] " + message + (exception == null ? "" : (": " + exception.getMessage())));
        }

        @Override
        public void error(String message) {
            System.err.println("[ERROR] " + message);
        }

        @Override
        public void warn(String message) {
            System.err.println("[WARN] " + message);
        }

        @Override
        public void info(String message) {
            System.out.println("[INFO] " + message);
        }

        @Override
        public void info(String message, Throwable exception) {
            System.out.println("[INFO] " + message + (exception == null ? "" : (": " + exception.getMessage())));
        }
    };

}
