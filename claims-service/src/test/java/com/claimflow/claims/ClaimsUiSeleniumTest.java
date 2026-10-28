package com.claimflow.claims;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimflow.events.Topics;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end UI tests in a real (headless) Chrome: both services, Kafka in between, driven
 * through the web console like a claims processor would use it. Run with: mvn verify -Pe2e
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {Topics.CLAIMS_SUBMITTED, Topics.CLAIMS_ADJUDICATED, Topics.CLAIMS_SUBMITTED_DLT})
class ClaimsUiSeleniumTest {

    private static WebDriver driver;
    private WebDriverWait wait;

    @LocalServerPort int port;

    @BeforeAll
    static void openBrowser() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1400,1000");
        String chromeBinary = System.getenv("CHROME_BIN");
        if (chromeBinary != null && !chromeBinary.isBlank()) {
            options.setBinary(chromeBinary);
        }
        driver = new ChromeDriver(options);
    }

    @AfterAll
    static void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @BeforeEach
    void openConsole() {
        AdjudicationRunner.ensureStarted();
        // The claims table re-renders every 1.5 s, so a found element can go stale: just retry.
        wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.ignoring(StaleElementReferenceException.class);
        driver.get("http://localhost:" + port + "/");
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("#member option"), 0));
    }

    /** Reads the selected row's cells in one JavaScript call, so a re-render can't split the read. */
    @SuppressWarnings("unchecked")
    private Map<String, String> selectedRow() {
        Object row = ((JavascriptExecutor) driver).executeScript(
                "const r = document.querySelector('#claims tbody tr.selected');"
                        + "return r ? {id: r.dataset.claimId, status: r.querySelector('.status').textContent,"
                        + " planPays: r.querySelector('.plan-pays').textContent,"
                        + " memberPays: r.querySelector('.member-pays').textContent} : null;");
        return row == null ? Map.of() : (Map<String, String>) row;
    }

    private String selectedClaimId() {
        return selectedRow().getOrDefault("id", "");
    }

    private String previous = "";

    private void submit(String member, String service, String amount) {
        previous = selectedClaimId();
        new Select(driver.findElement(By.id("member"))).selectByValue(member);
        new Select(driver.findElement(By.id("serviceType"))).selectByValue(service);
        WebElement amountBox = driver.findElement(By.id("amount"));
        amountBox.clear();
        amountBox.sendKeys(amount);
        WebElement provider = driver.findElement(By.id("provider"));
        provider.clear();
        provider.sendKeys("Apollo Clinic");
        driver.findElement(By.id("submit")).click();
    }

    /** The new claim becomes the selected row; wait until it is decided (status leaves SUBMITTED). */
    private Map<String, String> waitForDecision() {
        return wait.until(d -> {
            Map<String, String> row = selectedRow();
            boolean decided = !row.isEmpty() && !row.get("id").equals(previous) && !"SUBMITTED".equals(row.get("status"));
            return decided ? row : null;
        });
    }

    @Test
    void approvedClaimShowsWhatThePlanPays() {
        submit("M-1001", "SPECIALIST", "2000");
        Map<String, String> row = waitForDecision();
        assertThat(row.get("status")).isEqualTo("APPROVED");
        // Gold plan, deductible already met, 10% coinsurance.
        assertThat(row.get("planPays")).isEqualTo("$1,800.00");
        assertThat(row.get("memberPays")).isEqualTo("$200.00");
    }

    @Test
    void claimForAMemberWhoseCoverageEndedIsDenied() {
        submit("M-1004", "LAB", "300");
        assertThat(waitForDecision().get("status")).isEqualTo("DENIED");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("detail"), "COVERAGE_INACTIVE"));
    }

    @Test
    void historyPanelShowsEachStep() {
        submit("M-1005", "LAB", "120");
        waitForDecision();
        String history = wait.until(d -> {
            String text = d.findElement(By.id("detail")).getText();
            return text.contains("SENT_FOR_ADJUDICATION") && text.contains("APPROVED") ? text : null;
        });
        assertThat(history).contains("SUBMITTED").contains("SENT_FOR_ADJUDICATION").contains("APPROVED");
    }

    @Test
    void missingAmountShowsTheServersValidationMessage() {
        new Select(driver.findElement(By.id("member"))).selectByValue("M-1002");
        driver.findElement(By.id("amount")).clear();
        driver.findElement(By.id("provider")).sendKeys("City Lab");
        driver.findElement(By.id("submit")).click();
        WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("error")));
        assertThat(error.getText()).isEqualTo("billedAmount must not be null");
    }
}
