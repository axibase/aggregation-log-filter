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
import java.net.HttpURLConnection;
import java.net.URI;

public class HttpAtsdWriter extends BaseHttpAtsdWriter {

    HttpAtsdWriter(URI uri) {
        super(uri);
    }

    @Override
    protected void init() throws IOException{
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            initConnection();
        } catch (IOException e) {
            AtsdUtil.logError("Could not init HTTP writer", e);
            close();
        }
    }
}
