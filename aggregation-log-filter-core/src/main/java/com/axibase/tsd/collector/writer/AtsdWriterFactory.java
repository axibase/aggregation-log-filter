package com.axibase.tsd.collector.writer;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.nio.channels.WritableByteChannel;

/**
 * Created by Anna Striganova on 06.06.18
 */
public class AtsdWriterFactory {

    public static WritableByteChannel getWriter(String url) throws Exception {
        URI atsdURL = new URI(url);
        String scheme = StringUtils.lowerCase(atsdURL.getScheme());
        if (scheme == null) throw new IllegalStateException("Scheme cannot be empty.");
        String host = atsdURL.getHost();
        int port = atsdURL.getPort();

        switch (scheme) {

            case "udp":
                return new UdpAtsdWriter(host, port);
            case "tcp":
                return new TcpAtsdWriter(host, port);
            case "http":
                return new HttpAtsdWriter(atsdURL);
            case "https":
                return new HttpsAtsdWriter(atsdURL);
            default:
                String msg = "Cannot create writer for collector.";
                throw new IllegalStateException(msg);
        }

    }
}
