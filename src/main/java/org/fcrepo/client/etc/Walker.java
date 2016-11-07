/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.client.etc;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.core.Quad;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.fcrepo.client.FcrepoClient;
import org.fcrepo.client.FcrepoOperationFailedException;
import org.fcrepo.client.FcrepoResponse;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.TEMPORARY_REDIRECT;
import static org.apache.jena.graph.Node.ANY;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ModelFactory.createDefaultModel;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author awoods
 * @since 2016-11-06
 */
public class Walker {

    private static final Logger LOGGER = getLogger(Walker.class);

    public static final String TEXT_TURTLE = "text/turtle";

    private final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

    private static FcrepoClient client;

    private static int count = 0;

    private final String baseUrl;

    private final static String LDP = "http://www.w3.org/ns/ldp#";

    /**
     * Constructor
     *
     * @param config of Walker
     */
    public Walker(final WalkerConfig config) {
        this.baseUrl = config.getBaseUrl();

        connectionManager.setMaxTotal(Integer.MAX_VALUE);
        connectionManager.setDefaultMaxPerRoute(20);
        connectionManager.closeIdleConnections(3, TimeUnit.SECONDS);
        client = FcrepoClient.client().credentials(config.getUsername(), config.getPassword()).build();
    }

    /**
     * This method is the entry point of the Walker traversal
     *
     * @return number of resources traversed
     */
    public int run() {
        return getChildren(baseUrl);
    }

    private int getChildren(final String url) {
        LOGGER.info(count + " - Get: " + url);
        count++;

        final Optional<DatasetGraph> graph = getDatasetGraph(url);
        if (graph.isPresent()) {
            final Stream<Triple> triples = getChildrenTriples(graph.get(), url);
            triples.forEach(triple -> getChildren(triple.getObject().getURI()));
        }

        return count;
    }

    private Optional<DatasetGraph> getDatasetGraph(final String url) {
        try (final FcrepoResponse head = headResource(url)) {

            final List<URI> types = head.getLinkHeaders("type");
            LOGGER.debug("Link headers: " + types);

            if (types.contains(URI.create(LDP + "NonRDFSource"))) {
                // Is this a NonRDFSource?
                LOGGER.debug("Get NonRdfSource Description for: {}", url);
                verifyNonRdfSourceDescription(head);
                return Optional.empty();

            } else {
                // This is a container
                LOGGER.debug("Get triples for: {}", url);
                try (final FcrepoResponse get = getResource(url)) {
                    final Model model = createDefaultModel();
                    final Dataset d = new DatasetImpl(model.read(get.getBody(), "", TEXT_TURTLE));
                    return Optional.of(d.asDatasetGraph());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyNonRdfSourceDescription(final FcrepoResponse response) {

        final List<URI> description = response.getLinkHeaders("describedby");
        if (description.size() != 1) {
            throw new RuntimeException("Wrong number of descriptions: " + description);
        }

        try (FcrepoResponse response1 = getResource(description.get(0).toString())) {
            // do nothing
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<Triple> getChildrenTriples(final DatasetGraph graph, final String subject) {
        final Iterator<Quad> quads =
                graph.find(ANY, createURI(subject), createProperty(LDP + "contains").asNode(), ANY);
        return iteratorToStream(quads).map(Quad::asTriple);
    }

    private FcrepoResponse getResource(final String url) {
        FcrepoResponse response;
        try {
            LOGGER.trace("GET: {}", url);
            response = client.get(URI.create(url)).accept(TEXT_TURTLE).disableRedirects().perform();
            final int statusCode = response.getStatusCode();
            if (OK.getStatusCode() != statusCode && TEMPORARY_REDIRECT.getStatusCode() != statusCode) {
                throw new RuntimeException("Unexpected response status: " + statusCode + ", " + url);
            }

        } catch (FcrepoOperationFailedException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private FcrepoResponse headResource(final String url) {
        FcrepoResponse response;
        try {
            LOGGER.trace("HEAD: {}", url);
            response = client.head(URI.create(url)).disableRedirects().perform();
            final int statusCode = response.getStatusCode();
            if (OK.getStatusCode() != statusCode && TEMPORARY_REDIRECT.getStatusCode() != statusCode) {
                throw new RuntimeException("Unexpected response status: " + statusCode + ", " + url);
            }

        } catch (FcrepoOperationFailedException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private <T> Stream<T> iteratorToStream(final Iterator<T> iterator) {
        return stream(spliteratorUnknownSize(iterator, IMMUTABLE), false);
    }

    /**
     * This is the 'main' method for running as a standalone executable
     *
     * @param args from command line
     */
    public static void main(final String[] args) {
        WalkerConfig config;
        try {
            config = new ArgParser().getConfig(args);
        } catch (RuntimeException e) {
            LOGGER.debug("Error parsing args: {}", e.getMessage());
            return;
        }

        new Walker(config).run();
    }
}
