package com.tinkerpop.rexster;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Element;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Query;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.io.graphson.GraphSONUtility;
import com.tinkerpop.rexster.extension.ExtensionMethod;
import com.tinkerpop.rexster.extension.ExtensionPoint;
import com.tinkerpop.rexster.extension.ExtensionResponse;
import com.tinkerpop.rexster.extension.ExtensionSegmentSet;
import com.tinkerpop.rexster.extension.HttpMethod;
import com.tinkerpop.rexster.extension.RexsterExtension;
import com.tinkerpop.rexster.util.ElementHelper;
import com.tinkerpop.rexster.util.QueryProperties;
import com.tinkerpop.rexster.util.RequestObjectHelper;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;

/**
 * Vertex resource.
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
@Path("/graphs/{graphname}/vertices")
public class VertexResource extends AbstractSubResource {

    private static Logger logger = Logger.getLogger(VertexResource.class);

    public VertexResource() {
        super(null);
    }

    public VertexResource(UriInfo ui, HttpServletRequest req, RexsterApplication ra) {
        super(ra);
        this.httpServletRequest = req;
        this.uriInfo = ui;
    }

    @OPTIONS
    public Response optionsVertices() {
        return buildOptionsResponse(HttpMethod.GET.toString(),
                HttpMethod.POST.toString());
    }

    /**
     * GET http://host/graph/vertices
     * graph.getVertices();
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response getVertices(@PathParam("graphname") String graphName) {
        return getVertices(graphName, false);
    }

    @GET
    @Produces({RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response getVerticesRexsterJson(@PathParam("graphname") String graphName) {
        return getVertices(graphName, true);
    }

    private Response getVertices(final String graphName, final boolean showTypes) {
        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();
        
        final JSONObject theRequestObject = this.getRequestObject();
        final Long start = RequestObjectHelper.getStartOffset(theRequestObject);
        final Long end = RequestObjectHelper.getEndOffset(theRequestObject);
        final List<String> returnKeys = RequestObjectHelper.getReturnKeys(theRequestObject);

        String key = null;
        Object value = null;

        Object temp = theRequestObject.opt(Tokens.KEY);
        if (null != temp)
            key = temp.toString();

        temp = theRequestObject.opt(Tokens.VALUE);
        if (null != temp)
            value = ElementHelper.getTypedPropertyValue(temp.toString());
        
        final boolean filtered = key != null && value != null;
        
        try {
            long counter = 0l;
            final JSONArray vertexArray = new JSONArray();
            boolean wasInSection = false;
            
            final Iterable<Vertex> vertices = filtered ? graph.getVertices(key, value) : graph.getVertices();
            for (Vertex vertex : vertices) {
                if (counter >= start && counter < end) {
                    wasInSection = true;
                    vertexArray.put(GraphSONUtility.jsonFromElement(vertex, returnKeys, showTypes));
                } else if (wasInSection) {
                    break;
                }
                counter++;
            }

            this.resultObject.put(Tokens.RESULTS, vertexArray);
            this.resultObject.put(Tokens.TOTAL_SIZE, vertexArray.length());
            this.resultObject.put(Tokens.QUERY_TIME, this.sh.stopWatch());

        } catch (JSONException ex) {
            logger.error(ex);
            JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (RuntimeException re) {
            logger.error(re);

            JSONObject error = generateErrorObject(re.getMessage(), re);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    @OPTIONS
    @Path("/{id}")
    public Response optionsSingleVertex() {
        return buildOptionsResponse();
    }

    /**
     * GET http://host/graph/vertices/id
     * graph.getVertex(id);
     */
    @GET
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON})
    public Response getSingleVertex(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return getSingleVertex(graphName, id, false, false);
    }

    @GET
    @Path("/{id}")
    @Produces({RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response getSingleVertexRexsterJson(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return getSingleVertex(graphName, id, false, true);
    }

    @GET
    @Path("/{id}")
    @Produces({RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response getSingleVertexRexsterTypedJson(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return getSingleVertex(graphName, id, true, true);
    }

    private Response getSingleVertex(String graphName, String id, boolean showTypes, boolean showHypermedia) {
        RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        Vertex vertex = rag.getGraph().getVertex(id);
        if (null != vertex) {
            try {

                JSONObject theRequestObject = this.getRequestObject();
                List<String> returnKeys = RequestObjectHelper.getReturnKeys(theRequestObject);

                this.resultObject.put(Tokens.RESULTS, GraphSONUtility.jsonFromElement(vertex, returnKeys, showTypes));
                this.resultObject.put(Tokens.QUERY_TIME, this.sh.stopWatch());

                if (showHypermedia) {
                    JSONArray extensionsList = rag.getExtensionHypermedia(ExtensionPoint.VERTEX, this.getUriPath());
                    if (extensionsList != null) {
                        this.resultObject.put(Tokens.EXTENSIONS, extensionsList);
                    }
                }

            } catch (JSONException ex) {
                logger.error(ex);

                JSONObject error = generateErrorObjectJsonFail(ex);
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }
        } else {
            String msg = "Vertex with [" + id + "] cannot be found.";
            logger.info(msg);

            JSONObject error = generateErrorObject(msg);
            throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    @HEAD
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response headVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeVertexExtension(graphName, id, HttpMethod.HEAD);
    }

    @HEAD
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response headVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.HEAD);
    }

    @PUT
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeVertexExtension(graphName, id, HttpMethod.PUT);
    }

    @PUT
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response putVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.PUT);
    }

    @OPTIONS
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response optionsVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeVertexExtension(graphName, id, HttpMethod.OPTIONS);
    }

    @OPTIONS
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response optionsVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.OPTIONS);
    }

    @DELETE
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deleteVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeVertexExtension(graphName, id, HttpMethod.DELETE);
    }

    @DELETE
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response deleteVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.DELETE);
    }

    @POST
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        this.setRequestObject(json);
        return this.executeVertexExtension(graphName, id, HttpMethod.POST);
    }

    @POST
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response postVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.POST);
    }

    @GET
    @Path("/{id}/{extension: (?!outE)(?!bothE)(?!inE)(?!out)(?!both)(?!in)(?!query).+}")
    public Response getVertexExtension(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        return this.executeVertexExtension(graphName, id, HttpMethod.GET);
    }

    private Response executeVertexExtension(final String graphName, final String id, final HttpMethod httpMethodRequested) {

        final Vertex vertex = this.getRexsterApplicationGraph(graphName).getGraph().getVertex(id);

        ExtensionResponse extResponse;
        ExtensionMethod methodToCall;
        final ExtensionSegmentSet extensionSegmentSet = parseUriForExtensionSegment(graphName, ExtensionPoint.VERTEX);

        // determine if the namespace and extension are enabled for this graph
        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);

        if (rag.isExtensionAllowed(extensionSegmentSet)) {

            final Object returnValue;

            // namespace was allowed so try to run the extension
            try {

                // look for the extension as loaded through serviceloader
                final List<RexsterExtension> rexsterExtensions;
                try {
                    rexsterExtensions = findExtensionClasses(extensionSegmentSet);
                } catch (ServiceConfigurationError sce) {
                    logger.error("ServiceLoader could not find a class referenced in com.tinkerpop.rexster.extension.RexsterExtension.");
                    JSONObject error = generateErrorObject(
                            "Class specified in com.tinkerpop.rexster.extension.RexsterExtension could not be found.",
                            sce);
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                if (rexsterExtensions == null || rexsterExtensions.size() == 0) {
                    // extension was not found for some reason
                    logger.error("The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "].  Check com.tinkerpop.rexster.extension.RexsterExtension file in META-INF.services.");
                    JSONObject error = generateErrorObject(
                            "The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "]");
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                // look up the method on the extension that needs to be called.
                methodToCall = findExtensionMethod(rexsterExtensions, ExtensionPoint.VERTEX, extensionSegmentSet.getExtensionMethod(), httpMethodRequested);

                if (methodToCall == null) {
                    // extension method was not found for some reason
                    logger.error("The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "] with a HTTP method of [" + httpMethodRequested.name() + "].  Check com.tinkerpop.rexster.extension.RexsterExtension file in META-INF.services.");
                    JSONObject error = generateErrorObject(
                            "The [" + extensionSegmentSet + "] extension was not found for [" + graphName + "] with a HTTP method of [" + httpMethodRequested.name() + "]");
                    throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
                }

                // found the method...time to do work
                returnValue = invokeExtension(rag, methodToCall, vertex);

            } catch (WebApplicationException wae) {
                // already logged this...just throw it  up.
                throw wae;
            } catch (Exception ex) {
                logger.error("Dynamic invocation of the [" + extensionSegmentSet + "] extension failed.", ex);

                if (ex.getCause() != null) {
                    Throwable cause = ex.getCause();
                    logger.error("It would be smart to trap this this exception within the extension and supply a good response to the user:" + cause.getMessage(), cause);
                }

                JSONObject error = generateErrorObjectJsonFail(ex);
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }

            if (returnValue instanceof ExtensionResponse) {
                extResponse = (ExtensionResponse) returnValue;

                if (extResponse.isErrorResponse()) {
                    // an error was raised within the extension.  pass it back out as an error.
                    logger.warn("The [" + extensionSegmentSet + "] extension raised an error response.");
                    throw new WebApplicationException(Response.fromResponse(extResponse.getJerseyResponse()).build());
                }
            } else {
                // extension method is not returning the correct type...needs to be an ExtensionResponse
                logger.error("The [" + extensionSegmentSet + "] extension does not return an ExtensionResponse.");
                JSONObject error = generateErrorObject(
                        "The [" + extensionSegmentSet + "] extension does not return an ExtensionResponse.");
                throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
            }

        } else {
            // namespace was not allowed
            logger.error("The [" + extensionSegmentSet + "] extension was not configured for [" + graphName + "]");
            JSONObject error = generateErrorObject(
                    "The [" + extensionSegmentSet + "] extension was not configured for [" + graphName + "]");
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        String mediaType = MediaType.APPLICATION_JSON;
        if (methodToCall != null) {
            mediaType = methodToCall.getExtensionDefinition().produces();
            extResponse = tryAppendRexsterAttributesIfJson(extResponse, methodToCall, mediaType);
        }

        return Response.fromResponse(extResponse.getJerseyResponse()).type(mediaType).build();
    }

    @OPTIONS
    @Path("/{id}/{direction}")
    public Response optionsVertexEdges() {
        return buildOptionsResponse();
    }

    /**
     * GET http://host/graph/vertices/id/direction
     * graph.getVertex(id).get{Direction}Edges();
     */
    @GET
    @Path("/{id}/{direction}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response getVertexEdges(@PathParam("graphname") String graphName, @PathParam("id") String vertexId, @PathParam("direction") String direction) {
        return this.getVertexEdges(graphName, vertexId, direction, false);
    }

    @GET
    @Path("/{id}/{direction}")
    @Produces({RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response getVertexEdgesRexsterTypedJson(@PathParam("graphname") String graphName, @PathParam("id") String vertexId, @PathParam("direction") String direction) {
        return this.getVertexEdges(graphName, vertexId, direction, true);
    }

    private Response getVertexEdges(String graphName, String vertexId, String direction, boolean showTypes) {
        final Vertex vertex = this.getRexsterApplicationGraph(graphName).getGraph().getVertex(vertexId);
        if (vertex == null) {
            final String msg = "Vertex with [" + vertexId + "] cannot be found.";
            logger.info(msg);

            final JSONObject error = generateErrorObject(msg);
            throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
        }

        try {
            final JSONObject theRequestObject = this.getRequestObject();
            final Long start = RequestObjectHelper.getStartOffset(theRequestObject);
            final Long end = RequestObjectHelper.getEndOffset(theRequestObject);
            final List<String> returnKeys = RequestObjectHelper.getReturnKeys(theRequestObject);

            // accept either an array of labels or just one label
            final String[] labels = getLabelsFromRequest(theRequestObject);

            // break out the segment into the return and the direction
            final VertexQueryArguments queryArguments = new VertexQueryArguments(direction);

            // if this is a query and the _return is "count" then we don't bother to send back the
            // result array
            final boolean countOnly = queryArguments.isCountOnly();

            // what kind of data the calling client wants back (vertices, edges, count, vertex identifiers)
            final ReturnType returnType = queryArguments.getReturnType();

            // the query direction (both, out, in)
            final Direction queryDirection = queryArguments.getQueryDirection();

            long counter = 0l;
            final JSONArray elementArray = new JSONArray();

            Query query = vertex.query().direction(queryDirection);
            if (labels != null) {
                query = query.labels(labels);
            }

            final Set<QueryProperties> queryProperties = RequestObjectHelper.getQueryProperties(theRequestObject);
            if (queryProperties.size() > 0) {
                for (QueryProperties queryProperty : queryProperties) {
                    query = query.has(queryProperty.getName(), queryProperty.getValue(), queryProperty.getCompare());
                }
            }

            final long limit = theRequestObject.has(Tokens._LIMIT) ? theRequestObject.getLong(Tokens._LIMIT) : Long.MIN_VALUE;
            if (limit >= 0) {
                query = query.limit(limit);
            }

            if (returnType == ReturnType.VERTICES || returnType == ReturnType.VERTEX_IDS){
                final Iterable<Vertex> vertexQueryResults = query.vertices();
                for (Vertex v : vertexQueryResults) {
                    if (counter >= start && counter < end) {
                        if (returnType.equals(ReturnType.VERTICES)) {
                            elementArray.put(GraphSONUtility.jsonFromElement(v, returnKeys, showTypes));
                        } else {
                            elementArray.put(v.getId());
                        }
                    }
                    counter++;
                }
            } else if (returnType == ReturnType.EDGES) {
                final Iterable<Edge> edgeQueryResults = query.edges();
                for (Edge e : edgeQueryResults) {
                    if (counter >= start && counter < end) {
                        elementArray.put(GraphSONUtility.jsonFromElement(e, returnKeys, showTypes));
                    }
                    counter++;
                }
            } else if (returnType == ReturnType.COUNT) {
                counter = query.count();
            } else {
                final JSONObject error = generateErrorObject(direction + " direction segment was invalid.");
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(error).build());
            }

            if (!countOnly) {
                this.resultObject.put(Tokens.RESULTS, elementArray);
            }

            this.resultObject.put(Tokens.TOTAL_SIZE, counter);
            this.resultObject.put(Tokens.QUERY_TIME, this.sh.stopWatch());

        } catch (JSONException ex) {
            logger.error(ex);

            final JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (WebApplicationException wae) {
            throw wae;
        } catch (RuntimeException re) {
            logger.error(re);

            final JSONObject error = generateErrorObject(re.getMessage(), re);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response postNullVertexRexsterConsumesJson(@Context Request request, @PathParam("graphname") String graphName, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.postVertex(graphName, null, false, v);
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes({RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response postNullVertexRexsterConsumesTypedJson(@Context Request request, @PathParam("graphname") String graphName, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.postVertex(graphName, null, true, v);
    }

    /**
     * POST http://host/graph/vertices
     * graph.addVertex(null);
     */
    @POST
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response postNullVertexOnUri(@Context Request request, @PathParam("graphname") String graphName) {
        Variant v = request.selectVariant(producesVariantList);
        return this.postVertex(graphName, null, true, v);
    }

    /**
     * POST http://host/graph/vertices/id
     * Vertex v = graph.addVertex(id);
     * v.setProperty(key,value);
     */
    @POST
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response postVertexConsumesJson(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.postVertex(graphName, id, false, v);
    }

    @POST
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes({RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response postVertexRexsterConsumesTypedJson(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        // initializes the request object with the data POSTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.postVertex(graphName, id, true, v);
    }

    /**
     * POST http://host/graph/vertices/id?key=value
     * Vertex v = graph.addVertex(id);
     * v.setProperty(key,value);
     */
    @POST
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response postVertexOnUri(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id) {
        Variant v = request.selectVariant(producesVariantList);
        return postVertex(graphName, id, true, v);
    }

    private Response postVertex(final String graphName, final String id, final boolean parseTypes, final Variant variant) {
        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();

        final MediaType produces = variant.getMediaType();

        final boolean showTypes = produces.equals(RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON_TYPE);
        final boolean showHypermedia = produces.equals(RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON_TYPE)
                || produces.equals(RexsterMediaType.APPLICATION_REXSTER_JSON_TYPE);

        try {
            // blueprints throws IllegalArgumentException if the id is null
            Vertex vertex = id == null ? null : graph.getVertex(id);

            final JSONObject theRequestObject = this.getRequestObject();

            if (null == vertex) {
                vertex = graph.addVertex(id);
            } else {
                if (!RequestObjectHelper.hasElementProperties(theRequestObject)) {
                    // if the edge exists there better be some properties to assign
                    // this really isn't a BAD_REQUEST, but CONFLICT isn't much better...bah
                    JSONObject error = generateErrorObjectJsonFail(new Exception("Vertex with id " + id + " already exists"));
                    throw new WebApplicationException(Response.status(Status.CONFLICT).entity(error).build());
                }
            }

            final Iterator keys = theRequestObject.keys();
            while (keys.hasNext()) {
                final String key = keys.next().toString();
                if (!key.startsWith(Tokens.UNDERSCORE)) {
                    vertex.setProperty(key, ElementHelper.getTypedPropertyValue(theRequestObject.get(key), parseTypes));
                }
            }

            rag.tryStopTransactionSuccess();

            // some graph implementations close scope at the close of the transaction so this has to be
            // reconstituted
            final Vertex reconstitutedElement = graph.getVertex(vertex.getId());

            final List<String> returnKeys = RequestObjectHelper.getReturnKeys(theRequestObject);
            this.resultObject.put(Tokens.RESULTS, GraphSONUtility.jsonFromElement(reconstitutedElement, returnKeys, showTypes));

            if (showHypermedia) {
                final JSONArray extensionsList = rag.getExtensionHypermedia(ExtensionPoint.VERTEX, this.getUriPath());
                if (extensionsList != null) {
                    this.resultObject.put(Tokens.EXTENSIONS, extensionsList);
                }
            }

            this.resultObject.put(Tokens.QUERY_TIME, sh.stopWatch());
        } catch (JSONException ex) {
            rag.tryStopTransactionFailure();

            logger.error(ex);

            JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (RuntimeException re) {
            rag.tryStopTransactionFailure();

            logger.error(re);

            JSONObject error = generateErrorObject(re.getMessage(), re);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    /**
     * PUT http://host/graph/vertices/id
     * Vertex v = graph.addVertex(id);
     * v.setProperty(key,value);
     */
    @PUT
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON})
    public Response putVertexConsumesJson(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        // initializes the request object with the data PUTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.putVertex(graphName, id, false, v);
    }

    @PUT
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    @Consumes(RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON)
    public Response putVertexConsumesTypedJson(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id, JSONObject json) {
        // initializes the request object with the data PUTed to the resource.  URI parameters
        // will then be ignored when the getRequestObject is called as the request object will
        // have already been established.
        this.setRequestObject(json);
        Variant v = request.selectVariant(producesVariantList);
        return this.putVertex(graphName, id, true, v);
    }

    /**
     * PUT http://host/graph/vertices/id?key=value
     * remove all properties
     * v.setProperty(key,value);
     */
    @PUT
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response putVertexConsumesUri(@Context Request request, @PathParam("graphname") String graphName, @PathParam("id") String id) {
        Variant v = request.selectVariant(producesVariantList);
        return putVertex(graphName, id, true, v);
    }

    private Response putVertex(final String graphName, final String id, final boolean parseTypes, final Variant variant) {
        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();

        final MediaType produces = variant.getMediaType();
        final boolean showTypes = produces.equals(RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON_TYPE);
        final boolean showHypermedia = produces.equals(RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON_TYPE)
                || produces.equals(RexsterMediaType.APPLICATION_REXSTER_JSON_TYPE);

        try {
            final Vertex vertex = graph.getVertex(id);

            if (null == vertex) {
                final String msg = "Vertex with [" + id + "] cannot be found.";
                logger.info(msg);

                final JSONObject error = generateErrorObject(msg);
                throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
            }

            // remove all properties as this is a replace operation
            com.tinkerpop.blueprints.util.ElementHelper.removeProperties(new ArrayList<Element>() {{
                add(vertex);
            }});

            final JSONObject theRequestObject = this.getRequestObject();
            final Iterator keys = theRequestObject.keys();
            while (keys.hasNext()) {
                final String key = keys.next().toString();
                if (!key.startsWith(Tokens.UNDERSCORE)) {
                    vertex.setProperty(key, ElementHelper.getTypedPropertyValue(theRequestObject.get(key), parseTypes));
                }
            }

            rag.tryStopTransactionSuccess();

            // some graph implementations close scope at the close of the transaction so this has to be
            // reconstituted
            final Vertex reconstitutedElement = graph.getVertex(vertex.getId());

            final List<String> returnKeys = RequestObjectHelper.getReturnKeys(theRequestObject);
            this.resultObject.put(Tokens.RESULTS, GraphSONUtility.jsonFromElement(reconstitutedElement, returnKeys, showTypes));

            if (showHypermedia) {
                final JSONArray extensionsList = rag.getExtensionHypermedia(ExtensionPoint.VERTEX, this.getUriPath());
                if (extensionsList != null) {
                    this.resultObject.put(Tokens.EXTENSIONS, extensionsList);
                }
            }

            this.resultObject.put(Tokens.QUERY_TIME, sh.stopWatch());
        } catch (JSONException ex) {
            rag.tryStopTransactionFailure();

            logger.error(ex);

            JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (RuntimeException re) {
            rag.tryStopTransactionFailure();

            logger.error(re);

            JSONObject error = generateErrorObject(re.getMessage(), re);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();
    }

    /**
     * DELETE http://host/graph/vertices/id
     * graph.removeVertex(graph.getVertex(id));
     * <p/>
     * DELETE http://host/graph/vertices/id?key1&key2
     * Vertex v = graph.getVertex(id);
     * v.removeProperty(key1);
     * v.removeProperty(key2);
     */
    @DELETE
    @Path("/{id}")
    @Produces({MediaType.APPLICATION_JSON, RexsterMediaType.APPLICATION_REXSTER_JSON, RexsterMediaType.APPLICATION_REXSTER_TYPED_JSON})
    public Response deleteVertex(@PathParam("graphname") String graphName, @PathParam("id") String id) {
        final RexsterApplicationGraph rag = this.getRexsterApplicationGraph(graphName);
        final Graph graph = rag.getGraph();

        try {
            final List<String> keys = this.getNonRexsterRequestKeys();
            final Vertex vertex = graph.getVertex(id);
            if (null != vertex) {
                if (keys.size() > 0) {
                    // delete vertex properites
                    for (final String key : keys) {
                        vertex.removeProperty(key);
                    }
                } else {
                    // delete vertex
                    graph.removeVertex(vertex);
                }
            } else {
                final String msg = "Vertex with [" + id + "] cannot be found.";
                logger.info(msg);

                JSONObject error = generateErrorObject(msg);
                throw new WebApplicationException(Response.status(Status.NOT_FOUND).entity(error).build());
            }

            rag.tryStopTransactionSuccess();
            this.resultObject.put(Tokens.QUERY_TIME, sh.stopWatch());
        } catch (JSONException ex) {

            rag.tryStopTransactionFailure();

            logger.error(ex);

            final JSONObject error = generateErrorObjectJsonFail(ex);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        } catch (RuntimeException re) {

            rag.tryStopTransactionFailure();

            logger.error(re);

            final JSONObject error = generateErrorObject(re.getMessage(), re);
            throw new WebApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR).entity(error).build());
        }

        return Response.ok(this.resultObject).build();

    }

    private static String[] getLabelsFromRequest(JSONObject theRequestObject) {
        JSONArray labelSet = theRequestObject.optJSONArray(Tokens._LABEL);
        if (labelSet == null) {
            final String oneLabel = theRequestObject.optString(Tokens._LABEL);
            if (oneLabel != null && !oneLabel.isEmpty()) {
                labelSet = new JSONArray();
                labelSet.put(oneLabel);
            }
        }

        String[] labels = null;
        if (labelSet != null) {
            labels = new String[labelSet.length()];
            for (int ix = 0; ix < labelSet.length(); ix++) {
                labels[ix] = labelSet.optString(ix);
            }
        }
        return labels;
    }

    private enum ReturnType { VERTICES, EDGES, COUNT, VERTEX_IDS }
    private final class VertexQueryArguments {

        private final Direction queryDirection;
        private final ReturnType returnType;
        private final boolean countOnly;

        public VertexQueryArguments(String directionSegment){
            if (directionSegment.equals(Tokens.OUT_E)){
                returnType = ReturnType.EDGES;
                queryDirection = Direction.OUT;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.IN_E)) {
                returnType = ReturnType.EDGES;
                queryDirection = Direction.IN;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.BOTH_E)) {
                returnType = ReturnType.EDGES;
                queryDirection = Direction.BOTH;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.OUT)) {
                returnType = ReturnType.VERTICES;
                queryDirection = Direction.OUT;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.IN)) {
                returnType = ReturnType.VERTICES;
                queryDirection = Direction.IN;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.BOTH)) {
                returnType = ReturnType.VERTICES;
                queryDirection = Direction.BOTH;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.BOTH_COUNT)) {
                returnType = ReturnType.COUNT;
                queryDirection = Direction.BOTH;
                countOnly = true;
            } else if (directionSegment.equals(Tokens.IN_COUNT)) {
                returnType = ReturnType.COUNT;
                queryDirection = Direction.IN;
                countOnly = true;
            } else if (directionSegment.equals(Tokens.OUT_COUNT)) {
                returnType = ReturnType.COUNT;
                queryDirection = Direction.OUT;
                countOnly = true;
            } else if (directionSegment.equals(Tokens.BOTH_IDS)) {
                returnType = ReturnType.VERTEX_IDS;
                queryDirection = Direction.BOTH;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.IN_IDS)) {
                returnType = ReturnType.VERTEX_IDS;
                queryDirection = Direction.IN;
                countOnly = false;
            } else if (directionSegment.equals(Tokens.OUT_IDS)) {
                returnType = ReturnType.VERTEX_IDS;
                queryDirection = Direction.OUT;
                countOnly = false;
            } else {
                final JSONObject error = generateErrorObject(directionSegment + " segment was invalid.");
                throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(error).build());
            }
        }

        public Direction getQueryDirection() {
            return queryDirection;
        }

        public ReturnType getReturnType() {
            return returnType;
        }

        public boolean isCountOnly() {
            return countOnly;
        }

    }
}
