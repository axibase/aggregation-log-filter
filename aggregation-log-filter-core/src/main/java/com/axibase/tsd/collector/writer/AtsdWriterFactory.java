package com.axibase.tsd.collector.writer;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;

/**
 * Created by Anna Striganova on 06.06.18
 */
public class AtsdWriterFactory {

    public static WritableByteChannel getWriter(String url) throws URISyntaxException {

        WritableByteChannel writer;

        URI atsdURL = new URI(url);
        String scheme = atsdURL.getScheme().toLowerCase();
        String host = atsdURL.getHost();
        int port = atsdURL.getPort();

        if (atsdURL.getPath().isEmpty())
            url = url + "/api/v1/command";

        switch (scheme) {

            case "udp":
                AbstractAtsdWriter udpWriter = new UdpAtsdWriter(host, port);
                writer = udpWriter;
                break;
            case "tcp":
                AbstractAtsdWriter tcpWriter = new TcpAtsdWriter(host, port);
                writer = tcpWriter;
                break;
            case "http":
                HttpAtsdWriter httpWriter = new HttpAtsdWriter();
                httpWriter.setUrl(url);
                writer = httpWriter;
                break;
            case "https":
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
