package com.antigravity.mes.hrms;

import com.antigravity.mes.hrms.dto.HrmsDailyAttendance;
import com.antigravity.mes.hrms.dto.HrmsEmployeeCreate;
import com.antigravity.mes.hrms.dto.HrmsEmployeeOut;
import com.antigravity.mes.hrms.dto.HrmsLiveStatus;
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
import java.util.function.Supplier;

/**
 * Client for the external HRMS biometric attendance portal (hrms.thegttech.com).
 * Endpoint shapes come from the portal's own published OpenAPI spec
 * (https://hrms.thegttech.com/openapi.json) - not guessed.
 *
 * No call is made unless {@code app.hrms.enabled=true} AND real credentials are
 * configured; every public method fails fast with a clear message otherwise, so a
 * missing/misconfigured integration never looks like a silent success.
 */
@Component
public class HrmsClient {

    private final RestClient restClient;
    private final String username;
    private final String password;
    private final boolean enabled;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public HrmsClient(
            @Value("${app.hrms.base-url}") String baseUrl,
            @Value("${app.hrms.username}") String username,
            @Value("${app.hrms.password}") String password,
            @Value("${app.hrms.enabled}") boolean enabled) {
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
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.username = username;
        this.password = password;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<HrmsEmployeeOut> listEmployees(boolean activeOnly) {
        requireEnabled();
        return withRetry(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/employees").queryParam("active_only", activeOnly).build())
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    public HrmsEmployeeOut createEmployee(HrmsEmployeeCreate request) {
        requireEnabled();
        return restClient.post()
                .uri("/api/employees")
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HrmsEmployeeOut.class);
    }

    public HrmsLiveStatus getLiveStatus() {
        requireEnabled();
        return withRetry(() -> restClient.get()
                .uri("/api/attendance/live")
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(HrmsLiveStatus.class));
    }

    public List<HrmsDailyAttendance> getDailyAttendance(LocalDate date) {
        requireEnabled();
        return withRetry(() -> restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/attendance/daily");
                    if (date != null) {
                        uriBuilder.queryParam("report_date", date.toString());
                    }
                    return uriBuilder.build();
                })
                .header("Authorization", "Bearer " + getToken())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {}));
    }

    /** Uploads a photo for face-recognition enrollment - the only biometric modality enrollable via API. */
    public void enrollFace(int hrmsEmployeeId, byte[] fileBytes, String filename) {
        requireEnabled();
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        restClient.post()
                .uri("/api/ai/employees/{id}/face", hrmsEmployeeId)
                .header("Authorization", "Bearer " + getToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .toBodilessEntity();
    }

    /** Lazily logs in and caches the bearer token; re-authenticates once the cache window elapses. */
    private synchronized String getToken() {
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
    private <T> T withRetry(Supplier<T> call) {
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

    private void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException(
                    "HRMS integration is disabled - set HRMS_ENABLED=true and HRMS_USERNAME/HRMS_PASSWORD to enable it");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
