package io.elastest.epm.client.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.elastest.epm.client.api.ClusterApi;
import io.elastest.epm.client.model.AuthCredentials;
import io.elastest.epm.client.model.ClusterFromResourceGroup;
import io.elastest.epm.client.model.WorkerFromVDU;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.gson.reflect.TypeToken;

import io.elastest.epm.client.ApiClient;
import io.elastest.epm.client.ApiException;
import io.elastest.epm.client.JSON;
import io.elastest.epm.client.api.KeyApi;
import io.elastest.epm.client.api.PackageApi;
import io.elastest.epm.client.api.PoPApi;
import io.elastest.epm.client.api.WorkerApi;
import io.elastest.epm.client.model.Key;
import io.elastest.epm.client.model.KeyValuePair;
import io.elastest.epm.client.model.PoP;
import io.elastest.epm.client.model.RemoteEnvironment;
import io.elastest.epm.client.model.ResourceGroup;
import io.elastest.epm.client.model.VDU;
import io.elastest.epm.client.model.Worker;
import io.elastest.epm.client.model.Cluster;
import io.elastest.epm.client.service.ServiceException.ExceptionCode;

@Service
public class EpmServiceClient {
    private static final Logger logger = LoggerFactory
            .getLogger(EpmServiceClient.class);

    private final int MAX_ATTEMPTS = 10;
    private final int TIME_BETWEEN_ATTEMPTS = 15;

    @Value("${et.shared.folder}")
    private String sharedFolder;
    @Value("${et.epm.packages.path}")
    private String packageFilePath;
    @Value("${et.epm.key.path}")
    private String keyFilePath;
    public static String composePackageFilePath;
    public static String sharedDataTmpFolder;
    public static boolean etMasterSlaveMode;

    private FilesService filesService;
    private RemoteEnvironment re;

    private PackageApi packageApiInstance;
    private WorkerApi workerApiInstance;
    private ClusterApi clusterApiInstance;
    private KeyApi keyApiInstance;
    private PoPApi popApi;
    private ApiClient apiClient;
    private JSON json;

    private Map<String, String> adapters;
    private Map<String, ResourceGroup> deployedDockerCompose;

    public enum AdaptersNames {
        DOCKER("docker"), DOCKER_COMPOSE("docker-compose");

        private String name;

        public String getName() {
            return name;
        }

        private AdaptersNames(String name) {
            this.name = name;
        }
    }

    public EpmServiceClient(FilesService filesService) {
        apiClient = new ApiClient();
        packageApiInstance = new PackageApi();
        packageApiInstance.setApiClient(apiClient);
        workerApiInstance = new WorkerApi();
        clusterApiInstance = new ClusterApi();
        keyApiInstance = new KeyApi();
        popApi = new PoPApi();
        json = new JSON(apiClient);
        this.filesService = filesService;
        adapters = new HashMap<>();
        deployedDockerCompose = new HashMap<>();

    }

    @Value("${et.master.slave.mode}")
    public void setMasterSlaveMode(boolean isMasterSlaveMode) {
        etMasterSlaveMode = isMasterSlaveMode;
    }

    @Value("${et.shared.data.tmp.folder}")
    public String setSharedDataTmpFolder() {
        return sharedDataTmpFolder;
    }

    @Value("${et.epm.compose.packages.path}")
    public void setComposePackageFilePath(String composePackageFilePath) {
        EpmServiceClient.composePackageFilePath = composePackageFilePath;
    }

    @PostConstruct
    public void initRemoteenvironment() {
        if (etMasterSlaveMode) {
            logger.info("Creating slave");
            try {
                //if (workerApiInstance.getAllWorkers().size() > 0) {
                    re = provideRemoteEnvironment();
               // }
                //if (popApi.getAllPoPs().isEmpty()) {
                //    re = provisionRemoteEnvironment();
                //}
            } catch (ServiceException /*| ApiException | ApiException */ se) {
                etMasterSlaveMode = false;
                se.printStackTrace();
            }
        }
    }

    @PreDestroy
    public void endRemoteEnvironment() {
        if (etMasterSlaveMode) {
            logger.info("Removing slave");
            try {
                if (re != null) {
                    deprovisionRemoteEnvironment(re);
                }

            } catch (ServiceException se) {
                se.printStackTrace();
            }
        }
    }

    public RemoteEnvironment provideRemoteEnvironment()
            throws ServiceException {
        logger.info("Provisioning virtual machine.");
        RemoteEnvironment re = new RemoteEnvironment();
        ResourceGroup resourceGroup = null;
        Worker worker = null;
        PoP openstackPoP = null;

        try {
            // Register a OpenStack PoP
            openstackPoP = registerOpenstackPop();
            // Providing a new remote VM
            resourceGroup = sendPackage(packageFilePath, "m1tub.tar",
                    sharedFolder + "/tmp" + "/ansible");
            re.setResourceGroup(resourceGroup);
            logger.debug("Virtual machine provided with id: {}",
                    resourceGroup.getId());
            // Registering the privated key
            Key key = addKey(filesService.getFileFromResources(keyFilePath,
                    "key.json", sharedFolder + "/tmp" + "/ansible"));
            logger.debug("Key {} value: {}", key.getName(), key.getKey());
            re.setKey(key);
            
            // Registering a Worker

            /*
            Registering from a Resource Group can also be done in the following way:

            WorkerFromVDU workerFromVDU = new WorkerFromVDU();
            workerFromVDU.setVduId(resourceGroup.getVdus().get(0).getId());
            workerFromVDU.setType(new ArrayList<String>());
            workerFromVDU.addTypeItem("docker-compose");
            Worker registeredWorker = workerApiInstance.createWorker(workerFromVDU);

            Alternatively you can transform the RG into a K8s CLuster

            ClusterFromResourceGroup clusterFromResourceGroup = new ClusterFromResourceGroup();
            clusterFromResourceGroup.setResourceGroupId(resourceGroup.getId());
            clusterFromResourceGroup.setMasterId(resourceGroup.getVdus().get(0).getId());
            clusterFromResourceGroup.addTypeItem("kubernetes");

            Cluster cluster = clusterApiInstance.createCluster(clusterFromResourceGroup);

             */

            int currentAttempts = 0;
            boolean registeredWorker = false;
            while (currentAttempts < MAX_ATTEMPTS && !registeredWorker) {
                logger.debug("Attempts: {}", currentAttempts);
                worker = registerWorker(resourceGroup);
                registeredWorker = worker != null ? true : false;
                if (!registeredWorker) {
                    currentAttempts++;
                    TimeUnit.SECONDS.sleep(TIME_BETWEEN_ATTEMPTS);
                }
            }

            if (!registeredWorker) {
                throw new ServiceException(
                        "Error provisioning a new remote environment",
                        ExceptionCode.ERROR_PROVISIONING_VM);
            }
            re.setWorker(worker);
            re.setHostIp(worker.getIp());
            
            // Installing adapters
            logger.debug("Worker id: {}", worker.getId());
            adapters.put(AdaptersNames.DOCKER.getName(), installAdapter(
                    worker.getId(), AdaptersNames.DOCKER.getName()));
            adapters.put(AdaptersNames.DOCKER_COMPOSE.getName(), installAdapter(
                    worker.getId(), AdaptersNames.DOCKER_COMPOSE.getName()));

        } catch (ApiException | IOException | InterruptedException
                | ServiceException | URISyntaxException e) {
            logger.error("Error: {} ", e.getMessage());
            if (e instanceof ServiceException) {
                throw (ServiceException) e;
            } else {
                throw new ServiceException(
                        "Error provisioning a new remote environment",
                        e.getCause(), ExceptionCode.ERROR_PROVISIONING_VM);
            }
        }

        return re;
    }

    public String deprovisionRemoteEnvironment(RemoteEnvironment re)
            throws ServiceException {
        logger.info("Removing remote environment.");
        try {
            deleteWorker(re.getWorker().getId());
            deleteKey(re.getKey().getId());
            deleteAdapter(re.getResourceGroup().getId());
        } catch (FileNotFoundException | ApiException e) {
            e.printStackTrace();
            throw new ServiceException(
                    "Error removing a new remote environment", e.getCause(),
                    ExceptionCode.ERROR_PROVISIONING_VM);
        }

        return re.getResourceGroup().getId();
    }

    private Key parserKeyFromJsonFile(File key) throws FileNotFoundException {
        InputStream is = new FileInputStream(key);
        Scanner s = new Scanner(is).useDelimiter("\\A");
        String result = s.hasNext() ? s.next() : "";
        result = result.replace("  ", "");
        return json.deserialize(result, new TypeToken<Key>() {
        }.getType());
    }

    public ResourceGroup sendPackage(String packagePath, String packageName,
            String tmpFolder) throws ServiceException {
        logger.info("Registering adapter described in the file: {}",
                packagePath);
        ResourceGroup result = null;
        try {
            File file = filesService.getFileFromResources(packagePath,
                    packageName, tmpFolder);
            result = packageApiInstance.receivePackage(file);
            logger.debug("New instance id: {} ", result.getId());
            logger.debug(String.valueOf(result));
        } catch (ApiException | IOException | URISyntaxException re) {
            re.printStackTrace();
            throw new ServiceException("Error sending package", re.getCause(),
                    ExceptionCode.ERROR_SENDING_PACKAGE);
        }

        return result;
    }

    public ResourceGroup sendPackage(File packageFile) throws ServiceException {
        logger.info("Registering adapter described in the file: {}",
                packageFile.getName());
        ResourceGroup result = null;
        try {
            result = packageApiInstance.receivePackage(packageFile);
            logger.debug("New instance id: {} ", result.getId());
            logger.debug(String.valueOf(result));
        } catch (ApiException ae) {
            ae.printStackTrace();
            throw new ServiceException("Error sending package", ae.getCause(),
                    ExceptionCode.ERROR_SENDING_PACKAGE);
        }

        return result;
    }

    public void deleteAdapter(String id) {
        logger.info("Delete adapter: {}", id);
        try {
            packageApiInstance.deletePackage(id);
        } catch (ApiException e) {
            System.err.println(
                    "Exception when calling PackageApi#receivePackage");
            e.printStackTrace();
        }
    }

    public PoP registerOpenstackPop() {
        PoP pop = new PoP();
        pop.setName("tub-os");
        pop.setInterfaceEndpoint("http://cpu06.codeurjc.es:5000/v2.0");
        pop.addInterfaceInfoItem(new KeyValuePair().key("auth_url").value("http://cpu06.codeurjc.es:5000/v2.0"));
        pop.addInterfaceInfoItem(new KeyValuePair().key("password").value("Eil3rac8soojoam"));
        pop.addInterfaceInfoItem(new KeyValuePair().key("project_name").value("tub"));
        pop.addInterfaceInfoItem(new KeyValuePair().key("username").value("tub"));

        PoP result = null;
        try {
            result = popApi.registerPoP(pop);
        } catch (ApiException e) {
            System.err
                    .println("Exception when calling PoPApi#registerPoP");
            e.printStackTrace();
        }

        return result;
    }

    public Worker registerWorker(ResourceGroup rg) throws ServiceException {
        Worker worker = new Worker();
        AuthCredentials authCredentials = new AuthCredentials();
        authCredentials.setKey("tub-ansible");
        authCredentials.setUser("ubuntu");
        authCredentials.passphrase("");
        authCredentials.password("");

        worker.setIp(rg.getVdus().get(0).getIp());
        worker.setEpmIp("localhost");
        worker.setAuthCredentials(authCredentials);

        Worker result = null;
        try {
            result = workerApiInstance.registerWorker(worker);
        } catch (ApiException e) {
            System.err
                    .println("Exception when calling WorkerApi#registerWorker");
            e.printStackTrace();
        }

        return result;
    }

    public String deleteWorker(String id) throws ApiException {
        String result = workerApiInstance.deleteWorker(id);
        return result;
    }

    public Key addKey(File keyFile) throws FileNotFoundException, ApiException {
        Key key = parserKeyFromJsonFile(keyFile);
        return keyApiInstance.addKey(key);
    }

    public String deleteKey(String id)
            throws FileNotFoundException, ApiException {
        return keyApiInstance.deleteKey(id);
    }

    public String installAdapter(String workerId, String type)
            throws ApiException {

        return workerApiInstance.installAdapter(workerId, type);
    }

    public String getPopName(String reIp, String popType)
            throws ServiceException {
        String popName = null;
        try {
            for (PoP pop : popApi.getAllPoPs()) {
                if (pop.getName().equals(popType + "-" + reIp)) {
                    popName = pop.getName();
                    break;
                }
            }
            if (popName == null) {
                throw new ApiException(
                        "There isn't any pop with the name provided");
            }
        } catch (ApiException e) {
            throw new ServiceException(e.getMessage(), e.getCause(),
                    ExceptionCode.GENERIC_ERROR);
        }
        return popName;
    }
    
    public String getRemoteServiceIpByVduName(String vduName) {
        logger.info("VDU name passed as parameter: {}", vduName);
        String serviceIp = null;
        for (VDU vdu: getRe().getResourceGroup().getVdus()) {
            if (vdu.getName().equals(vduName)) {
                logger.info("VDU name: {}", vdu.getName());
                serviceIp = vdu.getIp();
                break;
            }
        }
        
        return serviceIp;
    }

    public RemoteEnvironment getRe() {
        return re;
    }

    public void setRe(RemoteEnvironment re) {
        this.re = re;
    }

    public Map<String, ResourceGroup> getDeployedDockerCompose() {
        return deployedDockerCompose;
    }

    public void setDeployedDockerCompose(
            Map<String, ResourceGroup> deployedDockerCompose) {
        this.deployedDockerCompose = deployedDockerCompose;
    }
}