package io.elastest.etm.api;

import java.util.List;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import br.eti.kinoshita.testlinkjavaapi.model.Build;
import br.eti.kinoshita.testlinkjavaapi.model.Execution;
import br.eti.kinoshita.testlinkjavaapi.model.Platform;
import br.eti.kinoshita.testlinkjavaapi.model.TestCase;
import br.eti.kinoshita.testlinkjavaapi.model.TestPlan;
import br.eti.kinoshita.testlinkjavaapi.model.TestProject;
import br.eti.kinoshita.testlinkjavaapi.model.TestSuite;
import br.eti.kinoshita.testlinkjavaapi.util.TestLinkAPIException;
import io.elastest.epm.client.model.DockerServiceStatus.DockerServiceStatusEnum;
import io.elastest.etm.config.EtSampleDataLoader;
import io.elastest.etm.model.EtPlugin;
import io.elastest.etm.model.external.ExternalProject;
import io.elastest.etm.model.external.ExternalTJob;
import io.elastest.etm.model.external.ExternalTestCase;
import io.elastest.etm.model.external.ExternalTestExecution;
import io.elastest.etm.service.EtPluginsService;
import io.elastest.etm.service.TestLinkService;
import io.swagger.annotations.ApiParam;

@Controller
public class TestLinkApiController implements TestLinkApi {
    private static final Logger logger = LoggerFactory
            .getLogger(TestLinkApiController.class);

    @Autowired
    TestLinkService testLinkService;

    @Autowired
    EtPluginsService etPluginsService;

    @Autowired
    EtSampleDataLoader etSampleDataLoader;

    public ResponseEntity<Boolean> isStarted() {
        return new ResponseEntity<Boolean>(testLinkService.isStarted(),
                HttpStatus.OK);
    }

    public ResponseEntity<Boolean> isReady() {
        return new ResponseEntity<Boolean>(testLinkService.isReady(),
                HttpStatus.OK);
    }

    @Override
    public ResponseEntity<EtPlugin> startTestLink() {
        EtPlugin engine = etPluginsService.getUniqueEtPlugin("testlink");

        if (engine.getStatus()
                .equals(DockerServiceStatusEnum.NOT_INITIALIZED)) {
            engine.setStatus(DockerServiceStatusEnum.INITIALIZING);
            engine.setStatusMsg("Initializing...");
        }
        testLinkService.startTLOnDemand();

        etSampleDataLoader.createTestLinkAsync();

        return new ResponseEntity<EtPlugin>(engine, HttpStatus.OK);
    }

    /* ************************************************************************/
    /* **************************** Test Projects *****************************/
    /* ************************************************************************/

    // @JsonView()
    public ResponseEntity<TestProject[]> getAllTestProjects() {

        return new ResponseEntity<TestProject[]>(testLinkService.getProjects(),
                HttpStatus.OK);
    }

    public ResponseEntity<TestProject> getProjectByName(
            @ApiParam(value = "Name of the project.", required = true) @PathVariable("projectName") String projectName) {
        return new ResponseEntity<TestProject>(
                testLinkService.getProjectByName(projectName), HttpStatus.OK);
    }

    public ResponseEntity<TestProject> getProjectById(
            @ApiParam(value = "Name of the project.", required = true) @PathVariable("projectId") Integer projectId) {
        return new ResponseEntity<TestProject>(
                testLinkService.getProjectById(projectId), HttpStatus.OK);
    }

    public ResponseEntity<TestProject> createProject(
            @ApiParam(value = "Object with the test project data to create.", required = true) @Valid @RequestBody TestProject body) {
        TestProject project = null;
        try {
            project = testLinkService.createProject(body);
            return new ResponseEntity<TestProject>(project, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<TestProject>(project,
                    HttpStatus.CONFLICT);
        }
    }

    /* ************************************************************************/
    /* ***************************** Test Suites ******************************/
    /* ************************************************************************/

    public ResponseEntity<TestSuite> getTestSuiteById(
            @ApiParam(value = "ID of the project.", required = true) @PathVariable("projectId") Integer projectId,
            @ApiParam(value = "Id of Test suite.", required = true) @PathVariable("suiteId") Integer suiteId) {
        return new ResponseEntity<TestSuite>(
                testLinkService.getTestSuiteById(suiteId), HttpStatus.OK);
    }

    public ResponseEntity<TestSuite> getSuiteByName(
            @ApiParam(value = "ID of the Test Project.", required = true) @PathVariable("projectId") Integer projectId,
            @ApiParam(value = "Name of the suite.", required = true) @PathVariable("suiteName") String suiteName) {
        return new ResponseEntity<TestSuite>(
                testLinkService.getTestSuiteByName(suiteName, projectId),
                HttpStatus.OK);
    }

    public ResponseEntity<TestSuite[]> getProjectTestSuites(
            @ApiParam(value = "ID of the Test Project.", required = true) @PathVariable("projectId") Integer projectId) {
        return new ResponseEntity<TestSuite[]>(
                testLinkService.getProjectTestSuites(projectId), HttpStatus.OK);

    }

    public ResponseEntity<TestSuite[]> getAllTestSuites() {
        return new ResponseEntity<TestSuite[]>(
                testLinkService.getAllTestSuites(), HttpStatus.OK);
    }

    public ResponseEntity<TestSuite> createSuite(
            @ApiParam(value = "Object with the Test Suite data to create.", required = true) @Valid @RequestBody TestSuite body) {
        TestSuite suite = null;
        try {
            suite = testLinkService.createTestSuite(body);
            return new ResponseEntity<TestSuite>(suite, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<TestSuite>(suite, HttpStatus.CONFLICT);
        }
    }

    /* ***********************************************************************/
    /* ***************************** Test Cases ******************************/
    /* ***********************************************************************/

    public ResponseEntity<TestCase> getTestcase(
            @ApiParam(value = "Id of Test case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<TestCase>(
                testLinkService.getTestCaseById(caseId), HttpStatus.OK);
    }

    public ResponseEntity<TestCase> getTestCaseByName(
            @ApiParam(value = "Id of Test Suite.", required = true) @PathVariable("suiteId") Integer suiteId,
            @ApiParam(value = "Name of Test case.", required = true) @PathVariable("caseName") String caseName) {
        return new ResponseEntity<TestCase>(
                testLinkService.getTestCaseByNameAndSuiteId(caseName, suiteId),
                HttpStatus.OK);
    }

    public ResponseEntity<TestCase[]> getAllTestCases() {
        return new ResponseEntity<TestCase[]>(testLinkService.getAllTestCases(),
                HttpStatus.OK);
    }

    public ResponseEntity<TestCase[]> getSuiteTestCases(
            @ApiParam(value = "Id of Test Suite.", required = true) @PathVariable("suiteId") Integer suiteId) {
        return new ResponseEntity<TestCase[]>(
                testLinkService.getSuiteTestCases(suiteId), HttpStatus.OK);
    }

    public ResponseEntity<TestCase[]> getPlanTestCases(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<TestCase[]>(
                testLinkService.getPlanTestCases(planId), HttpStatus.OK);
    }

    public ResponseEntity<TestCase> getPlanTestCaseByPlatformId(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Id of Test Case.", required = true) @PathVariable("caseId") Integer caseId,
            @ApiParam(value = "Id of Platform.", required = true) @PathVariable("platformId") Integer platformId) {
        return new ResponseEntity<TestCase>(
                testLinkService.getPlanTestCaseByIdAndPlatformIdAndBuildId(
                        planId, caseId, platformId),
                HttpStatus.OK);
    }

    public ResponseEntity<TestCase> getPlanTestCaseByPlatformIdAndBuildId(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Id of the Build.", required = true) @PathVariable("buildId") Integer buildId,
            @ApiParam(value = "Id of Platform.", required = true) @PathVariable("platformId") Integer platformId,
            @ApiParam(value = "Id of Test Case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<TestCase>(
                testLinkService.getPlanTestCaseByIdAndPlatformIdAndBuildId(
                        planId, caseId, platformId, buildId),
                HttpStatus.OK);
    }

    public ResponseEntity<List<TestCase>> getPlanTestCasesByPlatformId(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Id of Platform.", required = true) @PathVariable("platformId") Integer platformId) {
        return new ResponseEntity<List<TestCase>>(testLinkService
                .getPlanTestCasesByPlatformId(planId, platformId),
                HttpStatus.OK);
    }

    public ResponseEntity<List<TestCase>> getPlanTestCasesByPlatformIdAndBuildId(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Id of the Build.", required = true) @PathVariable("buildId") Integer buildId,
            @ApiParam(value = "Id of Platform.", required = true) @PathVariable("platformId") Integer platformId) {
        return new ResponseEntity<List<TestCase>>(
                testLinkService.getPlanTestCasesByPlatformIdAndBuildId(planId,
                        platformId, buildId),
                HttpStatus.OK);
    }

    public ResponseEntity<TestCase> createTestCase(
            @ApiParam(value = "Id of Test Suite.", required = true) @PathVariable("suiteId") Integer suiteId,
            @ApiParam(value = "Object with the Test Case data to create.", required = true) @Valid @RequestBody TestCase body) {
        TestCase testCase = null;
        try {
            testCase = testLinkService.createTestCase(body, suiteId);
            return new ResponseEntity<TestCase>(testCase, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<TestCase>(testCase, HttpStatus.CONFLICT);
        }
    }
    /* ***********************************************************************/
    /* ***************************** Test Plans ******************************/
    /* ***********************************************************************/

    public ResponseEntity<TestPlan[]> getAllTestPlans() {
        return new ResponseEntity<TestPlan[]>(testLinkService.getAllTestPlans(),
                HttpStatus.OK);
    }

    public ResponseEntity<TestPlan[]> getProjectTestPlans(
            @ApiParam(value = "ID of the project.", required = true) @PathVariable("id") Integer id) {
        return new ResponseEntity<TestPlan[]>(
                testLinkService.getProjectTestPlans(id), HttpStatus.OK);
    }

    public ResponseEntity<TestPlan> getPlanByNameAndProjectName(
            @ApiParam(value = "Name of the project.", required = true) @PathVariable("projectName") String projectName,
            @ApiParam(value = "Name of the plan.", required = true) @PathVariable("planName") String planName) {
        return new ResponseEntity<TestPlan>(testLinkService
                .getTestPlanByNameAndProjectName(planName, projectName),
                HttpStatus.OK);
    }

    public ResponseEntity<TestPlan> getPlanByName(
            @ApiParam(value = "Name of the plan.", required = true) @PathVariable("planName") String planName) {
        return new ResponseEntity<TestPlan>(
                testLinkService.getTestPlanByName(planName), HttpStatus.OK);
    }

    public ResponseEntity<TestPlan> getPlanById(
            @ApiParam(value = "Id of the plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<TestPlan>(
                testLinkService.getTestPlanById(planId), HttpStatus.OK);
    }

    public ResponseEntity<TestPlan> createPlan(
            @ApiParam(value = "Object with the Test Plan data to create.", required = true) @Valid @RequestBody TestPlan body) {
        TestPlan plan = null;
        try {
            plan = testLinkService.createTestPlan(body);
            return new ResponseEntity<TestPlan>(plan, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<TestPlan>(plan, HttpStatus.CONFLICT);
        }
    }

    public ResponseEntity<Integer> addTestCaseToTestPlan(
            @ApiParam(value = "Id of the plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Object with the Test Case data to create.", required = true) @Valid @RequestBody TestCase body) {
        try {
            Integer associatedCaseId = testLinkService
                    .addTestCaseToTestPlan(body, planId);
            return new ResponseEntity<Integer>(associatedCaseId, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<Integer>(-1, HttpStatus.CONFLICT);
        }
    }
    /* **********************************************************************/
    /* **************************** Plan Builds *****************************/
    /* **********************************************************************/

    public ResponseEntity<Build[]> getPlanBuilds(
            @ApiParam(value = "ID of the plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<Build[]>(
                testLinkService.getPlanBuilds(planId), HttpStatus.OK);
    }

    public ResponseEntity<Build> getPlanBuildById(
            @ApiParam(value = "ID of the plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "ID of the build.", required = true) @PathVariable("buildId") Integer buildId) {
        return new ResponseEntity<Build>(
                testLinkService.getPlanBuildById(planId, buildId),
                HttpStatus.OK);
    }

    public ResponseEntity<Build> getBuildByName(
            @ApiParam(value = "Name of the build.", required = true) @PathVariable("buildName") String buildName) {
        return new ResponseEntity<Build>(
                testLinkService.getPlanBuildByName(buildName), HttpStatus.OK);
    }

    public ResponseEntity<Build> getLatestPlanBuild(
            @ApiParam(value = "Name of the project.", required = true) @PathVariable("projectName") String projectName,
            @ApiParam(value = "ID of the plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<Build>(
                testLinkService.getLatestPlanBuild(planId), HttpStatus.OK);
    }

    public ResponseEntity<Build> createBuild(
            @ApiParam(value = "Object with the Test Plan data to create.", required = true) @Valid @RequestBody Build body) {
        Build build = null;
        try {
            build = testLinkService.createBuild(body);
            return new ResponseEntity<Build>(build, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            return new ResponseEntity<Build>(build, HttpStatus.CONFLICT);
        }
    }

    public ResponseEntity<Build> getBuildById(
            @ApiParam(value = "Id of the Build.", required = true) @PathVariable("buildId") Integer buildId) {
        return new ResponseEntity<Build>(testLinkService.getBuildById(buildId),
                HttpStatus.OK);
    }

    public ResponseEntity<TestCase[]> getBuildTestCases(
            @ApiParam(value = "Id of the Build.", required = true) @PathVariable("buildId") Integer buildId) {
        return new ResponseEntity<TestCase[]>(
                testLinkService.getBuildTestCasesById(buildId), HttpStatus.OK);
    }

    public ResponseEntity<TestCase> getBuildTestCaseById(
            @ApiParam(value = "Id of the Build.", required = true) @PathVariable("buildId") Integer buildId,
            @ApiParam(value = "Id of the Test Case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<TestCase>(
                testLinkService.getBuildTestCaseById(buildId, caseId),
                HttpStatus.OK);
    }

    /* **********************************************************************/
    /* ***************************** Platforms ******************************/
    /* **********************************************************************/

    public ResponseEntity<Platform[]> getTestPlanPlatforms(
            @ApiParam(value = "Id of the Test Plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<Platform[]>(
                testLinkService.getPlanPlatforms(planId), HttpStatus.OK);
    }

    /* ***********************************************************************/
    /* ***************************** Executions ******************************/
    /* ***********************************************************************/

    public ResponseEntity<Execution> executeTestCase(
            @ApiParam(value = "ID of the test case.", required = true) @PathVariable("caseId") Integer caseId,
            @ApiParam(value = "ID of the platform.", required = true) @PathVariable("platformId") Integer platformId,
            @ApiParam(value = "Object with the Test Case Results.", required = true) @Valid @RequestBody Execution body) {
        Execution exec = null;

        try {
            exec = testLinkService.saveExecution(body, caseId, platformId);
            return new ResponseEntity<Execution>(exec, HttpStatus.OK);
        } catch (TestLinkAPIException e) {
            logger.error("Error on save Test Case execution {}", caseId, e);
            return new ResponseEntity<Execution>(exec, HttpStatus.CONFLICT);
        }
    }

    public ResponseEntity<Execution[]> getAllExecs() {
        return new ResponseEntity<Execution[]>(testLinkService.getAllExecs(),
                HttpStatus.OK);
    }

    public ResponseEntity<Execution[]> getTestCaseExecs(
            @ApiParam(value = "Id of Test case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<Execution[]>(
                testLinkService.getTestCaseExecs(caseId), HttpStatus.OK);
    }

    public ResponseEntity<Execution> getTestExecById(
            @ApiParam(value = "Id of Test Case.", required = true) @PathVariable("caseId") Integer caseId,
            @ApiParam(value = "Id of Test Execution.", required = true) @PathVariable("execId") Integer execId) {
        return new ResponseEntity<Execution>(
                testLinkService.getTestExecById(caseId, execId), HttpStatus.OK);

    }

    public ResponseEntity<Execution[]> getPlanTestCaseExecs(
            @ApiParam(value = "Id of Test Plan.", required = true) @PathVariable("planId") Integer planId,
            @ApiParam(value = "Id of Test case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<Execution[]>(
                testLinkService.getPlanTestCaseExecs(planId, caseId),
                HttpStatus.OK);
    }

    public ResponseEntity<Execution[]> getBuildTestCaseExecs(
            @ApiParam(value = "ID of the build.", required = true) @PathVariable("buildId") Integer buildId,
            @ApiParam(value = "Id of Test case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<Execution[]>(
                testLinkService.getBuildTestCaseExecs(buildId, caseId),
                HttpStatus.OK);
    }

    /* *********************************************************/
    /* ************************ Others *************************/
    /* *********************************************************/

    public ResponseEntity<String> getTestLinkUrl() {
        return new ResponseEntity<String>(testLinkService.getTestLinkUrl(),
                HttpStatus.OK);
    }

    public ResponseEntity<Boolean> syncTestLink() {
        return new ResponseEntity<Boolean>(testLinkService.syncTestLink(),
                HttpStatus.OK);
    }

    public ResponseEntity<Boolean> dropExternalTLData() {
        return new ResponseEntity<Boolean>(testLinkService.dropExternalTLData(),
                HttpStatus.OK);
    }

    public ResponseEntity<ExternalProject> getExternalProjectByTestProjectId(
            @ApiParam(value = "ID of the project.", required = true) @PathVariable("projectId") Integer projectId) {
        return new ResponseEntity<ExternalProject>(
                testLinkService.getExternalProjectByTestProjectId(projectId),
                HttpStatus.OK);
    }

    public ResponseEntity<ExternalTJob> getExternalTJobByTestPlanId(
            @ApiParam(value = "ID of the plan.", required = true) @PathVariable("planId") Integer planId) {
        return new ResponseEntity<ExternalTJob>(
                testLinkService.getExternalTJobByPlanId(planId), HttpStatus.OK);
    }

    public ResponseEntity<ExternalTestCase> getExternalTestCaseByTestCaseId(
            @ApiParam(value = "ID of the Test Case.", required = true) @PathVariable("caseId") Integer caseId) {
        return new ResponseEntity<ExternalTestCase>(
                testLinkService.getExternalTestCaseByTestCaseId(caseId),
                HttpStatus.OK);
    }

    public ResponseEntity<ExternalTestExecution> getExternalTestExecutionByExecutionId(
            @ApiParam(value = "ID of the Execution.", required = true) @PathVariable("execId") Integer execId) {
        return new ResponseEntity<ExternalTestExecution>(
                testLinkService.getExternalTestExecByExecutionId(execId),
                HttpStatus.OK);
    }

}
