package io.elastest.etm.docker;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.reporting.MavenReportException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.LogConfig.LoggingType;
import com.github.dockerjava.api.model.Ports.Binding;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.WaitContainerResultCallback;

import io.elastest.etm.api.model.SutExecution;
import io.elastest.etm.docker.utils.ExecStartResultCallbackWebsocket;
import io.elastest.etm.rabbitmq.service.RabbitmqService;

@Service
public class DockerExecution {

	private String testImage = "";
	private static String appImage = "edujgurjc/torm-loadapp";
	private static String logstashImage = "edujgurjc/logstash", dockbeatImage = "edujgurjc/dockbeat";
	private static final String volumeDirectory = "/springbootdemotest/springbootdemotest";

	@Autowired
	private ApplicationContext context;

	@Autowired
	private RabbitmqService rabbitmqService;

	private DockerClient dockerClient;
	private CreateContainerResponse container, logstashContainer, dockbeatContainer;
	private String testContainerId, appContainerId, logstashContainerId, dockbeatContainerId;

	private boolean windowsSo = false;
	private String surefirePath = "/testcontainers-java-examples/selenium-container/target/surefire-reports";
	private String testsuitesPath = "/home/edujg/torm/testsuites.json";

	private String network, logstashIP, sutIP;

	private String testLogId;
	String exchange, queue;

	/* Config Methods */

	public String initializeLog() {
		testLogId = RandomStringUtils.randomAlphanumeric(17).toLowerCase();
		return "localhost:9200/" + testLogId;
	}

	public void configureDocker() {
		if (windowsSo) {
			DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
					.withDockerHost("tcp://192.168.99.100:2376").build();
			this.dockerClient = DockerClientBuilder.getInstance(config).build();

			this.surefirePath = "";
			this.testsuitesPath = "";
		} else {
			this.dockerClient = DockerClientBuilder.getInstance().build();
		}

		network = "Logstash-" + RandomStringUtils.randomAlphanumeric(19);
		this.dockerClient.createNetworkCmd().withName(network).exec();

		Info info = this.dockerClient.infoCmd().exec();
		System.out.println("Info: " + info);
	}

	/* Starting Methods */

	public void startRabbitmq() {
		try {
			System.out.println("Starting Rabbitmq...");
			rabbitmqService.createRabbitmqConnection("localhost", "admin", "admin");
			exchange = "ex-" + testLogId;
			queue = "q-" + testLogId;
			rabbitmqService.createFanoutExchange(exchange);
			rabbitmqService.createQueue(queue);
			rabbitmqService.bindQueueToExchange(queue, exchange, "1");
			System.out.println("Successfully started Rabbitmq...");
		} catch (Exception e) {
			e.printStackTrace();
			purgeRabbitmq();
		}
	}

	public void startLogstash() {
		try {
			String envVar = "ELASID=" + testLogId;
			String envVar2 = "HOSTIP=" + getHostIp();
			String envVar3 = "EXCHANGENAME=" + exchange;

			ArrayList<String> envList = new ArrayList<>();
			envList.add(envVar);
			envList.add(envVar2);
			envList.add(envVar3);

			System.out.println("Pulling logstash image...");
			this.dockerClient.pullImageCmd(logstashImage).exec(new PullImageResultCallback()).awaitSuccess();
			System.out.println("Pulling logstash image ends");

			this.logstashContainer = this.dockerClient.createContainerCmd(logstashImage).withEnv(envList)
					.withNetworkMode(network).withName("logstash_container").exec();

			logstashContainerId = this.logstashContainer.getId();

			this.dockerClient.startContainerCmd(logstashContainerId).exec();

			logstashIP = getContainerIp(logstashContainerId);
			if (logstashIP == null || logstashIP.isEmpty()) {
				throw new Exception();
			}
			this.manageLogstash();

		} catch (Exception e) {
			e.printStackTrace();
			endLogstashExec();
			purgeRabbitmq();
		}
	}

	public void manageLogstash() {
		System.out.println("Starting logstash");
		try {

			Object lock = new Object();
			ExecStartResultCallbackWebsocket execStartResultCallbackWebsocket = context
					.getBean(ExecStartResultCallbackWebsocket.class);
			execStartResultCallbackWebsocket.setLock(lock);

			synchronized (lock) {
				this.dockerClient.logContainerCmd(logstashContainerId).withStdErr(true).withStdOut(true)
						.withFollowStream(true).exec(execStartResultCallbackWebsocket);
				lock.wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void startBeats() {
		try {
			String envVar = "LOGSTASHIP=" + logstashIP + ":5044";

			System.out.println("Pulling dockbeat image...");
			this.dockerClient.pullImageCmd(dockbeatImage).exec(new PullImageResultCallback()).awaitSuccess();
			System.out.println("Pulling dockbeat image ends");

			Volume volume = new Volume("/var/run/docker.sock");

			this.dockbeatContainer = this.dockerClient.createContainerCmd(dockbeatImage).withEnv(envVar)
					.withNetworkMode(network).withVolumes(volume).withBinds(new Bind("/var/run/docker.sock", volume))
					.withName("beats_container").exec();

			dockbeatContainerId = this.dockbeatContainer.getId();

			this.dockerClient.startContainerCmd(dockbeatContainerId).exec();
			Thread.sleep(2000);

		} catch (Exception e) {
			e.printStackTrace();
			endBeatsExec();
			endLogstashExec();
			purgeRabbitmq();
		}
	}

	public SutExecution startSut(SutExecution sutExec) {
		try {
			System.out.println("Starting sut");
			String envVar = "REPO_URL=https://github.com/EduJGURJC/springbootdemo";
			
			LogConfig logConfig = new LogConfig();
			logConfig.setType(LoggingType.SYSLOG);
			Map<String, String> configMap = new HashMap<String, String>();
			configMap.put("syslog-address", "tcp://" + logstashIP + ":5001");
			logConfig.setConfig(configMap);
			
			this.dockerClient.pullImageCmd(appImage).exec(new PullImageResultCallback()).awaitSuccess();

			CreateContainerResponse appContainer = this.dockerClient.createContainerCmd(appImage).withEnv(envVar)
					.withLogConfig(logConfig).withNetworkMode(network)
					.withName("sut_container").exec();

			sutExec.deployStatus(SutExecution.DeployStatusEnum.DEPLOYED);

			appContainerId = appContainer.getId();

			this.dockerClient.startContainerCmd(appContainerId).exec();
			sutIP = getContainerIp(appContainerId);
			sutIP = sutIP.split("/")[0];
			sutIP = "http://" + sutIP + ":8080";

			sutExec.setUrl(sutIP);
		} catch (Exception e) {
			e.printStackTrace();
			sutExec.deployStatus(SutExecution.DeployStatusEnum.ERROR);
			endSutExec();
			endBeatsExec();
			endLogstashExec();
			purgeRabbitmq();
		}
		return sutExec;
	}

	public void startTest(String testImage) {
		try {
			System.out.println("Starting test");
			this.testImage = testImage;
			ExposedPort tcp6080 = ExposedPort.tcp(6080);

			Ports portBindings = new Ports();
			portBindings.bind(tcp6080, Binding.bindPort(6080));

			String envVar = "DOCKER_HOST=tcp://172.17.0.1:2376";
			String envVar2 = "APP_IP=" + sutIP;
			String envVar3 = "NETWORK=" + network;
			ArrayList<String> envList = new ArrayList<>();
			envList.add(envVar);
			envList.add(envVar2);
			envList.add(envVar3);

			Volume volume = new Volume(volumeDirectory);
			
			LogConfig logConfig = new LogConfig();
			logConfig.setType(LoggingType.SYSLOG);
			Map<String, String> configMap = new HashMap<String, String>();
			configMap.put("syslog-address", "tcp://" + logstashIP + ":5000");
			logConfig.setConfig(configMap);

			this.dockerClient.pullImageCmd(testImage).exec(new PullImageResultCallback()).awaitSuccess();

			this.container = this.dockerClient.createContainerCmd(testImage).withExposedPorts(tcp6080)
					.withPortBindings(portBindings).withVolumes(volume).withBinds(new Bind(volumeDirectory, volume))
					.withEnv(envList).withLogConfig(logConfig).withNetworkMode(network)
					.withName("test_container").exec();

			testContainerId = this.container.getId();

			this.dockerClient.startContainerCmd(testContainerId).exec();
			int code = this.dockerClient.waitContainerCmd(testContainerId).exec(new WaitContainerResultCallback())
					.awaitStatusCode();
			System.out.println("Test container ends with code " + code);

			this.saveTestSuite();
		} catch (Exception e) {
			endExec();
		}
	}

	public void saveTestSuite() {
		File surefireXML = new File(this.surefirePath);
		List<File> reportsDir = new ArrayList<>();
		reportsDir.add(surefireXML);

		SurefireReportParser surefireReport = new SurefireReportParser(reportsDir, new Locale("en", "US"), null);
		try {
			List<ReportTestSuite> testSuites = surefireReport.parseXMLReportFiles();

			ObjectMapper mapper = new ObjectMapper();
			// Object to JSON in file
			try {
				mapper.writeValue(new File(this.testsuitesPath), testSuites);
			} catch (JsonGenerationException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (MavenReportException e) {
			e.printStackTrace();
		}
	}

	/* End execution methods */

	public void endExec() {
		endTestExec();
		endBeatsExec();
		endLogstashExec();
		purgeRabbitmq();
	}

	public void endAllExec() {
		endTestExec();
		endSutExec();
		endBeatsExec();
		endLogstashExec();
		purgeRabbitmq();
	}

	public void endTestExec() {
		try {
			System.out.println("Ending test execution");
			try {
				this.dockerClient.stopContainerCmd(testContainerId).exec();
			} catch (Exception e) {
			}
			this.dockerClient.removeContainerCmd(testContainerId).exec();
			this.dockerClient.removeImageCmd(testImage).withForce(true).exec();
		} catch (Exception e) {
			System.out.println("Error on ending test execution");

		}
	}

	public void endSutExec() {
		try {
			try {
				this.dockerClient.stopContainerCmd(appContainerId).exec();
			} catch (Exception e) {
			}
			this.dockerClient.removeContainerCmd(appContainerId).exec();
			this.dockerClient.removeImageCmd(appImage).withForce(true).exec();
		} catch (Exception e) {
			System.out.println("Error on ending Sut execution");
		}
	}

	public void endLogstashExec() {
		try {
			System.out.println("Ending Logstash execution");
			this.dockerClient.stopContainerCmd(logstashContainerId).exec();
			this.dockerClient.removeContainerCmd(logstashContainerId).exec();
		} catch (Exception e) {
			System.out.println("Error on ending Logstash execution");
		}
		System.out.println("Removing docker network...");
		this.dockerClient.removeNetworkCmd(network).exec();
	}

	public void purgeRabbitmq() {
		try {
			System.out.println("Purging Rabbitmq");
			rabbitmqService.deleteQueue(queue);
			rabbitmqService.deleteFanoutExchange(exchange);
			rabbitmqService.closeChannel();
			rabbitmqService.closeConnection();
		} catch (Exception e) {
			System.out.println("Error on purging Rabbitmq");
		}
	}

	public void endBeatsExec() {
		try {
			System.out.println("Ending dockbeat execution");
			this.dockerClient.stopContainerCmd(dockbeatContainerId).exec();
			this.dockerClient.removeContainerCmd(dockbeatContainerId).exec();
		} catch (Exception e) {
			System.out.println("Error on ending dockbeat execution");
		}
	}

	/* Utils */

	public String getContainerIp(String containerId) {
		return this.dockerClient.inspectContainerCmd(containerId).exec().getNetworkSettings().getNetworks().get(network)
				.getIpAddress();
	}

	public String getHostIp() {
		return this.dockerClient.inspectNetworkCmd().withNetworkId(network).exec().getIpam().getConfig().get(0)
				.getGateway();
	}
}
