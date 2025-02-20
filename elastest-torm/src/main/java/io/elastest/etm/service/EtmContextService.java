package io.elastest.etm.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.spotify.docker.client.messages.Container;

import io.elastest.etm.dao.LogAnalyzerRepository;
import io.elastest.etm.model.ContextInfo;
import io.elastest.etm.model.CoreServiceInfo;
import io.elastest.etm.model.HelpInfo;
import io.elastest.etm.model.LogAnalyzerConfig;
import io.elastest.etm.model.TJobExecution;
import io.elastest.etm.model.VersionInfo;
import io.elastest.etm.platform.service.PlatformService;

@Service
public class EtmContextService {

    private static final Logger logger = LoggerFactory
            .getLogger(EtmContextService.class);
    private final LogAnalyzerRepository logAnalyzerRepository;

    @Value("${et.etm.rabbit.path.with-proxy}")
    public String etEtmRabbitPathWithProxy;
    @Value("${exec.mode}")
    String execMode;
    @Value("${et.images}")
    String etImages;
    @Value("${et.core.images}")
    String etCoreImages;
    @Value("${et.esm.ss.desc.files.path}")
    public String etEsmSsDescFilesPath;
    @Value("${server.port}")
    public String serverPort;
    @Value("${elastest.docker.network}")
    private String etDockerNetwork;
    @Value("${et.edm.alluxio.api}")
    public String etEdmAlluxioApi;
    @Value("${et.edm.mysql.host}")
    public String etEdmMysqlHost;
    @Value("${et.edm.mysql.port}")
    public String etEdmMysqlPort;
    @Value("${et.edm.api}")
    public String etEdmApi;
    @Value("${et.epm.api}")
    public String etEpmApi;
    @Value("${et.etm.api}")
    public String etEtmApi;
    @Value("${et.esm.api}")
    public String etEsmApi;
    @Value("${et.eim.api}")
    public String etEimApi;

    @Value("${et.etm.rabbit.host}")
    public String etEtmRabbitHost;
    @Value("${et.etm.rabbit.port}")
    public String etEtmRabbitPort;
    @Value("${et.emp.api}")
    public String etEmpApi;
    @Value("${et.emp.influxdb.api}")
    public String etEmpInfluxdbApi;
    @Value("${et.emp.influxdb.host}")
    public String etEmpInfluxdbHost;
    @Value("${et.emp.influxdb.graphite.port}")
    public String etEmpInfluxdbGraphitePort;

    // Logstash
    @Value("${et.etm.lsbeats.host}")
    public String etEtmLsBeatsHost;
    @Value("${et.etm.lsbeats.port}")
    public String etEtmLsBeatsPort;
    @Value("${et.etm.binded.lstcp.port}")
    public String etEtmBindedLstcpPort;
    @Value("${et.etm.binded.lsbeats.port}")
    public String etEtmBindedLsbeatsPort;
    @Value("${et.etm.binded.internal.lsbeats.port}")
    public String etEtmBindedInternalLsbeatsPort;
    @Value("${et.etm.lshttp.api}")
    public String etEtmLsHttpApi;
    @Value("${et.etm.lshttp.port}")
    public String etEtmLsHttpPort;
    @Value("${et.etm.lstcp.host}")
    public String etEtmLsTcpHost;
    @Value("${et.etm.lstcp.port}")
    public String etEtmLsTcpPort;

    HelpInfo helpInfo;
    ContextInfo contextInfo;
    EtmContextAuxService etmContextAuxService;
    PlatformService platformService;

    public EtmContextService(LogAnalyzerRepository logAnalyzerRepository,
            TSSService esmService, EtmContextAuxService etmContextAuxService,
            PlatformService platformService) {
        this.logAnalyzerRepository = logAnalyzerRepository;
        this.etmContextAuxService = etmContextAuxService;
        this.platformService = platformService;
    }

    @PostConstruct
    public void createContextInfo() {
        contextInfo = this.etmContextAuxService.getContextInfo();
    }

    public ContextInfo getContextInfo() {
        logger.debug("Loading ElasTest Context");
        // TODO timeout
        while (contextInfo.getEusSSInstance() == null) {
            logger.debug("Waiting for the ElasTest Context to be ready");
        }

        return contextInfo;
    }

    public HelpInfo getHelpInfo() {
        if (helpInfo == null) {
            loadHelpInfoFromImages();
        }
        return helpInfo;
    }

    private void loadHelpInfoFromImages() {
        List<String> imagesNames = Arrays.asList(etImages.split(","));
        helpInfo = new HelpInfo();

        imagesNames.forEach((imageName) -> {
            try {
                VersionInfo imageVersionInfo = getImageVersionInfo(imageName);
                helpInfo.getVersionsInfo().put(imageName, imageVersionInfo);
            } catch (Exception e) {
                logger.error("Unable to retrieve ElasTest Help Information.");
            }
        });
    }

    private VersionInfo getImageVersionInfo(String imageName) throws Exception {
        return platformService.getImageInfo(imageName);
    }

    private VersionInfo getImageVersionInfoByContainer(Container container) {
        return new VersionInfo(container);
    }

    /* ********************* */
    /* *** Core Services *** */
    /* ********************* */

    public List<CoreServiceInfo> getCoreServicesInfo() {
        List<CoreServiceInfo> coreServices = new ArrayList<>();
        List<String> imagesNames = Arrays.asList(etCoreImages.split(","));
        imagesNames.forEach((imageName) -> {
            try {
                CoreServiceInfo coreService = new CoreServiceInfo();
                String version = platformService
                        .getImageTagFromImageName(imageName);
                String serviceName = imageName.split("/")[1].split(":")[0];
                coreService.setName(serviceName);
                VersionInfo versionInfo = platformService
                        .getVersionInfoFromContainer(imageName, version);
                versionInfo.setTag(version);
                coreService.setVersionInfo(versionInfo);
                coreService.setImageName(platformService
                        .getImageNameFromCompleteImageName(imageName));
                platformService.setCoreServiceInfoFromContainer(version,
                        imageName, coreService);

                coreService.setImageDate(platformService.getImageInfo(imageName)
                        .getCreationDate());

                coreServices.add(coreService);
            } catch (Exception e) {
                logger.error(
                        "Unable to retrieve ElasTest Core Service {} Information. Probably not started. Obtaining information from Image",
                        imageName);
                try {
                    CoreServiceInfo coreService = new CoreServiceInfo();
                    String version = platformService
                            .getImageTagFromImageName(imageName);
                    VersionInfo versionInfo;
                    versionInfo = platformService.getImageInfo(imageName);
                    versionInfo.setTag(version);
                    String serviceName = imageName.split("/")[1].split(":")[0];
                    coreService.setName(serviceName);
                    coreService.setVersionInfo(versionInfo);
                    coreService.setImageName(platformService
                            .getImageNameFromCompleteImageName(imageName));
                    coreService.setStatus("Not Started");
                    coreService.setImageDate(platformService
                            .getImageInfo(imageName).getCreationDate());

                    coreServices.add(coreService);
                } catch (Exception e1) {
                    logger.error(
                            "Unable to retrieve ElasTest Core Service {} Information Definitively",
                            imageName);
                }
            }
        });

        return coreServices;
    }

    public String getAllCoreServiceLogs(String coreServiceName,
            boolean withFollow) throws Exception {
        CoreServiceInfo coreService = getCoreServiceIfExist(coreServiceName);
        if (coreService != null) {
            String containerName = coreService.getFirstContainerNameCleaned();
            if (containerName != null) {
                return platformService.getAllContainerLogs(containerName,
                        withFollow);
            }
        }
        throw new Exception("Error on get " + coreServiceName
                + " logs. Invalid Core Service Name");
    }

    public String getSomeCoreServiceLogs(String coreServiceName, int amount,
            boolean withFollow) throws Exception {
        CoreServiceInfo coreService = getCoreServiceIfExist(coreServiceName);
        if (coreService != null) {
            String containerName = coreService.getFirstContainerNameCleaned();
            if (containerName != null) {
                return platformService.getSomeContainerLogs(containerName,
                        amount, withFollow);
            }
        }
        return null;
    }

    public String getCoreServiceLogsSince(String coreServiceName, int since,
            boolean withFollow) throws Exception {
        CoreServiceInfo coreService = getCoreServiceIfExist(coreServiceName);
        if (coreService != null) {
            String containerName = coreService.getFirstContainerNameCleaned();
            if (containerName != null) {
                return platformService.getContainerLogsFrom(containerName, since,
                                withFollow);
            }
        }
        throw new Exception("Error on get " + coreServiceName
                + " logs. Invalid Core Service Name");
    }

    public CoreServiceInfo getCoreServiceIfExist(String coreServiceName) {
        List<CoreServiceInfo> coreServices = this.getCoreServicesInfo();
        for (CoreServiceInfo currentCoreService : coreServices) {
            if (currentCoreService.getName().equals(coreServiceName)) {
                return currentCoreService;
            }
        }
        return null;
    }

    public boolean isPlatformDevImage(String imageName, String version) {
        return isPlatformImage(imageName) && version.equals("dev");
    }

    public boolean isPlatformImage(String imageName) {
        return imageName.startsWith("elastest/platform")
                && !imageName.startsWith("elastest/platform-services");
    }

    public Map<String, String> getTJobExecMonitoringEnvVars(
            TJobExecution tJobExec) throws Exception {
        Map<String, String> monEnvs = new HashMap<String, String>();
        monEnvs.putAll(this.etmContextAuxService.getMonitoringEnvVars());

        if (tJobExec != null) {
            monEnvs.put("ET_MON_LOG_TAG", "sut_" + tJobExec.getId() + "_exec");
            monEnvs.put("ET_MON_EXEC", tJobExec.getId().toString());
            if (tJobExec.getTjob().isExternal()) {
                monEnvs.put("ET_SUT_LOG_TAG",
                        "sut_" + tJobExec.getId() + "_exec");
                // Override
                String host = etmContextAuxService.getLogstashHostForExtJob();

                monEnvs.put("ET_MON_LSHTTP_API",
                        contextInfo.getLogstashHttpUrl());
                monEnvs.put("ET_MON_LSBEATS_HOST", host);
                monEnvs.put("ET_MON_LSBEATS_PORT", etEtmBindedLsbeatsPort);
                monEnvs.put("ET_MON_INTERNAL_LSBEATS_PORT",
                        etEtmBindedInternalLsbeatsPort);
                monEnvs.put("ET_MON_LSTCP_HOST", host);
                monEnvs.put("ET_MON_LSTCP_PORT", etEtmBindedLstcpPort);
            }
        }

        return monEnvs;
    }

    /* ******************** */
    /* *** Log Analyzer *** */
    /* ******************** */

    public LogAnalyzerConfig saveLogAnalyzerConfig(
            LogAnalyzerConfig logAnalizerConfig) {
        if (logAnalizerConfig.getId() == 0) {
            logAnalizerConfig.setId(new Long(1));
        }

        return this.logAnalyzerRepository.save(logAnalizerConfig);
    }

    public LogAnalyzerConfig getLogAnalyzerConfig() {
        Optional<LogAnalyzerConfig> config = this.logAnalyzerRepository
                .findById(new Long(1));
        return config.isPresent() ? config.get() : null;
    }

}
