package io.elastest.etm;

import java.util.concurrent.Executor;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import io.elastest.epm.client.service.DockerComposeService;
import io.elastest.epm.client.service.DockerService;
import io.elastest.epm.client.service.K8sService;
import io.elastest.etm.dao.TraceRepository;
import io.elastest.etm.platform.service.DockerServiceImpl;
import io.elastest.etm.platform.service.K8ServiceImpl;
import io.elastest.etm.platform.service.PlatformService;
import io.elastest.etm.service.AbstractMonitoringService;
import io.elastest.etm.service.ElasticsearchService;
import io.elastest.etm.service.EtPluginsService;
import io.elastest.etm.service.EtmTestResultService;
import io.elastest.etm.service.TestSuiteService;
import io.elastest.etm.service.TracesSearchService;
import io.elastest.etm.service.client.EsmServiceClientImpl;
import io.elastest.etm.service.client.MiniEsmServiceClient;
import io.elastest.etm.service.client.EsmServiceClient;
import io.elastest.etm.utils.EtmFilesService;
import io.elastest.etm.utils.UtilTools;
import io.elastest.etm.utils.UtilsService;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@EnableAsync
@ComponentScan(basePackages = { "io.elastest" })
public class ElasTestTormApp extends AsyncConfigurerSupport {
    @Autowired
    EtmFilesService etmFilesService;
    @Autowired
    private UtilsService utilsService;
    @Autowired
    TraceRepository traceRepository;
    @Autowired
    TestSuiteService testSuiteService;
    @Autowired
    DockerComposeService dockerComposeService;
    @Autowired
    DockerService dockerService;
    @Autowired
    EtmTestResultService etmTestResultService;
    @Autowired
    K8sService k8Service;

    @Value("${additional.server.port}")
    int additionalServerPort;

    @Bean
    UtilTools utils() {
        UtilTools utils = new UtilTools();
        return utils;
    }

    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ET-");
        executor.initialize();
        return executor;
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(65500);
        container.setMaxBinaryMessageBufferSize(65500);
        return container;
    }

    @Bean
    public AbstractMonitoringService getMonitoringService() {
        if (utilsService.isElastestMini()) {
            return new TracesSearchService(traceRepository, testSuiteService,
                    utilsService);
        } else {
            return new ElasticsearchService(utilsService, testSuiteService);
        }
    }

    @Bean
    @Primary
    public PlatformService platformService() {
        PlatformService platformService = null;
        if (utilsService.isKubernetes()) {
            platformService = new K8ServiceImpl(k8Service, etmFilesService,
                    utilsService);
        } else {
            platformService = new DockerServiceImpl(dockerComposeService,
                    etmFilesService, utilsService, dockerService);
        }
        return platformService;
    }

    @Bean
    @Primary
    public EtPluginsService getEtPluginsService() {
        return new EtPluginsService(platformService(), utilsService);
    }

    @Bean
    // TODO Change dockerComposeService for the right platform implementation
    public EsmServiceClient getSupportServiceClientInterface() {
        if (utilsService.isElastestMini()) {
            return new MiniEsmServiceClient(getEtPluginsService(),
                    utilsService, platformService());
        } else {
            return new EsmServiceClientImpl(utilsService);
        }
    }

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        tomcat.addAdditionalTomcatConnectors(createStandardConnector());
        return tomcat;
    }

    private Connector createStandardConnector() {
        Connector connector = new Connector(
                "org.apache.coyote.http11.Http11NioProtocol");
        connector.setPort(additionalServerPort);
        return connector;
    }

    public static void main(String[] args) throws Exception {
        new SpringApplication(ElasTestTormApp.class).run(args);
    }
}
