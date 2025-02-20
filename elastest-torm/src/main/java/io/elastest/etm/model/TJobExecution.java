package io.elastest.etm.model;

import static io.elastest.etm.utils.ToStringUtils.toIndentedString;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonView;

import io.elastest.etm.model.Enums.MonitoringStorageType;
import io.elastest.etm.model.Project.ProjectCompleteView;
import io.elastest.etm.model.SutSpecification.SutTypeEnum;
import io.elastest.etm.model.TJob.TJobCompleteView;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * TJobExecution
 */

@Entity
@ApiModel(description = "Data generated by the execution of a TJob.")
public class TJobExecution {

    public interface TJobExecMinimalView {
    }

    public interface TJobExecMediumView extends TJobExecMinimalView {
    }

    public interface TJobExecCompleteView extends TJobExecMediumView {
    }

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "native")
    @GenericGenerator(name = "native", strategy = "native")
    @Column(name = "id")
    @JsonProperty("id")
    private Long id = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "duration")
    @JsonProperty("duration")
    private Long duration = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "result")
    @JsonProperty("result")
    private ResultEnum result = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sut_execution")
    @JsonProperty("sutExecution")
    private SutExecution sutExecution = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "error")
    @JsonProperty("error")
    private String error = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "monitoringIndex")
    private String monitoringIndex = null;

    @JsonView({ TJobExecCompleteView.class })
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tjob")
    private TJob tJob;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @OneToMany(mappedBy = "tJobExec", cascade = CascadeType.REMOVE)
    private List<TestSuite> testSuites;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @ElementCollection
    @CollectionTable(name = "TJobExecParameter", joinColumns = @JoinColumn(name = "TJobExec"))
    private List<Parameter> parameters = new ArrayList<>();

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })

    @ElementCollection
    private List<String> servicesInstances;

    @ElementCollection
    @MapKeyColumn(name = "VAR_NAME", length = 200)
    @Column(name = "value", length = 400)
    @CollectionTable(name = "ENV_VARS", joinColumns = @JoinColumn(name = "TJOB_EXEC"))
    private Map<String, String> envVars;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "resultMsg")
    private String resultMsg = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "startDate")
    private Date startDate = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "endDate")
    private Date endDate = null;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @ElementCollection
    @MapKeyColumn(name = "URL_NAME", length = 100)
    @Column(name = "URL_VALUE", length = 400)
    @CollectionTable(name = "TJOB_EXEC_EXTERNAL_URLS", joinColumns = @JoinColumn(name = "TJOB_EXEC"))
    private Map<String, String> externalUrls;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "monitoringStorageType")
    @JsonProperty("monitoringStorageType")
    private MonitoringStorageType monitoringStorageType;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @Column(name = "type")
    @JsonProperty("type")
    private TypeEnum type = TypeEnum.SIMPLE;

    /* *** Multi *** */
    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execParent")
    @JsonBackReference
    private TJobExecution execParent;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @OneToMany(mappedBy = "execParent", cascade = CascadeType.REMOVE)
    @JsonManagedReference
    private List<TJobExecution> execChilds;

    @JsonView({ TJobExecCompleteView.class, TJobCompleteView.class,
            ProjectCompleteView.class })
    @ElementCollection
    @CollectionTable(name = "TJobExecMultiConfiguration", joinColumns = @JoinColumn(name = "TJobExec"))
    @MapKeyColumn(name = "NAME")
    @Column(name = "VALUES", length = 16777215)
    private List<MultiConfig> multiConfigurations = new ArrayList<MultiConfig>();

    // Constructors
    public TJobExecution() {
        this.id = (long) 0;
        this.duration = (long) 0;
        this.result = ResultEnum.QUEUED;
        this.resultMsg = "QUEUED (Waiting for executor)";
        this.servicesInstances = new ArrayList<>();
        this.envVars = new HashMap<>();
        this.testSuites = new ArrayList<>();
        this.externalUrls = new HashMap<>();
        this.type = TypeEnum.SIMPLE;
        this.execChilds = new ArrayList<>();
        this.multiConfigurations = new ArrayList<MultiConfig>();
    }

    public TJobExecution(Long id, Long duration, ResultEnum result) {
        this.id = id == null ? 0 : id;
        this.duration = duration == null ? 0 : duration;
        this.result = result;
        this.servicesInstances = new ArrayList<>();
        this.envVars = new HashMap<>();
        this.testSuites = new ArrayList<>();
        this.externalUrls = new HashMap<>();
        this.type = TypeEnum.SIMPLE;
        this.execChilds = new ArrayList<>();
        this.multiConfigurations = new ArrayList<MultiConfig>();
    }

    /**
     * Gets or Sets result
     */
    public enum ResultEnum {
        /* FINISED STATUS */

        SUCCESS("SUCCESS"),

        FAIL("FAIL"),

        ERROR("ERROR"),

        STOPPED("STOPPED"),

        NOT_EXECUTED("NOT_EXECUTED"),

        /* PROGRESS STATUS */

        IN_PROGRESS("IN PROGRESS"),

        WAITING("WAITING"), // Waiting for copy test results

        STARTING_TSS("STARTING TSS"),

        EXECUTING_SUT("EXECUTING SUT"),

        WAITING_SUT("WAITING SUT"),

        EXECUTING_TEST("EXECUTING TEST"),

        WAITING_TSS("WAITING TSS"),

        QUEUED("QUEUED"),

        PAUSED("PAUSED");

        private String value;

        ResultEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static ResultEnum fromValue(String text) {
            for (ResultEnum b : ResultEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }

        public static boolean isFinished(ResultEnum result) {
            return result.equals(SUCCESS) || result.equals(FAIL)
                    || result.equals(ERROR) || result.equals(STOPPED);
        }

        public static boolean isNotExecuted(ResultEnum result) {
            return result.equals(NOT_EXECUTED);
        }

        public static boolean isFinishedOrNotExecuted(ResultEnum result) {
            return isFinished(result) || isNotExecuted(result);
        }

        public static List<ResultEnum> getNotFinishedOrExecutedResultList() {
            List<ResultEnum> resultList = new ArrayList<>();
            for (ResultEnum currentResult : ResultEnum.values()) {
                if (!isFinishedOrNotExecuted(currentResult)) {
                    resultList.add(currentResult);
                }
            }
            return resultList;
        }

        public static List<ResultEnum> getFinishedAndNotExecutedResultList() {
            List<ResultEnum> resultList = new ArrayList<>();
            for (ResultEnum currentResult : ResultEnum.values()) {
                if (isFinishedOrNotExecuted(currentResult)) {
                    resultList.add(currentResult);
                }
            }
            return resultList;
        }
    }

    /**
     * Get id
     * 
     * @return id
     **/
    @ApiModelProperty(required = true, value = "")
    @NotNull

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id == null ? 0 : id;
    }

    /**
     * Get duration
     * 
     * @return duration
     **/
    @ApiModelProperty(required = true, value = "")
    @NotNull

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration == null ? 0 : duration;
    }

    /**
     * Get result
     * 
     * @return result
     **/
    @ApiModelProperty(required = true, value = "")
    @NotNull

    public ResultEnum getResult() {
        return result;
    }

    public void setResult(ResultEnum result) {
        this.result = result;
    }

    /**
     * Get sutExecution
     * 
     * @return sutExecution
     **/
    @ApiModelProperty(value = "")

    public SutExecution getSutExecution() {
        return sutExecution;
    }

    public void setSutExecution(SutExecution sutExecution) {
        this.sutExecution = sutExecution;
    }

    /**
     * Get error
     * 
     * @return error
     **/
    @ApiModelProperty(value = "")

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Monitoring Index
     * 
     * @return monitoringIndex
     */
    public String getMonitoringIndex() {
        return monitoringIndex;
    }

    public void setMonitoringIndex(String monitoringIndex) {
        this.monitoringIndex = monitoringIndex;
    }

    public TJob getTjob() {
        return this.tJob;
    }

    public void setTjob(TJob tjob) {
        this.tJob = tjob;
    }

    public List<TestSuite> getTestSuites() {
        return testSuites;
    }

    public void setTestSuites(List<TestSuite> testSuites) {
        this.testSuites = testSuites;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    /*
     * servicesInstances get/set
     */

    public List<String> getServicesInstances() {
        return servicesInstances;
    }

    public void setServicesInstances(List<String> servicesInstances) {
        this.servicesInstances = servicesInstances;
    }

    /*
     * getEnvVars get/set
     */

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(Map<String, String> envVars) {
        this.envVars = envVars;
    }

    public TJobExecution addLogsItem(Parameter parameter) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<Parameter>();
        }
        this.parameters.add(parameter);
        return this;
    }

    /*
     * resultMsg get/set
     */

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    /*
     * startDate get/set
     */

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /*
     * endDate get/set
     */

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Map<String, String> getExternalUrls() {
        return externalUrls;
    }

    public void setExternalUrls(Map<String, String> externalUrls) {
        this.externalUrls = externalUrls;
    }

    /*
     * monitoringStorageType get/set
     */

    public MonitoringStorageType getMonitoringStorageType() {
        return monitoringStorageType;
    }

    public void setMonitoringStorageType(
            MonitoringStorageType monitoringStorageType) {
        this.monitoringStorageType = monitoringStorageType;
    }

    /*
     * type get/set
     */

    public enum TypeEnum {

        SIMPLE("SIMPLE"),

        PARENT("PARENT"),

        CHILD("CHILD");

        private String value;

        TypeEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static TypeEnum fromValue(String text) {
            for (TypeEnum b : TypeEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }

        public static boolean isSimple(TypeEnum result) {
            return result.equals(SIMPLE);
        }

        public static boolean isParent(TypeEnum result) {
            return result.equals(PARENT);
        }

        public static boolean isChild(TypeEnum result) {
            return result.equals(CHILD);
        }
    }

    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
    }

    /*
     * execParent
     */

    public TJobExecution getExecParent() {
        return execParent;
    }

    public void setExecParent(TJobExecution execParent) {
        this.execParent = execParent;
    }

    /*
     * execChilds
     */

    public List<TJobExecution> getExecChilds() {
        return execChilds;
    }

    public void setExecChilds(List<TJobExecution> execChilds) {
        this.execChilds = execChilds;
    }

    /*
     * multiConfigurations
     */

    public List<MultiConfig> getMultiConfigurations() {
        return multiConfigurations;
    }

    public void setMultiConfigurations(List<MultiConfig> multiConfigurations) {
        this.multiConfigurations = multiConfigurations;
    }

    /* ************** */
    /* *** Others *** */
    /* ************** */
    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TJobExecution tjobExecution = (TJobExecution) o;
        return Objects.equals(this.id, tjobExecution.id)
                && Objects.equals(this.duration, tjobExecution.duration)
                && Objects.equals(this.result, tjobExecution.result)
                && Objects.equals(this.sutExecution, tjobExecution.sutExecution)
                && Objects.equals(this.error, tjobExecution.error)
                && Objects.equals(this.monitoringIndex,
                        tjobExecution.monitoringIndex)
                && Objects.equals(this.testSuites, tjobExecution.testSuites)
                && Objects.equals(this.parameters, tjobExecution.parameters)
                && Objects.equals(this.resultMsg, tjobExecution.resultMsg)
                && Objects.equals(this.startDate, tjobExecution.startDate)
                && Objects.equals(this.endDate, tjobExecution.endDate)
                && Objects.equals(this.monitoringStorageType,
                        tjobExecution.monitoringStorageType)
                && Objects.equals(this.execParent, tjobExecution.execParent)
                && Objects.equals(this.execChilds, tjobExecution.execChilds)
                && Objects.equals(this.multiConfigurations,
                        tjobExecution.multiConfigurations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, duration, result, sutExecution, error,
                testSuites, parameters, resultMsg, startDate, endDate,
                monitoringStorageType, execParent, execChilds,
                multiConfigurations);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class TJobExecution {\n");

        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    duration: ").append(toIndentedString(duration))
                .append("\n");
        sb.append("    result: ").append(toIndentedString(result)).append("\n");
        sb.append("    sutExecution: ").append(toIndentedString(sutExecution))
                .append("\n");
        sb.append("    error: ").append(toIndentedString(error)).append("\n");
        sb.append("    monitoringIndex: ")
                .append(toIndentedString(monitoringIndex)).append("\n");
        sb.append("    testSuites: ").append(toIndentedString(testSuites))
                .append("\n");
        sb.append("    parameters: ").append(toIndentedString(parameters))
                .append("\n");
        sb.append("    resultMsg: ").append(toIndentedString(resultMsg))
                .append("\n");
        sb.append("    startDate: ").append(toIndentedString(startDate))
                .append("\n");
        sb.append("    endDate: ").append(toIndentedString(endDate))
                .append("\n");
        sb.append("    monitoringStorageType: ")
                .append(toIndentedString(monitoringStorageType)).append("\n");
        sb.append("    execParent: ")
                .append(toIndentedString(
                        execParent != null ? execParent.getId() : "null"))
                .append("\n");
        sb.append("    execChilds: ").append(toIndentedString(execChilds))
                .append("\n");
        sb.append("    multiConfigurations: ")
                .append(toIndentedString(multiConfigurations)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    public void generateMonitoringIndex() {
        SutSpecification sut = this.getTjob().getSut();
        String monitoringIndex = getOnlyTJobExecMonitoringIndex();
        if (sut != null && sut.getSutType() == SutTypeEnum.DEPLOYED) {
            monitoringIndex += "," + sut.getSutMonitoringIndex();
        }
        this.setMonitoringIndex(monitoringIndex);
    }

    public String getOnlyTJobExecMonitoringIndex() {
        return this.getId().toString();
    }

    public String[] getMonitoringIndicesList() {
        return this.getMonitoringIndex().split(",");
    }

    public boolean isFinished() {
        ResultEnum result = this.getResult();
        return result.equals(ResultEnum.SUCCESS)
                || result.equals(ResultEnum.FAIL)
                || result.equals(ResultEnum.ERROR)
                || result.equals(ResultEnum.STOPPED);
    }

    public boolean isWithSut() {
        return this.tJob != null && this.tJob.isWithSut();
    }

    public boolean isMultiExecutionParent() {
        return this.getTjob().isMulti() && this.type == TypeEnum.PARENT;
    }

    public boolean isMultiExecutionChild() {
        return this.getTjob().isMulti() && this.type == TypeEnum.CHILD;
    }

    public boolean isSimpleExecution() {
        return !this.getTjob().isMulti() || this.type == TypeEnum.SIMPLE;
    }
}
