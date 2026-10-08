package com.onrender.zipai.config;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Temporary, key-free outbound connectivity check; disabled by default. */
@Component
@ConditionalOnProperty(name = "zipai.vworld.diagnostics.enabled", havingValue = "true")
public class VworldConnectionDiagnosticRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(VworldConnectionDiagnosticRunner.class);
    private static final URI TARGET = URI.create(
        "https://api.vworld.kr/req/search?service=search&request=search&version=2.0&format=json");

    @Override
    public void run(ApplicationArguments args) {
        log.info("[VWORLD-DIAG] START key-free HTTPS check; PARAM_REQUIRED is an expected response");
        checkJdkClient();
        checkUrlConnection();
        checkCurl();
        log.info("[VWORLD-DIAG] END; disable ZIPAI_VWORLD_DIAGNOSTICS_ENABLED after collecting these logs");
    }

    private void checkCurl() {
        long started = System.nanoTime();
        Path output = null;
        Path headers = null;
        Process process = null;
        try {
            output = Files.createTempFile("vworld-diag-", ".txt");
            headers = Files.createTempFile("vworld-headers-", ".txt");
            process = new ProcessBuilder("curl", "--silent", "--show-error",
                "--http1.1", "--connect-timeout", "5", "--max-time", "10",
                "--max-filesize", "8192", "--dump-header", headers.toString(), "--write-out", "\nVWORLD_HTTP_STATUS=%{http_code}",
                TARGET.toASCIIString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("[VWORLD-DIAG] transport=CURL_HTTP_1_1 timeout=true elapsedMs={}", elapsed(started));
                return;
            }
            String response = Files.readString(output, StandardCharsets.UTF_8);
            String status = "000";
            int marker = response.lastIndexOf("VWORLD_HTTP_STATUS=");
            if (marker >= 0) {
                String value = response.substring(marker + "VWORLD_HTTP_STATUS=".length()).trim();
                if (value.matches("[0-9]{3}")) status = value;
            }
            // Only the key-free fixed diagnostic target is used here.
            log.info("[VWORLD-DIAG] transport=CURL_HTTP_1_1 exitCode={} httpStatus={} expectedMissingKey={} elapsedMs={}",
                process.exitValue(), status, response.contains("PARAM_REQUIRED"), elapsed(started));
            log.info("[VWORLD-DIAG] transport=CURL_HTTP_1_1 detail={}", safeDetail(response));
            for (String line : Files.readAllLines(headers, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon > 0 && allowedHeader(line.substring(0, colon))) {
                    log.info("[VWORLD-DIAG] transport=CURL_HTTP_1_1 header={}", safeDetail(line));
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure("CURL_HTTP_1_1", error, started);
        } catch (Exception error) {
            failure("CURL_HTTP_1_1", error, started);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (headers != null) {
                try { Files.deleteIfExists(headers); }
                catch (Exception ignored) { /* Key-free diagnostic temporary file. */ }
            }
            if (output != null) {
                try { Files.deleteIfExists(output); }
                catch (Exception ignored) { /* Temporary OS file; contains no API key. */ }
            }
        }
    }

    private void checkJdkClient() {
        long started = System.nanoTime();
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(TARGET)
                .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            report("JDK_HTTP_1_1", response.statusCode(), response.body(), started);
            reportHeaders("JDK_HTTP_1_1", response.headers().map());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure("JDK_HTTP_1_1", error, started);
        } catch (Exception error) {
            failure("JDK_HTTP_1_1", error, started);
        }
    }

    private void checkUrlConnection() {
        long started = System.nanoTime();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) TARGET.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            var stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (stream != null) {
                try (var input = stream) {
                    body = new String(input.readNBytes(2048), StandardCharsets.UTF_8);
                }
            }
            report("URL_CONNECTION", status, body, started);
            reportHeaders("URL_CONNECTION", connection.getHeaderFields());
        } catch (Exception error) {
            failure("URL_CONNECTION", error, started);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void report(String transport, int status, String body, long started) {
        // A short redacted body from the fixed key-free diagnostic target only.
        log.info("[VWORLD-DIAG] transport={} httpStatus={} expectedMissingKey={} elapsedMs={}",
            transport, status, body.contains("PARAM_REQUIRED"), elapsed(started));
        log.info("[VWORLD-DIAG] transport={} body={}", transport, safeDetail(body));
    }

    private void failure(String transport, Exception error, long started) {
        Throwable root = error;
        for (int depth = 0; depth < 10 && root.getCause() != null && root.getCause() != root; depth++) {
            root = root.getCause();
        }
        log.warn("[VWORLD-DIAG] transport={} failure={} rootCause={} elapsedMs={}",
            transport, error.getClass().getSimpleName(), root.getClass().getSimpleName(), elapsed(started));
        log.warn("[VWORLD-DIAG] transport={} failureDetail={}", transport, safeDetail(root.getMessage()));
    }

    private static boolean allowedHeader(String name) {
        return name != null && List.of("server", "via", "content-type", "content-length")
            .contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private void reportHeaders(String transport, Map<String, List<String>> headers) {
        headers.forEach((name, values) -> {
            if (allowedHeader(name)) {
                log.info("[VWORLD-DIAG] transport={} header={} value={}",
                    transport, name, safeDetail(String.join(", ", values)));
            }
        });
    }

    static String safeDetail(String value) {
        if (value == null || value.isBlank()) return "(empty)";
        String cleaned = value
            .replaceAll("(?im)^.*(?:set-cookie|cookie|authorization)\\s*:.*$", "[redacted header]")
            .replaceAll("(?i)([?&](?:key|token|apikey|api_key)=)[^&\\s]*", "$1[redacted]")
            .replaceAll("[\\p{Cntrl}]", " ");
        return cleaned.length() <= 600 ? cleaned : cleaned.substring(0, 600) + "...";
    }

    private long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
