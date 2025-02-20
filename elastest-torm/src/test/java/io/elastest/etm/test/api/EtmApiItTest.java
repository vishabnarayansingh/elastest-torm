package io.elastest.etm.test.api;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.elastest.etm.model.Parameter;
import io.elastest.etm.model.Project;
import io.elastest.etm.model.SutExecution;
import io.elastest.etm.model.SutSpecification;
import io.elastest.etm.model.SutSpecification.CommandsOptionEnum;
import io.elastest.etm.model.SutSpecification.InstrumentedByEnum;
import io.elastest.etm.model.SutSpecification.ManagedDockerType;
import io.elastest.etm.model.SutSpecification.SutTypeEnum;
import io.elastest.etm.model.TJob;
import io.elastest.etm.model.TJobExecution;
import io.elastest.etm.test.IntegrationBaseTest;

public class EtmApiItTest extends IntegrationBaseTest {

    static final Logger log = getLogger(lookup().lookupClass());

    @Autowired
    TestRestTemplate httpClient;

    @LocalServerPort
    int serverPort;

    protected String baseUrl() {
        return "http://localhost:" + serverPort;
    }

    protected static Map<String, SutSpecification> sutExamples;

    @BeforeAll
    public static void initData() {
        log.info("Initializing the test environment");
        sutExamples = new HashMap<>();

        SutSpecification sutExample1 = new SutSpecification();
        sutExample1.setId(new Long(0));
        sutExample1.setName("sut_definition_1");
        sutExample1.setDescription("This is a SuT description example");
        sutExample1.setSpecification("elastest/test-etm-alpinegitjava");
        sutExample1.setSutType(SutTypeEnum.MANAGED);
        sutExample1.setManagedDockerType(ManagedDockerType.COMMANDS);
        sutExample1.setCommands("env");
        sutExample1.setInstrumentalize(false);
        sutExample1.setInstrumentalized(false);
        sutExample1.setCurrentSutExec(null);
        sutExample1.setInstrumentedBy(InstrumentedByEnum.WITHOUT);
        sutExample1.setPort(null);
        sutExample1.setManagedDockerType(ManagedDockerType.IMAGE);
        sutExample1.setCommandsOption(CommandsOptionEnum.DEFAULT);

        SutSpecification sutExample2 = new SutSpecification();
        sutExample2.setId(new Long(0));
        sutExample2.setName("sut_definition_1");
        sutExample2.setDescription("This is a SuT description example using docker-compose");
        sutExample2.setSpecification("version: '2.1'\n" + 
                "services:\n" + 
                "   dummy-sut:\n" + 
                "      image: elastest/etm-dummy-tss\n" + 
                "      environment:\n" + 
                "         - USE_TORM=true\n" + 
                "      expose:\n" + 
                "         - 8095\n" + 
                "      networks:\n" + 
                "         - elastest_elastest      \n" + 
                "      labels:\n" + 
                "         - io.elastest.type=tss\n" + 
                "         - io.elastest.tjob.tss.id=elastest/etm-dummy-tss\n" + 
                "         - io.elastest.tjob.tss.type=main\n" + 
                "networks:\n" + 
                "  elastest_elastest:\n" + 
                "    external: true");
        sutExample2.setMainService("dummy-sut");
        sutExample2.setSutType(SutTypeEnum.MANAGED);
        sutExample2.setManagedDockerType(ManagedDockerType.COMPOSE);
        sutExample2.setInstrumentalize(false);
        sutExample2.setInstrumentalized(false);
        sutExample2.setCurrentSutExec(null);
        sutExample2.setInstrumentedBy(InstrumentedByEnum.WITHOUT);
        sutExample2.setPort(null);

        sutExamples.put("sutFromImage", sutExample1);
        sutExamples.put("sutFromCompose", sutExample2);
        log.info("Finished Initializing the test environment");
    }

    /* *************** */
    /* *** Project *** */
    /* *************** */

    protected Project createProject(String projectName) {

        String requestJson = "{ \"id\": 0,\"name\": \"" + projectName + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,
                headers);

        log.info("POST /project");
        ResponseEntity<Project> response = httpClient
                .postForEntity("/api/project", entity, Project.class);

        return response.getBody();

    }

    protected void deleteProject(Long projectToDeleteId) {

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("projectId", projectToDeleteId);

        log.info("DELETE /project");
        ResponseEntity<Long> response = httpClient.exchange(
                "/api/project/{projectId}", HttpMethod.DELETE, null, Long.class,
                urlParams);
        log.info("Deleted project:" + response.getBody());

    }

    /* ************ */
    /* *** TJob *** */
    /* ************ */

    protected TJob getSampleTJob(long projectId) {
        Project project = new Project();
        project.setId(projectId);

        Parameter param = new Parameter();
        param.setName("Param1");
        param.setValue("Value1");

        TJob tJob = new TJob();
        tJob.setId(new Long(0));
        tJob.setName("testApp1");
        tJob.setImageName("elastest/test-etm-alpinegitjava");
        tJob.setResultsPath(
                "/demo-projects/unit-java-test/target/surefire-reports/");
        tJob.setCommands(
                "git clone https://github.com/elastest/demo-projects; cd demo-projects/unit-java-test;mvn -B -Dtest=CalcTest test");
        tJob.setParameters(Arrays.asList(param));
        tJob.setProject(project);

        tJob.setSelectedServices("[]");

        return tJob;
    }

    protected SutSpecification getSut(long sutId) {
        SutSpecification sut = new SutSpecification();
        sut.setId(sutId);
        sut = getSutById(sut.getId());
        return sut;
    }

    protected TJob getSampleTJobWithSut(long projectId, long sutId) {
        TJob tJob = this.getSampleTJob(projectId);

        if (sutId > -1) {
            SutSpecification sut = new SutSpecification();
            sut.setId(sutId);
            sut = getSutById(sut.getId());
            tJob.setSut(sut);
        }

        return tJob;
    }

    protected TJob createTJob(long projectId) throws JsonProcessingException {
        return createTJob(projectId, -1);
    }

    protected ResponseEntity<TJob> createTJobByGiven(TJob tJob)
            throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_NULL);
        JsonNode node = mapper.convertValue(tJob, JsonNode.class);
        ((ObjectNode) node).remove("selectedServicesObj");
        ((ObjectNode) node).remove("supportServicesObj");

        String requestJson = mapper.writeValueAsString(node);
        log.info("Json request {}", requestJson);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,
                headers);

        log.info("POST /api/tjob");
        ResponseEntity<TJob> response = httpClient.postForEntity("/api/tjob",
                entity, TJob.class);

        return response;
    }

    protected TJob createTJob(long projectId, long sutId)
            throws JsonProcessingException {
        TJob tJob = null;
        if (sutId > 0) {
            tJob = this.getSampleTJobWithSut(projectId, sutId);
        } else {
            tJob = this.getSampleTJob(projectId);
        }
        ResponseEntity<TJob> response = createTJobByGiven(tJob);
        log.info("TJob creation response: " + response);

        if (response.getStatusCode() != HttpStatus.OK) {
            log.warn("Error creating TJob: " + response);
        }

        return response.getBody();

    }

    protected TJob createTJob(TJob tJob) throws JsonProcessingException {

        ResponseEntity<TJob> response = createTJobByGiven(tJob);
        log.info("TJob creation response: " + response);

        if (response.getStatusCode() != HttpStatus.OK) {
            log.warn("Error creating TJob: " + response);
        }

        return response.getBody();

    }

    protected void modifyTJob(TJob tJob) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        String requestJson = mapper.writeValueAsString(tJob);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,
                headers);

        log.info("PUT /api/tjob");
        httpClient.put("/api/tjob", entity, TJob.class);
    }

    protected void deleteTJob(Long tJobId) {

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("tjobId", tJobId);

        log.info("DELETE /api/tjob/{tjobId");
        ResponseEntity<Long> response = httpClient.exchange(
                "/api/tjob/{tjobId}", HttpMethod.DELETE, null, Long.class,
                urlParams);
        log.info("Deleted tjob:" + response.getBody().longValue());

    }

    protected TJob getTJobById(Long tJobId) {

        log.info("Start the method getTJobById");

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("tJobId", tJobId);

        log.info("GET /api/tjob/{tJobId}");
        ResponseEntity<TJob> response = httpClient
                .getForEntity("/api/tjob/{tJobId}", TJob.class, urlParams);

        return response.getBody();

    }

    /* **************** */
    /* *** TJobExec *** */
    /* **************** */

    protected void deleteTJobExecution(Long tJobExecId, Long tJobId) {

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("tJobExecId", tJobExecId);
        urlParams.put("tJobId", tJobId);

        log.info("DELETE /api/tjob/{tJobId}/exec/{tJobExecId}");
        ResponseEntity<Long> response = httpClient.exchange(
                "/api/tjob/{tJobId}/exec/{tJobExecId}", HttpMethod.DELETE, null,
                Long.class, urlParams);
        log.info("Deleted tJobExec:" + response.getBody().longValue());

    }

    protected ResponseEntity<TJobExecution> getTJobExecutionById(
            Long tJobExecId, Long tJobId) {

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("tJobExecId", tJobExecId);
        urlParams.put("tJobId", tJobId);

        log.info("GET /api/tjob/{tJobId}/exec/{tJobExecId}");
        ResponseEntity<TJobExecution> response = httpClient.getForEntity(
                "/api/tjob/{tJobId}/exec/{tJobExecId}", TJobExecution.class,
                urlParams);

        return response;
    }

    /* *********** */
    /* *** SuT *** */
    /* *********** */

    protected ResponseEntity<SutSpecification> createSutByGiven(
            SutSpecification sut) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        String requestJson = mapper.writeValueAsString(sut);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,
                headers);

        log.info("POST /api/sut", requestJson);
        ResponseEntity<SutSpecification> response = httpClient
                .postForEntity("/api/sut", entity, SutSpecification.class);
        log.info("Sut created:" + response.getBody());

        return response;
    }

    protected SutSpecification createSut(long projectId)
            throws JsonProcessingException {
        SutSpecification sut = this.getSampleSut(projectId);
        ResponseEntity<SutSpecification> response = createSutByGiven(sut);
        log.info("Sut creation response: " + response);

        return response.getBody();
    }

    protected SutSpecification createSutBySutSpec(SutSpecification sut)
            throws JsonProcessingException {
        ResponseEntity<SutSpecification> response = createSutByGiven(sut);
        log.info("Sut creation response: " + response);

        return response.getBody();
    }

    protected void modifySutByGiven(SutSpecification sut)
            throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        String requestJson = mapper.writeValueAsString(sut);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<String>(requestJson,
                headers);

        log.info("PUT /api/sut");
        httpClient.put("/api/sut", entity, SutSpecification.class);
    }

    protected SutSpecification getSampleSut(long projectId) {
        Project project = new Project();
        project.setId(projectId);

        SutSpecification sut = new SutSpecification();
        sut.setId(new Long(0));
        sut.setName("sut_definition_1");
        sut.setDescription("This is a SuT description example");
        sut.setProject(project);
        sut.setSpecification("elastest/test-etm-alpinegitjava");
        sut.setSutType(SutTypeEnum.MANAGED);
        sut.setManagedDockerType(ManagedDockerType.COMMANDS);
        sut.setCommands("env");
        sut.setInstrumentalize(false);
        sut.setInstrumentalized(false);
        sut.setCurrentSutExec(null);
        sut.setInstrumentedBy(InstrumentedByEnum.WITHOUT);
        sut.setPort(null);
        sut.setManagedDockerType(ManagedDockerType.IMAGE);
        sut.setCommandsOption(CommandsOptionEnum.DEFAULT);
        return sut;
    }

    protected Long deleteSut(Long sutId) {
        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("sutId", sutId);

        log.info("DELETE /api/sut/{}", sutId);
        ResponseEntity<Long> response = httpClient.exchange("/api/sut/{sutId}",
                HttpMethod.DELETE, null, Long.class, urlParams);
        log.info("Deleted sutSpecification:" + response.getBody());

        return response.getBody();
    }

    public SutSpecification getSutById(Long sutId) {
        log.info("Start the method getSutById");

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("sutId", sutId);

        log.info("GET /api/sut/{}", sutId);
        ResponseEntity<SutSpecification> response = httpClient.getForEntity(
                "/api/sut/{sutId}", SutSpecification.class, urlParams);

        return response.getBody();
    }

    public SutExecution[] getAllSutExecBySut(Long sutId) {
        log.info("Start the method getAllSutExecBySut");

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("sutId", sutId);

        log.info("GET /api/sut/{}/exec", sutId);
        ResponseEntity<SutExecution[]> response = httpClient.getForEntity(
                "/api/sut/{sutId}/exec", SutExecution[].class, urlParams);

        return response.getBody();
    }

    public SutExecution getSutExec(Long sutId, Long sutExecId) {
        log.info("Start the method getSutExec");

        Map<String, Long> urlParams = new HashMap<>();
        urlParams.put("sutId", sutId);
        urlParams.put("sutExecId", sutExecId);

        log.info("GET /api/sut/{}/exec/{}", sutId, sutExecId);
        ResponseEntity<SutExecution> response = httpClient.getForEntity(
                "/api/sut/{sutId}/exec/{sutExecId}", SutExecution.class,
                urlParams);

        return response.getBody();
    }

    protected void deleteSuTExec(Long sutExecId) {
        log.info("DELETE /api/sut/exec/{}", sutExecId);
        httpClient.delete("/api/sut/exec/" + sutExecId);
    }
}
