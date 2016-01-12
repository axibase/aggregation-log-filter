package com.axibase.tsd.collector;

/**
 * @author Nikolay Malevanny.
 */
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
            System.err.println("[ERROR] " +message);
        }

        @Override
        public void warn(String message) {
            System.err.println("[WARN] " +message);
        }

        @Override
        public void info(String message) {
            System.out.println("[INFO] " +message);
        }

        @Override
        public void info(String message, Throwable exception) {
            System.out.println("[INFO] " + message + (exception == null ? "" : (": " + exception.getMessage())));
        }
    };

}
