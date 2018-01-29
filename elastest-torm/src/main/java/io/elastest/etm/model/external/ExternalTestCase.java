package io.elastest.etm.model.external;

import java.io.Serializable;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import io.elastest.etm.model.external.ExternalProject.BasicAttExternalProject;
import io.elastest.etm.model.external.ExternalTJob.BasicAttExternalTJob;
import io.elastest.etm.model.external.ExternalTestExecution.BasicAttExternalTestExecution;

@Entity
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = { "externalId", "externalSystemId" }) })
public class ExternalTestCase implements Serializable {
    private static final long serialVersionUID = 1L;

    public interface BasicAttExternalTestCase {
    }

    @JsonView({ BasicAttExternalProject.class, BasicAttExternalTJob.class,
            BasicAttExternalTestCase.class,
            BasicAttExternalTestExecution.class })
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    @JsonProperty("id")
    private Long id = null;

    @JsonView({ BasicAttExternalTestCase.class, BasicAttExternalProject.class,
            BasicAttExternalTestExecution.class, BasicAttExternalTJob.class, })
    @Column(name = "name")
    @JsonProperty("name")
    private String name = null;

    @JsonView({ BasicAttExternalTestCase.class, BasicAttExternalProject.class,
            BasicAttExternalTestExecution.class, BasicAttExternalTJob.class, })
    @Column(name = "fields", columnDefinition = "TEXT", length = 65535)
    @JsonProperty("fields")
    private String fields = null;

    @JsonView({ BasicAttExternalProject.class, BasicAttExternalTestCase.class,
            BasicAttExternalTestExecution.class, BasicAttExternalTJob.class, })
    @Column(name = "externalId")
    @JsonProperty("externalId")
    private String externalId;

    @JsonView({ BasicAttExternalProject.class, BasicAttExternalTestCase.class,
            BasicAttExternalTestExecution.class, BasicAttExternalTJob.class, })
    @Column(name = "externalSystemId")
    @JsonProperty("externalSystemId")
    private String externalSystemId;

    @JsonView({ BasicAttExternalTestCase.class,
            BasicAttExternalTestExecution.class })
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exTJob")
    private ExternalTJob exTJob;

    @JsonView({ BasicAttExternalTestCase.class, BasicAttExternalProject.class,
            BasicAttExternalTJob.class, })
    @OneToMany(mappedBy = "exTestCase", cascade = CascadeType.REMOVE)
    private List<ExternalTestExecution> exTestExecs;

    /* **************************/
    /* ***** Constructors *******/
    /* **************************/

    public ExternalTestCase() {
    }

    public ExternalTestCase(Long id) {
        this.id = id == null ? 0 : id;
    }

    /* *****************************/
    /* ***** Getters/Setters *******/
    /* *****************************/

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id == null ? 0 : id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalSystemId() {
        return externalSystemId;
    }

    public void setExternalSystemId(String externalSystemId) {
        this.externalSystemId = externalSystemId;
    }

    public ExternalTJob getExTJob() {
        return exTJob;
    }

    public void setExTJob(ExternalTJob exTJob) {
        this.exTJob = exTJob;
    }

    public List<ExternalTestExecution> getExTestExecs() {
        return exTestExecs;
    }

    public void setExTestExecs(List<ExternalTestExecution> exTestExecs) {
        this.exTestExecs = exTestExecs;
    }

}
