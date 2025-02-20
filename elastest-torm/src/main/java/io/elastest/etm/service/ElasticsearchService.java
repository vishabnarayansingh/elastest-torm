package io.elastest.etm.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.validation.Valid;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexResponse;
import org.elasticsearch.action.main.MainResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchPhrasePrefixQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Value;

import io.elastest.etm.model.AggregationTree;
import io.elastest.etm.model.LogAnalyzerQuery;
import io.elastest.etm.model.MonitoringQuery;
import io.elastest.etm.model.MultiConfig;
import io.elastest.etm.model.TimeRange;
import io.elastest.etm.model.Trace;
import io.elastest.etm.utils.UtilTools;
import io.elastest.etm.utils.UtilsService;

public class ElasticsearchService extends AbstractMonitoringService {
    @Value("${et.edm.elasticsearch.api}")
    private String esApiUrl;

    private String protocol;
    private String host;
    private int port;

    private String user;
    private String pass;
    private String path;

    RestHighLevelClient esClient;

    public ElasticsearchService(UtilsService utilsService,
            TestSuiteService testSuiteService) {
        super(testSuiteService, utilsService);
    }

    public ElasticsearchService(String esApiUrl, String user, String pass,
            String path, UtilsService utilsService) {
        this.esApiUrl = esApiUrl;
        this.utilsService = utilsService;
        this.user = !"".equals(user) ? user : null;
        this.pass = pass;
        this.path = path;
        init();
    }

    @PostConstruct
    private void init() {
        URL url;
        try {
            url = new URL(this.esApiUrl);
            this.protocol = url.getProtocol();
            this.host = url.getHost();
            this.port = url.getPort();
            RestClientBuilder builder = RestClient
                    .builder(new HttpHost(this.host, this.port, this.protocol));
            if (this.path != null && !this.path.isEmpty()) {
                builder.setPathPrefix(this.path);
            }

            if (this.user != null) {
                // TODO complex authentication if it's necessary
                final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(this.user, this.pass));

                builder.setHttpClientConfigCallback(
                        new RestClientBuilder.HttpClientConfigCallback() {
                            @Override
                            public HttpAsyncClientBuilder customizeHttpClient(
                                    HttpAsyncClientBuilder httpClientBuilder) {
                                return httpClientBuilder
                                        .setDefaultCredentialsProvider(
                                                credentialsProvider);
                            }
                        });
            }

            this.esClient = new RestHighLevelClient(builder);

        } catch (MalformedURLException e) {
            logger.error("Cannot get Elasticsearch url by given: {}",
                    this.esApiUrl);
        }

    }

    /* ************** */
    /* **** Info **** */
    /* ************** */

    public MainResponse getInfo() throws IOException {
        return this.esClient.info(RequestOptions.DEFAULT);
    }

    /* ************* */
    /* *** Index *** */
    /* ************* */

    public void createMonitoringIndex(String[] indicesList) {
        boolean hasFailures = false;
        logger.info("Creating ES indices...");
        for (String index : indicesList) {
            logger.info("Creating index: {}", index);
            String type = "_doc";

            Map<String, String> mappings = new HashMap<>();
            mappings.put(type, "{ \"" + type + "\": { \"properties\": {"
                    + "\"component\": { \"type\": \"text\", \"fielddata\": true, \"fields\": { \"keyword\": { \"type\": \"keyword\" } } },"
                    + "\"stream\": { \"type\": \"text\", \"fielddata\": true, \"fields\": { \"keyword\": { \"type\": \"keyword\" } } },"
                    + "\"level\": { \"type\": \"text\", \"fielddata\": true, \"fields\": { \"keyword\": { \"type\": \"keyword\" } } },"
                    + "\"et_type\": { \"type\": \"text\", \"fielddata\": true, \"fields\": { \"keyword\": { \"type\": \"keyword\" } } },"
                    + "\"stream_type\": { \"type\": \"text\", \"fielddata\": true, \"fields\": { \"keyword\": { \"type\": \"keyword\" } } }"
                    + "} }" + "}");
            try {
                if (indexExist(index)) {
                    logger.info("ES Index {} already exist!", index);
                } else {
                    this.createIndexSync(index, mappings, null, null);
                    logger.info("Index {} created", index);
                }
            } catch (ElasticsearchStatusException e) {
                if (e.getMessage()
                        .contains("resource_already_exists_exception")) {
                    logger.info("ES Index {} already exist!", index);
                } else {
                    logger.error("Error creating index {}", index, e);
                }
            } catch (Exception e) {
                hasFailures = true;
                logger.error("Error creating index {}", index, e);
            }
        }
        if (hasFailures) {
            logger.info("Create ES indices finished with some errors");
        } else {
            logger.info("Create ES indices finished!");
        }
    }

    public CreateIndexRequest createIndexRequest(String index,
            Map<String, String> mappings, String alias, String timeout) {
        CreateIndexRequest request = new CreateIndexRequest(index);

        if (mappings != null && !mappings.isEmpty()) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                request.mapping(entry.getKey(), entry.getValue(),
                        XContentType.JSON);
            }
        }

        if (alias != null) {
            request.alias(new Alias(alias));
        }

        if (timeout != null && !timeout.isEmpty()) {
            request.timeout(timeout);
        }

        return request;
    }

    public CreateIndexResponse createIndexSync(String index,
            Map<String, String> mappings, String alias, String timeout)
            throws Exception {
        CreateIndexRequest request = this.createIndexRequest(index, mappings,
                alias, timeout);
        return this.esClient.indices().create(request, RequestOptions.DEFAULT);
    }

    public void createIndexAsync(ActionListener<CreateIndexResponse> listener,
            String index, Map<String, String> mappings, String alias,
            String timeout) throws IOException {
        CreateIndexRequest request = this.createIndexRequest(index, mappings,
                alias, timeout);

        this.esClient.indices().createAsync(request, RequestOptions.DEFAULT,
                listener);
    }

    public List<String> getAllIndices() throws Exception {
        List<String> indices = new ArrayList<>();
        GetIndexRequest request = new GetIndexRequest().indices("*");
        GetIndexResponse getIndexResponse = this.esClient.indices().get(request,
                RequestOptions.DEFAULT);
        String[] indicesArray = getIndexResponse.getIndices();
        if (indicesArray != null) {
            indices = Arrays.asList(indicesArray);
        }

        return indices;
    }

    public boolean indexExist(String index) throws Exception {
        GetIndexRequest request = new GetIndexRequest();
        request.indices(index);
        return this.esClient.indices().exists(request, RequestOptions.DEFAULT);
    }

    public ClusterHealthStatus getIndexHealth(String index) throws Exception {
        ClusterHealthRequest request = new ClusterHealthRequest(index);
        ClusterHealthResponse response = esClient.cluster().health(request,
                RequestOptions.DEFAULT);
        Map<String, ClusterIndexHealth> indices = response.getIndices();
        ClusterIndexHealth indexHealth = indices.get(index);
        return indexHealth.getStatus();
    }

    public List<String> getIndicesByHealth(ClusterHealthStatus health)
            throws Exception {
        List<String> indicesWithHealth = new ArrayList<>();

        List<String> allIndices = getAllIndices();
        if (allIndices != null) {
            for (String index : allIndices) {
                if (health == getIndexHealth(index)) {
                    indicesWithHealth.add(index);
                }
            }
        }

        return indicesWithHealth;
    }

    public List<String> getIndicesByHealth(String health) throws Exception {
        return getIndicesByHealth(ClusterHealthStatus.fromString(health));
    }

    public boolean deleteIndex(String index) throws Exception {
        boolean deleted = false;
        try {
            DeleteIndexRequest request = new DeleteIndexRequest(index);
            AcknowledgedResponse deleteIndexResponse = esClient.indices()
                    .delete(request, RequestOptions.DEFAULT);
            deleted = deleteIndexResponse.isAcknowledged();
        } catch (ElasticsearchException e) {
            if (e.status() == RestStatus.NOT_FOUND) {
                logger.debug(
                        "Index {} has not been removed because it has not been found",
                        index);
                deleted = true;
            }
        }
        return deleted;
    }

    public boolean deleteGivenIndices(List<String> indices) {
        boolean allDeleted = true;
        if (indices != null) {
            for (String index : indices) {
                try {
                    allDeleted = deleteIndex(index) && allDeleted;
                } catch (Exception e) {
                    allDeleted = false;
                }
            }
        }

        return allDeleted;
    }

    public boolean deleteIndicesByHealth(ClusterHealthStatus health)
            throws Exception {
        List<String> indices = getIndicesByHealth(health);
        return deleteGivenIndices(indices);
    }

    public boolean deleteIndicesByHealth(String health) throws Exception {
        return deleteIndicesByHealth(ClusterHealthStatus.fromString(health));
    }

    /* ********************* */
    /* *** Search Config *** */
    /* ********************* */

    public SearchSourceBuilder getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
            BoolQueryBuilder boolQueryBuilder) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(10000);
        sourceBuilder
                .sort(new FieldSortBuilder("@timestamp").order(SortOrder.ASC));
        sourceBuilder.sort(new FieldSortBuilder("_id").order(SortOrder.ASC));

        return sourceBuilder;

    }

    public SearchSourceBuilder getDefaultInverseSearchSourceBuilderByGivenBoolQueryBuilder(
            BoolQueryBuilder boolQueryBuilder) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.size(10000);
        sourceBuilder
                .sort(new FieldSortBuilder("@timestamp").order(SortOrder.DESC));
        sourceBuilder.sort(new FieldSortBuilder("_id").order(SortOrder.DESC));

        return sourceBuilder;
    }

    private BoolQueryBuilder getTimeRangeByMonitoringQuery(
            MonitoringQuery monitoringQuery,
            BoolQueryBuilder boolQueryBuilder) {
        TimeRange timeRange = monitoringQuery.getTimeRange();
        if (timeRange != null && !timeRange.isEmpty()) {
            // Range Time
            RangeQueryBuilder timeRangeBuilder = new RangeQueryBuilder(
                    "@timestamp");
            if (timeRange.getLt() != null && timeRange.getLte() == null) {
                timeRangeBuilder.lt(timeRange.getLt());
            }
            if (timeRange.getLte() != null) {
                timeRangeBuilder.lte(timeRange.getLte());
            }
            if (timeRange.getGt() != null && timeRange.getGte() == null) {
                timeRangeBuilder.gt(timeRange.getGt());
            }
            if (timeRange.getGte() != null) {
                timeRangeBuilder.gte(timeRange.getGte());
            }

            boolQueryBuilder.must(timeRangeBuilder);
        }
        return boolQueryBuilder;
    }

    /* ************** */
    /* *** Search *** */
    /* ************** */

    public List<SearchHit> searchAll(SearchRequest searchRequest)
            throws IOException {
        List<SearchHit> hits = new ArrayList<>();

        SearchResponseHitsIterator hitsIterator = new SearchResponseHitsIterator(
                searchRequest);

        while (hitsIterator.hasNext()) {
            List<SearchHit> currentHits = Arrays.asList(hitsIterator.next());
            hits.addAll(currentHits);
        }

        return hits;
    }

    public SearchResponse searchBySearchSourceBuilder(
            SearchSourceBuilder searchSourceBuilder, String[] indices)
            throws IOException {

        SearchRequest searchRequest = new SearchRequest(indices);
        searchRequest.source(searchSourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));
        return this.esClient.search(searchRequest, RequestOptions.DEFAULT);
    }

    public List<SearchHit> searchAllHits(SearchRequest request) {
        List<SearchHit> hits = new ArrayList<>();
        SearchResponseHitsIterator hitIterator = new SearchResponseHitsIterator(
                request);

        while (hitIterator.hasNext()) {
            List<SearchHit> currentHits = Arrays.asList(hitIterator.next());
            hits.addAll(currentHits);
        }
        return hits;
    }

    public List<Map<String, Object>> searchAllByRequest(SearchRequest request) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        SearchHitIterator hitIterator = new SearchHitIterator(request);

        while (hitIterator.hasNext()) {
            SearchHit currentHit = hitIterator.next();
            if (currentHit.getSortValues() != null
                    && currentHit.getSortValues().length > 0) {
                currentHit.getSourceAsMap().put("sort",
                        currentHit.getSortValues());
            }
            mapList.add(currentHit.getSourceAsMap());
        }
        return mapList;
    }

    public List<Map<String, Object>> searchRequestAndGetSourceMapList(
            SearchRequest request) throws IOException {
        List<Map<String, Object>> mapList = new ArrayList<>();
        SearchResponse response = this.esClient.search(request,
                RequestOptions.DEFAULT);

        if (response.getHits() != null
                && response.getHits().getHits() != null) {
            for (SearchHit hit : response.getHits().getHits()) {
                if (hit.getSortValues() != null
                        && hit.getSortValues().length > 0) {
                    hit.getSourceAsMap().put("sort", hit.getSortValues());
                }
                mapList.add(hit.getSourceAsMap());
            }
        }
        return mapList;
    }

    public List<SearchHit> searchByTimestamp(String[] indices,
            BoolQueryBuilder boolQueryBuilder, String timestamp) {
        boolQueryBuilder = (BoolQueryBuilder) UtilTools
                .cloneObject(boolQueryBuilder);

        TermQueryBuilder timestampTerm = QueryBuilders.termQuery("@timestamp",
                timestamp);
        boolQueryBuilder.must().add(timestampTerm);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(indices);
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return searchAllHits(searchRequest);
    }

    public List<SearchHit> getPreviousFromTimestamp(String[] indices,
            BoolQueryBuilder boolQueryBuilder, String timestamp) {
        // search all hits to find hit with given timestamp
        List<SearchHit> hits = this.searchByTimestamp(indices, boolQueryBuilder,
                timestamp);

        if (hits.size() > 0) {
            // Inverse Search Source builder
            SearchSourceBuilder inverseSourceBuilder = getDefaultInverseSearchSourceBuilderByGivenBoolQueryBuilder(
                    boolQueryBuilder);

            inverseSourceBuilder
                    .searchAfter(hits.get(hits.size() - 1).getSortValues());

            SearchRequest searchRequest = new SearchRequest(indices);
            searchRequest.source(inverseSourceBuilder);
            searchRequest.indicesOptions(
                    IndicesOptions.fromOptions(true, false, false, false));

            List<SearchHit> previousHits = this.searchAllHits(searchRequest);
            // Sort ASC
            Collections.reverse(previousHits);
            return previousHits;

        } else {
            return new ArrayList<>();
        }
    }

    public List<SearchHit> getLast(String[] indices,
            BoolQueryBuilder boolQueryBuilder, int size) throws Exception {
        // Inverse Search Source builder
        SearchSourceBuilder inverseSourceBuilder = getDefaultInverseSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);

        inverseSourceBuilder.size(size);

        SearchRequest searchRequest = new SearchRequest(indices);
        searchRequest.source(inverseSourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        SearchResponse response = this.esClient.search(searchRequest,
                RequestOptions.DEFAULT);
        List<SearchHit> lastHits = new ArrayList<>();
        if (response.getHits() != null
                && response.getHits().getHits() != null) {
            List<SearchHit> currentHits = Arrays
                    .asList(response.getHits().getHits());
            lastHits.addAll(currentHits);
            // Sort ASC
            Collections.reverse(lastHits);
        }
        return lastHits;
    }

    public BoolQueryBuilder getBoolQueryBuilderByMonitoringQuery(
            MonitoringQuery monitoringQuery) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        for (String selectedTerm : monitoringQuery.getSelectedTerms()) {
            TermQueryBuilder term = monitoringQuery
                    .getAttributeTermByGivenName(selectedTerm);
            if (term != null) {
                boolQueryBuilder.must(term);
            }
        }

        return boolQueryBuilder;
    }

    public List<Map<String, Object>> searchAllByTerms(
            MonitoringQuery monitoringQuery) throws IOException {
        BoolQueryBuilder boolQueryBuilder = getBoolQueryBuilderByMonitoringQuery(
                monitoringQuery);

        boolQueryBuilder = getTimeRangeByMonitoringQuery(monitoringQuery,
                boolQueryBuilder);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);
        SearchRequest searchRequest = new SearchRequest(
                monitoringQuery.getIndicesAsArray());
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return this.searchAllByRequest(searchRequest);
    }

    /* *** Aggregations *** */

    public List<Map<String, Object>> searchAllAggregationsByRequest(
            SearchRequest request) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        SearchHitIterator hitIterator = new SearchHitIterator(request);

        while (hitIterator.hasNext()) {
            SearchHit currentHit = hitIterator.next();
            if (currentHit.getSortValues() != null
                    && currentHit.getSortValues().length > 0) {
                currentHit.getSourceAsMap().put("sort",
                        currentHit.getSortValues());
            }
            mapList.add(currentHit.getSourceAsMap());
        }
        return mapList;
    }

    public AggregationBuilder createNestedAggs(List<String> orderedFieldList) {
        AggregationBuilder aggregationBuilder = null;
        if (orderedFieldList.size() > 0) {
            String firstField = orderedFieldList.get(0);
            aggregationBuilder = AggregationBuilders.terms(firstField + "s")
                    .size(10000).field(firstField);
            if (orderedFieldList.size() > 1) {
                aggregationBuilder.subAggregation(this.createNestedAggs(
                        orderedFieldList.subList(1, orderedFieldList.size())));
            }
        }

        return aggregationBuilder;
    }

    public List<AggregationTree> getAggTreeList(Aggregations aggs,
            List<String> fields) {
        List<AggregationTree> aggTreeList = new ArrayList<>();

        if (fields.size() > 0) {
            String field = fields.get(0) + 's';
            Terms terms = aggs.get(field);
            if (terms != null && terms.getBuckets() != null) {
                Collection<? extends Bucket> buckets = terms.getBuckets();
                for (Bucket bucket : buckets) {
                    AggregationTree aggObj = new AggregationTree();
                    aggObj.setName(bucket.getKeyAsString());
                    aggObj.setChildren(
                            this.getAggTreeList(bucket.getAggregations(),
                                    fields.subList(1, fields.size())));
                    aggTreeList.add(aggObj);
                }
            }

        }
        return aggTreeList;
    }

    public Aggregations getAggregationsTree(MonitoringQuery monitoringQuery,
            BoolQueryBuilder boolQueryBuilder) throws IOException {
        AggregationBuilder aggregationBuilder = createNestedAggs(
                monitoringQuery.getSelectedTerms());

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);
        sourceBuilder.aggregation(aggregationBuilder);

        SearchRequest searchRequest = new SearchRequest(
                monitoringQuery.getIndicesAsArray());
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return esClient.search(searchRequest, RequestOptions.DEFAULT)
                .getAggregations();

    }

    public List<AggregationTree> getMonitoringTree(
            MonitoringQuery monitoringQuery, boolean isMetric)
            throws IOException {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        TermQueryBuilder streamTypeTerm = QueryBuilders.termQuery("stream_type",
                "log");

        if (isMetric) {
            boolQueryBuilder.mustNot().add(streamTypeTerm);

            TermQueryBuilder etTypeContainerTerm = QueryBuilders
                    .termQuery("et_type", "container");
            boolQueryBuilder.mustNot().add(etTypeContainerTerm);
        } else {
            boolQueryBuilder.must().add(streamTypeTerm);
        }
        Aggregations aggs = this.getAggregationsTree(monitoringQuery,
                boolQueryBuilder);
        List<AggregationTree> aggregationTreeList = getAggTreeList(aggs,
                monitoringQuery.getSelectedTerms());
        return aggregationTreeList;
    }

    /* ******************************************************************** */
    /* ************************** Implementation ************************** */
    /* ******************************************************************** */

    public boolean deleteMonitoringDataByExec(String exec) {
        boolean deleted = false;
        try {
            deleted = this.deleteIndex(exec);
        } catch (Exception e) {
            logger.error("Error on delete monitoring data by exec {}", exec);
        }

        return deleted;
    }

    /* ****************************************** */
    /* ****************** Logs ****************** */
    /* ****************************************** */

    public BoolQueryBuilder getLogBoolQueryBuilder(List<String> components,
            String stream, boolean underShould) {
        BoolQueryBuilder componentStreamBoolBuilder = QueryBuilders.boolQuery();
        TermsQueryBuilder componentsTerms = QueryBuilders
                .termsQuery("component", components);
        TermQueryBuilder streamTerm = QueryBuilders.termQuery("stream", stream);

        componentStreamBoolBuilder.must(componentsTerms);
        componentStreamBoolBuilder.must(streamTerm);

        if (underShould) {

            TermQueryBuilder streamTypeTerm = QueryBuilders
                    .termQuery("stream_type", "log");

            BoolQueryBuilder shouldBoolBuilder = QueryBuilders.boolQuery();
            shouldBoolBuilder.should(componentStreamBoolBuilder);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must(streamTypeTerm);
            boolQueryBuilder.must(shouldBoolBuilder);

            return boolQueryBuilder;
        } else {
            return componentStreamBoolBuilder;
        }
    }

    public BoolQueryBuilder getLogBoolQueryBuilder(String component,
            String stream, boolean underShould) {
        return getLogBoolQueryBuilder(Arrays.asList(component), stream,
                underShould);
    }

    public List<Map<String, Object>> searchAllLogs(
            MonitoringQuery monitoringQuery) throws IOException {

        // If components list not empty, use list. Else, use unique component
        List<String> components = monitoringQuery.getComponents();
        components = components != null && components.size() > 0 ? components
                : Arrays.asList(monitoringQuery.getComponent());

        BoolQueryBuilder boolQueryBuilder = getLogBoolQueryBuilder(components,
                monitoringQuery.getStream(), false);

        boolQueryBuilder = getTimeRangeByMonitoringQuery(monitoringQuery,
                boolQueryBuilder);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);
        SearchRequest searchRequest = new SearchRequest(
                monitoringQuery.getIndicesAsArray());
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return this.searchAllByRequest(searchRequest);
    }

    @Override
    public List<String> searchAllLogsMessage(MonitoringQuery monitoringQuery,
            boolean withTimestamp, boolean timeDiff) throws Exception {
        return searchAllLogsMessage(monitoringQuery, withTimestamp, timeDiff,
                false);
    }

    @Override
    public List<String> searchAllLogsMessage(MonitoringQuery monitoringQuery,
            boolean withTimestamp, boolean timeDiff,
            boolean modifyStartFinishTestTraces) throws Exception {

        List<String> logs = new ArrayList<>();
        List<Map<String, Object>> logTraces = searchAllLogs(monitoringQuery);

        if (logTraces != null) {
            Trace firstTrace = null;
            for (Map<String, Object> traceMap : logTraces) {
                if (traceMap != null) {
                    String message = null;
                    try {
                        Trace trace = (Trace) UtilTools
                                .convertStringKeyMapToObj(traceMap,
                                        Trace.class);
                        trace.setTimestamp(
                                utilsService.getIso8601UTCDateFromStr(
                                        (String) traceMap.get("@timestamp")));
                        message = trace.getMessage();

                        boolean isStartFinishTraceAndModifyActivated = modifyStartFinishTestTraces
                                && (utilsService
                                        .containsTCStartMsgPrefix(message)
                                        || utilsService
                                                .containsTCFinishMsgPrefix(
                                                        message));

                        boolean noContinue = false;

                        // If is start/finish test trace and modify
                        // if (isStartFinishTraceAndModifyActivated) {
                        // String testCaseName = utilsService
                        // .getTestCaseNameFromStartFinishTrace(message);
                        // if (testCaseName != null && testCaseName != "") {
                        // message = "Test Case: " + testCaseName;
                        // noContinue = true;
                        // }
                        // }

                        if (!noContinue && withTimestamp
                                && trace.getTimestamp() != null) {
                            if (timeDiff) {
                                long traceTimeDiff = trace.getTimestamp()
                                        .getTime();
                                // First is 0
                                if (firstTrace == null) {
                                    traceTimeDiff = 0;
                                    firstTrace = trace;
                                } else { // Others is diff with first
                                    traceTimeDiff -= firstTrace.getTimestamp()
                                            .getTime();
                                }

                                message = traceTimeDiff + " " + message;
                            } else {
                                message = trace.getTimestamp().toString() + " "
                                        + message;
                            }
                        }

                    } catch (Exception e) {
                    }
                    if (message != null) {
                        logs.add(message);
                    }
                }

            }
        }

        return logs;
    }

    public List<Map<String, Object>> getPreviousLogsFromTimestamp(
            MonitoringQuery monitoringQuery) {

        BoolQueryBuilder boolQueryBuilder = getLogBoolQueryBuilder(
                monitoringQuery.getComponent(), monitoringQuery.getStream(),
                false);

        boolQueryBuilder = getTimeRangeByMonitoringQuery(monitoringQuery,
                boolQueryBuilder);

        List<SearchHit> hits = this.getPreviousFromTimestamp(
                monitoringQuery.getIndicesAsArray(), boolQueryBuilder,
                monitoringQuery.getTimestamp());

        return getTracesFromHitList(hits);
    }

    public List<Map<String, Object>> getLastLogs(
            MonitoringQuery monitoringQuery, int size) throws Exception {
        BoolQueryBuilder boolQueryBuilder = getLogBoolQueryBuilder(
                monitoringQuery.getComponent(), monitoringQuery.getStream(),
                false);

        List<SearchHit> hits = this.getLast(monitoringQuery.getIndicesAsArray(),
                boolQueryBuilder, size);

        return getTracesFromHitList(hits);
    }

    @Override
    public List<AggregationTree> searchLogsTree(
            @Valid MonitoringQuery monitoringQuery) throws Exception {
        return this.getMonitoringTree(monitoringQuery, false);
    }

    @Override
    public List<AggregationTree> searchLogsLevelsTree(
            @Valid MonitoringQuery monitoringQuery) throws Exception {
        return this.getMonitoringTree(monitoringQuery, false);
    }

    /* *** Messages *** */

    public SearchRequest getFindMessageSearchRequest(String index, String msg,
            List<String> components, String stream) {

        MatchPhrasePrefixQueryBuilder messageMatchTerm = QueryBuilders
                .matchPhrasePrefixQuery("message", msg);

        BoolQueryBuilder boolQueryBuilder = getLogBoolQueryBuilder(components,
                stream, true);
        boolQueryBuilder.must(messageMatchTerm);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);

        SearchRequest searchRequest = new SearchRequest(index);
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));
        return searchRequest;
    }

    public SearchResponse findMessageSync(String index, String msg,
            List<String> components) throws IOException {
        SearchRequest searchRequest = this.getFindMessageSearchRequest(index,
                msg, components, "default_log");
        return this.esClient.search(searchRequest, RequestOptions.DEFAULT);
    }

    public Date findFirstMsgAndGetTimestamp(String index, String msg,
            List<String> components) throws IOException, ParseException {
        SearchResponse response = this.findMessageSync(index, msg, components);
        SearchHits hits = response.getHits();
        if (hits != null && hits.getTotalHits() > 0) {
            SearchHit firstResult = hits.getAt(0);
            String timestamp = firstResult.getSourceAsMap().get("@timestamp")
                    .toString();
            Date date = utilsService.getIso8601UTCDateFromStr(timestamp);

            return date;
        }

        return null;
    }

    @Override
    public Date findLastMsgAndGetTimestamp(String index, String msg,
            List<String> components) throws Exception {
        SearchResponse response = this.findMessageSync(index, msg, components);
        SearchHits hits = response.getHits();
        if (hits != null && hits.getTotalHits() > 0) {
            SearchHit lastResult = hits.getAt((int) (hits.getTotalHits() - 1));
            String timestamp = lastResult.getSourceAsMap().get("@timestamp")
                    .toString();
            Date date = utilsService.getIso8601UTCDateFromStr(timestamp);

            return date;
        }

        return null;
    }

    /* ***************************************** */
    /* **************** Metrics **************** */
    /* ***************************************** */

    public BoolQueryBuilder getMetricBoolQueryBuilder(
            MonitoringQuery monitoringQuery, boolean underShould) {
        BoolQueryBuilder componentEtTypeBoolBuilder = QueryBuilders.boolQuery();
        if (monitoringQuery.getComponent() != null) {
            TermQueryBuilder componentTerm = QueryBuilders
                    .termQuery("component", monitoringQuery.getComponent());
            componentEtTypeBoolBuilder.must(componentTerm);
        }
        TermQueryBuilder etTypeTerm = QueryBuilders.termQuery("et_type",
                monitoringQuery.getEtType());
        componentEtTypeBoolBuilder.must(etTypeTerm);

        if (underShould) {
            TermQueryBuilder streamTypeTerm = QueryBuilders
                    .termQuery("stream_type", "log"); // TODO fix

            BoolQueryBuilder shouldBoolBuilder = QueryBuilders.boolQuery();
            shouldBoolBuilder.should(componentEtTypeBoolBuilder);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.must(streamTypeTerm);
            boolQueryBuilder.must(shouldBoolBuilder);

            return boolQueryBuilder;
        } else {
            return componentEtTypeBoolBuilder;
        }
    }

    public List<Map<String, Object>> searchAllMetrics(
            MonitoringQuery monitoringQuery) throws IOException {
        BoolQueryBuilder boolQueryBuilder = getMetricBoolQueryBuilder(
                monitoringQuery, false);

        boolQueryBuilder = getTimeRangeByMonitoringQuery(monitoringQuery,
                boolQueryBuilder);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);
        SearchRequest searchRequest = new SearchRequest(
                monitoringQuery.getIndicesAsArray());
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return this.searchAllByRequest(searchRequest);
    }

    public List<Map<String, Object>> getLastMetrics(
            MonitoringQuery monitoringQuery, int size) throws Exception {
        BoolQueryBuilder boolQueryBuilder = getMetricBoolQueryBuilder(
                monitoringQuery, false);

        List<SearchHit> hits = this.getLast(monitoringQuery.getIndicesAsArray(),
                boolQueryBuilder, size);

        return getTracesFromHitList(hits);
    }

    public List<Map<String, Object>> getPreviousMetricsFromTimestamp(
            MonitoringQuery monitoringQuery) {

        BoolQueryBuilder boolQueryBuilder = getMetricBoolQueryBuilder(
                monitoringQuery, false);

        boolQueryBuilder = getTimeRangeByMonitoringQuery(monitoringQuery,
                boolQueryBuilder);

        List<SearchHit> hits = this.getPreviousFromTimestamp(
                monitoringQuery.getIndicesAsArray(), boolQueryBuilder,
                monitoringQuery.getTimestamp());

        return getTracesFromHitList(hits);
    }

    @Override
    public List<AggregationTree> searchMetricsTree(
            @Valid MonitoringQuery monitoringQuery) throws Exception {
        return this.getMonitoringTree(monitoringQuery, true);
    }

    /* ******************* */
    /* *** LogAnalyzer *** */
    /* ******************* */

    public BoolQueryBuilder getLogAnalyzerQueryBuilder(
            LogAnalyzerQuery logAnalyzerQuery) {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder componentsStreamsBoolBuilder = QueryBuilders
                .boolQuery();

        // Components/streams
        for (AggregationTree componentStream : logAnalyzerQuery
                .getComponentsStreams()) {
            for (AggregationTree stream : componentStream.getChildren()) {
                BoolQueryBuilder componentStreamBoolBuilder = QueryBuilders
                        .boolQuery();
                TermQueryBuilder componentTerm = QueryBuilders
                        .termQuery("component", componentStream.getName());
                TermQueryBuilder streamTerm = QueryBuilders.termQuery("stream",
                        stream.getName());

                componentStreamBoolBuilder.must().add(componentTerm);
                componentStreamBoolBuilder.must().add(streamTerm);
                componentsStreamsBoolBuilder.should()
                        .add(componentStreamBoolBuilder);
            }
        }

        // Levels
        for (String level : logAnalyzerQuery.getLevels()) {
            TermQueryBuilder levelTerm = QueryBuilders.termQuery("level",
                    level);
            boolQueryBuilder.must(levelTerm);
        }

        // Range Time
        RangeQueryBuilder timeRange = new RangeQueryBuilder("@timestamp");
        if (logAnalyzerQuery.getRangeLT() != null) {
            timeRange.lt(logAnalyzerQuery.getRangeLT());
        }
        if (logAnalyzerQuery.getRangeLTE() != null) {
            timeRange.lte(logAnalyzerQuery.getRangeLTE());
        }
        if (logAnalyzerQuery.getRangeGT() != null) {
            timeRange.gt(logAnalyzerQuery.getRangeGT());
        }
        if (logAnalyzerQuery.getRangeGTE() != null) {
            timeRange.gte(logAnalyzerQuery.getRangeGTE());
        }

        // Match Message
        if (logAnalyzerQuery.getMatchMessage() != null
                && !logAnalyzerQuery.getMatchMessage().equals("")) {
            MatchPhrasePrefixQueryBuilder matchPhrasePrefix = new MatchPhrasePrefixQueryBuilder(
                    "message", "*" + logAnalyzerQuery.getMatchMessage() + "*");
            boolQueryBuilder.must(matchPhrasePrefix);
        }

        // Stream Type
        TermQueryBuilder streamTypeTerm = QueryBuilders.termQuery("stream_type",
                "log");
        boolQueryBuilder.must(streamTypeTerm);

        boolQueryBuilder.must(timeRange);
        boolQueryBuilder.must(componentsStreamsBoolBuilder);

        return boolQueryBuilder;

    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchLogAnalyzerQuery(
            LogAnalyzerQuery logAnalyzerQuery) throws IOException {
        BoolQueryBuilder boolQueryBuilder = getLogAnalyzerQueryBuilder(
                logAnalyzerQuery);

        SearchSourceBuilder sourceBuilder = getDefaultSearchSourceBuilderByGivenBoolQueryBuilder(
                boolQueryBuilder);

        // Size
        sourceBuilder.size(logAnalyzerQuery.getSize());

        // Search After
        if (logAnalyzerQuery.getSearchAfterTrace() != null
                && logAnalyzerQuery.getSearchAfterTrace().get("sort") != null) {

            ArrayList<Object> sort = (ArrayList<Object>) logAnalyzerQuery
                    .getSearchAfterTrace().get("sort");

            sourceBuilder.searchAfter(sort.toArray());
        }

        SearchRequest searchRequest = new SearchRequest(
                logAnalyzerQuery.getIndicesAsArray());
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        // return this.searchAllByRequest(searchRequest); TODO get all option
        return this.searchRequestAndGetSourceMapList(searchRequest);
    }

    /* ****************************** */
    /* *** External Elasticsearch *** */
    /* ****************************** */
    public List<Map<String, Object>> searchTraces(String[] indices,
            Date fromTime, Date toTime, Object[] searchAfter, int size,
            List<MultiConfig> fieldFilters) throws Exception {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(size);
        sourceBuilder
                .sort(new FieldSortBuilder("@timestamp").order(SortOrder.ASC));
        sourceBuilder.sort(new FieldSortBuilder("_id").order(SortOrder.ASC));

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolean addBoolQueryBuilder = false;

        if (fromTime != null || toTime != null) {
            RangeQueryBuilder timeRange = new RangeQueryBuilder("@timestamp");
            boolean filterByTime = false;
            if (fromTime != null) {
                String fromTimeStr = utilsService
                        .getIso8601UTCStrFromDate(fromTime);
                timeRange.gte(fromTimeStr);
                filterByTime = true;
            }

            if (toTime != null) {
                String toTimeStr = utilsService
                        .getIso8601UTCStrFromDate(toTime);
                timeRange.lte(toTimeStr);
                filterByTime = true;
            }
            if (filterByTime) {
                boolQueryBuilder.must(timeRange);
                addBoolQueryBuilder = true;
            }
        }
        if (fieldFilters != null && fieldFilters.size() > 0) {
            for (MultiConfig fieldFilter : fieldFilters) {
                if (fieldFilter.getName() != null
                        && !fieldFilter.getName().isEmpty()
                        && fieldFilter.getValues() != null
                        && fieldFilter.getValues().size() > 0) {
                    TermsQueryBuilder fieldFilterTerm = QueryBuilders
                            .termsQuery(fieldFilter.getName(),
                                    fieldFilter.getValues());

                    BoolQueryBuilder shouldBoolBuilder = QueryBuilders
                            .boolQuery();
                    shouldBoolBuilder.should(fieldFilterTerm);
                    boolQueryBuilder.must(shouldBoolBuilder);

                    addBoolQueryBuilder = true;
                }
            }

        }

        if (addBoolQueryBuilder) {
            sourceBuilder.query(boolQueryBuilder);
        }

        if (searchAfter != null) {
            sourceBuilder.searchAfter(searchAfter);
        }

        SearchRequest searchRequest = new SearchRequest(indices);
        searchRequest.source(sourceBuilder);
        searchRequest.indicesOptions(
                IndicesOptions.fromOptions(true, false, false, false));

        return this.searchRequestAndGetSourceMapList(searchRequest);
    }

    /* ************** */
    /* * Exceptions * */
    /* ************** */

    public class IndexAlreadyExistException extends Exception {

        private static final long serialVersionUID = -5156214804587007246L;

        public IndexAlreadyExistException() {
        }

        public IndexAlreadyExistException(String message) {
            super(message);
        }

        public IndexAlreadyExistException(Throwable cause) {
            super(cause);
        }

        public IndexAlreadyExistException(String message, Throwable cause) {
            super(message, cause);
        }

    }

    /* ************** */
    /* *** Others *** */
    /* ************** */

    public List<Map<String, Object>> getTracesFromHitList(
            List<SearchHit> hits) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (SearchHit currentHit : hits) {
            if (currentHit.getSortValues() != null
                    && currentHit.getSortValues().length > 0) {
                currentHit.getSourceAsMap().put("sort",
                        currentHit.getSortValues());
            }
            mapList.add(currentHit.getSourceAsMap());
        }
        return mapList;
    }

    public class SearchHitIterator implements Iterator<SearchHit> {

        private final SearchRequest initialRequest;

        private SearchHit[] currentPageResults;
        private int currentResultIndex;
        private SearchResponse currentPageResponse;

        public SearchHitIterator(SearchRequest initialRequest) {
            this.initialRequest = initialRequest;
            this.currentResultIndex = -1;
        }

        @Override
        public boolean hasNext() {
            if (currentPageResults == null
                    || currentResultIndex + 1 >= currentPageResults.length) {

                // If is not first search
                if (currentPageResponse != null) {
                    SearchSourceBuilder source = initialRequest.source();
                    if (currentPageResponse.getHits() != null
                            && currentPageResponse.getHits().getHits() != null
                            && currentPageResponse.getHits()
                                    .getHits().length > 0) {

                        SearchHit hit = currentPageResponse.getHits()
                                .getHits()[currentPageResponse.getHits()
                                        .getHits().length - 1];

                        source.searchAfter(hit.getSortValues());
                        initialRequest.source(source);
                    }
                }

                try {
                    currentPageResponse = esClient.search(initialRequest,
                            RequestOptions.DEFAULT);
                } catch (IOException e) {
                    return false;
                }

                currentPageResults = currentPageResponse.getHits().getHits();

                if (currentPageResults.length < 1)
                    return false;

                currentResultIndex = -1;
            }

            return true;
        }

        @Override
        public SearchHit next() {
            if (!hasNext())
                return null;

            currentResultIndex++;
            return currentPageResults[currentResultIndex];
        }

    }

    public class SearchResponseHitsIterator implements Iterator<SearchHit[]> {
        private final SearchRequest initialRequest;
        private SearchResponse currentPageResponse;

        public SearchResponseHitsIterator(SearchRequest initialRequest) {
            this.initialRequest = initialRequest;
        }

        @Override
        public boolean hasNext() {
            // If is not first search
            if (currentPageResponse != null) {
                SearchSourceBuilder source = initialRequest.source();
                if (currentPageResponse.getHits() != null
                        && currentPageResponse.getHits().getHits() != null
                        && currentPageResponse.getHits().getHits().length > 0) {

                    SearchHit hit = currentPageResponse.getHits()
                            .getHits()[currentPageResponse.getHits()
                                    .getHits().length - 1];

                    source.searchAfter(hit.getSortValues());
                    initialRequest.source(source);
                }
            }
            SearchResponse response = null;
            try {
                response = esClient.search(initialRequest,
                        RequestOptions.DEFAULT);
            } catch (IOException e) {
                return false;
            }

            if (response == null || response.getHits() == null
                    || response.getHits().getHits() == null
                    || response.getHits().getHits().length == 0) {
                return false;
            }

            return true;
        }

        @Override
        public SearchHit[] next() {
            if (!hasNext()) {
                return new SearchHit[0];
            }

            try {
                currentPageResponse = esClient.search(initialRequest,
                        RequestOptions.DEFAULT);
            } catch (IOException e) {
                return new SearchHit[0];
            }

            if (currentPageResponse.getHits() != null
                    && currentPageResponse.getHits().getHits() != null) {

                return currentPageResponse.getHits().getHits();
            } else {
                return new SearchHit[0];
            }
        }

    }
}
