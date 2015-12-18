package com.axibase.tsd.collector.writer;

import com.axibase.tsd.collector.AtsdUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * @author Nikolay Malevanny.
 */
public class LoggingWrapper implements WritableByteChannel {
    private final WritableByteChannel wrapped;

    public LoggingWrapper(WritableByteChannel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        ByteBuffer bbCopy = src.duplicate();
        final byte[] b = new byte[bbCopy.remaining()];
        bbCopy.get(b);
        AtsdUtil.logInfo("WRITING:" + wrapped.getClass().getName() + ";" + new String(b).trim());
        final int written = wrapped.write(src);
        AtsdUtil.logInfo(("WRITTEN:" + written + " bytes"));
        return written;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }

    public static WritableByteChannel tryWrap(String debug, WritableByteChannel writer) {
        if (debug != null && ("1".equals(debug) || "true".equals(debug) || "TRUE".equals(debug))) {
            return new LoggingWrapper(writer);
        } else {
            return writer;
        }
    }
}
