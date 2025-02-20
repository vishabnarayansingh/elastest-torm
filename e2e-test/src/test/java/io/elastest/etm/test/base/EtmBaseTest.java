/*
 * (C) Copyright 2017-2019 ElasTest (http://elastest.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.elastest.etm.test.base;

import static java.lang.System.getProperty;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openqa.selenium.logging.LogType.BROWSER;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.invisibilityOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.textToBePresentInElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfElementLocated;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.elastest.etm.test.utils.RestClient;
import io.github.bonigarcia.seljup.BrowserType;
import io.github.bonigarcia.wdm.ChromeDriverManager;
import io.github.bonigarcia.wdm.FirefoxDriverManager;

public class EtmBaseTest {
    protected static final String BROWSER_VERSION_LATEST = "latest";
    protected final Logger log = getLogger(lookup().lookupClass());

    protected String tormUrl = "http://172.17.0.1:37000/"; // local by default
    protected String secureTorm = "http://user:pass@172.17.0.1:37000/";
    protected static final String TSS_PAGE_SUFFIX = "#/support-services";
    protected String apiPath = "api";
    protected String tormApiUrl;
    protected String eUser = null;
    protected String ePassword = null;
    protected boolean secureElastest = false;
    public WebDriver driver;
    protected RestClient restClient;
    protected String eusURL;

    @BeforeEach
    void setup(TestInfo testInfo) {
        String testName = testInfo.getTestMethod().get().getName();
        log.info("##### Start test: {}", testName);

        String etmApi = getProperty("etEtmApi");
        if (etmApi != null) {
            tormUrl = etmApi;
        }
        String elastestUser = getProperty("eUser");
        if (elastestUser != null) {
            log.info("Elastest User received: {}", elastestUser);
            eUser = elastestUser;

            String elastestPassword = getProperty("ePass");
            if (elastestPassword != null) {
                log.info("Elastest Password received: {}", elastestPassword);
                ePassword = elastestPassword;
                secureElastest = true;
            }

        }

        this.tormApiUrl = this.tormUrl
                + (this.tormUrl.endsWith("/") ? this.apiPath
                        : "/" + this.apiPath);

        if (secureElastest) {
            String split_url[] = tormUrl.split("//");
            secureTorm = split_url[0] + "//" + eUser + ":" + ePassword + "@"
                    + split_url[1];

            this.tormApiUrl = secureTorm
                    + (secureTorm.endsWith("/") ? this.apiPath
                            : "/" + this.apiPath);

        }

        log.info("Using URL {} to connect to {} TORM", tormApiUrl,
                secureElastest ? "secure" : "unsecure");

        this.restClient = new RestClient(this.tormApiUrl, eUser, ePassword,
                secureElastest);

        eusURL = System.getenv("ET_EUS_API");
        if (eusURL == null) {
            // Outside ElasTest
            ChromeDriverManager.chromedriver().setup();
            FirefoxDriverManager.firefoxdriver().setup();
        }
    }

    @AfterEach
    void teardown(TestInfo testInfo) throws IOException {
        String testName = testInfo.getTestMethod().get().getName();
        log.info("##### Finish test: {}", testName);
        if (driver != null) {
            if (eusURL != null) {
                driver.quit();
            } else {
                log.info("Browser console at the end of the test");
                LogEntries logEntries = driver.manage().logs().get(BROWSER);
                logEntries.forEach((entry) -> log.info("[{}] {} {}",
                        new Date(entry.getTimestamp()), entry.getLevel(),
                        entry.getMessage()));
            }
        }
    }

    /* ******************************** */
    /* *********** Navigate *********** */
    /* ******************************** */

    protected void navigateTo(WebDriver driver, String url) {
        log.info("Navigating to: {}", url);
        if (eusURL == null) {
            driver.manage().window().setSize(new Dimension(1024, 1024));
        }
        driver.manage().timeouts().implicitlyWait(10, SECONDS);
        driver.get(url);
    }

    protected void navigateToTorm(WebDriver driver) {
        log.info("Navigating to TORM");
        if (secureElastest) {
            navigateTo(driver, secureTorm);
        } else {
            navigateTo(driver, tormUrl);
        }
    }

    protected void navigateToRoot(WebDriver driver) {
        log.info("Navigating to Root Path (/)");
        driver.findElement(By.xpath("//*[@id='main_nav']/div/mat-toolbar/span"))
                .click();
    }

    protected void navigateToProjects(WebDriver driver) {
        log.info("Navigating to Projects Path (/project)");
        this.getElementById(driver, "nav_projects").click();
    }

    protected void navigateToHelpPage(WebDriver driver) {
        log.debug("Navigating to Help page");
        getElementById(driver, "help").click();
    }

    protected void navigateToTestEnginesPage(WebDriver driver) {
        log.debug("Navigating to Test Engines page");
        getElementById(driver, "nav_test_engines").click();
    }

    protected void navigateToTssPage(WebDriver driver) {
        log.debug("Navigating to Test Support Services page");
        if (secureElastest) {
            navigateTo(driver, secureTorm + TSS_PAGE_SUFFIX);
        } else {
            navigateTo(driver, tormUrl + TSS_PAGE_SUFFIX);
        }
    }

    protected void navigateToElementByIdXpath(WebDriver driver, String id,
            String xpath) {
        this.getElementByIdXpath(driver, id, xpath).click();
    }

    protected void navigateToElementByXpath(WebDriver driver, String xpath) {
        this.getElementByXpath(driver, xpath).click();
    }

    /* ******************************** */
    /* ************ Exists ************ */
    /* ******************************** */

    protected boolean elementExistsByIdXpath(WebDriver driver, String id,
            String xpath, int timeout, boolean withScroll) {
        return this.getElementByIdXpath(driver, id, xpath) != null;
    }

    protected boolean elementExistsByIdXpath(WebDriver driver, String id,
            String xpath) {
        return elementExistsByIdXpath(driver, id, xpath, 30, false);
    }

    protected boolean elementExistsByXpath(WebDriver driver, String xpath) {
        By elementAvailable = By.xpath(xpath);
        return driver.findElements(elementAvailable).size() != 0;
    }

    /* ********************************************* */
    /* **************** Get Element **************** */
    /* ********************************************* */

    /* ****************** Presence ****************** */

    protected ExpectedCondition<WebElement> getWaitFunctionByExpectedCondition(
            ExpectedConditionsEnum expectedCondition, By by) {
        switch (expectedCondition) {
        case ELEMENT_TO_BE_CLICKABLE:
            return elementToBeClickable(by);

        case VISIBILITY_OF_ELEMENT_LOCATED:
            return visibilityOfElementLocated(by);

        case PRESENCE_OF_ELEMENT_LOCATED:
        default:
            return presenceOfElementLocated(by);
        }
    }

    protected WebElement getElementById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id,
            int secondsTimeout, boolean withScroll) {
        String xpath = "//*[@id='" + id + "']";
        return this.getElementByXpath(driver, expectedCondition, xpath,
                secondsTimeout, withScroll);
    }

    protected WebElement getElementById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id,
            int secondsTimeout) {
        return this.getElementById(driver, expectedCondition, id,
                secondsTimeout, false);
    }

    protected WebElement getElementById(WebDriver driver, String id,
            int secondsTimeout) {
        return this.getElementById(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, id,
                secondsTimeout);
    }

    protected WebElement getElementById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id) {
        return this.getElementById(driver, expectedCondition, id, 30);
    }

    protected WebElement getElementById(WebDriver driver, String id) {
        return this.getElementById(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, id);
    }

    protected WebElement getElementById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id,
            boolean withScroll) {
        return this.getElementById(driver, expectedCondition, id, 30,
                withScroll);
    }

    protected WebElement getElementById(WebDriver driver, String id,
            boolean withScroll) {
        return this.getElementById(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, id,
                withScroll);
    }

    protected WebElement getElementByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name,
            int secondsTimeout, boolean withScroll) {
        String xpath = "//*[@name='" + name + "']";
        return this.getElementByXpath(driver, expectedCondition, xpath,
                secondsTimeout, withScroll);
    }

    protected WebElement getElementByName(WebDriver driver, String name) {
        return this.getElementByName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, name, 30,
                false);
    }

    protected WebElement getElementByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name) {
        return this.getElementByName(driver, expectedCondition, name, 30,
                false);
    }

    protected WebElement getElementByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name,
            boolean withScroll) {
        return this.getElementByName(driver, expectedCondition, name, 30,
                withScroll);
    }

    protected WebElement getElementByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name,
            int secondsTimeout) {
        return this.getElementByName(driver, expectedCondition, name,
                secondsTimeout, false);
    }

    protected WebElement getElementByIdXpath(WebDriver driver, String id,
            String xpath) {
        return this.getElementByIdXpath(driver, id, xpath, 30, false);
    }

    protected WebElement getElementByIdXpath(WebDriver driver, String id,
            String xpath, int secondsTimeout, boolean withScroll) {
        // Wait for id present
        getElementById(driver, id, secondsTimeout);

        WebElement element = driver.findElement(By.xpath(xpath));

        if (withScroll && element != null) {
            scrollToElement(element);
        }

        return element;
    }

    protected WebElement getElementByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath,
            int secondsTimeout, boolean withScroll) {
        try {
            Thread.sleep(500l);
        } catch (InterruptedException e) {
            log.error("Time Slot between actions interrupted.");
        }

        WebElement element = getElementsByXpath(driver, expectedCondition,
                xpath, secondsTimeout).get(0);
        if (withScroll) {
            scrollToElement(element);
        }
        return element;
    }

    protected WebElement getElementByXpath(WebDriver driver, String xpath,
            int secondsTimeout, boolean withScroll) {
        return getElementByXpath(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, xpath,
                secondsTimeout, withScroll);
    }

    protected WebElement getElementByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath,
            int secondsTimeout) {
        return this.getElementByXpath(driver, expectedCondition, xpath,
                secondsTimeout, false);
    }

    protected WebElement getElementByXpath(WebDriver driver, String xpath,
            int secondsTimeout) {
        return getElementByXpath(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, xpath,
                secondsTimeout);
    }

    protected WebElement getElementByXpath(WebDriver driver, String xpath) {
        return this.getElementByXpath(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, xpath, 30,
                false);
    }

    protected WebElement getElementByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath) {
        return this.getElementByXpath(driver, expectedCondition, xpath, 30,
                false);
    }

    protected WebElement getElementByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath,
            boolean withScroll) {
        return this.getElementByXpath(driver, expectedCondition, xpath, 30,
                withScroll);
    }

    protected void scrollToElement(WebElement element) {
        JavascriptExecutor jse2 = (JavascriptExecutor) driver;
        jse2.executeScript("arguments[0].scrollIntoView()", element);
    }

    protected List<WebElement> getElementsByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name,
            int secondsTimeout) {
        WebDriverWait waitService = new WebDriverWait(driver, secondsTimeout);
        waitService.until(getWaitFunctionByExpectedCondition(expectedCondition,
                By.name(name)));

        return driver.findElements(By.name(name));
    }

    protected List<WebElement> getElementsByName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String name) {
        return this.getElementsByName(driver, expectedCondition, name, 30);
    }

    protected List<WebElement> getElementsByName(WebDriver driver,
            String name) {
        return getElementsByName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, name);
    }

    protected List<WebElement> getElementsByTagName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String tagName,
            int secondsTimeout) {
        WebDriverWait waitService = new WebDriverWait(driver, secondsTimeout);
        waitService.until(getWaitFunctionByExpectedCondition(expectedCondition,
                By.tagName(tagName)));

        return driver.findElements(By.tagName(tagName));
    }

    protected List<WebElement> getElementsByTagName(WebDriver driver,
            String tagName, int secondsTimeout) {
        return getElementsByTagName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, tagName,
                secondsTimeout);
    }

    protected List<WebElement> getElementsByTagName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String tagName) {
        return this.getElementsByTagName(driver, expectedCondition, tagName,
                30);
    }

    protected List<WebElement> getElementsByTagName(WebDriver driver,
            String tagName) {
        return getElementsByTagName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, tagName);
    }

    protected List<WebElement> getElementsByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath) {
        return this.getElementsByXpath(driver, expectedCondition, xpath, 30);
    }

    protected List<WebElement> getElementsByXpath(WebDriver driver,
            String xpath) {
        return this.getElementsByXpath(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, xpath, 30);
    }

    protected List<WebElement> getElementsByXpath(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String xpath,
            int secondsTimeout) {
        WebDriverWait waitService = new WebDriverWait(driver, secondsTimeout);
        By elementAvailable = By.xpath(xpath);

        waitService.until(getWaitFunctionByExpectedCondition(expectedCondition,
                elementAvailable));

        return driver.findElements(elementAvailable);
    }

    protected List<WebElement> getElementsById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id,
            int secondsTimeout) {
        String xpath = "//*[@id='" + id + "']";
        return this.getElementsByXpath(driver, expectedCondition, xpath,
                secondsTimeout);
    }

    protected List<WebElement> getElementsById(WebDriver driver, String id,
            int secondsTimeout) {
        return getElementsById(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, id,
                secondsTimeout);
    }

    protected List<WebElement> getElementsById(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String id) {
        return this.getElementsById(driver, expectedCondition, id, 30);
    }

    protected List<WebElement> getElementsByClassName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String className,
            int secondsTimeout) {
        WebDriverWait waitService = new WebDriverWait(driver, secondsTimeout);
        waitService.until(getWaitFunctionByExpectedCondition(expectedCondition,
                By.className(className)));

        return driver.findElements(By.className(className));
    }

    protected List<WebElement> getElementsByClassName(WebDriver driver,
            String className, int secondsTimeout) {
        return getElementsByClassName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, className,
                secondsTimeout);
    }

    protected List<WebElement> getElementsByClassName(WebDriver driver,
            ExpectedConditionsEnum expectedCondition, String className) {
        return this.getElementsByClassName(driver, expectedCondition, className,
                30);
    }

    protected List<WebElement> getElementsByClassName(WebDriver driver,
            String className) {
        return getElementsByClassName(driver,
                ExpectedConditionsEnum.PRESENCE_OF_ELEMENT_LOCATED, className);
    }

    /* ***************************************************************** */
    /* **************************** Project **************************** */
    /* ***************************************************************** */

    protected void createNewETProject(WebDriver driver, String projectName) {
        log.info("Create project");
        getElementById(driver, "newProjectBtn").click();
        driver.findElement(By.name("project.name")).sendKeys(projectName);
        driver.findElement(By.xpath("//button[contains(string(), 'SAVE')]"))
                .click();
        sleep(1000);
    }

    protected void removeETProject(WebDriver driver, String projectName) {
        this.navigateToRoot(driver);
        // TODO
    }

    protected String getProjectsTableXpathFromProjectPage() {
        String id = "projects";
        String xpath = "//td-data-table[@id='" + id + "']";
        return xpath;
    }

    protected String getProjectXpathFromProjectPage(String projectName) {
        String xpath = getProjectsTableXpathFromProjectPage();
        xpath += "//*/td/div/div[text()='" + projectName + "']";

        return xpath;
    }

    protected void navigateToETProject(WebDriver driver, String projectName) {
        this.navigateToProjects(driver);
        log.info("Navigating to {} project", projectName);

        String xpath = getProjectXpathFromProjectPage(projectName);
        this.navigateToElementByXpath(driver, xpath);
    }

    protected boolean etProjectExists(WebDriver driver, String projectName) {
        navigateToProjects(driver);
        log.info("Checking if Project {} exists", projectName);
        String projectsTableXpath = getProjectsTableXpathFromProjectPage();

        // If exist PJ table (if there are some pj)
        if (this.driver.findElements(By.xpath(projectsTableXpath)).size() > 0) {
            String projectXpath = getProjectXpathFromProjectPage(projectName);
            boolean projectExist = this.elementExistsByXpath(driver,
                    projectXpath);
            String existStr = projectExist ? "already exist" : "does not exist";
            log.info("Project {} {} ", projectName, existStr);
            return projectExist;
        } else {
            return false;
        }
    }

    /* ***************************************************************** */
    /* ****************************** Sut ****************************** */
    /* ***************************************************************** */

    protected void createSutAndInsertCommonFields(WebDriver driver,
            String sutName, String desc, Map<String, String> params) {
        log.info("Creating new SuT");
        this.getElementById(driver, "newSutBtn").click();
        this.getElementsByName(driver, "sutName").get(0).sendKeys(sutName);
        sleep(1000);
        this.getElementsByName(driver, "sutDesc").get(0).sendKeys(desc);
        sleep(1000);
        // TODO params
    }

    /* ******************************** */
    /* *********** ElasTest *********** */
    /* ******************************** */

    protected void insertDeployedByElastestCommonFields(
            SutDeployedByElastestType type, String specification, String port,
            boolean https) {
        this.getElementsByName(driver, "managedSut").get(0).click();

        switch (type) {
        case IMAGE:
            this.getElementsByName(driver, "dockerImageRadio").get(0).click();
            break;
        case COMPOSE:
            this.getElementsByName(driver, "dockerComposeRadio").get(0).click();
            break;
        case COMMANDS:
        default:
            this.getElementsByName(driver, "commandsRadio").get(0).click();
        }

        this.getElementByName(driver, "specification").sendKeys(specification);

        if (https) {
            selectItem(driver, "https", "Select a protocol");
        }
        if (port != null && !"".equals(port)) {
            this.getElementsByName(driver, "port").get(0).sendKeys(port);
        }
    }

    protected void createNewSutDeployedByElastestWithCommands(WebDriver driver,
            String commands, SutCommandsOptionEnum option, String sutName,
            String desc, String image, String port, Map<String, String> params,
            boolean https) throws InterruptedException {
        this.createSutAndInsertCommonFields(driver, sutName, desc, params);
        insertDeployedByElastestCommonFields(SutDeployedByElastestType.COMMANDS,
                image, port, https);

        getElementById(driver, "commands").sendKeys(commands);

        switch (option) {
        case IN_DOCKER_COMPOSE:
            this.getElementsByName(driver, "defaultRadio").get(0).click();
            break;
        case IN_NEW_CONTAINER:
            this.getElementsByName(driver, "inNewContainerRadio").get(0)
                    .click();
            break;
        case DEFAULT:
        default:
            this.getElementsByName(driver, "inDockerComposeRadio").get(0)
                    .click();
        }

        // Save
        this.clickSaveSut(driver);
    }

    protected void createNewSutDeployedByElastestWithImage(WebDriver driver,
            String sutName, String desc, String image, String port,
            Map<String, String> params, boolean https)
            throws InterruptedException {
        this.createSutAndInsertCommonFields(driver, sutName, desc, params);

        insertDeployedByElastestCommonFields(SutDeployedByElastestType.IMAGE,
                image, port, https);

        // Save
        this.clickSaveSut(driver);
    }

    protected void createNewSutDeployedByElastestWithImage(WebDriver driver,
            String sutName, String desc, String image, String port,
            Map<String, String> params) throws InterruptedException {
        createNewSutDeployedByElastestWithImage(driver, sutName, desc, image,
                port, params, false);
    }

    protected void createNewSutDeployedByElastestWithCompose(WebDriver driver,
            String sutName, String desc, String compose, String mainServiceName,
            String port, Map<String, String> params, boolean https)
            throws InterruptedException {
        this.createSutAndInsertCommonFields(driver, sutName, desc, params);
        insertDeployedByElastestCommonFields(SutDeployedByElastestType.COMPOSE,
                compose, port, https);

        this.getElementsByName(driver, "mainService").get(0)
                .sendKeys(mainServiceName);

        // Save
        this.clickSaveSut(driver);
    }

    /* ******************************* */
    /* *********** Outside *********** */
    /* ******************************* */

    protected void createNewSutDeployedOutsideWithManualInstrumentation(
            WebDriver driver, String sutName, String desc, String ip,
            Map<String, String> params) throws InterruptedException {
        this.createSutAndInsertCommonFields(driver, sutName, desc, params);

        this.getElementsByName(driver, "deployedSut").get(0).click();
        this.getElementsByName(driver, "adminIns").get(0).click();
        this.getElementsByName(driver, "specification").get(0).sendKeys(ip);

        // Save
        this.clickSaveSut(driver);
    }

    protected void clickSaveSut(WebDriver driver) {
        sleep(2000);
        log.debug("Saving Sut");
        this.getElementByXpath(driver, "//button[contains(string(), 'SAVE')]")
                .click();
        sleep(1000);
    }

    protected String getSutsTableXpathFromProjectPage() {
        String id = "sutsTable";
        String xpath = "//td-data-table[@id='" + id + "']";
        return xpath;
    }

    protected String getSutXpathFromProjectPage(String sutName) {
        String xpath = getSutsTableXpathFromProjectPage();
        xpath += "//*/td/*/span[text()='" + sutName + "']";

        return xpath;
    }

    protected boolean etSutExistsIntoProject(WebDriver driver,
            String projectName, String sutName) {
        log.info("Checking if Sut {} exists into Project {}", sutName,
                projectName);
        this.navigateToETProject(driver, projectName);

        // Sleep for wait to load tables
        sleep(2500);

        String sutsTableXpath = getSutsTableXpathFromProjectPage();

        // If sut table exist
        if (this.driver.findElements(By.xpath(sutsTableXpath)).size() > 0) {
            String sutXpath = getSutXpathFromProjectPage(sutName);
            boolean sutExist = this.elementExistsByXpath(driver, sutXpath);
            String existStr = sutExist ? "already exist" : "does not exist";
            log.info("Sut {} {} into Project {}", sutName, existStr,
                    projectName);
            return sutExist;
        } else {
            log.warn("Sut table does not exist");
            return false;
        }
    }

    protected boolean etSutExistsIntoTLProject(WebDriver driver,
            String projectName, String sutName) {
        log.info("Checking if Sut {} exists into Project TL {}", sutName,
                projectName);

        // Sleep for wait to load tables
        sleep(1500);

        String sutsTableXpath = getSutsTableXpathFromProjectPage();

        // If sut table exist
        if (getElementByXpath(driver, sutsTableXpath) != null) {
            String sutXpath = getSutXpathFromProjectPage(sutName);
            boolean sutExist = this.elementExistsByXpath(driver, sutXpath);
            String existStr = sutExist ? "already exist" : "does not exist";
            log.info("Sut {} {} into TL Project {}", sutName, existStr,
                    projectName);
            return sutExist;
        } else {
            log.warn("Sut table does not exist");
            return false;
        }
    }

    protected void selectItem(WebDriver driver, String item,
            String selectDesc) {
        String sutSelectXpath = "//*[@placeholder='" + selectDesc + "']";

        this.getElementByXpath(driver, sutSelectXpath).click();

        if (item != null) {
            this.getElementByXpath(driver,
                    "//*/span[contains(string(), '" + item + "')]").click();
        }
    }

    public enum SutDeployedByElastestType {
        IMAGE("IMAGE"),

        COMPOSE("COMPOSE"),

        COMMANDS("COMMANDS");

        private String value;

        SutDeployedByElastestType(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static SutDeployedByElastestType fromValue(String text) {
            for (SutDeployedByElastestType b : SutDeployedByElastestType
                    .values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    public enum SutCommandsOptionEnum {
        DEFAULT("DEFAULT"),

        IN_NEW_CONTAINER("IN_NEW_CONTAINER"),

        IN_DOCKER_COMPOSE("IN_DOCKER_COMPOSE");

        private String value;

        SutCommandsOptionEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static SutCommandsOptionEnum fromValue(String text) {
            for (SutCommandsOptionEnum b : SutCommandsOptionEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    /* **************************************************************** */
    /* ***************************** TJob ***************************** */
    /* **************************************************************** */

    protected String getTJobsTableXpathFromProjectPage() {
        String id = "tJobs";
        String xpath = "//td-data-table[@id='" + id + "']";
        return xpath;
    }

    protected String getTJobXpathFromProjectPage(String tJobName) {
        String xpath = getTJobsTableXpathFromProjectPage();
        xpath += "//*/td/div/span[text()='" + tJobName + "']";

        return xpath;
    }

    protected boolean etTJobExistsIntoProject(WebDriver driver,
            String projectName, String tJobName) {
        log.info("Checking if TJob {} exists into Project {}", tJobName,
                projectName);
        this.navigateToETProject(driver, projectName);

        // Sleep for wait to load tables
        sleep(1500);

        String tJobsTableXpath = getTJobsTableXpathFromProjectPage();

        // If tjob table exist
        if (this.driver.findElements(By.xpath(tJobsTableXpath)).size() > 0) {
            String tJobXpath = getTJobXpathFromProjectPage(tJobName);
            boolean tJobExist = this.elementExistsByXpath(driver, tJobXpath);
            String existStr = tJobExist ? "already exist" : "does not exist";
            log.info("TJob {} {} into Project {}", tJobName, existStr,
                    projectName);
            return tJobExist;

        } else {
            return false;
        }
    }

    protected void navigateToTJobFromETProjectPage(WebDriver driver,
            String tJobName) {
        log.info("Navigating to {} tJob", tJobName);

        String xpath = getTJobXpathFromProjectPage(tJobName);
        this.navigateToElementByXpath(driver, xpath);
    }

    protected void navigateToTJobFromETProjectsPage(WebDriver driver,
            String projectName, String tJobName) {
        navigateToETProject(driver, projectName);
        navigateToTJobFromETProjectPage(driver, tJobName);
    }

    protected void createNewTJob(WebDriver driver, String tJobName,
            String testResultPath, String sutName, String dockerImage,
            boolean imageCommands, String commands,
            Map<String, String> parameters, Map<String, List<String>> tssMap,
            Map<String, List<String>> multiConfigurations,
            Integer maxExecutions) {
        log.info("Wait for the \"New TJob\" button");
        getElementById(driver, "newTJobBtn").click();

        log.info("Creating TJob...");
        getElementByName(driver,
                ExpectedConditionsEnum.VISIBILITY_OF_ELEMENT_LOCATED,
                "tJobName", 20).sendKeys(tJobName);

        if (testResultPath != null) {
            driver.findElement(By.name("resultsPath")).sendKeys(testResultPath);
        }

        if (maxExecutions == null) {
            maxExecutions = 5;
        }

        WebElement maxExecutionsInput = driver
                .findElement(By.name("maxExecutions"));
        // Select all to replace
        maxExecutionsInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        maxExecutionsInput.sendKeys(maxExecutions + "");

        // Select SuT
        String sutSelectXpath = "//mat-select/div/div/span[contains(string(), 'Select a SuT')]";
        this.getElementByXpath(driver, sutSelectXpath).click();

        if (sutName != null) {
            this.getElementByXpath(driver,
                    "//mat-option/span[contains(string(), '" + sutName + "')]")
                    .click();
        } else {
            this.getElementByXpath(driver,
                    "//mat-option/span[contains(string(), 'None')]").click();
        }

        // Image and commands
        driver.findElement(By.name("tJobImageName")).sendKeys(dockerImage);

        if (imageCommands) {
            driver.findElement(By.name("toggleCommands")).click();
        } else {
            driver.findElement(By.name("commands")).sendKeys(commands);
        }

        if (parameters != null) {
            addParameters(driver, parameters);
        }

        // MultiConfigurations
        if (multiConfigurations != null && multiConfigurations.size() > 0) {
            getElementById(driver, "multiConfigCheckbox-input")
                    .sendKeys(Keys.SPACE);

            int currentMultiConfig = 0;
            for (HashMap.Entry<String, List<String>> multiConfig : multiConfigurations
                    .entrySet()) {
                if (multiConfig.getValue().size() > 0) {
                    // Add new multi config
                    getElementById(driver, "addNewMultiConfiguration").click();

                    // Set name
                    getElementById(driver,
                            "multiConfigName" + currentMultiConfig)
                                    .sendKeys(multiConfig.getKey());

                    int currentValue = 0;
                    for (String value : multiConfig.getValue()) {
                        if (currentValue > 0) {
                            getElementById(driver, "addValueToMultiConfig"
                                    + currentMultiConfig).click();
                        }
                        // Set value
                        driver.findElement(By.id("multiConfig"
                                + currentMultiConfig + "Value" + currentValue))
                                .sendKeys(value);
                        currentValue++;

                    }

                    currentMultiConfig++;
                }
            }
        }

        // TSS
        if (tssMap != null) {
            // Tss Map has a TSS name in the key and subconfigs into value list
            // (only EUS at moment has a subconfig: webRtcStats)

            for (Entry<String, List<String>> tss : tssMap.entrySet()) {
                this.getElementById(driver, "service" + tss.getKey() + "-input",
                        true).sendKeys(Keys.SPACE);

                if (tss.getValue() != null && tss.getValue().size() > 0) {
                    // Expand TSS panel first
                    this.getElementById(driver,
                            "service" + tss.getKey() + "Expansion").click();

                    for (String subConfig : tss.getValue()) {
                        this.getElementById(driver,
                                "config" + subConfig + "Checkbox-input", true)
                                .sendKeys(Keys.SPACE);
                    }
                }
            }
        }

        // Save
        driver.findElement(By.xpath("//button[contains(string(), 'SAVE')]"))
                .click();
    }

    protected void addParameters(WebDriver driver,
            Map<String, String> parameters) {
        if (parameters != null) {
            int currentParam = 0;

            for (HashMap.Entry<String, String> param : parameters.entrySet()) {
                // Add new param
                getElementById(driver, "addNewParameter").click();

                // Set name
                getElementById(driver, "parameterName" + currentParam)
                        .sendKeys(param.getKey());

                // Set value
                getElementById(driver, "parameterValue" + currentParam)
                        .sendKeys(param.getValue());
                currentParam++;
            }
        }
    }

    protected void deleteAllParameters() {
        String deleteBtnsXpath = "//parameters-view//button[@mattooltip='Delete parameter']";
        List<WebElement> buttons = driver
                .findElements(By.xpath(deleteBtnsXpath));
        if (buttons != null) {
            for (WebElement btn : buttons) {
                btn.click();
            }
        }

    }

    protected void deleteTJob(WebDriver driver, String tJobName2) {
        log.info("Wait for the \"Delete TJob\" button");
        getElementById(driver, "deleteTJobBtn").click();
        log.info("Wait for the confirm delete TJob button");
        String confirmXpath = "//*[@id=\"mat-dialog-0\"]/td-confirm-dialog//td-dialog-actions/button[2]";
        getElementByXpath(driver, confirmXpath).click();
        sleep(1500);
    }

    protected void selectAllTJobExecutions() {
        String selectAllCheckboxXpath = "//*[@id=\"tJobExecsTable\"]//input[contains(@class,\"mat-checkbox-input\")][1]";
        getElementByXpath(driver, selectAllCheckboxXpath).sendKeys(Keys.SPACE);
    }

    protected void compareExecutions() throws Exception {
        WebElement compareExecsBtn = getElementById(driver, "compareExecsBtn");
        if (compareExecsBtn.isEnabled()) {
            compareExecsBtn.click();
        } else {
            throw new Exception(
                    "Error on click to 'Compare Executions' button: there are no executions selected or there is only one");
        }
    }

    protected void runTJobFromProjectPage(WebDriver driver, String tJobName,
            boolean fromParamsModal, Map<String, String> params) {
        log.info("Run TJob");

        // Wait for tJobs table
        getElementById(driver, "tJobs");

        String xpath = getTJobXpathFromProjectPage(tJobName);

        // Navigate to tjob
        driver.findElement(By.xpath(xpath)).click();
        sleep(2200);

        this.getElementByXpath(driver, "//button[@title='Run TJob']").click();

        if (fromParamsModal) {
            if (params != null && params.size() > 0) {
                getElementByXpath(driver, "//parameters-view");
                deleteAllParameters();
                addParameters(driver, params);
            }
            // Run TJob btn into Modal
            this.getElementByXpath(driver, "//button[@id='runTJobModalBtn']")
                    .click();
        }
    }

    protected void runTJobFromProjectPage(WebDriver driver, String tJobName) {
        runTJobFromProjectPage(driver, tJobName, false, null);
    }

    /* ***************************************************************** */
    /* *************************** TJob Exec *************************** */
    /* ***************************************************************** */

    protected void checkFinishTJobExec(WebDriver driver, int timeout,
            String expectedResult, boolean waitForMetrics) {

        log.info("Wait for the execution page to show");
        if (waitForMetrics) {
            log.info("Wait for metrics");
            getElementsByClassName(driver, "tick", timeout);
        }

        log.info("Wait for Execution ends");
        WebDriverWait wait = new WebDriverWait(driver, timeout);
        wait.until(invisibilityOfElementLocated(By.id("runningSpinner")));

        log.info("Check finish Execution status. Expected result {}",
                expectedResult);
        wait.until(textToBePresentInElementLocated(By.id("resultMsgText"),
                expectedResult));
    }

    protected void deleteJob(WebDriver driver, String jobName) {
        // http://172.17.0.2:8080/job/FJob1/doDelete
    }

    protected void openTJobExecMonitoringConfigModal(WebDriver driver) {
        getElementById(driver, "openMonitoringConfigBtn").click();
    }

    // Test Cases
    protected WebElement expandExecTestSuite(WebDriver driver, int position) {
        // Real position starts from 1 instead of 0
        String suiteExpansionXpath = "//*[@id=\"testSuitesView\"]/etm-test-suites-view//td-expansion-panel["
                + position + "]";
        log.debug("Expanding Test Suite in position {}", position);

        WebElement suiteExpansionElement = getElementByXpath(driver,
                suiteExpansionXpath);
        suiteExpansionElement.click();
        return suiteExpansionElement;
    }

    protected void navigateToExecTestCase(WebDriver driver, int suitePosition,
            int casePosition, boolean withSuiteExpand) {
        if (withSuiteExpand) {
            expandExecTestSuite(driver, suitePosition);
        }

        // Real position starts from 1 instead of 0
        String xpath = "//*[@id=\"testSuitesView\"]/etm-test-suites-view//td-expansion-panel["
                + suitePosition + "]//td-data-table//tr[" + casePosition
                + "]/td[3]/div";

        log.debug(
                "Navigating to Test Case in position {} of Test Suite in position {}",
                casePosition, suitePosition);

        getElementByXpath(driver, xpath).click();
    }

    protected void waitForLogComparatorCard() {
        getElementById(driver, "logComparator");
    }

    protected void startLogComparison() {
        getElementById(driver, "lcStartComparisonBtn").click();
    }

    protected boolean isLogComparatorTableNotEmpty() {
        return getElementsByXpath(driver,
                "//*[@id=\"logComparator\"]//table/tbody/tr").size() > 0;
    }

    protected void assertLogComparatorTableNotEmpty() {
        boolean comparisonNotEmpty = isLogComparatorTableNotEmpty();
        assertTrue(comparisonNotEmpty);
    }

    protected void assertLogComparatorAllCombinationsTablesNotEmpty() {
        // Default (complete view with no timestamp comparison)
        startLogComparison();
        assertLogComparatorTableNotEmpty();

        clickToCompleteComparison();
        assertLogComparatorTableNotEmpty();

        clickToTimeDiffComparison();
        assertLogComparatorTableNotEmpty();

        // Tests Logs view with time diff comparison
        clickToTestsLogsView();
        assertLogComparatorTableNotEmpty();

        clickToCompleteComparison();
        assertLogComparatorTableNotEmpty();

        clickToNoTimestampComparison();
        assertLogComparatorTableNotEmpty();

        // Failed tests view with no timestamp comparison
        clickToFailedTestsView();
        assertLogComparatorTableNotEmpty();

        clickToCompleteComparison();
        assertLogComparatorTableNotEmpty();

        clickToTimeDiffComparison();
        assertLogComparatorTableNotEmpty();
    }

    protected void clickToCompleteComparison() {
        getElementById(driver, "lcCompleteComparisonBtn").click();
    }

    protected void clickToNoTimestampComparison() {
        getElementById(driver, "lcNoTimeComparisonBtn").click();
    }

    protected void clickToTimeDiffComparison() {
        getElementById(driver, "lcTimeDiffComparisonBtn").click();
    }

    protected void clickToCompleteView() {
        getElementById(driver, "lcCompleteViewBtn").click();
    }

    protected void clickToTestsLogsView() {
        getElementById(driver, "lcTestsViewBtn").click();
    }

    protected void clickToFailedTestsView() {
        getElementById(driver, "lcFailedViewBtn").click();
    }

    /* ****************************************************************** */
    /* ***************************** OTHERS ***************************** */
    /* ****************************************************************** */

    public void setupTestBrowser(TestInfo testInfo, BrowserType browser,
            WebDriver driver) throws MalformedURLException {
        setupTestBrowser(testInfo, browser, "latest", driver);
    }

    public void setupTestBrowser(TestInfo testInfo, BrowserType browser,
            String browserVersion, WebDriver driver)
            throws MalformedURLException {
        String testName = testInfo.getTestMethod().get().getName();

        log.info("EUS hub URL: {}", eusURL);
        if (eusURL != null) {
            DesiredCapabilities caps = new DesiredCapabilities();
            if (browser.equals(BrowserType.CHROME)) {
                DesiredCapabilities.chrome();
                caps.setBrowserName("chrome");
            } else {
                DesiredCapabilities.firefox();
                caps.setBrowserName("firefox");
            }
            caps.setCapability("testName", testName);
            if (!browserVersion.equals(BROWSER_VERSION_LATEST)) {
                log.info("Use this browser version: {}", browserVersion);
                caps.setVersion(browserVersion);
            }
            this.driver = new RemoteWebDriver(new URL(eusURL), caps);
            driver = this.driver;
        } else {
            this.driver = driver;
        }
    }

    protected void startTestSupportService(WebDriver driver,
            String supportServiceLabel) {
        WebElement tssNavButton = getElementById(driver,
                "nav_support_services");
        if (!tssNavButton.isDisplayed()) {
            driver.findElement(By.id("main_menu")).click();
        }
        tssNavButton.click();

        log.info("Select {}", supportServiceLabel);
        selectItem(driver, supportServiceLabel, "Select a Service");

        log.info("Create and wait for instance");
        getElementById(driver, "create_instance").click();

        log.info("Navigate for instance view");
        getElementByXpath(driver, "//button[@title='View Service Detail']", 240)
                .click();
    }

    protected void deleteTSSInstance(WebDriver driver) {
        WebElement tssId = getElementByXpath(driver,
                "//*[@id=\"tss-instances\"]/div/table/tbody/tr[1]/td[1]/div/span");
        log.info("TSS session id: {}", tssId.getText());
        By deleteServices = By.id("deleteService-" + tssId.getText().trim());
        driver.findElement(deleteServices).click();
        log.debug("Wait for Test Support Service to be stopped");
        WebDriverWait waitEnd = new WebDriverWait(driver, 120);
        waitEnd.until(invisibilityOfElementLocated(deleteServices));
    }

    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    public By byDom(String domExpression) {
        final Object o = ((JavascriptExecutor) driver)
                .executeScript("return " + domExpression + ";");

        if (o instanceof WebElement) {
            return new By() {
                @Override
                public List<WebElement> findElements(
                        SearchContext searchContext) {
                    return new ArrayList<WebElement>() {
                        private static final long serialVersionUID = 1L;

                        {
                            add((WebElement) o);
                        }
                    };
                }
            };
        } else {
            return null;
        }
    }

    public enum ExpectedConditionsEnum {
        ELEMENT_TO_BE_CLICKABLE("elementToBeClickable"),

        PRESENCE_OF_ELEMENT_LOCATED("presenceOfElementLocated"),

        VISIBILITY_OF_ELEMENT_LOCATED("visibilityOfElementLocated");

        private String value;

        ExpectedConditionsEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static ExpectedConditionsEnum fromValue(String text) {
            for (ExpectedConditionsEnum b : ExpectedConditionsEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }
}
