package io.elastest.etm.service;

import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ProgressMessage;

import io.elastest.epm.client.json.DockerContainerInfo.DockerContainer;
import io.elastest.epm.client.json.DockerContainerInfo.PortInfo;
import io.elastest.epm.client.model.DockerPullImageProgress;
import io.elastest.epm.client.model.DockerServiceStatus.DockerServiceStatusEnum;
import io.elastest.epm.client.service.DockerComposeService;
import io.elastest.etm.model.EtPlugin;
import io.elastest.etm.model.SupportServiceInstance;

@Service
public class EtPluginsService {
    final Logger logger = getLogger(lookup().lookupClass());

    public DockerComposeService dockerComposeService;
    public DockerEtmService dockerEtmService;

    Map<String, EtPlugin> enginesMap = new HashMap<>();
    Map<String, EtPlugin> uniqueEtPluginsMap = new HashMap<>();
    Map<String, EtPlugin> tssInstancesMap = new HashMap<>();

    @Value("${et.compose.project.name}")
    String etComposeProjectName;

    @Value("${elastest.docker.network}")
    public String network;

    @Value("${et.test.engines.path}")
    public String ET_TEST_ENGINES_PATH;

    @Value("${exec.mode")
    public String execmode;

    @Value("${et.public.host}")
    public String etPublicHost;

    @Value("${et.edm.mysql.host}")
    public String etEdmMysqlHost;

    @Value("${et.edm.mysql.port}")
    public String etEdmMysqlPort;

    @Value("${et.shared.folder}")
    private String sharedFolder;

    private String tmpEnginesYmlFolder;
    private String uniqueEtPluginsYmlFolder;
    private String tmpTssInstancesYmlFolder;

    public EtPluginsService(DockerComposeService dockerComposeService,
            DockerEtmService dockerEtmService) {
        this.dockerComposeService = dockerComposeService;
        this.dockerEtmService = dockerEtmService;
    }

    public void registerEngines() {
        this.enginesMap.put("ece", new EtPlugin("ece"));
        // It's necessary to auth:
        // https://docs.google.com/document/d/1RMMnJO3rA3KRg-q_LRgpmmvSTpaCPsmfAQjs9obVNeU
        this.enginesMap.put("ere", new EtPlugin("ere"));

        this.uniqueEtPluginsMap.put("eim", new EtPlugin("eim"));
        this.uniqueEtPluginsMap.put("testlink", new EtPlugin("testlink"));
    }

    @PostConstruct
    public void init() throws Exception {
        String path = sharedFolder.endsWith("/") ? sharedFolder
                : sharedFolder + "/";
        this.tmpEnginesYmlFolder = path + "tmp-engines-yml";
        this.tmpTssInstancesYmlFolder = path + "tmp-support-services-yml";
        this.uniqueEtPluginsYmlFolder = path + "tmp-unique-etplugins-yml";

        registerEngines();
        for (String engine : this.enginesMap.keySet()) {
            createTestEngineProject(engine);
        }

        for (String plugin : this.uniqueEtPluginsMap.keySet()) {
            createUniqueEtPluginProject(plugin);
        }
    }

    @PreDestroy
    public void destroy() {
        for (String engine : this.enginesMap.keySet()) {
            stopAndRemoveProject(engine);
        }
        for (String uniqueEtPlugin : this.uniqueEtPluginsMap.keySet()) {
            stopAndRemoveProject(uniqueEtPlugin);
        }
        for (String tssInstance : this.tssInstancesMap.keySet()) {
            stopAndRemoveProject(tssInstance);
        }
    }

    /* ****************************** */
    /* *** Single Create Projects *** */
    /* ****************************** */

    public void createTestEngineProject(String name) {
        String dockerComposeYml = getDockerCompose(name);
        this.createProject(name, dockerComposeYml, tmpEnginesYmlFolder);
    }

    public void createUniqueEtPluginProject(String name) throws Exception {
        String dockerComposeYml = getDockerCompose(name);
        if ("eim".equals(name) || "testlink".equals(name)) {
            try {
                String mysqlHost = dockerEtmService.getEdmMySqlHost();
                dockerComposeYml = dockerComposeYml.replace("edm-mysql",
                        mysqlHost);
            } catch (Exception e) {
                throw new Exception("Error on get MySQL host", e);
            }
        }

        if ("testlink".equals(name)) {
            // Create project and bind exposed ports to random host port
            this.createProject(name, dockerComposeYml, true, false,
                    uniqueEtPluginsYmlFolder);
        } else {
            this.createProject(name, dockerComposeYml,
                    uniqueEtPluginsYmlFolder);
        }
    }

    public SupportServiceInstance createTssInstanceProject(String instanceId,
            String dockerComposeYml, SupportServiceInstance serviceInstance)
            throws Exception {
        dockerComposeService.createProjectWithEnv(instanceId, dockerComposeYml,
                tmpTssInstancesYmlFolder, true, serviceInstance.getParameters(),
                false, false);

        List<String> images = dockerComposeService.getProjectImages(instanceId);
        serviceInstance.setImagesList(images);

        tssInstancesMap.put(instanceId, serviceInstance);

        return serviceInstance;
    }

    /* ******************************* */
    /* *** Generic Create Projects *** */
    /* ******************************* */

    public void createProject(String name, String dockerComposeYml,
            boolean withBindedExposedPortsToRandom, boolean withRemoveVolumes,
            String ymlPath) {
        try {
            dockerComposeService.createProject(name, dockerComposeYml, ymlPath,
                    true, withBindedExposedPortsToRandom, withRemoveVolumes);
        } catch (Exception e) {
            logger.error("Exception creating project {}", name, e);
        }
    }

    public void createProject(String name, String dockerComposeYml,
            String ymlPath) {
        this.createProject(name, dockerComposeYml, false, false, ymlPath);
    }

    /* **************************** */
    /* *** Stop/Remove Projects *** */
    /* **************************** */

    public EtPlugin stopEtPlugin(String projectName) {
        try {
            dockerComposeService.stopProject(projectName);
            this.getEtPlugin(projectName).initToDefault();
        } catch (IOException e) {
            logger.error("Error while stopping EtPlugin {}", projectName);
        }
        return this.getEtPlugin(projectName);
    }

    public boolean stopAndRemoveProject(String projectName) {
        boolean removed = dockerComposeService
                .stopAndRemoveProject(projectName);

        if (!removed) {
            return removed;
        }

        if (enginesMap.containsKey(projectName)) {
            enginesMap.remove(projectName);
        } else if (uniqueEtPluginsMap.containsKey(projectName)) {
            uniqueEtPluginsMap.remove(projectName);
        } else {
            tssInstancesMap.remove(projectName);
        }
        return removed;
    }

    /* ************************** */
    /* ***** Start Projects ***** */
    /* ************************** */

    @Async
    public void startEtPluginAsync(String projectName) {
        this.startEtPlugin(projectName);
    }

    public EtPlugin startEtPlugin(String projectName) {
        // Initialize
        this.getEtPlugin(projectName)
                .setStatus(DockerServiceStatusEnum.INITIALIZING);
        this.getEtPlugin(projectName).setStatusMsg("Initializing...");
        try {
            logger.debug("Starting {} plugin", projectName);

            // Pull
            this.pullProject(projectName);

            // Start
            this.getEtPlugin(projectName)
                    .setStatus(DockerServiceStatusEnum.STARTING);
            this.getEtPlugin(projectName).setStatusMsg("Starting...");
            dockerComposeService.startProject(projectName, false);
            insertIntoETNetwork(projectName);
        } catch (Exception e) {
            logger.error("Cannot start {} plugin", projectName, e);
            logger.error("Stopping service {}", projectName);
            this.stopEtPlugin(projectName);
        }
        return this.getEtPlugin(projectName);
    }

    @Async
    public void startEngineOrUniquePluginAsync(String projectName) {
        this.startEngineOrUniquePlugin(projectName);
    }

    public EtPlugin startEngineOrUniquePlugin(String projectName) {
        String url = "";
        logger.debug("Checking if {} is not already running", projectName);
        if (!isRunning(projectName)) {
            this.startEtPlugin(projectName);
        }

        this.waitForReady(projectName, 2500);
        url = getEngineOrEtPluginUrl(projectName);
        this.getEtPlugin(projectName).setUrl(url);

        return this.getEtPlugin(projectName);
    }

    /* *************************** */
    /* ****** Pull Projects ****** */
    /* *************************** */

    private void pullProject(String projectName) throws Exception {
        Map<String, EtPlugin> currentEtPluginMap;
        if (enginesMap.containsKey(projectName)) {
            currentEtPluginMap = enginesMap;
        } else if (uniqueEtPluginsMap.containsKey(projectName)) {
            currentEtPluginMap = uniqueEtPluginsMap;
        } else if (tssInstancesMap.containsKey(projectName)) {
            currentEtPluginMap = tssInstancesMap;
        } else {
            throw new Exception("Error on pulling images of " + projectName
                    + ": EtPlugin project does not exists");
        }

        List<String> images = currentEtPluginMap.get(projectName)
                .getImagesList();

        if (images == null || images.isEmpty()) {
            images = dockerComposeService.getProjectImages(projectName);
            currentEtPluginMap.get(projectName).setImagesList(images);
        }

        for (String image : images) {
            ProgressHandler progressHandler = this.getEtPluginProgressHandler(
                    currentEtPluginMap, projectName, image);

            dockerComposeService.pullImageWithProgressHandler(projectName,
                    progressHandler, image);
        }
    }

    public ProgressHandler getEtPluginProgressHandler(Map<String, EtPlugin> map,
            String projectName, String image) {
        DockerPullImageProgress dockerPullImageProgress = new DockerPullImageProgress();
        dockerPullImageProgress.setImage(image);
        dockerPullImageProgress.setCurrentPercentage(0);

        map.get(projectName).setStatus(DockerServiceStatusEnum.PULLING);
        map.get(projectName).setStatusMsg("Pulling " + image + " image");
        return new ProgressHandler() {
            @Override
            public void progress(ProgressMessage message)
                    throws DockerException {
                dockerPullImageProgress.processNewMessage(message);
                String msg = "Pulling image " + image + " from " + projectName
                        + " ET Plugin: "
                        + dockerPullImageProgress.getCurrentPercentage() + "%";

                map.get(projectName).setStatusMsg(msg);
            }

        };

    }

    /* ************************** */
    /* *** Wait/Check methods *** */
    /* ************************** */

    public String getEngineOrEtPluginUrl(String serviceName) {
        String url = "";
        try {
            for (DockerContainer container : dockerComposeService
                    .getContainers(serviceName).getContainers()) {
                String containerName = container.getName(); // example:
                                                            // ece_ece_1
                if (containerName != null
                        && containerName.endsWith(serviceName + "_1")) {
                    logger.debug("Container info: {}", container);

                    String ip = etPublicHost;

                    boolean useBindedPort = true;
                    if (ip.equals("localhost")) {
                        useBindedPort = false;
                        ip = dockerEtmService.dockerService
                                .getContainerIpByNetwork(containerName,
                                        network);
                    }

                    String port = "";

                    switch (serviceName) {
                    case "testlink":
                        if (!useBindedPort) {
                            port = "80";
                        } else {
                            port = "37071";
                        }
                        break;

                    case "ere":
                        if (!useBindedPort) {
                            port = "9080";
                        } else {
                            port = "37007";
                        }
                        break;

                    case "ece":
                        if (!useBindedPort) {
                            port = "8888";
                        } else {
                            port = "37008";
                        }
                        break;

                    case "eim":
                        if (!useBindedPort) {
                            port = "8080";
                        } else {
                            port = "37004";
                        }
                        break;
                    default:
                        for (Entry<String, List<PortInfo>> portList : container
                                .getPorts().entrySet()) {
                            if (portList.getValue() != null) {
                                if (!useBindedPort) {
                                    port = portList.getKey().split("/")[0];
                                } else {
                                    port = portList.getValue().get(0)
                                            .getHostPort();
                                }
                                break;

                            }
                        }
                        break;
                    }

                    if ("".equals(port)) {
                        throw new Exception("Port not found");
                    }

                    String protocol = "http";
                    if ("443".equals(port)) {
                        protocol = "https";
                    }

                    url = protocol + "://" + ip + ":" + port;
                    if ("ere".equals(serviceName)) {
                        url += "/ere-app";
                    }
                    logger.debug("Url: " + url);
                    break;
                }
            }

        } catch (Exception e) {
            logger.error("Service url not exist {}", serviceName, e);
        }
        return url;
    }

    public boolean checkIfEngineUrlIsUp(String engineName) {
        String url = getEngineOrEtPluginUrl(engineName);
        boolean isUp = checkIfUrlIsUp(url);
        if (isUp) {
            this.getEtPlugin(engineName)
                    .setStatus(DockerServiceStatusEnum.READY);
            this.getEtPlugin(engineName).setStatusMsg("Ready");
        }
        return isUp;
    }

    public boolean checkIfUrlIsUp(String engineUrl) {
        boolean up = false;
        URL url;
        try {
            url = new URL(engineUrl);
            logger.info("Service url to check: " + engineUrl);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            int responseCode = huc.getResponseCode();
            up = (responseCode >= 200 && responseCode <= 299);
            if (!up) {
                logger.info("Service no ready at url: " + engineUrl);
                return up;
            }
        } catch (IOException e) {
            return false;
        }

        logger.info("Service ready at url: " + engineUrl);

        return up;
    }

    public boolean waitForReady(String projectName, int interval) {
        while (!this.getEtPlugin(projectName).getStatus()
                .equals(DockerServiceStatusEnum.NOT_INITIALIZED)
                && !this.checkIfEngineUrlIsUp(projectName)) {
            // Wait
            try { // TODO timeout
                Thread.sleep(interval);
            } catch (InterruptedException e) {
            }
        }
        return true;
    }

    public Boolean isRunning(String engineName) {
        try {
            for (DockerContainer container : dockerComposeService
                    .getContainers(engineName).getContainers()) {
                String containerName = engineName + "_" + engineName + "_1";
                if (container.getName().equals(containerName)) {
                    return container.isRunning();
                }
            }
            return false;

        } catch (Exception e) {
            logger.error("Engine not started or not exist {}", engineName, e);
            return false;
        }
    }

    /* ************************* */
    /* ****** Get Methods ****** */
    /* ************************* */

    public List<EtPlugin> getEngines() {
        return new ArrayList<>(enginesMap.values());
    }

    public List<EtPlugin> getUniqueEtPlugins() {
        return new ArrayList<>(uniqueEtPluginsMap.values());
    }

    public List<EtPlugin> getTssInstances() {
        return new ArrayList<>(tssInstancesMap.values());
    }

    public EtPlugin getEtPlugin(String name) {
        if (enginesMap.containsKey(name)) {
            return enginesMap.get(name);
        } else if (uniqueEtPluginsMap.containsKey(name)) {
            return uniqueEtPluginsMap.get(name);
        } else {
            return tssInstancesMap.get(name);
        }
    }

    public String getUrlIfIsRunning(String engineName) {
        return getEngineOrEtPluginUrl(engineName);
    }

    public String getDockerCompose(String engineName) {
        String content = "";
        try {
            InputStream inputStream = getClass().getResourceAsStream(
                    "/" + ET_TEST_ENGINES_PATH + engineName + ".yml");
            content = IOUtils.toString(inputStream, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }

        content = this.replaceProjectNameMatchesByElastestProjectName(content);

        return content;
    }

    /* ******************** */
    /* ****** Others ****** */
    /* ******************** */

    public void insertIntoETNetwork(String engineName) throws Exception {
        try {
            for (DockerContainer container : dockerComposeService
                    .getContainers(engineName).getContainers()) {
                try {
                    dockerEtmService.dockerService.insertIntoNetwork(network,
                            container.getName());
                } catch (DockerException | InterruptedException
                        | DockerCertificateException e) {
                    throw new Exception(
                            "Error on insert container " + container.getName()
                                    + " into " + network + " network");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String replaceProjectNameMatchesByElastestProjectName(
            String content) {
        return content.replaceAll("projectnametoreplace", etComposeProjectName);
    }

}
