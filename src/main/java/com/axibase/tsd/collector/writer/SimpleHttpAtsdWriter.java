package com.axibase.tsd.collector.writer;

import com.axibase.tsd.collector.AtsdUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

/**
 * @author Nikolay Malevanny.
 */
public class SimpleHttpAtsdWriter extends BaseHttpAtsdWriter {
    private HttpURLConnection connection;
    private OutputStream outputStream;

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (!isOpen()) {
            init();
        }
        return writeBuffer(outputStream, src);
    }

    private void init() throws IOException {
        connection = null;
        outputStream = null;

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            initConnection(connection);
            connection.setChunkedStreamingMode(DEFAULT_CHUNK_SIZE);
            connection.setUseCaches(false);

            outputStream = connection.getOutputStream();
            AtsdUtil.logInfo("Connected to " + url);
        } catch (Throwable e) {
            AtsdUtil.logInfo("Could not write messages", e);
            close();
        }
    }

    @Override
    public boolean isOpen() {
        return outputStream != null;
    }

    @Override
    public void close() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
            try {
                outputStream.close();
            } catch (IOException e) {
                AtsdUtil.logInfo("Could not close output stream", e);
            }
            outputStream = null;
        }
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                AtsdUtil.logInfo("Could not disconnect", e);
            }
            connection = null;
        }
    }

    @Override
    public void setUrl(String url) {
        if (!url.trim().endsWith(STREAM_FALSE_PARAM)) {
            super.setUrl(url + "?" + STREAM_FALSE_PARAM);
        } else {
            super.setUrl(url);
        }
    }
}
