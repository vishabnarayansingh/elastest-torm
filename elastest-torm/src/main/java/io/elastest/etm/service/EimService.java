package io.elastest.etm.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import io.elastest.etm.dao.EimBeatConfigRepository;
import io.elastest.etm.dao.EimConfigRepository;
import io.elastest.etm.dao.EimMonitoringConfigRepository;
import io.elastest.etm.model.EimBeatConfig;
import io.elastest.etm.model.EimConfig;
import io.elastest.etm.model.EimMonitoringConfig;
import io.elastest.etm.model.EimMonitoringConfig.ApiEimMonitoringConfig;
import io.elastest.etm.model.EimMonitoringConfig.BeatsStatusEnum;
import io.elastest.etm.utils.UtilsService;

@Service
public class EimService {
    private static final Logger logger = LoggerFactory
            .getLogger(EimService.class);

    private final EimConfigRepository eimConfigRepository;
    private final EimMonitoringConfigRepository eimMonitoringConfigRepository;
    private final EimBeatConfigRepository eimBeatConfigRepository;
    private final EtPluginsService etPluginsService;
    private final UtilsService utilsService;
    private DatabaseSessionManager dbmanager;

    @Value("${exec.mode}")
    private String execMode;

    @Value("${et.eim.api}")
    private String eimUrl;

    private String eimApiPath = "eim/api";

    private String eimApiUrl;

    private static final String EIM_ETPLUGIN_PROJECT_NAME = "eim";

    public EimService(EimConfigRepository eimConfigRepository,
            EimMonitoringConfigRepository eimMonitoringConfigRepository,
            EimBeatConfigRepository eimBeatConfigRepository,
            EtPluginsService testEnginesService, UtilsService utilsService,
            DatabaseSessionManager dbmanager) {
        this.eimConfigRepository = eimConfigRepository;
        this.eimMonitoringConfigRepository = eimMonitoringConfigRepository;
        this.eimBeatConfigRepository = eimBeatConfigRepository;
        this.etPluginsService = testEnginesService;
        this.utilsService = utilsService;
        this.dbmanager = dbmanager;
    }

    @PostConstruct
    public void initEimApiUrl() {
        this.eimApiUrl = this.eimUrl + eimApiPath;
    }

    public boolean isStarted() {
        // If not mini it's always started (at ET startup)
        return !utilsService.isElastestMini() || (utilsService.isElastestMini()
                && etPluginsService.isRunning(EIM_ETPLUGIN_PROJECT_NAME));
    }

    public String getEimApiUrl() {
        return this.eimApiUrl;
    }

    private void startEimIfNotStarted() {
        // Only in mini mode
        if (!isStarted()) {
            etPluginsService
                    .startEngineOrUniquePlugin(EIM_ETPLUGIN_PROJECT_NAME);

            // Init URL
            this.eimUrl = etPluginsService
                    .getEtPluginUrl(EIM_ETPLUGIN_PROJECT_NAME);
            this.eimUrl = this.eimUrl.endsWith("/") ? this.eimUrl
                    : this.eimUrl + "/";
            this.eimApiPath = this.eimApiPath.startsWith("/")
                    ? this.eimApiPath.substring(1)
                    : this.eimApiPath;
            this.initEimApiUrl();
            logger.debug("EIM is now ready at {}", this.eimApiUrl);
        }
    }

    /* ***************** */
    /* **** EIM API **** */
    /* ***************** */

    @SuppressWarnings("unchecked")
    public String getPublickey() {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> publicKeyObj = restTemplate
                .getForObject(eimUrl + eimApiPath + "/publickey", Map.class);
        return publicKeyObj.get("publickey");
    }

    @SuppressWarnings("unchecked")
    public EimConfig instrumentalize(EimConfig eimConfig) throws Exception {
        this.startEimIfNotStarted();
        RestTemplate restTemplate = new RestTemplate();

        Map<String, String> body = new HashMap<>();
        body.put("address", eimConfig.getIp());
        body.put("user", eimConfig.getUser());
        if (eimConfig.getPassword() != null
                && !eimConfig.getPassword().isEmpty()) {
            body.put("password", eimConfig.getPassword());
        }
        body.put("private_key", eimConfig.getPrivateKey());

        // Dev
        if (!utilsService.isEtmInContainer()
                || utilsService.isEtmInDevelopment()) {
            body.put("logstash_ip", eimConfig.getLogstashIp());
            body.put("logstash_port", eimConfig.getLogstashBeatsPort());
        } else { // Prod
            body.put("logstash_ip", eimConfig.getLogstashBindedBeatsHost());
            body.put("logstash_port", eimConfig.getLogstashBindedBeatsPort());
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(
                Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X_Broker_Api_Version", "2.12");

        HttpEntity<Map<String, String>> request = new HttpEntity<Map<String, String>>(
                body, headers);

        String url = this.eimApiUrl + "/agent";
        logger.debug("Instrumentalizing SuT: {}", url);
        Map<String, String> response = restTemplate.postForObject(url, request,
                Map.class);
        logger.debug("Instrumentalized! Saving agentId into SuT EimConfig");
        eimConfig.setAgentId(response.get("agentId"));
        return this.eimConfigRepository.save(eimConfig);
    }

    public EimConfig deinstrumentalize(EimConfig eimConfig) {
        this.startEimIfNotStarted();

        RestTemplate restTemplate = new RestTemplate();
        restTemplate
                .delete(this.eimApiUrl + "/agent/" + eimConfig.getAgentId());
        eimConfig.setAgentId(null);
        return this.eimConfigRepository.save(eimConfig);

    }

    @SuppressWarnings("unchecked")
    public void deployBeats(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) throws Exception {
        eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                eimMonitoringConfig, BeatsStatusEnum.ACTIVATING);
        this.startEimIfNotStarted();

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(
                Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("X_Broker_Api_Version", "2.12");

        HttpEntity<ApiEimMonitoringConfig> request = new HttpEntity<ApiEimMonitoringConfig>(
                eimMonitoringConfig.getEimMonitoringConfigInApiFormat(),
                headers);

        String url = this.eimApiUrl + "/agent/" + eimConfig.getAgentId()
                + "/monitor";
        logger.debug("Activating beats: {} {}", url, request);

        try {
            Map<String, Object> response = restTemplate.postForObject(url,
                    request, Map.class);
            if (response.get("monitored").toString().equals("true")) {
                eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                        eimMonitoringConfig, BeatsStatusEnum.ACTIVATED);
                logger.debug("Beats activated successfully!");
            } else {
                eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                        eimMonitoringConfig, BeatsStatusEnum.DEACTIVATED);
                throw new Exception("Beats not activated");
            }
        } catch (HttpClientErrorException e) {
            // EIM returns 406 (Non Acceptable) when agent is already
            // monitorized.
            // If is not 406, throw error
            logger.warn("{} | {}", e.getMessage(), e.getResponseBodyAsString());
            if (!e.getStatusCode().equals(HttpStatus.NOT_ACCEPTABLE)) {
                throw e;
            }
        }

    }

    public void unDeployBeats(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) {
        eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                eimMonitoringConfig, BeatsStatusEnum.DEACTIVATING);
        this.startEimIfNotStarted();

        String url = this.eimApiUrl + "/agent/" + eimConfig.getAgentId()
                + "/unmonitor";
        logger.debug("Deactivating beats: {}", url);

        RestTemplate restTemplate = new RestTemplate();

        try {
            restTemplate.delete(url);
            eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                    eimMonitoringConfig, BeatsStatusEnum.DEACTIVATED);
        } catch (Exception e) {
            logger.error("Error on Deactivate Beats: not Deactivated", e);
            eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                    eimMonitoringConfig, BeatsStatusEnum.ACTIVATED);
        }
    }

    /* ****************** */
    /* ****** BBDD ****** */
    /* ****************** */

    public EimMonitoringConfig createEimMonitoringConfig(
            EimMonitoringConfig eimMonitoringConfig) {
        return this.eimMonitoringConfigRepository.save(eimMonitoringConfig);
    }

    public EimMonitoringConfig createEimMonitoringConfigAndChilds(
            EimMonitoringConfig eimMonitoringConfig) {
        if (eimMonitoringConfig != null) {
            Map<String, EimBeatConfig> beats = eimMonitoringConfig.getBeats();
            eimMonitoringConfig.setBeats(null);
            eimMonitoringConfig = this.eimMonitoringConfigRepository
                    .save(eimMonitoringConfig);
            if (beats != null) {
                for (Map.Entry<String, EimBeatConfig> currentBeat : beats
                        .entrySet()) {
                    currentBeat.getValue()
                            .setEimMonitoringConfig(eimMonitoringConfig);
                    EimBeatConfig savedBeat = this.eimBeatConfigRepository
                            .save(currentBeat.getValue());
                    currentBeat.setValue(savedBeat);
                }
                eimMonitoringConfig.setBeats(beats);
            }
        }
        return eimMonitoringConfig;
    }

    public EimMonitoringConfig updateEimMonitoringConfigBeatsStatus(
            EimMonitoringConfig eimMonitoringConfig, BeatsStatusEnum status) {
        eimMonitoringConfig.setBeatsStatus(status);
        return this.eimMonitoringConfigRepository.save(eimMonitoringConfig);
    }

    /* ****************** */
    /* *** Additional *** */
    /* ****************** */

    @Async
    public void instrumentalizeAsync(EimConfig eimConfig) throws Exception {
        try {
            dbmanager.bindSession();
            this.startEimIfNotStarted();

            eimConfig = this.instrumentalize(eimConfig);
        } catch (Exception e) {
            dbmanager.unbindSession();
            throw new Exception(
                    "EIM is not started or response is a 500 Internal Server Error",
                    e);
        }
        dbmanager.unbindSession();
    }

    @Async
    public void deployBeatsAsync(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) throws Exception {
        try {
            dbmanager.bindSession();
            this.startEimIfNotStarted();

            eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                    eimMonitoringConfig, BeatsStatusEnum.ACTIVATING);
            this.deployBeats(eimConfig, eimMonitoringConfig);

        } catch (Exception e) {
            dbmanager.unbindSession();
            throw new Exception("Error on activate Beats: not activated", e);
        }
        dbmanager.unbindSession();
    }

    @Async
    public void instrumentalizeAndDeployBeats(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) throws Exception {
        try {
            dbmanager.bindSession();
            eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                    eimMonitoringConfig, BeatsStatusEnum.ACTIVATING);

            eimConfig = this.instrumentalize(eimConfig);
            try {
                this.deployBeats(eimConfig, eimMonitoringConfig);
            } catch (Exception e) {
                dbmanager.unbindSession();
                throw new Exception("Error on activate Beats: not activated",
                        e);
            }
        } catch (Exception e) {
            dbmanager.unbindSession();
            throw new Exception(
                    "EIM is not started or response is a 500 Internal Server Error",
                    e);
        }
        dbmanager.unbindSession();
    }

    @Async
    public void deinstrumentalizeAsync(EimConfig eimConfig) {
        dbmanager.bindSession();
        this.deinstrumentalize(eimConfig);
        dbmanager.unbindSession();
    }

    @Async
    public void undeployBeatsAsync(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) {
        dbmanager.bindSession();
        eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                eimMonitoringConfig, BeatsStatusEnum.DEACTIVATING);
        this.unDeployBeats(eimConfig, eimMonitoringConfig);
        dbmanager.unbindSession();
    }

    @Async
    public void deInstrumentalizeAndUnDeployBeats(EimConfig eimConfig,
            EimMonitoringConfig eimMonitoringConfig) {
        dbmanager.bindSession();
        eimMonitoringConfig = this.updateEimMonitoringConfigBeatsStatus(
                eimMonitoringConfig, BeatsStatusEnum.DEACTIVATING);
        this.unDeployBeats(eimConfig, eimMonitoringConfig);
        this.deinstrumentalize(eimConfig);
        dbmanager.unbindSession();
    }

    /* **************** */
    /* **** Others **** */
    /* **************** */

    public EimConfig getEimConfigById(Long eimConfigId) {
        return eimConfigRepository.findById(eimConfigId).get();
    }

    public EimMonitoringConfig getEimMonitoringConfigById(
            Long eimMonitoringConfigId) {
        return eimMonitoringConfigRepository.findById(eimMonitoringConfigId)
                .get();
    }

    public EimBeatConfig getEimBeatConfigById(Long eimBeatConfigId) {
        return eimBeatConfigRepository.findById(eimBeatConfigId).get();
    }

    public EimConfig duplicateEimConfig(Long eimConfigId) {
        EimConfig eimConfig = eimConfigRepository.findById(eimConfigId).get();
        return new EimConfig(eimConfig);
    }

    public EimMonitoringConfig duplicateEimMonitoringConfig(
            Long eimMonitoringConfigId) {
        EimMonitoringConfig eimMonitoringConfig = eimMonitoringConfigRepository
                .findById(eimMonitoringConfigId).get();
        return new EimMonitoringConfig(eimMonitoringConfig);
    }

    public EimBeatConfig duplicateEimBeatConfig(Long eimBeatConfigId) {
        EimBeatConfig eimBeatConfig = eimBeatConfigRepository
                .findById(eimBeatConfigId).get();
        return new EimBeatConfig(eimBeatConfig);
    }
}
