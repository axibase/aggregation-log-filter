package com.axibase.tsd.collector.writer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;

/**
 * Created by Anna Striganova on 06.06.18
 */
public class AtsdWriterFactory {

    public static WritableByteChannel getWriter(String url) throws URISyntaxException {
        URI atsdURL = new URI(url.trim());
        String scheme = atsdURL.getScheme().toLowerCase();
        if (scheme == null) throw new IllegalStateException("Scheme can not be null.");
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
                String msg = "Can not create writer for collector.";
                throw new IllegalStateException(msg);
        }

    }
}
