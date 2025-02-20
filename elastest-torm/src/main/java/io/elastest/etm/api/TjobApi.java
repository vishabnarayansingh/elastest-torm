package io.elastest.etm.api;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import io.elastest.etm.model.ElastestFile;
import io.elastest.etm.model.ExecData;
import io.elastest.etm.model.TJob;
import io.elastest.etm.model.TJobExecution;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.SpringCodegen", date = "2017-05-19T13:25:11.074+02:00")

@Api(value = "tjob")
public interface TjobApi extends EtmApiRoot {

    /* *************** */
    /* **** TJobs **** */
    /* *************** */

    @ApiOperation(value = "Create a new TJob", notes = "Creates a new TJob associated with an existing project. This method,"
            + " at least must receive as input a JSON with the following fields: TJob name, imageName, external and project.", response = TJob.class, tags = {
                    "TJob", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Creation successful", response = TJob.class),
            @ApiResponse(code = 405, message = "Invalid input") })
    @RequestMapping(value = "/tjob", produces = {
            "application/json" }, consumes = {
                    "application/json" }, method = RequestMethod.POST)
    ResponseEntity<TJob> createTJob(
            @ApiParam(value = "Data to create the new TJob", required = true) @Valid @RequestBody TJob body);

    @ApiOperation(value = "Deletes a TJob", notes = "Deletes the TJob identified by the received id.", response = Long.class, tags = {
            "TJob", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Deleteted successful", response = Long.class),
            @ApiResponse(code = 404, message = "TJob not found", response = Long.class) })
    @RequestMapping(value = "/tjob/{tJobId}", method = RequestMethod.DELETE)
    ResponseEntity<Long> deleteTJob(
            @ApiParam(value = "Id of a TJob.", required = true) @PathVariable("tJobId") Long tJobId);

    @ApiOperation(value = "Returns all tjobs", notes = "Returns all TJobs.", response = TJob.class, responseContainer = "List", tags = {
            "TJob", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJob.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "Resource not found") })
    @RequestMapping(value = "/tjob", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJob>> getAllTJobs();

    @ApiOperation(value = "Returns a TJob", notes = "Returns the TJob identified by the received id with all its detailed data.", response = TJob.class, tags = {
            "TJob", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJob.class),
            @ApiResponse(code = 400, message = "TJob not found.", response = TJob.class) })
    @RequestMapping(value = "/tjob/{tJobId}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<TJob> getTJobById(
            @ApiParam(value = "TJob id.", required = true) @PathVariable("tJobId") Long tJobId,
            @RequestParam(value = "viewType", required = false) String viewType);

    @ApiOperation(value = "Modifies a existing TJob", notes = "Modifies the TJob that matches the received TJob.", response = TJob.class, tags = {
            "TJob", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "TJob Mofification Successful", response = TJob.class),
            @ApiResponse(code = 405, message = "Invalid input", response = TJob.class) })
    @RequestMapping(value = "/tjob", produces = {
            "application/json" }, consumes = {
                    "application/json" }, method = RequestMethod.PUT)
    ResponseEntity<TJob> modifyTJob(
            @ApiParam(value = "TJob object that needs to modify.", required = true) @Valid @RequestBody TJob body);

    /* ******************* */
    /* **** TJobExecs **** */
    /* ******************* */

    @ApiOperation(value = "Executes a TJob", notes = "Execute the TJob with the received id.", response = Long.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class),
            @ApiResponse(code = 202, message = "The request has been accepted, but the processing has not been completed"),
            @ApiResponse(code = 400, message = "Invalid ID supplied"),
            @ApiResponse(code = 404, message = "TJob not found"),
            @ApiResponse(code = 500, message = "Server Error") })
    @RequestMapping(value = "/tjob/{tJobId}/exec", consumes = {
            "application/json" }, produces = {
                    "application/json" }, method = RequestMethod.POST)
    ResponseEntity<TJobExecution> execTJob(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId,
            @ApiParam(value = "Execution Parameters", required = false) @Valid @RequestBody ExecData parameters);

    @ApiOperation(value = "Deletes a TJob Execution", notes = "Deletes the TJob Execution for a given id.", response = Long.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Long.class),
            @ApiResponse(code = 404, message = "TJob Execution not found", response = Long.class) })
    @RequestMapping(value = "/tjob/{tJobId}/exec/{tJobExecId}", method = RequestMethod.DELETE)
    ResponseEntity<Long> deleteTJobExecution(
            @ApiParam(value = "Id of a TJob.", required = true) @PathVariable("tJobId") Long tJobId,
            @ApiParam(value = "TJob Execution Id associatd for a given TJob Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId);

    @ApiOperation(value = "Returns a TJob Execution", notes = "Returns the TJob Execution for a given id.", response = TJobExecution.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class),
            @ApiResponse(code = 404, message = "TJob Execution not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec/{tJobExecId}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<TJobExecution> getTJobExecution(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId,
            @ApiParam(value = "TJob Execution Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId);

    @ApiOperation(value = "Stops a TJob Execution", notes = "Stops the TJob Execution for a given id.", response = TJobExecution.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Long.class),
            @ApiResponse(code = 404, message = "TJob Execution not found", response = Long.class) })
    @RequestMapping(value = "/tjob/{tJobId}/exec/{tJobExecId}/stop", produces = {
            "application/json" }, method = RequestMethod.DELETE)
    ResponseEntity<TJobExecution> stopTJobExecution(
            @ApiParam(value = "Id of a TJob.", required = true) @PathVariable("tJobId") Long tJobId,
            @ApiParam(value = "TJob Execution Id associatd for a given TJob Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId);

    @ApiOperation(value = "Returns all TJob Executions of a TJob", notes = "Returns all TJob Executions of a TJob.", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getTJobExecutionsByTJob(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId);

    @ApiOperation(value = "Returns all TJob Executions of a TJob without Multi childs", notes = "Returns all TJob Executions of a TJob without Multi childs", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec/withoutchilds", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getTJobExecutionsByTJobWithoutChilds(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId);

    @ApiOperation(value = "Returns last N TJob Executions", notes = "Returns last N TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/{tJobId}/execs/last/{number}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getLastNTJobExecutions(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId,
            @ApiParam(value = "Number of TJobExecs to get.", required = true) @PathVariable("number") Long number,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns N TJob Executions from exec id since (less or greater)", notes = "Returns N TJob Executions from exec id since (less or greater)", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec/range", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getTJobExecsPageSinceId(
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId,
            @RequestParam(value = "page", required = true) int page,
            @RequestParam(value = "pageSize", required = true) int pageSize,
            @RequestParam(value = "than", required = true) String than,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    /* ****************************** */
    /* *** All execs of all TJobs *** */
    /* ****************************** */

    @ApiOperation(value = "Returns all TJob Executions of all tjobs", notes = "Returns all TJob Executions of all tjobs", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getAllTJobExecutions();

    /* *** By ID *** */

    @ApiOperation(value = "Returns TJob Executions By Range", notes = "Returns TJob Executions By Range", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/range", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getTJobsExecsRange(
            @RequestParam(value = "page", required = true) int page,
            @RequestParam(value = "pageSize", required = true) int pageSize,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns last N TJob Executions of all tjobs", notes = "Returns last N TJob Executions of all tjobs", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/last/{number}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getLastNTJobsExecutions(
            @ApiParam(value = "Number of TJobExecs to get.", required = true) @PathVariable("number") Long number,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    /* *** By results *** */

    @ApiOperation(value = "Returns all Running TJob Executions", notes = "Returns all Running TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/running", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getAllRunningTJobsExecutions(
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns all Running TJob Executions By Range", notes = "Returns all Running TJob Executions By Range", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/running/range", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getRunningTJobsExecutionsByPage(
            @RequestParam(value = "page", required = true) int page,
            @RequestParam(value = "pageSize", required = true) int pageSize,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns last N Running TJob Executions", notes = "Returns last N Running TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/running/last/{number}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getLastNRunningTJobsExecutions(
            @ApiParam(value = "Number of TJobExecs to get.", required = true) @PathVariable("number") Long number,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns all Finished or Not Executed TJob Executions", notes = "Returns all Finished or Not Executed TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/finished", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getAllFinishedOrNotExecutedTJobsExecutions(
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns all Finished or Not Executed TJob Executions", notes = "Returns all Finished or Not Executed TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/finished/{tJobExecId}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getAllFinishedOrNotExecutedTJobsExecutionsSinceId(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId,
            @RequestParam(value = "than", required = true) String than,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns last N Finished or Not Executed TJob Executions", notes = "Returns last N Finished or Not Executed TJob Executions", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/finished/last/{number}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getLastNFinishedOrNotExecutedTJobsExecutions(
            @ApiParam(value = "Number of TJobExecs to get.", required = true) @PathVariable("number") Long number,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns Finished or Not Executed TJob Executions By Range", notes = "Returns Finished or Not Executed TJob Executions By Range", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/finished/range", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getFinishedOrNotExecutedTJobsExecutionsByPage(
            @RequestParam(value = "page", required = true) int page,
            @RequestParam(value = "pageSize", required = true) int pageSize,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns Finished or Not Executed TJob Executions By Range", notes = "Returns Finished or Not Executed TJob Executions By Range", response = TJobExecution.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJobs Executions not found") })
    @RequestMapping(value = "/tjob/execs/finished/range/{tJobExecId}", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getFinishedOrNotExecutedTJobsExecutionsByPageAndSinceId(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId,
            @RequestParam(value = "page", required = true) int page,
            @RequestParam(value = "pageSize", required = true) int pageSize,
            @RequestParam(value = "than", required = true) String than,
            @RequestParam(value = "withoutChilds", required = true) Boolean withoutChilds);

    @ApiOperation(value = "Returns all files associated to a TJob Execution.", notes = "Returns all files associated to a TJob Execution, for a given TJob execution id.", response = ElastestFile.class, responseContainer = "List", tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class, responseContainer = "List"),
            @ApiResponse(code = 404, message = "TJob Executions files not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec/{tJobExecId}/files", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<ElastestFile>> getTJobExecutionFiles(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId,
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId);

    @ApiOperation(value = "Returns the current result and result message of a TJob Execution", notes = "Returns the current result and result message of a TJob Execution.", response = Map.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Map.class),
            @ApiResponse(code = 404, message = "Result status not found") })
    @RequestMapping(value = "/tjob/{tJobId}/exec/{tJobExecId}/result", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<Map<String, Object>> getTJobExecResultStatus(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId,
            @ApiParam(value = "TJob Id.", required = true) @PathVariable("tJobId") Long tJobId);

    @ApiOperation(value = "Returns the parent of a child TJob Execution", notes = "Returns the parent of a child TJob Execution.", response = TJobExecution.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class),
            @ApiResponse(code = 404, message = "Result status not found") })
    @RequestMapping(value = "/tjob/exec/{tJobExecId}/parent", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<TJobExecution> getChildTJobExecParent(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId);

    @ApiOperation(value = "Returns the child list of a parent TJob Execution", notes = "Returns the child list of a parent TJob Execution.", response = TJobExecution.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = TJobExecution.class),
            @ApiResponse(code = 404, message = "Result status not found") })
    @RequestMapping(value = "/tjob/exec/{tJobExecId}/childs", produces = {
            "application/json" }, method = RequestMethod.GET)
    ResponseEntity<List<TJobExecution>> getParentTJobExecChilds(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId);

    @ApiOperation(value = "Save a file attachment of a TJob Execution", notes = "Save a file attachment of a TJob Execution.", response = Boolean.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Boolean.class),
            @ApiResponse(code = 202, message = "The request has been accepted, but the processing has not been completed"),
            @ApiResponse(code = 400, message = "Invalid File supplied"),
            @ApiResponse(code = 404, message = "TJob not found"),
            @ApiResponse(code = 500, message = "Server Error") })
    @RequestMapping(value = "/tjob/exec/{tJobExecId}/attachment", consumes = {
            "multipart/form-data" }, produces = {
                    "application/json" }, method = RequestMethod.POST)
    ResponseEntity<Boolean> saveExecAttachment(
            @ApiParam(value = "TJobExec Id.", required = true) @PathVariable("tJobExecId") Long tJobExecId,
            @RequestParam(value = "file") MultipartFile file);
    
    @ApiOperation(value = "Copy the test results report from the pod given as parameter.", response = Long.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Integer.class),
            @ApiResponse(code = 404, message = "Resources not found") })
    @RequestMapping(value = "/tjob/exec/pod/{podName}/results/files", produces = {
            "text/plain" }, method = RequestMethod.POST)
    ResponseEntity<Integer> copyTestResultsFileFromContainer(
            @ApiParam(value = "Name of the pod to use in the finalization tasks.", required = true) @PathVariable("podName") String podName);
    
    
    @ApiOperation(value = "Notifies ElasTest that the TJob is finishing its execution to do some tasks before.", response = Long.class, tags = {
            "TJob Execution", })
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successful operation", response = Integer.class),
            @ApiResponse(code = 404, message = "Resources not found") })
    @RequestMapping(value = "/tjob/exec/pod/{podName}/results", produces = {
            "text/plain" }, method = RequestMethod.POST)
    ResponseEntity<Integer> saveTestResultsFromContainer(
            @ApiParam(value = "Name of the pod to use in the finalization tasks.", required = true) @PathVariable("podName") String podName);
    
    

}
