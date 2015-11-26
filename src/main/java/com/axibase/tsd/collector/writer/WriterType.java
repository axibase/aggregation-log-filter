package com.axibase.tsd.collector.writer;

import java.nio.channels.WritableByteChannel;

/**
 * @author Nikolay Malevanny.
 */
public enum WriterType {
    UDP(UdpAtsdWriter.class),
    TCP(TcpAtsdWriter.class),
    HTTP(HttpStreamingAtsdWriter.class),
    ;

    private Class writerClass;

    WriterType(Class writerClass) {
        this.writerClass = writerClass;
    }

    public Class getWriterClass() {
        return writerClass;
    }
}
