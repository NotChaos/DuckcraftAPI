package com.duckydeveloper;

import com.duckydeveloper.events.onDatabaseLoadEvent;
import com.duckydeveloper.events.onInventoryClickEvent;
import com.duckydeveloper.events.onPageChangeEvent;
import com.duckydeveloper.util.Message;
import com.duckydeveloper.util.Permission;
import lombok.Getter;
import lombok.Setter;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuckAPI {
    @Getter
    private static final Properties languageConfig = new Properties();
    private static final Map<String, Component> messageComponentMap = new HashMap<>();
    private static final String WEBSITE_BASE_URL = "https://keys.duckyhub.xyz";
    private static final String KEEPALIVE_API_URL_TEMPLATE = WEBSITE_BASE_URL + "/api/keys/%s/keepalive";
    private static final String UPDATE_API_URL_TEMPLATE = WEBSITE_BASE_URL + "/api/update/%s";
    private static final String DOWNLOAD_LATEST_URL_TEMPLATE = WEBSITE_BASE_URL + "/api/plugins/%s/download/latest";
    private static final HttpClient VALIDATION_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Duration VALIDATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration UPDATE_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern VALID_RESPONSE_PATTERN = Pattern.compile("\"valid\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Object INIT_LOCK = new Object();
    @Getter
    private static JavaPlugin plugin;
    private static String permissionName;
    private static boolean debug;
    private static Integer bstatsId;
    @Getter
    private static Metrics bstatsMetrics;
    @Getter
    private static String persistentDataKey;
    private static Boolean blockGuiItemInteractions;
    @Getter
    private static Boolean enableHeadAPIIntegration = false;
    @Getter
    private static UUID pluginUuid;
    @Getter
    private static String key;
    private static volatile CompletableFuture<HeadDatabaseAPI> headApiFuture = new CompletableFuture<>();
    private static boolean updateRunning = false;
    private static boolean initialized = false;

    public static CompletableFuture<HeadDatabaseAPI> getHeadApi() {
        return headApiFuture;
    }

    public static synchronized void setHeadApi(HeadDatabaseAPI api) {
        if (headApiFuture == null || headApiFuture.isDone()) {
            headApiFuture = new CompletableFuture<>();
        }
        headApiFuture.complete(api);
    }

    public static boolean getDebug() {
        return debug;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static HeadDatabaseAPI getHeadApiBlocking() {
        if (plugin != null && plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("Blocking call to getHeadApi on the main server thread is not allowed.");
        }

        try {
            return headApiFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (CancellationException ignore) {
            return null;
        }
    }

    private static void startAutoUpdateTask() {
        if (key == null || pluginUuid == null) {
            return;
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    boolean success = keepAlive(key, WEBSITE_BASE_URL);

                    if (debug) {
                        plugin.getLogger().info("DuckAPI keepalive: " + success);
                    }

                },
                1L,
                20L * 60
        );
    }

    public static CompletableFuture<Boolean> isKeyValidAsync() {
        return isKeyValidAsync(key, pluginUuid == null ? null : pluginUuid.toString(), WEBSITE_BASE_URL);
    }

    private static CompletableFuture<Boolean> isKeyValidAsync(
            String keyValue,
            String pluginUuidValue,
            String baseUrl
    ) {
        if (keyValue == null || keyValue.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl == null) {
            return CompletableFuture.completedFuture(false);
        }

        String encodedKey = URLEncoder.encode(keyValue.trim(), StandardCharsets.UTF_8);

        StringBuilder urlBuilder = new StringBuilder(normalizedBaseUrl)
                .append("/api/keys/")
                .append(encodedKey)
                .append("/valid");

        if (pluginUuidValue != null && !pluginUuidValue.isBlank()) {
            urlBuilder.append("?pluginUuid=")
                    .append(URLEncoder.encode(pluginUuidValue.trim(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(urlBuilder.toString()))
                .timeout(VALIDATION_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        CompletableFuture<HttpResponse<String>> future =
                VALIDATION_HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        return future
                .orTimeout(VALIDATION_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        boolean isTimeout =
                                throwable instanceof java.util.concurrent.TimeoutException
                                        || (throwable.getCause() != null && throwable.getCause() instanceof java.util.concurrent.TimeoutException);

                        if (isTimeout) {
                            if (plugin != null && Boolean.TRUE.equals(debug)) {
                                plugin.getLogger().warning("Key validation timed out");
                            }
                            return null;
                        }

                        if (plugin != null && Boolean.TRUE.equals(debug)) {
                            plugin.getLogger().warning("Key validation async error: " + throwable.getMessage());
                        }
                        return false;
                    }

                    int status = response.statusCode();
                    if (plugin != null && Boolean.TRUE.equals(debug)) {
                        plugin.getLogger().info("Key validation response: HTTP " + status);
                        if (response.body() != null && !response.body().isBlank()) {
                            plugin.getLogger().info("Key validation body: " + response.body());
                        }
                    }

                    if (status < 200 || status >= 300) {
                        return false;
                    }

                    return extractValidFlag(response.body());
                });
    }


    private static boolean isKeyValid(String keyValue, String pluginUuidValue, String baseUrl) {
        if (keyValue == null || keyValue.isBlank()) {
            return false;
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl == null) {
            return false;
        }

        String encodedKey = URLEncoder.encode(keyValue.trim(), StandardCharsets.UTF_8);
        StringBuilder urlBuilder = new StringBuilder(normalizedBaseUrl)
                .append("/api/keys/")
                .append(encodedKey)
                .append("/valid");

        if (pluginUuidValue != null && !pluginUuidValue.isBlank()) {
            urlBuilder.append("?pluginUuid=")
                    .append(URLEncoder.encode(pluginUuidValue.trim(), StandardCharsets.UTF_8));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(urlBuilder.toString()))
                .timeout(VALIDATION_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = VALIDATION_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().info("Key validation response: HTTP " + response.statusCode());
                if (response.body() != null && !response.body().isBlank()) {
                    plugin.getLogger().info("Key validation body: " + response.body());
                }
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            return extractValidFlag(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Key validation interrupted: " + e.getMessage());
            }
            return false;
        } catch (IOException e) {
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Key validation network error: " + e.getMessage());
            }
            return false;
        }
    }

    public static boolean isKeyValid() {
        return isKeyValid(key, pluginUuid == null ? null : pluginUuid.toString(), WEBSITE_BASE_URL);
    }

    public static boolean keepAlive(String keyValue, String baseUrl) {
        if (keyValue == null || keyValue.isBlank()) {
            return false;
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl == null) {
            return false;
        }

        String encodedKey = URLEncoder.encode(keyValue.trim(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/api/keys/" + encodedKey + "/keepalive"))
                .timeout(VALIDATION_TIMEOUT)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<Void> response = VALIDATION_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().info("Keep-alive response: HTTP " + response.statusCode());
            }
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Keep-alive interrupted: " + e.getMessage());
            }
            return false;
        } catch (IOException e) {
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Keep-alive network error: " + e.getMessage());
            }
            return false;
        }
    }

    private static void startKeepAliveTask() {

        if (key == null || pluginUuid == null) {
            return;
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                        performAutoUpdate();
                },
                20L,
                20L * 290
        );
    }

    private static PluginUpdateResult checkForUpdate(String currentVersion, boolean keepAliveSignal) {
        return checkForUpdate(
                currentVersion,
                key,
                pluginUuid == null ? null : pluginUuid.toString(),
                WEBSITE_BASE_URL,
                keepAliveSignal
        );
    }

    private static void performAutoUpdate() {

        if (!startUpdateLock()) {
            plugin.getLogger().warning(
                    "Auto update already running. Skipping duplicate call."
            );
            return;
        }

        try {
            PluginUpdateResult result = checkForUpdate(plugin.getDescription().getVersion(), true);

            if (debug) {
                plugin.getLogger().warning("Returned from checkForUpdate()");
                plugin.getLogger().warning("statusCode = " + result.statusCode());
                plugin.getLogger().warning("message = " + result.message());
                plugin.getLogger().warning("latestVersion = " + result.latestVersion());
                plugin.getLogger().warning("updateAvailable = " + result.updateAvailable());
            }


            if (!result.updateAvailable()) {
                if (debug) {
                    plugin.getLogger().info("No update available. Current version: " + result.currentVersion() + ", Latest version: " + result.latestVersion());
                }
                return;
            }

            if (!result.hasDownload()) {
                if (debug) {
                    plugin.getLogger().info("No download available.");
                }
                return;
            }

            replacePluginJar(result.jarBytes());

            plugin.getLogger().info(
                    "Downloaded update "
                            + result.latestVersion()
                            + ". It will be installed after the next restart."
            );

        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Automatic update failed.", ex);
        } finally {
            updateRunning = false;
        }
    }

    private static void replacePluginJar(byte[] jarBytes) throws IOException {
        if (debug) {
            plugin.getLogger().info("Replacing plugin jar with the downloaded update...");
        }

        if (jarBytes == null || jarBytes.length < 10000) {
            throw new IOException("Downloaded update is invalid or too small.");
        }

        File currentJar = getPluginJar(plugin);

        if (currentJar == null) {
            throw new IOException("Unable to determine plugin jar.");
        }

        Path current = currentJar.toPath();

        Path temp = current.resolveSibling(current.getFileName() + ".tmp");

        Files.write(
                temp,
                jarBytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        Files.move(
                temp,
                current,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );

        if (debug) {
            plugin.getLogger().info("Plugin jar replaced successfully: " + current);
        }
    }

    private static File getPluginJar(JavaPlugin plugin) {

        try {
            Method method = JavaPlugin.class.getDeclaredMethod("getFile");
            method.setAccessible(true);

            Path current = ((File) method.invoke(plugin)).toPath();

            Path parent = current.getParent();

            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equalsIgnoreCase(".paper-remapped")) {

                Path realPluginFolder = parent.getParent();

                if (realPluginFolder != null) {
                    current = realPluginFolder.resolve(current.getFileName());

                    if (debug) {
                        plugin.getLogger().info(
                                "Detected .paper-remapped folder. Using original plugin path: "
                                        + current
                        );
                    }
                }
            }
            return current.toFile();
        } catch (Exception ex) {
            return null;
        }
    }

    private static PluginUpdateResult checkForUpdate(String currentVersion,
                                                     String keyValue,
                                                     String pluginUuidValue,
                                                     String baseUrl,
                                                     boolean keepAliveSignal) {
        if (debug) {
            plugin.getLogger().warning("===== CHECK FOR UPDATE =====");
            plugin.getLogger().warning("Current version = " + currentVersion);
            plugin.getLogger().warning("Key = " + keyValue);
            plugin.getLogger().warning("UUID = " + pluginUuidValue);
            plugin.getLogger().warning("Base URL = " + baseUrl);
        }

        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (normalizedBaseUrl == null || keyValue == null || keyValue.isBlank()) {
            if (debug) {
                plugin.getLogger().info("[DuckAPI] Update failed: Missing key/base URL");
                plugin.getLogger().info("[DuckAPI] Base URL = " + normalizedBaseUrl);
                plugin.getLogger().info("[DuckAPI] Key = " + keyValue);
            }

            return PluginUpdateResult.failure(currentVersion, null, null, 0, "Missing key or base URL");
        }

        if (pluginUuidValue == null || pluginUuidValue.isBlank()) {
            if (debug) {
                plugin.getLogger().info("[DuckAPI] Update failed: Missing plugin UUID");
                plugin.getLogger().info("[DuckAPI] UUID = " + pluginUuidValue);
            }
            return PluginUpdateResult.failure(currentVersion, null, null, 0, "Missing plugin UUID");
        }

        if (keepAliveSignal && !keepAlive(keyValue, normalizedBaseUrl)) {

            if (debug) {
                plugin.getLogger().warning(
                        "[DuckAPI] KeepAlive failed"
                );
            }

            return PluginUpdateResult.failure(
                    currentVersion,
                    null,
                    null,
                    0,
                    "Keep-alive failed"
            );
        }

        String encodedPluginUuid = URLEncoder.encode(pluginUuidValue.trim(), StandardCharsets.UTF_8);
        String requestBody = buildUpdateRequestBody(
                currentVersion,
                keyValue,
                keepAliveSignal
        );

        File pluginJar = getPluginJar(plugin);

        byte[] hash;

        try {
            byte[] data = Files.readAllBytes(pluginJar.toPath());
            hash = MessageDigest.getInstance("MD5").digest(data);
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Creating file hash failed.");
            return PluginUpdateResult.failure(currentVersion, null, null, 0, "Creating file hash failed: " + e.getMessage());
        }

        String checksum = new BigInteger(1, hash).toString(16);

        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizedBaseUrl + "/api/update/" + encodedPluginUuid))
                .timeout(UPDATE_TIMEOUT)
                .header("Accept", "application/octet-stream")
                .header("Content-Type", "application/json")
                .header("Plugin-Hash", checksum)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        if (debug) {
            plugin.getLogger().info("========== UPDATE REQUEST ==========");
            plugin.getLogger().info("URL: " + normalizedBaseUrl + "/api/update/" + encodedPluginUuid);
            plugin.getLogger().info("Version: " + currentVersion);
            plugin.getLogger().info("Key: " + keyValue);
            plugin.getLogger().info("KeepAlive: " + keepAliveSignal);
            plugin.getLogger().info("Request Body: " + requestBody);
        }

        try {
            HttpResponse<byte[]> response = VALIDATION_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (debug) {
                plugin.getLogger().info("========== UPDATE RESPONSE ==========");
                plugin.getLogger().info("HTTP: " + response.statusCode());

                response.headers().map().forEach((k, v) ->
                        plugin.getLogger().info(k + " = " + v));

                plugin.getLogger().info("Body Length = " + response.body().length);
            }

            int statusCode = response.statusCode();
            byte[] responseBody = response.body() == null ? new byte[0] : response.body();
            String latestVersion = firstNonBlank(
                    response.headers().firstValue("X-Plugin-Version").orElse(null),
                    response.headers().firstValue("X-Latest-Version").orElse(null)
            );
            String changelog = response.headers().firstValue("X-Plugin-Changelog").orElse(null);
            String fileName = extractFileName(response.headers().firstValue("Content-Disposition").orElse(null));

            if (debug) {
                plugin.getLogger().info("Parsed latestVersion = " + latestVersion);
                plugin.getLogger().info("Parsed changelog = " + changelog);
                plugin.getLogger().info("Parsed filename = " + fileName);
            }

            if (statusCode == 204) {
                if (debug) {
                    plugin.getLogger().info("Server returned 204 No Content.");
                    plugin.getLogger().info("Header X-Latest-Version = "
                            + response.headers().firstValue("X-Latest-Version").orElse("<missing>"));
                }

                if (latestVersion == null || latestVersion.isBlank()) {
                    latestVersion = currentVersion;
                }
                return PluginUpdateResult.noUpdate(currentVersion, latestVersion, statusCode, "Client already up to date");
            }

            if (statusCode < 200 || statusCode >= 300) {
                if (debug) {
                    plugin.getLogger().info("Server returned HTTP " + statusCode);
                    plugin.getLogger().info("Body:");
                    plugin.getLogger().info(new String(responseBody, StandardCharsets.UTF_8));
                }

                String message = responseBody.length == 0
                        ? "Update check failed with HTTP " + statusCode
                        : new String(responseBody, StandardCharsets.UTF_8);
                return PluginUpdateResult.failure(currentVersion, latestVersion, fileName, statusCode, message);
            }

            if (latestVersion == null || latestVersion.isBlank()) {
                latestVersion = currentVersion;
            }

            if (fileName == null || fileName.isBlank()) {
                fileName = latestVersion + ".jar";
            }

            if (debug) {
                plugin.getLogger().info("Update available!");
                plugin.getLogger().info("Latest version = " + latestVersion);
                plugin.getLogger().info("Filename = " + fileName);
                plugin.getLogger().info("Downloaded bytes = " + responseBody.length);
            }

            return PluginUpdateResult.updateAvailable(currentVersion, latestVersion, fileName, changelog, responseBody, statusCode, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Update check interrupted: " + e.getMessage());
            }
            return PluginUpdateResult.failure(currentVersion, null, null, 0, "Update check interrupted");
        } catch (IOException e) {
            if (plugin != null && Boolean.TRUE.equals(debug)) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
            return PluginUpdateResult.failure(currentVersion, null, null, 0, e.getMessage());
        }
    }

    public static void init(DuckAPIBuilder builder) {
        plugin = builder.getPlugin();
        permissionName = builder.getPermissionName();
        bstatsId = builder.getBstatsId();
        persistentDataKey = builder.getPersistentDataKey();
        blockGuiItemInteractions = builder.getBlockGuiItemInteractions();
        enableHeadAPIIntegration = builder.getEnableHeadAPIIntegration();
        pluginUuid = builder.getPluginUuid();
        key = builder.getKey();


        if (bstatsId == null) {
            bstatsId = 0;
        }

        if (builder.getDebug() == null) {
            debug = false;
        } else {
            debug = builder.getDebug();
        }

        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }

        if (persistentDataKey == null || persistentDataKey.isEmpty()) {
            persistentDataKey = "duckapi_data";
            plugin.getLogger().warning("Persistent Data Key is not set. Defaulting to 'duckapi_data'.");
        }

        initLanguage();
        initUtils();
        initBstats();

        synchronized (INIT_LOCK) {
            if (initialized) {
                if (plugin != null) {
                    plugin.getLogger().warning(
                            "DuckAPI.init() called more than once. Ignoring duplicate initialization."
                    );
                }
                return;
            }

            initialized = true;
        }

        initEvents();
        startKeepAliveTask();
    }

    private static synchronized boolean startUpdateLock() {
        if (updateRunning) {
            return false;
        }

        updateRunning = true;
        return true;
    }

    private static void initLanguage() {
        Properties defaultMessages = new Properties();
        File dataFolder = plugin.getDataFolder();
        File messagesFile = new File(dataFolder, "messages.properties");

        try (InputStream defaultInput = plugin.getResource("messages.properties")) {
            if (defaultInput != null) {
                defaultMessages.load(defaultInput);
            } else {
                plugin.getLogger().warning("messages.properties has not been found in the jar!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading standard messages.properties: " + e.getMessage());
        }

        if (!messagesFile.exists()) {
            try (InputStream in = plugin.getResource("messages.properties")) {
                if (in == null) {
                    plugin.getLogger().warning("messages.properties not in the jar");
                } else {
                    try (OutputStream out = new FileOutputStream(messagesFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                        plugin.getLogger().info("messages.properties has been created in the resource folder.");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Error while copying messages.properties: " + e.getMessage());
            }
        }

        try (InputStream input = new FileInputStream(messagesFile)) {
            languageConfig.clear();
            languageConfig.load(input);
            plugin.getLogger().info("messages.properties loaded");
        } catch (IOException e) {
            plugin.getLogger().severe("Error loading messages.properties: " + e.getMessage());
        }

        boolean updated = false;
        for (String key : defaultMessages.stringPropertyNames()) {
            if (!languageConfig.containsKey(key)) {
                languageConfig.setProperty(key, defaultMessages.getProperty(key));
                updated = true;
            }
        }

        if (updated) {
            try (OutputStream out = new FileOutputStream(messagesFile)) {
                languageConfig.store(out, "Updated: missing default values added");
                plugin.getLogger().info("messages.properties has been updated with new default values.");
            } catch (IOException e) {
                plugin.getLogger().severe("Error saving messages.properties: " + e.getMessage());
            }
        }

        messageComponentMap.clear();
        for (String key : languageConfig.stringPropertyNames()) {
            String value = languageConfig.getProperty(key);
            messageComponentMap.put(key, Message.convertStringToComponent(value));
        }
    }

    private static void initUtils() {
        if (permissionName == null || permissionName.isEmpty()) {
            plugin.getLogger().warning("Permission name is not set. Defaulting to 'duckapi'.");
            permissionName = "duckapi";
        }

        Message.init(getLanguageComponent("Prefix"), getLanguageComponent("InsufficientPermissions"));
        Permission.init(plugin, permissionName);
    }

    private static void initBstats() {
        if (bstatsId == null || bstatsId <= 0) {
            plugin.getLogger().warning("BStats ID is not set or invalid. Metrics will not be initialized.");
            return;
        }

        bstatsMetrics = new Metrics(plugin, bstatsId);
    }

    public static Component getLanguageComponent(String key) {
        return messageComponentMap.get(key);
    }

    public static String getLanguageString(String key) {
        return languageConfig.getProperty(key);
    }

    public static void reloadConfiguration() {
        initLanguage();
        initUtils();
    }

    private static void initEvents() {
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new onInventoryClickEvent(plugin, debug, blockGuiItemInteractions), plugin);
        pm.registerEvents(new onPageChangeEvent(plugin, debug), plugin);

        if (pm.isPluginEnabled("HeadDatabase") && enableHeadAPIIntegration) {
            pm.registerEvents(new onDatabaseLoadEvent(plugin, debug), plugin);
            plugin.getLogger().info("HeadDatabase integration enabled.");
        } else {
            enableHeadAPIIntegration = false;
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String resolvedBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? WEBSITE_BASE_URL : baseUrl.trim();
        while (resolvedBaseUrl.endsWith("/")) {
            resolvedBaseUrl = resolvedBaseUrl.substring(0, resolvedBaseUrl.length() - 1);
        }
        return resolvedBaseUrl.isBlank() ? null : resolvedBaseUrl;
    }

    private static String buildUpdateRequestBody(
            String version,
            String keyValue,
            boolean keepAliveSignal
    ) {
        return "{"
                + "\"version\":\"" + escapeJson(version) + "\","
                + "\"key\":\"" + escapeJson(keyValue) + "\","
                + "\"keepAlive\":" + keepAliveSignal
                + "}";
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String extractFileName(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return null;
        }

        String[] segments = contentDisposition.split(";");
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.regionMatches(true, 0, "filename=", 0, "filename=".length())) {
                String fileName = trimmed.substring("filename=".length()).trim();
                if (fileName.startsWith("\"") && fileName.endsWith("\"") && fileName.length() >= 2) {
                    fileName = fileName.substring(1, fileName.length() - 1);
                }
                return fileName;
            }
        }

        return null;
    }

    private static boolean extractValidFlag(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        Matcher matcher = VALID_RESPONSE_PATTERN.matcher(body);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    public static class DuckAPIBuilder {
        @Getter
        @Setter
        private JavaPlugin plugin;
        @Getter
        @Setter
        private String permissionName;
        @Getter
        @Setter
        private Boolean debug;
        @Getter
        @Setter
        private Integer bstatsId;
        @Getter
        @Setter
        private String persistentDataKey;
        @Getter
        @Setter
        @NotNull
        private Boolean blockGuiItemInteractions = true;
        @Getter
        @Setter
        @NotNull
        private Boolean enableHeadAPIIntegration = false;
        @Getter
        @Setter
        private UUID pluginUuid;
        @Getter
        @Setter
        private String key;
    }

    private record PluginUpdateResult(boolean updateAvailable,
                                      String currentVersion,
                                      String latestVersion,
                                      String fileName,
                                      String changelog,
                                      byte[] jarBytes,
                                      int statusCode,
                                      String message) {

        public static PluginUpdateResult updateAvailable(String currentVersion,
                                                         String latestVersion,
                                                         String fileName,
                                                         String changelog,
                                                         byte[] jarBytes,
                                                         int statusCode,
                                                         boolean newerVersionDetected) {
            return new PluginUpdateResult(true, currentVersion, latestVersion, fileName, changelog,
                    jarBytes == null ? new byte[0] : jarBytes, statusCode,
                    newerVersionDetected ? "Update available" : "Server returned a plugin payload but the version is not newer");
        }

        public static PluginUpdateResult noUpdate(String currentVersion,
                                                  String latestVersion,
                                                  int statusCode,
                                                  String message) {
            return new PluginUpdateResult(false, currentVersion, latestVersion, null, null, new byte[0], statusCode, message);
        }

        public static PluginUpdateResult failure(String currentVersion,
                                                 String latestVersion,
                                                 String fileName,
                                                 int statusCode,
                                                 String message) {
            return new PluginUpdateResult(false, currentVersion, latestVersion, fileName, null, new byte[0], statusCode, message);
        }

        public boolean hasDownload() {
            return jarBytes != null && jarBytes.length > 0;
        }
    }
}
