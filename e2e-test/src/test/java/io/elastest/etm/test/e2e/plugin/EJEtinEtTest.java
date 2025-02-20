package io.elastest.etm.test.e2e.plugin;

import static io.github.bonigarcia.seljup.BrowserType.CHROME;
import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;

import io.elastest.etm.test.base.EtmPluginBaseTest;
import io.github.bonigarcia.seljup.BrowserType;
import io.github.bonigarcia.seljup.DockerBrowser;
import io.github.bonigarcia.seljup.SeleniumExtension;

/**
 * Checks the Jenkins plugin works correctly. Requirements tested: EJ1, EJ2,
 * EJ5, EJ11, EJ12
 *
 * @author franciscoRdiaz
 * @since 0.1.1
 */
@Tag("Et-in-Et_e2e")
@DisplayName("E2E test for the ElasTest Jenkins plugin")
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(SeleniumExtension.class)
public class EJEtinEtTest extends EtmPluginBaseTest {
    final Logger log = getLogger(lookup().lookupClass());
    final String jobName = "PJob_1";

    @Test
    @DisplayName("ETinET-Test: use plugin in a pipeline")
    void testETInETPluginInPipelineJob(
            @DockerBrowser(type = CHROME) RemoteWebDriver localDriver,
            TestInfo testInfo) throws Exception {
        setupTestBrowser(testInfo, BrowserType.CHROME, "71", localDriver);
        navigateTo(driver, jenkinsCIUrl);
        loginOnJenkins(driver);

        try {
            if (!isJobCreated(jobName)) {
                driver.findElement(By.linkText("New Item")).click();
                createPipelineJob(driver, jobName,
                        unitTestPipelineScriptOriginal);
            } else {
                driver.findElement(By.linkText(jobName)).click();
            }
            executeJob(driver);
            goToElasTest(driver);
            log.info("Wait for TJob end with sucess");
            checkFinishTJobExec(driver, 300, "SUCCESS", false);
        } finally {
            deletePipelineJob(driver, jobName);
        }
    }

}
