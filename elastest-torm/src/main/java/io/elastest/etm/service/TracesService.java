package io.elastest.etm.service;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;

import io.elastest.etm.dao.TraceRepository;
import io.elastest.etm.model.Enums.LevelEnum;
import io.elastest.etm.model.Enums.StreamType;
import io.elastest.etm.model.MultiConfig;
import io.elastest.etm.model.Trace;
import io.elastest.etm.prometheus.client.PrometheusQueryData;
import io.elastest.etm.prometheus.client.PrometheusQueryData.PrometheusQueryDataResultType;
import io.elastest.etm.prometheus.client.PrometheusQueryDataResult;
import io.elastest.etm.utils.UtilTools;
import io.elastest.etm.utils.UtilsService;
import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;

@Service
public class TracesService {
    final Logger logger = getLogger(lookup().lookupClass());

    private final TraceRepository traceRepository;
    private final QueueService queueService;
    private final UtilsService utilsService;

    GrokCompiler grokCompiler;

    @Value("${grok.patterns.file.path}")
    private String grokPatternsFilePath;

    String javaLogLevelExpression = "%{JAVALOGLEVEL:level}";
    String containerNameExpression = "%{CONTAINERNAME:containerName}";
    String monitoringExecExpression = "(\\d+|ext\\d+_e\\d+|s\\d+_e\\d+)";
    String componentExecAndComponentServiceExpression = "^(?<component>(test|sut|dynamic|k8s_test|k8s_sut))(_|-)?(?<exec>"
            + monitoringExecExpression
            + ")((_|-)(?<componentService>[^_]*(?=_\\d*)?))?";

    String cleanMessageExpression = "^([<]\\d*[>].*)?(?>test_"
            + monitoringExecExpression + "|sut_" + monitoringExecExpression
            + "|dynamic_" + monitoringExecExpression
            + ")\\D*(?>_exec)(\\[.*\\])?[\\s][-][\\s]";

    String startsWithTestOrSutExpression = "(^(test|sut)(_)?(\\d*)(.*)?)|(^(k8s_test|k8s_sut)(.*)?)";

    String dockbeatStream = "et_dockbeat";

    @Autowired
    public TracesService(TraceRepository traceRepository,
            QueueService queueService, UtilsService utilsService) {
        this.traceRepository = traceRepository;
        this.queueService = queueService;
        this.utilsService = utilsService;
    }

    @PostConstruct
    private void init() throws IOException {
        grokCompiler = GrokCompiler.newInstance();
        grokCompiler.registerDefaultPatterns();

        InputStream inputStream = getClass()
                .getResourceAsStream("/" + grokPatternsFilePath);
        grokCompiler.register(inputStream, StandardCharsets.UTF_8);
    }

    public Map<String, String> processGrokExpression(String message,
            String expression) {
        Grok compiledPattern = grokCompiler.compile(expression);
        Map<String, Object> map = compiledPattern.match(message).capture();
        Map<String, String> resultMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() != null) {
                resultMap.put(entry.getKey(), (String) entry.getValue());
            }

        }
        // As <String,String> Map
        return resultMap;
    }

    public Trace cleanCommonFields(Trace trace, String message) {
        // Message
        if (message != null) {
            message = message.replaceAll(cleanMessageExpression, "");
            trace.setMessage(message);
        }

        // Change containerName and component dashes to underscores
        if (trace.getContainerName() != null) {
            trace.setContainerName(
                    trace.getContainerName().replaceAll("-", "_"));
        }
        if (trace.getComponent() != null) {
            trace.setComponent(trace.getComponent().replaceAll("-", "_"));
        }

        if (trace.getComponentService() != null) {
            trace.setComponentService(
                    trace.getComponentService().replaceAll("-", "_"));
        }
        return trace;
    }

    public Trace matchesLevelAndContainerNameFromMessage(Trace trace,
            String message) {
        if (message != null) {
            // Level
            Map<String, String> levelMap = processGrokExpression(message,
                    javaLogLevelExpression);
            try {
                LevelEnum level = LevelEnum.fromValue(levelMap.get("level"));
                trace.setLevel(level);
            } catch (Exception e) {

            }

            // Container Name

            String containerName = this.getContainerNameFromMessage(message);
            if (containerName != null) {
                trace.setContainerName(containerName);
            }
        }
        return trace;
    }

    public String getContainerNameFromMessage(String message) {
        if (message != null) {
            // Container Name
            Map<String, String> containerNameMap = processGrokExpression(
                    message, containerNameExpression);

            return containerNameMap.get("containerName");
        } else {
            return null;
        }
    }

    public void saveTrace(Trace trace) {
        synchronized (this.traceRepository) {
            this.traceRepository.save(trace);
        }
    }

    /* *********** */
    /* *** TCP *** */
    /* *********** */

    public void processTcpTrace(String message, Date timestamp) {
        logger.trace("Processing TCP trace {} with timestamp {}", message,
                timestamp);

        if (message != null && !message.isEmpty()) {
            try {
                Trace trace = new Trace();
                trace.setEtType("et_logs");
                trace.setStream("default_log");
                trace.setStreamType(StreamType.LOG);

                // Timestamp
                trace.setTimestamp(
                        utilsService.getIso8601UTCDateFromDate(timestamp));

                // If message, set level and container name
                trace = this.matchesLevelAndContainerNameFromMessage(trace,
                        message);

                // Exec, Component and Component Service
                Map<String, String> componentExecAndComponentServiceMap = processGrokExpression(
                        trace.getContainerName(),
                        componentExecAndComponentServiceExpression);
                if (componentExecAndComponentServiceMap != null
                        && !componentExecAndComponentServiceMap.isEmpty()) {
                    trace.setExec(
                            componentExecAndComponentServiceMap.get("exec"));
                    trace.setComponent(componentExecAndComponentServiceMap
                            .get("component"));
                    trace.setComponentService(
                            componentExecAndComponentServiceMap
                                    .get("componentService"));
                }

                trace = cleanCommonFields(trace, message);

                if (trace.getComponentService() != null) {
                    trace.setComponent(trace.getComponent() + "_"
                            + trace.getComponentService());
                }

                logger.trace("Trace: {}", trace);
                this.saveTrace(trace);
                this.queueService.sendTrace(trace);
            } catch (Exception e) {
                logger.trace("Error on processing TCP trace {}: ", message, e);
            }
        }
    }

    /* ************* */
    /* *** Beats *** */
    /* ************* */

    public Trace setInitialBeatTraceData(Map<String, Object> dataMap)
            throws ParseException {
        Trace trace = new Trace();
        trace.setComponent((String) dataMap.get("component"));
        trace.setComponentService((String) dataMap.get("componentService"));
        trace.setContainerName((String) dataMap.get("containerName"));
        trace.setEtType((String) dataMap.get("et_type"));
        trace.setExec((String) dataMap.get("exec"));
        trace.setLevel(LevelEnum.fromValue((String) dataMap.get("level")));
        trace.setMessage((String) dataMap.get("message"));
        trace.setMetricName((String) dataMap.get("metricName"));
        trace.setStream((String) dataMap.get("stream"));
        trace.setStreamType(
                StreamType.fromValue((String) dataMap.get("stream_type")));

        String timestampAsStr = (String) dataMap.get("@timestamp");
        if (timestampAsStr == null) {
            timestampAsStr = utilsService.getIso8601UTCStrFromDate(new Date());
        }

        Date timestamp = null;
        try {
            timestamp = utilsService.getIso8601UTCDateFromStr(timestampAsStr);
        } catch (ParseException e) {
            timestamp = utilsService.getJavaUTCDateFromStr(timestampAsStr);
        }

        trace.setTimestamp(timestamp);

        trace.setUnit((String) dataMap.get("unit"));

        // Units
        String units = null;
        try {
            units = (String) dataMap.get("units");
        } catch (Exception e) {
            units = new JSONObject(dataMap.get("units")).toString();
        }
        trace.setUnits(units);

        return trace;
    }

    @SuppressWarnings("unchecked")
    public boolean processBeatTrace(Map<String, Object> dataMap,
            boolean fromDockbeat) {
        boolean procesed = false;
        logger.trace("Processing BEATS trace {}", dataMap.toString());

        if (dataMap != null && !dataMap.isEmpty()) {
            try {
                Trace trace = setInitialBeatTraceData(dataMap);

                try {
                    trace.setRawData(dataMap.get("raw_data").toString());
                } catch (Exception e) {
                    logger.trace("There is no raw_data");
                }

                // Ignore Packetbeat from EIM temporally
                if (trace.getStream() != null
                        && "et_packetbeat".equals(trace.getStream())) {
                    return false;
                }

                if (fromDockbeat) {
                    trace.setStream(dockbeatStream);
                }
                // If message, set level and container name
                trace = this.matchesLevelAndContainerNameFromMessage(trace,
                        (String) dataMap.get("message"));

                if (trace.getLevel() == null && dataMap.containsKey("level")) {
                    try {
                        LevelEnum level = LevelEnum
                                .fromValue(dataMap.get("level").toString());
                        trace.setLevel(level);
                    } catch (Exception e) {
                        logger.error("Error setting trace level");
                    }

                }

                String component = trace.getComponent();

                // Docker
                String[] containerNameTree = new String[] { "docker",
                        "container", "name" };
                String containerName = (String) UtilTools.getMapFieldByTreeList(
                        dataMap, Arrays.asList(containerNameTree));

                if (containerName == null) {
                    // Kubernetes
                    containerNameTree = new String[] { "kubernetes",
                            "container", "name" };
                    containerName = (String) UtilTools.getMapFieldByTreeList(
                            dataMap, Arrays.asList(containerNameTree));

                    if (containerName != null) {
                        // test-NNN to test_NNN
                        containerName = containerName.replaceAll("-", "_");
                    }
                }

                if (containerName != null) {
                    trace.setContainerName(containerName);
                    // Metricbeat
                    if (dataMap.get("metricset") != null) {
                        if (component != null) {
                            trace.setComponent(component + "_" + containerName);
                        }
                    } else {// Filebeat
                        if (component == null) {
                            // from etm filebeat, discard non sut/test
                            // containers
                            if (!containerName
                                    .matches(startsWithTestOrSutExpression)) {
                                logger.error(
                                        "Filebeat trace without component and container name {} does not matches sut/test, discarding",
                                        containerName);
                                return false;
                            }
                        } else {
                            trace.setComponent(component + "_" + containerName);
                        }

                        if (trace.getMessage() == null) {
                            if (dataMap.get("json") != null) {

                                String[] jsonLogTree = new String[] { "json",
                                        "log" };

                                String message = (String) UtilTools
                                        .getMapFieldByTreeList(dataMap,
                                                Arrays.asList(jsonLogTree));

                                if (message != null) {
                                    trace.setMessage(message);
                                }

                            } else {
                                String log = (String) dataMap.get("log");
                                if (log != null) {
                                    trace.setMessage(log);
                                }
                            }
                        }
                    }
                }

                // Exec, Component and Component Service
                if (trace.getContainerName() != null) {
                    Map<String, String> componentExecAndComponentServiceMap = processGrokExpression(
                            trace.getContainerName(),
                            componentExecAndComponentServiceExpression);
                    if (componentExecAndComponentServiceMap != null
                            && !componentExecAndComponentServiceMap.isEmpty()) {
                        trace.setExec(componentExecAndComponentServiceMap
                                .get("exec"));
                        trace.setComponent(componentExecAndComponentServiceMap
                                .get("component"));
                        trace.setComponentService(
                                componentExecAndComponentServiceMap
                                        .get("componentService"));
                    }
                }

                trace = cleanCommonFields(trace, trace.getMessage());

                if (trace.getMessage() != null) {
                    trace.setStreamType(StreamType.LOG);
                }

                // Its Metric
                if (trace.getStreamType() == null
                        || !trace.getStreamType().equals(StreamType.LOG)) {
                    // Dockbeat
                    if (trace.getStream() == null) {
                        return false;
                    }
                    if (trace.getStream().equals(dockbeatStream)) {
                        if (trace.getContainerName() != null
                                && trace.getContainerName().matches(
                                        startsWithTestOrSutExpression)) {
                            trace.setStreamType(StreamType.COMPOSED_METRICS);
                            if (trace.getComponentService() != null) {
                                trace.setComponent(trace.getComponent() + "_"
                                        + trace.getComponentService());
                            }
                            trace.setEtType((String) dataMap.get("type"));
                            trace.setMetricName(trace.getEtType());
                            trace.setContentFromLinkedHashMap(
                                    (LinkedHashMap<Object, Object>) dataMap
                                            .get(trace.getEtType()));

                        } else {
                            logger.trace(
                                    "Dockbeat trace container name {} does not matches sut/test, discarding",
                                    trace.getContainerName());
                            return false;
                        }
                    } else {
                        if (dataMap.get("metricset") != null) {
                            String[] metricsetModuleTree = new String[] {
                                    "metricset", "module" };
                            String metricsetModule = (String) UtilTools
                                    .getMapFieldByTreeList(dataMap,
                                            Arrays.asList(metricsetModuleTree));

                            String[] metricsetNameTree = new String[] {
                                    "metricset", "name" };
                            String metricsetName = (String) UtilTools
                                    .getMapFieldByTreeList(dataMap,
                                            Arrays.asList(metricsetNameTree));

                            String metricName = metricsetModule + "_"
                                    + metricsetName;
                            trace.setEtType(metricName);
                            trace.setMetricName(metricName);

                            String[] contentTree = new String[] {
                                    metricsetModule, metricsetName };
                            LinkedHashMap<Object, Object> content = (LinkedHashMap<Object, Object>) UtilTools
                                    .getMapFieldByTreeList(dataMap,
                                            Arrays.asList(contentTree));

                            trace.setContentFromLinkedHashMap(content);

                            if (trace.getStreamType() == null) {
                                trace.setStreamType(
                                        StreamType.COMPOSED_METRICS);
                            }

                        } else {
                            // HTTP custom metrics
                            try {
                                trace.setContentFromLinkedHashMap(
                                        (LinkedHashMap<Object, Object>) dataMap
                                                .get(trace.getEtType()));
                            } catch (ClassCastException cce) {
                                try {
                                    trace.setContent((String) dataMap
                                            .get(trace.getEtType()));
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                } else { // log
                    trace.setEtType("et_logs");

                    if (trace.getStream() == null) {
                        trace.setStream("default_log");
                    }

                    if (trace.getComponentService() != null) {
                        trace.setComponent(trace.getComponent() + "_"
                                + trace.getComponentService());
                    }
                }

                logger.trace("Trace: {}", trace);
                this.saveTrace(trace);
                procesed = true;
                this.queueService.sendTrace(trace);
            } catch (Exception e) {
                logger.error("Error on processing Beat trace {}: ", dataMap, e);
            }
        }
        return procesed;
    }

    public boolean processBeatTracesList(List<Map<String, Object>> dataMapList,
            boolean fromDockbeat) {
        boolean failures = false;
        if (dataMapList == null) {
            return true;
        }
        for (Map<String, Object> trace : dataMapList) {
            failures = !processBeatTrace(trace, fromDockbeat) || failures;
        }
        return failures;

    }

    /* ************ */
    /* *** HTTP *** */
    /* ************ */

    @SuppressWarnings("unchecked")
    public void processHttpTrace(Map<String, Object> dataMap) {
        logger.trace("Processing HTTP trace {}", dataMap.toString());
        if (dataMap != null && !dataMap.isEmpty()) {
            List<String> messages = (List<String>) dataMap.get("messages");
            // Multiple messages
            if (messages != null) {
                logger.trace("Is multiple message trace. Spliting...");
                for (String message : messages) {
                    Map<String, Object> currentMap = new HashMap<>();
                    currentMap.putAll(dataMap);
                    currentMap.remove("messages");
                    currentMap.put("message", message);
                    this.processHttpTrace(currentMap);
                }
            } else {
                this.processBeatTrace(dataMap, false);
            }
        }
    }

    /* ********************************** */
    /* ***** External Monitoring DB ***** */
    /* ********************************** */

    public Map<String, Object> convertExternalMonitoringDBTrace(
            Map<String, Object> dataMap, String contentFieldName,
            List<String> streamFieldsList, StreamType streamType) {

        if (dataMap != null && !dataMap.isEmpty()) {
            // Stream
            boolean useDefaultStream = false;
            if (streamFieldsList != null && streamFieldsList.size() > 0) {
                String currentValue = "";
                for (String field : streamFieldsList) {
                    if (!dataMap.containsKey(field)) {
                        useDefaultStream = true;
                        break;
                    }
                    try {
                        if (currentValue == "") {
                            currentValue = (String) dataMap.get(field);

                        } else {
                            currentValue += "_" + (String) dataMap.get(field);
                        }
                    } catch (Exception e) {
                    }
                }

                if (currentValue != null && !currentValue.isEmpty()) {
                    dataMap.put("stream", currentValue);
                    dataMap.put("stream_type", streamType.toString());
                } else {
                    useDefaultStream = true;
                }
            } else {
                useDefaultStream = true;
            }

            if (useDefaultStream) { // Default
                if (streamType == StreamType.LOG) {
                    dataMap.put("stream", "default_log");
                } else {
                    //
                }
                dataMap.put("stream_type", streamType.toString());
            }

            // If Logs
            if (streamType == StreamType.LOG) {
                // Message
                if (dataMap.containsKey(contentFieldName)) {
                    dataMap.put("stream_type", streamType.toString());
                    dataMap.put("message", dataMap.get(contentFieldName));
                }

                // Level
                if (dataMap.containsKey("severity")) {
                    dataMap.put("stream_type", streamType.toString());
                    LevelEnum level = LevelEnum
                            .fromValue(dataMap.get("severity").toString());
                    if (level != null) {
                        dataMap.put("level", level.toString());
                    }
                } else if (dataMap.containsKey("severity_unified")) {
                    dataMap.put("stream_type", streamType.toString());
                    LevelEnum level = LevelEnum.fromValue(
                            dataMap.get("severity_unified").toString());
                    if (level != null) {
                        dataMap.put("level", level.toString());
                    }
                }
            } else {
                dataMap.put("stream_type", streamType.toString());

                if (streamType == StreamType.ATOMIC_METRIC) {

                } else {
                    // TODO composed metrics
                }
            }

        }
        return dataMap;
    }

    public Map<String, Object> convertExternalElasticsearchLogTrace(
            Map<String, Object> dataMap, String contentFieldName,
            List<String> streamFieldsList) {
        logger.trace("Converting external Elasticsearch Log trace {}",
                dataMap.toString());

        if (dataMap != null && !dataMap.isEmpty()) {
            // Add raw data
            try {
                Gson gson = new Gson();
                String json = gson.toJson(dataMap);
                dataMap.put("raw_data", json);
            } catch (Exception e) {
            }
        }

        return convertExternalMonitoringDBTrace(dataMap, contentFieldName,
                streamFieldsList, StreamType.LOG);
    }

    public List<Map<String, Object>> convertExternalPrometheusMetricTraces(
            PrometheusQueryData labelTraces, String traceNameField,
            List<String> streamFieldsList, Map<String, Object> additionalFields,
            List<MultiConfig> fieldFilters) {
        List<Map<String, Object>> traces = new ArrayList<>();

        if (labelTraces != null && labelTraces.getResultType() != null
                && labelTraces.getResult() != null) {
            for (PrometheusQueryDataResult trace : labelTraces.getResult()) {
                try {
                    // First, filter by field if necessary
                    boolean add = true;
                    if (fieldFilters != null && fieldFilters.size() > 0) {
                        for (MultiConfig fieldFilter : fieldFilters) {
                            if (fieldFilter.getName() != null
                                    && !fieldFilter.getName().isEmpty()
                                    && fieldFilter.getValues() != null
                                    && fieldFilter.getValues().size() > 0) {
                                if (trace.getMetric()
                                        .containsKey(fieldFilter.getName())) {
                                    String value = (String) trace.getMetric()
                                            .get(fieldFilter.getName());
                                    if (!fieldFilter.getValues()
                                            .contains(value)) {
                                        add = false;
                                        break;
                                    }
                                } else {
                                    add = false;
                                    break;
                                }

                            }
                        }

                    }

                    if (add) {
                        // Init data Map
                        Map<String, Object> auxTraceMap = UtilTools
                                .convertObjToMap(trace);
                        Map<String, Object> traceMetricFieldMap = UtilTools
                                .convertObjToMap(auxTraceMap.get("metric"));

                        auxTraceMap.putAll(traceMetricFieldMap);
                        auxTraceMap.putAll(additionalFields);

                        String traceName = (String) auxTraceMap
                                .get(traceNameField);

                        // Default stream = original TraceName
                        String stream = traceName;
                        auxTraceMap.put("stream", stream);

                        // TraceName = combination of all metric fields
                        for (HashMap.Entry<String, Object> pair : traceMetricFieldMap
                                .entrySet()) {
                            if (!pair.getKey().trim()
                                    .equals(traceNameField.trim())
                                    && pair.getValue() != null) {
                                traceName += "_" + pair.getValue();
                            }
                        }
                        // Points are not allowed
                        traceName = traceName.replaceAll("\\.", "_");

                        auxTraceMap.put("et_type", traceName);
                        auxTraceMap.put("metricName", traceName);

                        // A list of values for each metric
                        if (labelTraces
                                .getResultType() == PrometheusQueryDataResultType.MATRIX) {
                            for (List<Object> valueObj : trace.getValues()) {
                                // Convert individual trace/value
                                traces.add(convertExternalPrometheusMetricTrace(
                                        trace, auxTraceMap, traceName, valueObj,
                                        streamFieldsList));
                            }
                        } else
                        // Single value for each metric
                        if (labelTraces
                                .getResultType() == PrometheusQueryDataResultType.VECTOR) {
                            List<Object> valueObj = trace.getValue();
                            // Convert individual trace/value
                            traces.add(convertExternalPrometheusMetricTrace(
                                    trace, auxTraceMap, traceName, valueObj,
                                    streamFieldsList));

                        }
                    }
                } catch (IllegalArgumentException | ParseException e1) {
                    logger.error(
                            "Could not to process Prometheus Metric trace {}",
                            trace, e1);
                }

            }
        }

        return traces;
    }

    private Map<String, Object> convertExternalPrometheusMetricTrace(
            PrometheusQueryDataResult trace, Map<String, Object> auxTraceMap,
            String traceName, List<Object> valueObj,
            List<String> streamFieldsList) throws ParseException {
        Map<String, Object> traceMap = new HashMap<>();
        traceMap.putAll(auxTraceMap);

        // Add raw data
        try {
            Gson gson = new Gson();
            String json = gson.toJson(trace);
            traceMap.put("raw_data", json);
        } catch (Exception e) {
        }

        traceMap = convertExternalMonitoringDBTrace(traceMap, "",
                streamFieldsList, StreamType.ATOMIC_METRIC);

        // rfc3339 | unix_timestamp
        Long timestamp = Double.valueOf((double) valueObj.get(0) * 1000)
                .longValue();
        String timestampDateStr = utilsService
                .getIso8601UTCStrFromDate(new Date(timestamp));

        traceMap.put("@timestamp", timestampDateStr);
        traceMap.put(traceName, valueObj.get(1));
        // Unit not known
        traceMap.put("unit", "unit");
        return traceMap;
    }
}
