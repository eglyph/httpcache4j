/*
 * Copyright (c) 2009. The Codehaus. All Rights Reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.codehaus.httpcache4j.cache;

import org.codehaus.httpcache4j.HTTPRequest;
import org.codehaus.httpcache4j.HTTPResponse;
import org.codehaus.httpcache4j.HeaderConstants;
import org.codehaus.httpcache4j.Headers;
import org.codehaus.httpcache4j.uri.URIBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

import static org.codehaus.httpcache4j.HeaderConstants.VARY;

/**
 * @author <a href="mailto:hamnis@codehaus.org">Erlend Hamnaberg</a>
 * @version $Revision: #5 $ $Date: 2008/09/15 $
 */
public final class Key implements Serializable {
    private static final long serialVersionUID = 5827064595759738979L;

    private URI uri;
    private Vary vary;

    public static Key create(URI uri, Vary vary) {
        return new Key(
                URIBuilder.fromURI(Objects.requireNonNull(uri, "URI may not be null")).toNormalizedURI(),
                Objects.requireNonNull(vary, "vary may not be null")
        );
    }

    public static Key create(HTTPRequest request, HTTPResponse response) {
        URI uri = request.getNormalizedURI();
        return new Key(uri, determineVariation(response.getHeaders(), request));
    }

    private static Vary determineVariation(Headers responseHeaders, HTTPRequest request) {
        Headers requestHeaders = request.getAllHeaders();
        Optional<String> varyHeader = responseHeaders.getFirstHeaderValue(VARY);
        Map<String, String> resolvedVaryHeaders = new HashMap<String, String>();
        if (varyHeader.isPresent()) {
            String[] varies = varyHeader.get().split(",");
            for (String vary : varies) {
                Optional<String> value = requestHeaders.getFirstHeaderValue(vary);
                if (value.isPresent()) {
                    resolvedVaryHeaders.put(vary, value.get());
                }
            }
        }
        if (request.getChallenge().isPresent() && Boolean.getBoolean("Vary.authorization")) {
            resolvedVaryHeaders.put(HeaderConstants.AUTHORIZATION, request.getChallenge().get().getIdentifier());
        }
        return new Vary(resolvedVaryHeaders);
    }


    Key(URI uri, Vary vary) {
        Objects.requireNonNull(uri, "URI may not be null");
        Objects.requireNonNull(vary, "Vary may not be null");
        this.uri = uri;
        this.vary = vary;
    }

    public URI getURI() {
        return uri;
    }

    public Vary getVary() {
        return vary;
    }

    @Override
    public String toString() {
       return toProperties().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Key key = (Key) o;

        if (uri != null ? !uri.equals(key.uri) : key.uri != null) {
            return false;
        }
        if (vary != null ? !vary.equals(key.vary) : key.vary != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = uri != null ? uri.hashCode() : 0;
        result = 31 * result + (vary != null ? vary.hashCode() : 0);
        return result;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(toProperties());
    }


    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
        Properties properties = (Properties) in.readObject();
        Key key = parse(properties);
        uri = key.getURI();
        vary = key.getVary();
    }

    public static Key parse(Properties properties) {
        URI uri = null;
        Vary vary = null;
        if (properties.containsKey("uri")) {
            uri = URIBuilder.fromURI(URI.create(properties.getProperty("uri"))).toNormalizedURI();
        }
        if (properties.containsKey("vary")) {
            vary = Vary.parse(properties.getProperty("vary"));
        }
        return new Key(uri, vary);
    }

    public Properties toProperties() {
        Properties object = new Properties();
        object.put("uri", uri.toString());
        object.put("vary", vary.toString());
        return object;
    }
}
