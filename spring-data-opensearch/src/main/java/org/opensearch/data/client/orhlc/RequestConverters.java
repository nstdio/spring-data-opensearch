/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.data.client.orhlc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.lucene.util.BytesRef;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.storedscripts.DeleteStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.opensearch.action.admin.cluster.storedscripts.PutStoredScriptRequest;
import org.opensearch.action.admin.indices.alias.Alias;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.opensearch.action.admin.indices.close.CloseIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.flush.FlushRequest;
import org.opensearch.action.admin.indices.get.GetIndexRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.opensearch.action.admin.indices.open.OpenIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.opensearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.explain.ExplainRequest;
import org.opensearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.action.support.ActiveShardCount;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.WriteRequest.RefreshPolicy;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Request;
import org.opensearch.client.RethrottleRequest;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.indices.AnalyzeRequest;
import org.opensearch.client.indices.GetFieldMappingsRequest;
import org.opensearch.client.indices.GetIndexTemplatesRequest;
import org.opensearch.client.indices.IndexTemplatesExistRequest;
import org.opensearch.client.indices.PutIndexTemplateRequest;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.common.Priority;
import org.opensearch.common.SuppressForbidden;
import org.opensearch.common.lucene.uid.Versions;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.tasks.TaskId;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentHelper;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.VersionType;
import org.opensearch.index.rankeval.RankEvalRequest;
import org.opensearch.index.reindex.AbstractBulkByScrollRequest;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.index.reindex.ReindexRequest;
import org.opensearch.index.reindex.UpdateByQueryRequest;
import org.opensearch.index.seqno.SequenceNumbers;
import org.opensearch.script.mustache.SearchTemplateRequest;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.transport.client.Requests;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * <p>
 * Original implementation source {@link org.opensearch.client.RequestConverters},
 * {@link org.opensearch.client.IndicesRequestConverters} and
 * {@link org.opensearch.client.ClusterRequestConverters}. Only intended for internal use.
 * @since 0.1
 */
@SuppressWarnings("JavadocReference")
public class RequestConverters {

    private static final XContentType REQUEST_BODY_CONTENT_TYPE = XContentType.JSON;

    private RequestConverters() {
        // Contains only status utility methods
    }

    public static Request delete(DeleteRequest deleteRequest) {
        String endpoint = endpoint(deleteRequest.index(), deleteRequest.id());
        Request request = new Request(HttpMethod.DELETE.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withRouting(deleteRequest.routing());
        parameters.withTimeout(deleteRequest.timeout());
        parameters.withVersion(deleteRequest.version());
        parameters.withVersionType(deleteRequest.versionType());
        parameters.withIfSeqNo(deleteRequest.ifSeqNo());
        parameters.withIfPrimaryTerm(deleteRequest.ifPrimaryTerm());
        parameters.withRefreshPolicy(deleteRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(deleteRequest.waitForActiveShards());
        return request;
    }

    public static Request info() {
        return new Request(HttpMethod.GET.name(), "/");
    }

    public static Request bulk(BulkRequest bulkRequest) throws IOException {
        Request request = new Request(HttpMethod.POST.name(), "/_bulk");

        Params parameters = new Params(request);
        parameters.withTimeout(bulkRequest.timeout());
        parameters.withRefreshPolicy(bulkRequest.getRefreshPolicy());

        // parameters.withPipeline(bulkRequest.pipeline());
        // parameters.withRouting(bulkRequest.routing());

        // Bulk API only supports newline delimited JSON or Smile. Before executing
        // the bulk, we need to check that all requests have the same content-type
        // and this content-type is supported by the Bulk API.
        MediaType bulkMediaType = null;
        for (int i = 0; i < bulkRequest.numberOfActions(); i++) {
            DocWriteRequest<?> action = bulkRequest.requests().get(i);

            DocWriteRequest.OpType opType = action.opType();
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                bulkMediaType = enforceSameContentType((IndexRequest) action, bulkMediaType);

            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                UpdateRequest updateRequest = (UpdateRequest) action;
                if (updateRequest.doc() != null) {
                    bulkMediaType = enforceSameContentType(updateRequest.doc(), bulkMediaType);
                }
                if (updateRequest.upsertRequest() != null) {
                    bulkMediaType = enforceSameContentType(updateRequest.upsertRequest(), bulkMediaType);
                }
            }
        }

        if (bulkMediaType == null) {
            bulkMediaType = XContentType.JSON;
        }

        final byte separator = bulkMediaType.xContent().streamSeparator();
        final ContentType requestContentType = createContentType(bulkMediaType);

        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (DocWriteRequest<?> action : bulkRequest.requests()) {
            DocWriteRequest.OpType opType = action.opType();

            try (XContentBuilder metadata = XContentBuilder.builder(bulkMediaType.xContent())) {
                metadata.startObject();
                {
                    metadata.startObject(opType.getLowercase());
                    if (StringUtils.hasLength(action.index())) {
                        metadata.field("_index", action.index());
                    }
                    if (StringUtils.hasLength(action.id())) {
                        metadata.field("_id", action.id());
                    }
                    if (StringUtils.hasLength(action.routing())) {
                        metadata.field("routing", action.routing());
                    }
                    if (action.version() != Versions.MATCH_ANY) {
                        metadata.field("version", action.version());
                    }

                    VersionType versionType = action.versionType();
                    if (versionType != VersionType.INTERNAL) {
                        if (versionType == VersionType.EXTERNAL) {
                            metadata.field("version_type", "external");
                        } else if (versionType == VersionType.EXTERNAL_GTE) {
                            metadata.field("version_type", "external_gte");
                        }
                    }

                    if (action.ifSeqNo() != SequenceNumbers.UNASSIGNED_SEQ_NO) {
                        metadata.field("if_seq_no", action.ifSeqNo());
                        metadata.field("if_primary_term", action.ifPrimaryTerm());
                    }

                    if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                        IndexRequest indexRequest = (IndexRequest) action;
                        if (StringUtils.hasLength(indexRequest.getPipeline())) {
                            metadata.field("pipeline", indexRequest.getPipeline());
                        }
                    } else if (opType == DocWriteRequest.OpType.UPDATE) {
                        UpdateRequest updateRequest = (UpdateRequest) action;
                        if (updateRequest.retryOnConflict() > 0) {
                            metadata.field("retry_on_conflict", updateRequest.retryOnConflict());
                        }
                        if (updateRequest.fetchSource() != null) {
                            metadata.field("_source", updateRequest.fetchSource());
                        }
                    }
                    metadata.endObject();
                }
                metadata.endObject();

                BytesRef metadataSource = BytesReference.bytes(metadata).toBytesRef();
                content.write(metadataSource.bytes, metadataSource.offset, metadataSource.length);
                content.write(separator);
            }

            BytesRef source = null;
            if (opType == DocWriteRequest.OpType.INDEX || opType == DocWriteRequest.OpType.CREATE) {
                IndexRequest indexRequest = (IndexRequest) action;
                BytesReference indexSource = indexRequest.source();
                MediaType indexMediaType = indexRequest.getContentType();

                try (XContentParser parser = org.opensearch.common.xcontent.XContentHelper.createParser(
                        /*
                         * EMPTY and THROW are fine here because we just call
                         * copyCurrentStructure which doesn't touch the
                         * registry or deprecation.
                         */
                        NamedXContentRegistry.EMPTY,
                        DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                        indexSource,
                        indexMediaType)) {
                    try (XContentBuilder builder = XContentBuilder.builder(bulkMediaType.xContent())) {
                        builder.copyCurrentStructure(parser);
                        source = BytesReference.bytes(builder).toBytesRef();
                    }
                }
            } else if (opType == DocWriteRequest.OpType.UPDATE) {
                source = XContentHelper.toXContent((UpdateRequest) action, bulkMediaType, false)
                        .toBytesRef();
            }

            if (source != null) {
                content.write(source.bytes, source.offset, source.length);
                content.write(separator);
            }
        }
        request.setEntity(new ByteArrayEntity(content.toByteArray(), 0, content.size(), requestContentType));
        return request;
    }

    public static Request exists(GetRequest getRequest) {
        return getStyleRequest(HttpMethod.HEAD.name(), getRequest);
    }

    public static Request get(GetRequest getRequest) {
        return getStyleRequest(HttpMethod.GET.name(), getRequest);
    }

    private static Request getStyleRequest(String method, GetRequest getRequest) {
        Request request = new Request(method, endpoint(getRequest.index(), getRequest.id()));

        Params parameters = new Params(request);
        parameters.withPreference(getRequest.preference());
        parameters.withRouting(getRequest.routing());
        parameters.withRefresh(getRequest.refresh());
        parameters.withRealtime(getRequest.realtime());
        parameters.withStoredFields(getRequest.storedFields());
        parameters.withVersion(getRequest.version());
        parameters.withVersionType(getRequest.versionType());
        parameters.withFetchSourceContext(getRequest.fetchSourceContext());

        return request;
    }

    public static Request sourceExists(GetRequest getRequest) {
        Request request = new Request(HttpMethod.HEAD.name(), endpoint(getRequest.index(), getRequest.id(), "_source"));

        Params parameters = new Params(request);
        parameters.withPreference(getRequest.preference());
        parameters.withRouting(getRequest.routing());
        parameters.withRefresh(getRequest.refresh());
        parameters.withRealtime(getRequest.realtime());
        // Version params are not currently supported by the source exists API so are not passed
        return request;
    }

    public static Request multiGet(MultiGetRequest multiGetRequest) {
        Request request = new Request(HttpMethod.POST.name(), "/_mget");

        Params parameters = new Params(request);
        parameters.withPreference(multiGetRequest.preference());
        parameters.withRealtime(multiGetRequest.realtime());
        parameters.withRefresh(multiGetRequest.refresh());

        request.setEntity(createEntity(multiGetRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request index(IndexRequest indexRequest) {
        String method = StringUtils.hasLength(indexRequest.id()) ? HttpMethod.PUT.name() : HttpMethod.POST.name();
        boolean isCreate = (indexRequest.opType() == DocWriteRequest.OpType.CREATE);
        String endpoint = endpoint(indexRequest.index(), indexRequest.id(), isCreate ? "_create" : null);
        Request request = new Request(method, endpoint);

        Params parameters = new Params(request);
        parameters.withRouting(indexRequest.routing());
        parameters.withTimeout(indexRequest.timeout());
        parameters.withVersion(indexRequest.version());
        parameters.withVersionType(indexRequest.versionType());
        parameters.withIfSeqNo(indexRequest.ifSeqNo());
        parameters.withIfPrimaryTerm(indexRequest.ifPrimaryTerm());
        parameters.withPipeline(indexRequest.getPipeline());
        parameters.withRefreshPolicy(indexRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(indexRequest.waitForActiveShards());

        BytesRef source = indexRequest.source().toBytesRef();
        ContentType contentType = createContentType(indexRequest.getContentType());
        request.setEntity(new ByteArrayEntity(source.bytes, source.offset, source.length, contentType));
        return request;
    }

    public static Request ping() {
        return new Request(HttpMethod.HEAD.name(), "/");
    }

    public static Request update(UpdateRequest updateRequest) {
        String endpoint = endpoint(updateRequest.index(), updateRequest.id(), "_update");
        Request request = new Request(HttpMethod.POST.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withRouting(updateRequest.routing());
        parameters.withTimeout(updateRequest.timeout());
        parameters.withRefreshPolicy(updateRequest.getRefreshPolicy());
        parameters.withWaitForActiveShards(updateRequest.waitForActiveShards());
        parameters.withDocAsUpsert(updateRequest.docAsUpsert());
        parameters.withFetchSourceContext(updateRequest.fetchSource());
        parameters.withRetryOnConflict(updateRequest.retryOnConflict());
        parameters.withVersion(updateRequest.version());
        parameters.withVersionType(updateRequest.versionType());

        // The Java API allows update requests with different content types
        // set for the partial document and the upsert document. This client
        // only accepts update requests that have the same content types set
        // for both doc and upsert.
        MediaType mediaType = null;
        if (updateRequest.doc() != null) {
            mediaType = updateRequest.doc().getContentType();
        }
        if (updateRequest.upsertRequest() != null) {
            MediaType upsertMediaType = updateRequest.upsertRequest().getContentType();
            if ((mediaType != null) && (mediaType != upsertMediaType)) {
                throw new IllegalStateException("Update request cannot have different content types for doc ["
                        + mediaType + ']' + " and upsert [" + upsertMediaType + "] documents");
            } else {
                mediaType = upsertMediaType;
            }
        }
        if (mediaType == null) {
            mediaType = Requests.INDEX_CONTENT_TYPE;
        }
        request.setEntity(createEntity(updateRequest, mediaType));
        return request;
    }

    public static Request search(SearchRequest searchRequest) {
        Request request = new Request(HttpMethod.POST.name(), endpoint(searchRequest.indices(), "_search"));

        Params params = new Params(request);
        addSearchRequestParams(params, searchRequest);

        if (searchRequest.source() != null) {
            request.setEntity(createEntity(searchRequest.source(), REQUEST_BODY_CONTENT_TYPE));
        }
        return request;
    }

    public static Request searchTemplate(SearchTemplateRequest templateRequest) {
        SearchRequest searchRequest = templateRequest.getRequest();

        String endpoint = new EndpointBuilder()
                .addCommaSeparatedPathParts(templateRequest.getRequest().indices())
                .addPathPart("_search")
                .addPathPart("template")
                .build();

        Request request = new Request(HttpMethod.GET.name(), endpoint);
        Params params = new Params(request);
        addSearchRequestParams(params, searchRequest);

        request.setEntity(createEntity(templateRequest, REQUEST_BODY_CONTENT_TYPE));

        return request;
    }

    /**
     * Creates a count request.
     *
     * @param countRequest the search defining the data to be counted
     * @return Elasticsearch count request
     * @since 4.0
     */
    public static Request count(CountRequest countRequest) {
        Request request =
                new Request(HttpMethod.POST.name(), endpoint(countRequest.indices(), countRequest.types(), "_count"));

        Params params = new Params(request);
        addCountRequestParams(params, countRequest);

        if (countRequest.source() != null) {
            request.setEntity(createEntity(countRequest.source(), REQUEST_BODY_CONTENT_TYPE));
        }
        return request;
    }

    private static void addCountRequestParams(Params params, CountRequest countRequest) {
        params.withRouting(countRequest.routing());
        params.withPreference(countRequest.preference());
        params.withIndicesOptions(countRequest.indicesOptions());
    }

    private static void addSearchRequestParams(Params params, SearchRequest searchRequest) {
        params.putParam("typed_keys", "true");
        params.withRouting(searchRequest.routing());
        params.withPreference(searchRequest.preference());
        params.withIndicesOptions(searchRequest.indicesOptions());
        params.putParam("search_type", searchRequest.searchType().name().toLowerCase(Locale.ROOT));
        if (searchRequest.requestCache() != null) {
            params.putParam("request_cache", Boolean.toString(searchRequest.requestCache()));
        }
        if (searchRequest.allowPartialSearchResults() != null) {
            params.putParam(
                    "allow_partial_search_results", Boolean.toString(searchRequest.allowPartialSearchResults()));
        }
        params.putParam("batched_reduce_size", Integer.toString(searchRequest.getBatchedReduceSize()));
        if (searchRequest.scroll() != null) {
            params.putParam("scroll", searchRequest.scroll().keepAlive());
        }
    }

    public static Request searchScroll(SearchScrollRequest searchScrollRequest) {
        Request request = new Request(HttpMethod.POST.name(), "/_search/scroll");
        request.setEntity(createEntity(searchScrollRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request clearScroll(ClearScrollRequest clearScrollRequest) {
        Request request = new Request(HttpMethod.DELETE.name(), "/_search/scroll");
        request.setEntity(createEntity(clearScrollRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request multiSearch(MultiSearchRequest multiSearchRequest) throws IOException {
        Request request = new Request(HttpMethod.POST.name(), "/_msearch");

        Params params = new Params(request);
        params.putParam("typed_keys", "true");
        if (multiSearchRequest.maxConcurrentSearchRequests()
                != MultiSearchRequest.MAX_CONCURRENT_SEARCH_REQUESTS_DEFAULT) {
            params.putParam(
                    "max_concurrent_searches", Integer.toString(multiSearchRequest.maxConcurrentSearchRequests()));
        }

        XContent xContent = REQUEST_BODY_CONTENT_TYPE.xContent();
        byte[] source = MultiSearchRequest.writeMultiLineFormat(multiSearchRequest, xContent);
        request.setEntity(new ByteArrayEntity(source, createContentType(xContent.mediaType())));
        return request;
    }

    public static Request explain(ExplainRequest explainRequest) {
        Request request =
                new Request(HttpMethod.GET.name(), endpoint(explainRequest.index(), explainRequest.id(), "_explain"));

        Params params = new Params(request);
        params.withStoredFields(explainRequest.storedFields());
        params.withFetchSourceContext(explainRequest.fetchSourceContext());
        params.withRouting(explainRequest.routing());
        params.withPreference(explainRequest.preference());
        request.setEntity(createEntity(explainRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request fieldCaps(FieldCapabilitiesRequest fieldCapabilitiesRequest) {
        Request request =
                new Request(HttpMethod.GET.name(), endpoint(fieldCapabilitiesRequest.indices(), "_field_caps"));

        Params params = new Params(request);
        params.withFields(fieldCapabilitiesRequest.fields());
        params.withIndicesOptions(fieldCapabilitiesRequest.indicesOptions());
        return request;
    }

    public static Request rankEval(RankEvalRequest rankEvalRequest) {
        Request request = new Request(
                HttpMethod.GET.name(), endpoint(rankEvalRequest.indices(), Strings.EMPTY_ARRAY, "_rank_eval"));

        Params params = new Params(request);
        params.withIndicesOptions(rankEvalRequest.indicesOptions());

        request.setEntity(createEntity(rankEvalRequest.getRankEvalSpec(), REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request reindex(ReindexRequest reindexRequest) {
        return prepareReindexRequest(reindexRequest, true);
    }

    public static Request submitReindex(ReindexRequest reindexRequest) {
        return prepareReindexRequest(reindexRequest, false);
    }

    private static Request prepareReindexRequest(ReindexRequest reindexRequest, boolean waitForCompletion) {
        String endpoint = new EndpointBuilder().addPathPart("_reindex").build();
        Request request = new Request(HttpMethod.POST.name(), endpoint);
        Params params = new Params(request)
                .withWaitForCompletion(waitForCompletion)
                .withRefresh(reindexRequest.isRefresh())
                .withTimeout(reindexRequest.getTimeout())
                .withWaitForActiveShards(reindexRequest.getWaitForActiveShards())
                .withRequestsPerSecond(reindexRequest.getRequestsPerSecond());

        if (reindexRequest.getDestination().isRequireAlias()) {
            params.putParam("require_alias", Boolean.TRUE.toString());
        }

        if (reindexRequest.getScrollTime() != null) {
            params.putParam("scroll", reindexRequest.getScrollTime());
        }

        params.putParam("slices", Integer.toString(reindexRequest.getSlices()));

        if (reindexRequest.getMaxDocs() > -1) {
            params.putParam("max_docs", Integer.toString(reindexRequest.getMaxDocs()));
        }
        request.setEntity(createEntity(reindexRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request updateByQuery(UpdateByQueryRequest updateByQueryRequest) {
        String endpoint = endpoint(updateByQueryRequest.indices(), "_update_by_query");
        Request request = new Request(HttpMethod.POST.name(), endpoint);
        Params params = new Params(request)
                .withRouting(updateByQueryRequest.getRouting()) //
                .withPipeline(updateByQueryRequest.getPipeline()) //
                .withRefresh(updateByQueryRequest.isRefresh()) //
                .withTimeout(updateByQueryRequest.getTimeout()) //
                .withWaitForActiveShards(updateByQueryRequest.getWaitForActiveShards()) //
                .withRequestsPerSecond(updateByQueryRequest.getRequestsPerSecond()) //
                .withIndicesOptions(updateByQueryRequest.indicesOptions()); //

        if (!updateByQueryRequest.isAbortOnVersionConflict()) {
            params.putParam("conflicts", "proceed");
        }

        if (updateByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(updateByQueryRequest.getBatchSize()));
        }

        if (updateByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", updateByQueryRequest.getScrollTime());
        }

        if (updateByQueryRequest.getMaxDocs() > 0) {
            params.putParam("max_docs", Integer.toString(updateByQueryRequest.getMaxDocs()));
        }

        request.setEntity(createEntity(updateByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request deleteByQuery(DeleteByQueryRequest deleteByQueryRequest) {
        String endpoint = endpoint(deleteByQueryRequest.indices(), "_delete_by_query");
        Request request = new Request(HttpMethod.POST.name(), endpoint);
        Params params = new Params(request)
                .withRouting(deleteByQueryRequest.getRouting())
                .withRefresh(deleteByQueryRequest.isRefresh())
                .withTimeout(deleteByQueryRequest.getTimeout())
                .withWaitForActiveShards(deleteByQueryRequest.getWaitForActiveShards())
                .withRequestsPerSecond(deleteByQueryRequest.getRequestsPerSecond())
                .withIndicesOptions(deleteByQueryRequest.indicesOptions());
        if (!deleteByQueryRequest.isAbortOnVersionConflict()) {
            params.putParam("conflicts", "proceed");
        }
        if (deleteByQueryRequest.getBatchSize() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_SIZE) {
            params.putParam("scroll_size", Integer.toString(deleteByQueryRequest.getBatchSize()));
        }
        if (deleteByQueryRequest.getScrollTime() != AbstractBulkByScrollRequest.DEFAULT_SCROLL_TIMEOUT) {
            params.putParam("scroll", deleteByQueryRequest.getScrollTime());
        }
        if (deleteByQueryRequest.getSize() > 0) {
            params.putParam("size", Integer.toString(deleteByQueryRequest.getSize()));
        }
        request.setEntity(createEntity(deleteByQueryRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request rethrottleReindex(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_reindex");
    }

    public static Request rethrottleUpdateByQuery(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_update_by_query");
    }

    public static Request rethrottleDeleteByQuery(RethrottleRequest rethrottleRequest) {
        return rethrottle(rethrottleRequest, "_delete_by_query");
    }

    private static Request rethrottle(RethrottleRequest rethrottleRequest, String firstPathPart) {
        String endpoint = new EndpointBuilder()
                .addPathPart(firstPathPart)
                .addPathPart(rethrottleRequest.getTaskId().toString())
                .addPathPart("_rethrottle")
                .build();
        Request request = new Request(HttpMethod.POST.name(), endpoint);
        Params params = new Params(request).withRequestsPerSecond(rethrottleRequest.getRequestsPerSecond());
        // we set "group_by" to "none" because this is the response format we can parse back
        params.putParam("group_by", "none");
        return request;
    }

    public static Request putScript(PutStoredScriptRequest putStoredScriptRequest) {
        String endpoint = new EndpointBuilder()
                .addPathPartAsIs("_scripts")
                .addPathPart(putStoredScriptRequest.id())
                .build();
        Request request = new Request(HttpMethod.POST.name(), endpoint);
        Params params = new Params(request);
        params.withTimeout(putStoredScriptRequest.timeout());
        params.withMasterTimeout(putStoredScriptRequest.masterNodeTimeout());
        if (StringUtils.hasText(putStoredScriptRequest.context())) {
            params.putParam("context", putStoredScriptRequest.context());
        }
        request.setEntity(createEntity(putStoredScriptRequest, REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request analyze(AnalyzeRequest request) {
        EndpointBuilder builder = new EndpointBuilder();
        String index = request.index();
        if (index != null) {
            builder.addPathPart(index);
        }
        builder.addPathPartAsIs("_analyze");
        Request req = new Request(HttpMethod.GET.name(), builder.build());
        req.setEntity(createEntity(request, REQUEST_BODY_CONTENT_TYPE));
        return req;
    }

    // static Request termVectors(TermVectorsRequest request) throws IOException {
    // String endpoint = new EndpointBuilder().addPathPart(request.index(), request.type(), request.id())
    // .addPathPartAsIs("_termvectors").build();
    //
    // Request req = new Request(HttpMethod.GET.name(), endpoint);
    // Params params = new Params(req);
    // params.withRouting(request.routing());
    // params.withPreference(request.preference());
    // params.withFields(request.selectedFields().toArray(new String[0]));
    // params.withRealtime(request.realtime());
    //
    // req.setEntity(createEntity(request, REQUEST_BODY_CONTENT_TYPE));
    // return req;
    // }

    public static Request getScript(GetStoredScriptRequest getStoredScriptRequest) {
        String endpoint = new EndpointBuilder()
                .addPathPartAsIs("_scripts")
                .addPathPart(getStoredScriptRequest.id())
                .build();
        Request request = new Request(HttpMethod.GET.name(), endpoint);
        Params params = new Params(request);
        params.withMasterTimeout(getStoredScriptRequest.masterNodeTimeout());
        return request;
    }

    public static Request deleteScript(DeleteStoredScriptRequest deleteStoredScriptRequest) {
        String endpoint = new EndpointBuilder()
                .addPathPartAsIs("_scripts")
                .addPathPart(deleteStoredScriptRequest.id())
                .build();
        Request request = new Request(HttpMethod.DELETE.name(), endpoint);
        Params params = new Params(request);
        params.withTimeout(deleteStoredScriptRequest.timeout());
        params.withMasterTimeout(deleteStoredScriptRequest.masterNodeTimeout());
        return request;
    }

    // --> INDICES

    public static Request getIndex(GetIndexRequest getIndexRequest) {
        String[] indices = getIndexRequest.indices() == null ? Strings.EMPTY_ARRAY : getIndexRequest.indices();

        String endpoint = endpoint(indices);
        Request request = new Request(HttpMethod.GET.name(), endpoint);

        Params params = new Params(request);
        params.withIndicesOptions(getIndexRequest.indicesOptions());
        params.withLocal(getIndexRequest.local());
        params.withIncludeDefaults(getIndexRequest.includeDefaults());
        params.withHuman(getIndexRequest.humanReadable());
        params.withMasterTimeout(getIndexRequest.masterNodeTimeout());

        return request;
    }

    public static Request getIndex(org.opensearch.client.indices.GetIndexRequest getIndexRequest) {
        String[] indices = getIndexRequest.indices() == null ? Strings.EMPTY_ARRAY : getIndexRequest.indices();

        String endpoint = endpoint(indices);
        Request request = new Request(HttpMethod.GET.name(), endpoint);

        Params params = new Params(request);
        params.withIndicesOptions(getIndexRequest.indicesOptions());
        params.withLocal(getIndexRequest.local());
        params.withIncludeDefaults(getIndexRequest.includeDefaults());
        params.withHuman(getIndexRequest.humanReadable());
        params.withMasterTimeout(getIndexRequest.clusterManagerNodeTimeout());

        return request;
    }

    public static Request indexDelete(DeleteIndexRequest deleteIndexRequest) {
        String endpoint = RequestConverters.endpoint(deleteIndexRequest.indices());
        Request request = new Request(HttpMethod.DELETE.name(), endpoint);

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withTimeout(deleteIndexRequest.timeout());
        parameters.withMasterTimeout(deleteIndexRequest.masterNodeTimeout());
        parameters.withIndicesOptions(deleteIndexRequest.indicesOptions());
        return request;
    }

    public static Request indexExists(GetIndexRequest getIndexRequest) {
        // this can be called with no indices as argument by transport client, not via REST though
        if (getIndexRequest.indices() == null || getIndexRequest.indices().length == 0) {
            throw new IllegalArgumentException("indices are mandatory");
        }
        String endpoint = endpoint(getIndexRequest.indices(), "");
        Request request = new Request(HttpMethod.HEAD.name(), endpoint);

        Params params = new Params(request);
        params.withLocal(getIndexRequest.local());
        params.withHuman(getIndexRequest.humanReadable());
        params.withIndicesOptions(getIndexRequest.indicesOptions());
        params.withIncludeDefaults(getIndexRequest.includeDefaults());
        return request;
    }

    public static Request indexExists(org.opensearch.client.indices.GetIndexRequest getIndexRequest) {
        // this can be called with no indices as argument by transport client, not via REST though
        if (getIndexRequest.indices() == null || getIndexRequest.indices().length == 0) {
            throw new IllegalArgumentException("indices are mandatory");
        }
        String endpoint = endpoint(getIndexRequest.indices(), "");
        Request request = new Request(HttpMethod.HEAD.name(), endpoint);

        Params params = new Params(request);
        params.withLocal(getIndexRequest.local());
        params.withHuman(getIndexRequest.humanReadable());
        params.withIndicesOptions(getIndexRequest.indicesOptions());
        params.withIncludeDefaults(getIndexRequest.includeDefaults());
        return request;
    }

    public static Request indexOpen(OpenIndexRequest openIndexRequest) {
        String endpoint = RequestConverters.endpoint(openIndexRequest.indices(), "_open");
        Request request = new Request(HttpMethod.POST.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withTimeout(openIndexRequest.timeout());
        parameters.withMasterTimeout(openIndexRequest.masterNodeTimeout());
        parameters.withWaitForActiveShards(openIndexRequest.waitForActiveShards(), ActiveShardCount.NONE);
        parameters.withIndicesOptions(openIndexRequest.indicesOptions());
        return request;
    }

    public static Request indexClose(CloseIndexRequest closeIndexRequest) {
        String endpoint = RequestConverters.endpoint(closeIndexRequest.indices(), "_close");
        Request request = new Request(HttpMethod.POST.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withTimeout(closeIndexRequest.timeout());
        parameters.withMasterTimeout(closeIndexRequest.masterNodeTimeout());
        parameters.withIndicesOptions(closeIndexRequest.indicesOptions());
        return request;
    }

    public static Request indexCreate(CreateIndexRequest createIndexRequest) {
        String endpoint = RequestConverters.endpoint(createIndexRequest.indices());
        Request request = new Request(HttpMethod.PUT.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withTimeout(createIndexRequest.timeout());
        parameters.withMasterTimeout(createIndexRequest.masterNodeTimeout());
        parameters.withWaitForActiveShards(createIndexRequest.waitForActiveShards(), ActiveShardCount.DEFAULT);

        request.setEntity(createEntity(
                new ToXContent() {
                    @Override
                    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                        builder.startObject();

                        builder.startObject(CreateIndexRequest.SETTINGS.getPreferredName());
                        createIndexRequest.settings().toXContent(builder, params);
                        builder.endObject();

                        builder.startObject(CreateIndexRequest.MAPPINGS.getPreferredName());
                        try (InputStream stream = new BytesArray(createIndexRequest.mappings()).streamInput()) {
                            builder.rawValue(stream, XContentType.JSON);
                        }
                        builder.endObject();

                        builder.startObject(CreateIndexRequest.ALIASES.getPreferredName());
                        for (Alias alias : createIndexRequest.aliases()) {
                            alias.toXContent(builder, params);
                        }
                        builder.endObject();

                        builder.endObject();
                        return builder;
                    }
                },
                RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request indexCreate(org.opensearch.client.indices.CreateIndexRequest createIndexRequest) {
        String endpoint = RequestConverters.endpoint(new String[] {createIndexRequest.index()});
        Request request = new Request(HttpMethod.PUT.name(), endpoint);

        Params parameters = new Params(request);
        parameters.withTimeout(createIndexRequest.timeout());
        parameters.withMasterTimeout(createIndexRequest.clusterManagerNodeTimeout());
        parameters.withWaitForActiveShards(createIndexRequest.waitForActiveShards(), ActiveShardCount.DEFAULT);

        request.setEntity(createEntity(createIndexRequest, RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request indexRefresh(RefreshRequest refreshRequest) {

        String[] indices = refreshRequest.indices() == null ? Strings.EMPTY_ARRAY : refreshRequest.indices();
        // using a GET here as reactor-netty set the transfer-encoding to chunked on POST requests which blocks on
        // Elasticsearch when no body is sent.
        Request request = new Request(HttpMethod.GET.name(), RequestConverters.endpoint(indices, "_refresh"));

        Params parameters = new Params(request);
        parameters.withIndicesOptions(refreshRequest.indicesOptions());
        return request;
    }

    @Deprecated
    public static Request putMapping(PutMappingRequest putMappingRequest) {
        // The concreteIndex is an internal concept, not applicable to requests made over the REST API.
        if (putMappingRequest.getConcreteIndex() != null) {
            throw new IllegalArgumentException(
                    "concreteIndex cannot be set on PutMapping requests made over the REST API");
        }

        Request request =
                new Request(HttpMethod.PUT.name(), RequestConverters.endpoint(putMappingRequest.indices(), "_mapping"));

        RequestConverters.Params parameters = new RequestConverters.Params(request) //
                .withTimeout(putMappingRequest.timeout()) //
                .withMasterTimeout(putMappingRequest.masterNodeTimeout());
        request.setEntity(
                RequestConverters.createEntity(putMappingRequest, RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request putMapping(org.opensearch.client.indices.PutMappingRequest putMappingRequest) {
        Request request =
                new Request(HttpMethod.PUT.name(), RequestConverters.endpoint(putMappingRequest.indices(), "_mapping"));

        new RequestConverters.Params(request) //
                .withTimeout(putMappingRequest.timeout()) //
                .withMasterTimeout(putMappingRequest.clusterManagerNodeTimeout());
        request.setEntity(
                RequestConverters.createEntity(putMappingRequest, RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request flushIndex(FlushRequest flushRequest) {
        String[] indices = flushRequest.indices() == null ? Strings.EMPTY_ARRAY : flushRequest.indices();
        Request request = new Request(HttpMethod.POST.name(), RequestConverters.endpoint(indices, "_flush"));

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withIndicesOptions(flushRequest.indicesOptions());
        parameters.putParam("wait_if_ongoing", Boolean.toString(flushRequest.waitIfOngoing()));
        parameters.putParam("force", Boolean.toString(flushRequest.force()));
        return request;
    }

    public static Request getMapping(GetMappingsRequest getMappingsRequest) {
        String[] indices = getMappingsRequest.indices() == null ? Strings.EMPTY_ARRAY : getMappingsRequest.indices();

        Request request = new Request(HttpMethod.GET.name(), RequestConverters.endpoint(indices, "_mapping"));

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withMasterTimeout(getMappingsRequest.masterNodeTimeout());
        parameters.withIndicesOptions(getMappingsRequest.indicesOptions());
        parameters.withLocal(getMappingsRequest.local());
        return request;
    }

    public static Request getMapping(org.opensearch.client.indices.GetMappingsRequest getMappingsRequest) {
        String[] indices = getMappingsRequest.indices() == null ? Strings.EMPTY_ARRAY : getMappingsRequest.indices();

        Request request = new Request(HttpMethod.GET.name(), RequestConverters.endpoint(indices, "_mapping"));

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withMasterTimeout(getMappingsRequest.clusterManagerNodeTimeout());
        parameters.withIndicesOptions(getMappingsRequest.indicesOptions());
        parameters.withLocal(getMappingsRequest.local());
        return request;
    }

    public static Request getSettings(GetSettingsRequest getSettingsRequest) {
        String[] indices = getSettingsRequest.indices() == null ? Strings.EMPTY_ARRAY : getSettingsRequest.indices();
        String[] names = getSettingsRequest.names() == null ? Strings.EMPTY_ARRAY : getSettingsRequest.names();

        Request request = new Request(HttpMethod.GET.name(), RequestConverters.endpoint(indices, "_settings", names));

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withIndicesOptions(getSettingsRequest.indicesOptions());
        parameters.withLocal(getSettingsRequest.local());
        parameters.withIncludeDefaults(getSettingsRequest.includeDefaults());
        parameters.withMasterTimeout(getSettingsRequest.masterNodeTimeout());
        return request;
    }

    public static Request updateAliases(IndicesAliasesRequest indicesAliasesRequest) {
        Request request = new Request(HttpPost.METHOD_NAME, "/_aliases");

        RequestConverters.Params parameters = new RequestConverters.Params(request);
        parameters.withTimeout(indicesAliasesRequest.timeout());
        parameters.withMasterTimeout(indicesAliasesRequest.masterNodeTimeout());
        request.setEntity(
                RequestConverters.createEntity(indicesAliasesRequest, RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request getAlias(GetAliasesRequest getAliasesRequest) {

        String[] indices = getAliasesRequest.indices() == null ? Strings.EMPTY_ARRAY : getAliasesRequest.indices();
        String[] aliases = getAliasesRequest.aliases() == null ? Strings.EMPTY_ARRAY : getAliasesRequest.aliases();
        String endpoint = RequestConverters.endpoint(indices, "_alias", aliases);

        Request request = new Request(HttpGet.METHOD_NAME, endpoint);

        RequestConverters.Params params = new RequestConverters.Params(request);
        params.withIndicesOptions(getAliasesRequest.indicesOptions());
        params.withLocal(getAliasesRequest.local());
        return request;
    }

    public static Request putTemplate(PutIndexTemplateRequest putIndexTemplateRequest) {
        String endpoint = (new RequestConverters.EndpointBuilder()) //
                .addPathPartAsIs("_template") //
                .addPathPart(putIndexTemplateRequest.name()) //
                .build(); //

        Request request = new Request(HttpPut.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params(request);
        params.withMasterTimeout(putIndexTemplateRequest.masterNodeTimeout());
        if (putIndexTemplateRequest.create()) {
            params.putParam("create", Boolean.TRUE.toString());
        }

        if (StringUtils.hasText(putIndexTemplateRequest.cause())) {
            params.putParam("cause", putIndexTemplateRequest.cause());
        }

        request.setEntity(
                RequestConverters.createEntity(putIndexTemplateRequest, RequestConverters.REQUEST_BODY_CONTENT_TYPE));
        return request;
    }

    public static Request getTemplates(GetIndexTemplatesRequest getIndexTemplatesRequest) {
        final String endpoint = new RequestConverters.EndpointBuilder()
                .addPathPartAsIs("_template")
                .addCommaSeparatedPathParts(getIndexTemplatesRequest.names())
                .build();
        final Request request = new Request(HttpGet.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params(request);
        params.withLocal(getIndexTemplatesRequest.isLocal());
        params.withMasterTimeout(getIndexTemplatesRequest.getClusterManagerNodeTimeout());
        return request;
    }

    public static Request templatesExist(IndexTemplatesExistRequest indexTemplatesExistRequest) {
        final String endpoint = new RequestConverters.EndpointBuilder()
                .addPathPartAsIs("_template")
                .addCommaSeparatedPathParts(indexTemplatesExistRequest.names())
                .build();
        final Request request = new Request(HttpHead.METHOD_NAME, endpoint);
        final RequestConverters.Params params = new RequestConverters.Params(request);
        params.withLocal(indexTemplatesExistRequest.isLocal());
        params.withMasterTimeout(indexTemplatesExistRequest.getClusterManagerNodeTimeout());
        return request;
    }

    public static Request deleteTemplate(DeleteIndexTemplateRequest deleteIndexTemplateRequest) {
        String name = deleteIndexTemplateRequest.name();
        String endpoint = new RequestConverters.EndpointBuilder()
                .addPathPartAsIs("_template")
                .addPathPart(name)
                .build();
        Request request = new Request(HttpDelete.METHOD_NAME, endpoint);
        RequestConverters.Params params = new RequestConverters.Params(request);
        params.withMasterTimeout(deleteIndexTemplateRequest.masterNodeTimeout());
        return request;
    }

    public static Request getFieldMapping(GetFieldMappingsRequest getFieldMappingsRequest) {
        String[] indices =
                getFieldMappingsRequest.indices() == null ? Strings.EMPTY_ARRAY : getFieldMappingsRequest.indices();
        String[] fields =
                getFieldMappingsRequest.fields() == null ? Strings.EMPTY_ARRAY : getFieldMappingsRequest.fields();

        final String endpoint = new EndpointBuilder()
                .addCommaSeparatedPathParts(indices)
                .addPathPartAsIs("_mapping")
                .addPathPartAsIs("field")
                .addCommaSeparatedPathParts(fields)
                .build();

        Request request = new Request(HttpMethod.GET.name(), endpoint);

        RequestConverters.Params parameters = new Params(request);
        parameters.withIndicesOptions(getFieldMappingsRequest.indicesOptions());
        parameters.withIncludeDefaults(getFieldMappingsRequest.includeDefaults());
        return request;
    }

    public static Request clusterHealth(ClusterHealthRequest healthRequest) {
        String[] indices = healthRequest.indices() == null ? Strings.EMPTY_ARRAY : healthRequest.indices();
        String endpoint = new EndpointBuilder()
                .addPathPartAsIs(new String[] {"_cluster/health"})
                .addCommaSeparatedPathParts(indices)
                .build();

        Request request = new Request("GET", endpoint);

        RequestConverters.Params parameters = new Params(request);
        parameters.withWaitForStatus(healthRequest.waitForStatus());
        parameters.withWaitForNoRelocatingShards(healthRequest.waitForNoRelocatingShards());
        parameters.withWaitForNoInitializingShards(healthRequest.waitForNoInitializingShards());
        parameters.withWaitForActiveShards(healthRequest.waitForActiveShards(), ActiveShardCount.NONE);
        parameters.withWaitForNodes(healthRequest.waitForNodes());
        parameters.withWaitForEvents(healthRequest.waitForEvents());
        parameters.withTimeout(healthRequest.timeout());
        parameters.withMasterTimeout(healthRequest.masterNodeTimeout());
        parameters.withLocal(healthRequest.local()).withLevel(healthRequest.level());
        return request;
    }

    static HttpEntity createEntity(ToXContent toXContent, MediaType xContentType) {

        try {
            BytesRef source =
                    XContentHelper.toXContent(toXContent, xContentType, false).toBytesRef();
            return new ByteArrayEntity(source.bytes, source.offset, source.length, createContentType(xContentType));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String endpoint(String index, String id) {
        return new EndpointBuilder().addPathPart(index, id).build();
    }

    static String endpoint(String index, String id, String endpoint) {
        return new EndpointBuilder()
                .addPathPart(index, id)
                .addPathPartAsIs(endpoint)
                .build();
    }

    static String endpoint(String[] indices) {
        return new EndpointBuilder().addCommaSeparatedPathParts(indices).build();
    }

    static String endpoint(String[] indices, String endpoint) {
        return new EndpointBuilder()
                .addCommaSeparatedPathParts(indices)
                .addPathPartAsIs(endpoint)
                .build();
    }

    static String endpoint(String[] indices, String[] types, String endpoint) {
        return new EndpointBuilder()
                .addCommaSeparatedPathParts(indices)
                .addCommaSeparatedPathParts(types)
                .addPathPartAsIs(endpoint)
                .build();
    }

    static String endpoint(String[] indices, String endpoint, String[] suffixes) {
        return new EndpointBuilder()
                .addCommaSeparatedPathParts(indices)
                .addPathPartAsIs(endpoint)
                .addCommaSeparatedPathParts(suffixes)
                .build();
    }

    static String endpoint(String[] indices, String endpoint, String type) {
        return new EndpointBuilder()
                .addCommaSeparatedPathParts(indices)
                .addPathPartAsIs(endpoint)
                .addPathPart(type)
                .build();
    }

    /**
     * Returns a {@link ContentType} from a given {@link XContentType}.
     *
     * @param mediaType the {@link MediaType}
     * @return the {@link ContentType}
     */
    @SuppressForbidden(reason = "Only allowed place to convert a XContentType to a ContentType")
    public static ContentType createContentType(final MediaType mediaType) {
        return ContentType.create(mediaType.mediaTypeWithoutParameters(), (Charset) null);
    }

    /**
     * Utility class to help with common parameter names and patterns. Wraps a {@link Request} and adds the parameters to
     * it directly.
     */
    static class Params {
        private final Request request;

        Params(Request request) {
            this.request = request;
        }

        Params putParam(String name, String value) {
            if (StringUtils.hasLength(value)) {
                request.addParameter(name, value);
            }
            return this;
        }

        Params putParam(String key, TimeValue value) {
            if (value != null) {
                return putParam(key, value.getStringRep());
            }
            return this;
        }

        Params withDocAsUpsert(boolean docAsUpsert) {
            if (docAsUpsert) {
                return putParam("doc_as_upsert", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withFetchSourceContext(FetchSourceContext fetchSourceContext) {
            if (fetchSourceContext != null) {
                if (!fetchSourceContext.fetchSource()) {
                    putParam("_source", Boolean.FALSE.toString());
                }
                if (fetchSourceContext.includes() != null && fetchSourceContext.includes().length > 0) {
                    putParam("_source_includes", String.join(",", fetchSourceContext.includes()));
                }
                if (fetchSourceContext.excludes() != null && fetchSourceContext.excludes().length > 0) {
                    putParam("_source_excludes", String.join(",", fetchSourceContext.excludes()));
                }
            }
            return this;
        }

        Params withFields(String[] fields) {
            if (fields != null && fields.length > 0) {
                return putParam("fields", String.join(",", fields));
            }
            return this;
        }

        Params withMasterTimeout(TimeValue masterTimeout) {
            return putParam("master_timeout", masterTimeout);
        }

        Params withPipeline(String pipeline) {
            return putParam("pipeline", pipeline);
        }

        Params withPreference(String preference) {
            return putParam("preference", preference);
        }

        Params withRealtime(boolean realtime) {
            if (!realtime) {
                return putParam("realtime", Boolean.FALSE.toString());
            }
            return this;
        }

        Params withRefresh(boolean refresh) {
            if (refresh) {
                return withRefreshPolicy(RefreshPolicy.IMMEDIATE);
            }
            return this;
        }

        Params withRefreshPolicy(RefreshPolicy refreshPolicy) {
            if (refreshPolicy != RefreshPolicy.NONE) {
                return putParam("refresh", refreshPolicy.getValue());
            }
            return this;
        }

        Params withRequestsPerSecond(float requestsPerSecond) {
            // the default in AbstractBulkByScrollRequest is Float.POSITIVE_INFINITY,
            // but we don't want to add that to the URL parameters, instead we use -1
            if (Float.isFinite(requestsPerSecond)) {
                return putParam("requests_per_second", Float.toString(requestsPerSecond));
            } else {
                return putParam("requests_per_second", "-1");
            }
        }

        Params withRetryOnConflict(int retryOnConflict) {
            if (retryOnConflict > 0) {
                return putParam("retry_on_conflict", String.valueOf(retryOnConflict));
            }
            return this;
        }

        Params withRouting(String routing) {
            return putParam("routing", routing);
        }

        Params withStoredFields(String[] storedFields) {
            if (storedFields != null && storedFields.length > 0) {
                return putParam("stored_fields", String.join(",", storedFields));
            }
            return this;
        }

        Params withTimeout(TimeValue timeout) {
            return putParam("timeout", timeout);
        }

        Params withVersion(long version) {
            if (version != Versions.MATCH_ANY) {
                return putParam("version", Long.toString(version));
            }
            return this;
        }

        Params withVersionType(VersionType versionType) {
            if (versionType != VersionType.INTERNAL) {
                return putParam("version_type", versionType.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        Params withIfSeqNo(long seqNo) {
            if (seqNo != SequenceNumbers.UNASSIGNED_SEQ_NO) {
                return putParam("if_seq_no", Long.toString(seqNo));
            }
            return this;
        }

        Params withIfPrimaryTerm(long primaryTerm) {
            if (primaryTerm != SequenceNumbers.UNASSIGNED_PRIMARY_TERM) {
                return putParam("if_primary_term", Long.toString(primaryTerm));
            }
            return this;
        }

        Params withWaitForActiveShards(ActiveShardCount activeShardCount) {
            return withWaitForActiveShards(activeShardCount, ActiveShardCount.DEFAULT);
        }

        Params withWaitForActiveShards(ActiveShardCount activeShardCount, ActiveShardCount defaultActiveShardCount) {
            if (activeShardCount != null && activeShardCount != defaultActiveShardCount) {
                // in Elasticsearch 7, "default" cannot be sent anymore, so it needs to be mapped to the default value
                // of 1
                String value = activeShardCount == ActiveShardCount.DEFAULT
                        ? "1"
                        : activeShardCount.toString().toLowerCase(Locale.ROOT);
                return putParam("wait_for_active_shards", value);
            }
            return this;
        }

        Params withIndicesOptions(@Nullable IndicesOptions indicesOptions) {

            if (indicesOptions == null) {
                return this;
            }

            withIgnoreUnavailable(indicesOptions.ignoreUnavailable());
            putParam("allow_no_indices", Boolean.toString(indicesOptions.allowNoIndices()));
            String expandWildcards;
            if (!indicesOptions.expandWildcardsOpen() && !indicesOptions.expandWildcardsClosed()) {
                expandWildcards = "none";
            } else {
                StringJoiner joiner = new StringJoiner(",");
                if (indicesOptions.expandWildcardsOpen()) {
                    joiner.add("open");
                }
                if (indicesOptions.expandWildcardsClosed()) {
                    joiner.add("closed");
                }
                expandWildcards = joiner.toString();
            }
            putParam("expand_wildcards", expandWildcards);
            return this;
        }

        Params withIgnoreUnavailable(boolean ignoreUnavailable) {
            // Always explicitly place the ignore_unavailable value.
            putParam("ignore_unavailable", Boolean.toString(ignoreUnavailable));
            return this;
        }

        Params withHuman(boolean human) {
            if (human) {
                putParam("human", "true");
            }
            return this;
        }

        Params withLocal(boolean local) {
            if (local) {
                putParam("local", "true");
            }
            return this;
        }

        Params withIncludeDefaults(boolean includeDefaults) {
            if (includeDefaults) {
                return putParam("include_defaults", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withPreserveExisting(boolean preserveExisting) {
            if (preserveExisting) {
                return putParam("preserve_existing", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withDetailed(boolean detailed) {
            if (detailed) {
                return putParam("detailed", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withWaitForCompletion(Boolean waitForCompletion) {
            return putParam("wait_for_completion", waitForCompletion.toString());
        }

        Params withNodes(String[] nodes) {
            if (nodes != null && nodes.length > 0) {
                return putParam("nodes", String.join(",", nodes));
            }
            return this;
        }

        Params withActions(String[] actions) {
            if (actions != null && actions.length > 0) {
                return putParam("actions", String.join(",", actions));
            }
            return this;
        }

        Params withTaskId(TaskId taskId) {
            if (taskId != null && taskId.isSet()) {
                return putParam("task_id", taskId.toString());
            }
            return this;
        }

        Params withParentTaskId(TaskId parentTaskId) {
            if (parentTaskId != null && parentTaskId.isSet()) {
                return putParam("parent_task_id", parentTaskId.toString());
            }
            return this;
        }

        Params withVerify(boolean verify) {
            if (verify) {
                return putParam("verify", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withWaitForStatus(ClusterHealthStatus status) {
            if (status != null) {
                return putParam("wait_for_status", status.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        Params withWaitForNoRelocatingShards(boolean waitNoRelocatingShards) {
            if (waitNoRelocatingShards) {
                return putParam("wait_for_no_relocating_shards", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withWaitForNoInitializingShards(boolean waitNoInitShards) {
            if (waitNoInitShards) {
                return putParam("wait_for_no_initializing_shards", Boolean.TRUE.toString());
            }
            return this;
        }

        Params withWaitForNodes(String waitForNodes) {
            return putParam("wait_for_nodes", waitForNodes);
        }

        Params withLevel(ClusterHealthRequest.Level level) {
            return putParam("level", level.name().toLowerCase(Locale.ROOT));
        }

        Params withWaitForEvents(Priority waitForEvents) {
            if (waitForEvents != null) {
                return putParam("wait_for_events", waitForEvents.name().toLowerCase(Locale.ROOT));
            }
            return this;
        }
    }

    /**
     * Ensure that the {@link IndexRequest}'s content type is supported by the Bulk API and that it conforms to the
     * current {@link BulkRequest}'s content type (if it's known at the time of this method get called).
     *
     * @return the {@link IndexRequest}'s content type
     */
    static MediaType enforceSameContentType(IndexRequest indexRequest, @Nullable MediaType mediaType) {
        MediaType requestMediaType = indexRequest.getContentType();
        if (requestMediaType != XContentType.JSON && requestMediaType != XContentType.SMILE) {
            throw new IllegalArgumentException("Unsupported content-type found for request with content-type ["
                    + requestMediaType + "], only JSON and SMILE are supported");
        }
        if (mediaType == null) {
            return requestMediaType;
        }
        if (requestMediaType != mediaType) {
            throw new IllegalArgumentException("Mismatching content-type found for request with content-type ["
                    + requestMediaType + "], previous requests have content-type [" + mediaType + ']');
        }
        return mediaType;
    }

    /**
     * Utility class to build request's endpoint given its parts as strings
     */
    static class EndpointBuilder {

        private final StringJoiner joiner = new StringJoiner("/", "/", "");

        EndpointBuilder addPathPart(String... parts) {
            for (String part : parts) {
                if (StringUtils.hasLength(part)) {
                    joiner.add(encodePart(part));
                }
            }
            return this;
        }

        EndpointBuilder addCommaSeparatedPathParts(String[] parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        EndpointBuilder addCommaSeparatedPathParts(List<String> parts) {
            addPathPart(String.join(",", parts));
            return this;
        }

        EndpointBuilder addPathPartAsIs(String... parts) {
            for (String part : parts) {
                if (StringUtils.hasLength(part)) {
                    joiner.add(part);
                }
            }
            return this;
        }

        String build() {
            return joiner.toString();
        }

        private static String encodePart(String pathPart) {
            try {
                // encode each part (e.g. index, type and id) separately before merging them into the path
                // we prepend "/" to the path part to make this path absolute, otherwise there can be issues with
                // paths that start with `-` or contain `:`
                // the authority must be an empty string and not null, else paths that being with slashes could have
                // them
                URI uri = new URI((String) null, "", "/" + pathPart, (String) null, (String) null);
                // manually encode any slash that each part may contain
                return uri.getRawPath().substring(1).replaceAll("/", "%2F");
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Path part [" + pathPart + "] couldn't be encoded", e);
            }
        }
    }
}
