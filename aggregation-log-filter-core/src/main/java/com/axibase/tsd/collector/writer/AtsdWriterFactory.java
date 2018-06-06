package com.axibase.tsd.collector.writer;

import java.nio.channels.WritableByteChannel;

/**
 * Created by Anna Striganova on 06.06.18
 */
public class AtsdWriterFactory {
    public static WritableByteChannel getWriter(String url, String scheme, String host, int port) {
        WritableByteChannel writer = null;
        final WriterType writerType = WriterType.valueOf(scheme.toUpperCase());
        switch (writerType) {
            case UDP:
                AbstractAtsdWriter udpWriter = new UdpAtsdWriter();
                if (host == null) {
                    throw new IllegalStateException("Host can not be null for UDP.");
                }
                udpWriter.setHost(host);
                udpWriter.setPort(port);
                writer = udpWriter;
                break;
            case TCP:
                AbstractAtsdWriter tcpWriter = new TcpAtsdWriter();
                if (host == null) {
                    throw new IllegalStateException("Host can not be null for TCP.");
                }
                tcpWriter.setHost(host);
                tcpWriter.setPort(port);
                writer = tcpWriter;
                break;
            case HTTP:
                HttpAtsdWriter httpWriter = new HttpAtsdWriter();
                httpWriter.setUrl(url);
                writer = httpWriter;
                break;
            case HTTPS:
                HttpsAtsdWriter httpsWriter = new HttpsAtsdWriter();
                httpsWriter.setUrl(url);
                writer = httpsWriter;
                break;
            default:
                String msg = "Can not create writer for collector.";
                throw new IllegalStateException(msg);
        }

        return writer;
    }
}
