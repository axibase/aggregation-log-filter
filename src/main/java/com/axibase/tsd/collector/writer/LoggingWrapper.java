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
        return wrapped.isOpen();
    }

    @Override
    public void close() throws IOException {
        wrapped.close();
    }

    public static WritableByteChannel tryWrap(String debug, WritableByteChannel writer) {
        if (debug != null && ("1".equals(debug) || "true".equalsIgnoreCase(debug))) {
            return new LoggingWrapper(writer);
        } else {
            return writer;
        }
    }
}
