package io.elastest.etm.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EusExecutionData {

    @JsonProperty("tJobId")
    Long tJobId;

    @JsonProperty("tJobExecId")
    Long tJobExecId;

    @JsonProperty("monitoringIndex")
    String monitoringIndex;

    @JsonProperty("webRtcStatsActivated")
    boolean webRtcStatsActivated;

    @JsonProperty("folderPath")
    String folderPath;

    public EusExecutionData() {
    }

    public EusExecutionData(Long tJobId, Long tJobExecId,
            String monitoringIndex, boolean webRtcStatsActivated,
            String folderPath) {
        super();
        this.tJobId = tJobId;
        this.tJobExecId = tJobExecId;
        this.monitoringIndex = monitoringIndex;
        this.webRtcStatsActivated = webRtcStatsActivated;
        this.folderPath = folderPath;
    }

    public EusExecutionData(TJobExecution tJobExec, String folderPath) {
        this.tJobId = tJobExec.getTjob().getId();
        this.tJobExecId = tJobExec.getId();
        this.monitoringIndex = tJobExec.getMonitoringIndex();
        this.folderPath = folderPath;
        initWebRtcStatsActivated(tJobExec);
    }

    private void initWebRtcStatsActivated(TJobExecution tJobExec) {
        this.webRtcStatsActivated = false;
        try {
            Map<String, String> vars = tJobExec.getTjob()
                    .getTJobTSSConfigEnvVars("EUS");
            String statsKey = "ET_CONFIG_WEB_RTC_STATS";
            if (vars.containsKey(statsKey) && vars.get(statsKey) != null) {
                this.webRtcStatsActivated = "true".equals(vars.get(statsKey));
            }

        } catch (Exception e) {
        }

    }

    public Long gettJobId() {
        return tJobId;
    }

    public void settJobId(Long tJobId) {
        this.tJobId = tJobId;
    }

    public Long gettJobExecId() {
        return tJobExecId;
    }

    public void settJobExecId(Long tJobExecId) {
        this.tJobExecId = tJobExecId;
    }

    public String getMonitoringIndex() {
        return monitoringIndex;
    }

    public void setMonitoringIndex(String monitoringIndex) {
        this.monitoringIndex = monitoringIndex;
    }

    public boolean isWebRtcStatsActivated() {
        return webRtcStatsActivated;
    }

    public void setWebRtcStatsActivated(boolean webRtcStatsActivated) {
        this.webRtcStatsActivated = webRtcStatsActivated;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    @Override
    public String toString() {
        return "ExecutionData [tJobId=" + tJobId + ", tJobExecId=" + tJobExecId
                + ", monitoringIndex=" + monitoringIndex
                + ", webRtcStatsActivated=" + webRtcStatsActivated
                + ", folderPath=" + folderPath + "]";
    }

    public String getKey() {
        return tJobId + "_" + tJobExecId;
    }
}
