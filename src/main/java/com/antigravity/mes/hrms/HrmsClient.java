package com.antigravity.mes.hrms;

import com.antigravity.mes.hrms.dto.HrmsDailyAttendance;
import com.antigravity.mes.hrms.dto.HrmsEmployeeCreate;
import com.antigravity.mes.hrms.dto.HrmsEmployeeOut;
import com.antigravity.mes.hrms.dto.HrmsLiveStatus;
import com.antigravity.mes.model.Factory;
import com.antigravity.mes.repository.FactoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.springframework.web.client.ResourceAccessException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client for the HRMS biometric attendance portal(s). Each factory can point at either its
 * own external HRMS or the one this app's operator provides - see the "HRMS Configuration"
 * section of Factory Management. The bean's own {@code app.hrms.*} env vars remain the
 * fallback "default" connection, used whenever a factory has HRMS disabled or no config
 * saved - this is deliberate so a factory that never touches the new per-factory config
 * (e.g. Default Factory today) behaves EXACTLY as it always has, with zero risk of the new
 * per-factory wiring changing its existing, working behaviour.
 *
 * No call is made unless a connection is enabled AND has real credentials; every public
 * method fails fast with a clear message otherwise, so a missing/misconfigured integration
 * never looks like a silent success.
 */
@Component
public class HrmsClient {

    private static final Logger log = LoggerFactory.getLogger(HrmsClient.class);

    private final Connection defaultConnection;
    private final Map<Long, Connection> factoryConnections = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FactoryRepository factoryRepository;

    public HrmsClient(
            @Value("${app.hrms.base-url}") String baseUrl,
            @Value("${app.hrms.username}") String username,
            @Value("${app.hrms.password}") String password,
            @Value("${app.hrms.enabled}") boolean enabled) {
        this.defaultConnection = new Connection(baseUrl, username, password, enabled);
    }

    // ------------------------------------------------------------------
    // Factory-aware overloads. A null factoryId (or a factory with no
    // usable per-factory config) always resolves to the default env-var
    // connection - the exact behaviour every caller already had.
    // ------------------------------------------------------------------

    public boolean isEnabled() {
        return isEnabled(null);
    }

    public boolean isEnabled(Long factoryId) {
        return resolveConnection(factoryId).isUsable();
    }

    public List<HrmsEmployeeOut> listEmployees(boolean activeOnly) {
        return listEmployees(null, activeOnly);
    }

    public List<HrmsEmployeeOut> listEmployees(Long factoryId, boolean activeOnly) {
        Connection c = resolveConnection(factoryId);
        c.requireEnabled();
        return c.withRetry(() -> c.restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/employees").queryParam("active_only", activeOnly).build())
                .header("Authorization", "Bearer " + c.getToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    public HrmsEmployeeOut createEmployee(HrmsEmployeeCreate request) {
        return createEmployee(null, request);
    }

    public HrmsEmployeeOut createEmployee(Long factoryId, HrmsEmployeeCreate request) {
        Connection c = resolveConnection(factoryId);
        c.requireEnabled();
        return c.restClient.post()
                .uri("/api/employees")
                .header("Authorization", "Bearer " + c.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HrmsEmployeeOut.class);
    }

    public HrmsLiveStatus getLiveStatus() {
        return getLiveStatus(null);
    }

    public HrmsLiveStatus getLiveStatus(Long factoryId) {
        Connection c = resolveConnection(factoryId);
        c.requireEnabled();
        return c.withRetry(() -> c.restClient.get()
                .uri("/api/attendance/live")
                .header("Authorization", "Bearer " + c.getToken())
                .retrieve()
                .body(HrmsLiveStatus.class));
    }

    public List<HrmsDailyAttendance> getDailyAttendance(LocalDate date) {
        return getDailyAttendance(null, date);
    }

    public List<HrmsDailyAttendance> getDailyAttendance(Long factoryId, LocalDate date) {
        Connection c = resolveConnection(factoryId);
        c.requireEnabled();
        return c.withRetry(() -> c.restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/attendance/daily");
                    if (date != null) {
                        uriBuilder.queryParam("report_date", date.toString());
                    }
                    return uriBuilder.build();
                })
                .header("Authorization", "Bearer " + c.getToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    /** Uploads a photo for face-recognition enrollment - the only biometric modality enrollable via API. */
    public void enrollFace(int hrmsEmployeeId, byte[] fileBytes, String filename) {
        enrollFace(null, hrmsEmployeeId, fileBytes, filename);
    }

    public void enrollFace(Long factoryId, int hrmsEmployeeId, byte[] fileBytes, String filename) {
        Connection c = resolveConnection(factoryId);
        c.requireEnabled();
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        c.restClient.post()
                .uri("/api/ai/employees/{id}/face", hrmsEmployeeId)
                .header("Authorization", "Bearer " + c.getToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Verifies a candidate baseUrl/username/password actually works, without touching
     * any bean-managed connection or its cached token - used by the Factory Management
     * "Test Connection" button before an admin saves an HRMS config, so a typo or wrong
     * password is caught immediately instead of surfacing later as a silent sync
     * failure. Builds a throwaway RestClient against the given URL and exercises both
     * login and one real read (employee count), since a login can succeed while the
     * account still lacks read access.
     */
    public HrmsConnectionTestResult testConnection(String baseUrl, String testUsername, String testPassword) {
        if (isBlank(baseUrl) || isBlank(testUsername) || isBlank(testPassword)) {
            return new HrmsConnectionTestResult(false, "Base URL, username and password are all required", 0);
        }
        RestClient testClient = buildRestClient(baseUrl);

        try {
            String form = "grant_type=password&username=" + urlEncode(testUsername) + "&password=" + urlEncode(testPassword);
            Map<String, Object> loginResponse = testClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            Object token = loginResponse == null ? null : loginResponse.getOrDefault("access_token", loginResponse.get("token"));
            if (token == null) {
                return new HrmsConnectionTestResult(false, "Login succeeded but returned no access token - unexpected HRMS response shape", 0);
            }

            List<HrmsEmployeeOut> employees = testClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/employees").queryParam("active_only", true).build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            int count = employees == null ? 0 : employees.size();
            return new HrmsConnectionTestResult(true, "Connected - fetched " + count + " active employee(s)", count);
        } catch (Exception e) {
            return new HrmsConnectionTestResult(false, "Connection failed: " + e.getMessage(), 0);
        }
    }

    public record HrmsConnectionTestResult(boolean success, String message, int employeeCount) {}

    /**
     * Resolves which HRMS connection a call should use. A null factoryId, a factory that
     * can't be found, one with HRMS turned off, or one with no (or malformed/incomplete)
     * saved config all fall back to {@link #defaultConnection} - the same env-var-backed
     * connection every caller used before per-factory config existed. Only a factory with
     * a genuinely complete saved config gets its own connection, built and cached the
     * first time it's needed and rebuilt automatically if the saved config changes.
     */
    private Connection resolveConnection(Long factoryId) {
        if (factoryId == null) {
            return defaultConnection;
        }
        Optional<Factory> factoryOpt = factoryRepository.findById(factoryId);
        if (factoryOpt.isEmpty()) {
            return defaultConnection;
        }
        Factory factory = factoryOpt.get();
        if (!factory.isHrmsEnabled()) {
            return defaultConnection;
        }
        String configJson = factory.getHrmsConfigJson();
        if (isBlank(configJson)) {
            return defaultConnection;
        }

        Connection cached = factoryConnections.get(factoryId);
        if (cached != null && configJson.equals(cached.sourceConfigJson)) {
            return cached;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsed = objectMapper.readValue(configJson, Map.class);
            String baseUrl = parsed.get("baseUrl");
            String username = parsed.get("username");
            String password = parsed.get("password");
            if (isBlank(baseUrl) || isBlank(username) || isBlank(password)) {
                log.warn("Factory {} has an incomplete HRMS config - falling back to the default HRMS connection", factoryId);
                return defaultConnection;
            }
            Connection fresh = new Connection(baseUrl, username, password, true);
            fresh.sourceConfigJson = configJson;
            factoryConnections.put(factoryId, fresh);
            return fresh;
        } catch (Exception e) {
            log.warn("Factory {} has an unparsable HRMS config - falling back to the default HRMS connection: {}", factoryId, e.getMessage());
            return defaultConnection;
        }
    }

    private static RestClient buildRestClient(String baseUrl) {
        // hrms.thegttech.com's DNS returns both a public and a private-range IP for the
        // same hostname. The JDK's default java.net.http.HttpClient (Spring RestClient's
        // default backend) mishandles retrying across mixed-reachability addresses like
        // that (ClosedChannelException); plain HttpURLConnection only tries the first
        // address DNS returns, which hangs until timeout if that's the unreachable one.
        // HappyEyeballsSslSocketFactory tries every resolved address itself (like curl
        // does) and uses whichever one actually connects.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws java.io.IOException {
                if (connection instanceof HttpsURLConnection https) {
                    https.setSSLSocketFactory(new HappyEyeballsSslSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault()));
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(20_000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** One HRMS connection's client, credentials, enabled flag and cached bearer token. */
    private static final class Connection {
        final RestClient restClient;
        final String username;
        final String password;
        final boolean enabled;

        /** Set only on a factory-specific connection - lets resolveConnection() detect a
         * saved config change and rebuild rather than serving a stale cached connection. */
        String sourceConfigJson;

        private volatile String cachedToken;
        private volatile Instant tokenExpiry = Instant.EPOCH;

        Connection(String baseUrl, String username, String password, boolean enabled) {
            this.restClient = buildRestClient(baseUrl);
            this.username = username;
            this.password = password;
            this.enabled = enabled;
        }

        /** Credentials are checked here, not just the flag: with the flag on but a blank
         * username/password (e.g. an external config file that silently wasn't loaded) the
         * client would otherwise send empty credentials and surface HRMS's opaque 401 as if
         * the account were wrong. Treating that as "not configured" points at the real cause. */
        boolean isUsable() {
            return enabled && !isBlank(username) && !isBlank(password);
        }

        void requireEnabled() {
            if (!enabled) {
                throw new IllegalStateException(
                        "HRMS integration is disabled - set HRMS_ENABLED=true and HRMS_USERNAME/HRMS_PASSWORD to enable it");
            }
            if (isBlank(username) || isBlank(password)) {
                throw new IllegalStateException(
                        "HRMS is enabled but has no credentials - app.hrms.username/password are blank. "
                                + "Check that the external config file is actually being loaded (a file named "
                                + "application-<name>.properties is read as a PROFILE, not as plain extra config).");
            }
        }

        /** Lazily logs in and caches the bearer token; re-authenticates once the cache window elapses. */
        synchronized String getToken() {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                return cachedToken;
            }
            String form = "grant_type=password"
                    + "&username=" + urlEncode(username)
                    + "&password=" + urlEncode(password);

            Map<String, Object> response = withRetry(() -> restClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {}));

            Object token = response == null ? null : response.getOrDefault("access_token", response.get("token"));
            if (token == null) {
                throw new IllegalStateException("HRMS login did not return a recognizable access token");
            }
            cachedToken = token.toString();
            tokenExpiry = Instant.now().plus(Duration.ofMinutes(20));
            return cachedToken;
        }

        /** A single retry after a short pause - HRMS's connection occasionally has a brief hiccup
         * (DNS/network blip) that a second attempt clears up, without making the caller wait through
         * a second full timeout cycle on a truly dead connection. */
        <T> T withRetry(Supplier<T> call) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                return call.get();
            }
        }
    }
}
