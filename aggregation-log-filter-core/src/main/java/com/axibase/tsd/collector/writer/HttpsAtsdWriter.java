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
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class HttpsAtsdWriter extends BaseHttpAtsdWriter {
    private String ignoreSslErrors;

    HttpsAtsdWriter(URI uri, String ignoreSslErrors) {
        super(uri);
        this.ignoreSslErrors = ignoreSslErrors;
        AtsdUtil.logInfo("Ignore SSL Errors: " + ignoreSslErrors);
    }

    @Override
    protected void init() throws IOException {
        try {
            if (StringUtils.equalsIgnoreCase(ignoreSslErrors, "true")) {
                disableSSLCertificateChecks();
            }
            connection = (HttpsURLConnection) uri.toURL().openConnection();
            initConnection();
        } catch (IOException e) {
            AtsdUtil.logError("Could not init HTTPS writer", e);
            close();
        }
    }

    private void disableSSLCertificateChecks() {

        TrustManager[] trustAllCerts = {new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }
        }};

        try {
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

            HostnameVerifier allHostsValid = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            AtsdUtil.logError("Cannot create SSL context.");
        }
    }
}
