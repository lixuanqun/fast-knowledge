package com.fast.knowledge.vector.sqlitevec;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
public final class SqliteVecSupport {

    private SqliteVecSupport() {
    }

    public static boolean tryLoadExtension(Connection connection, String configuredPath) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA enable_load_extension = ON");
            String path = resolveExtensionPath(configuredPath);
            if (path == null) {
                log.warn("未找到 sqlite-vec 扩展库，向量检索将使用 Java 回退实现");
                return false;
            }
            stmt.execute("SELECT load_extension('" + path.replace("'", "''") + "')");
            log.info("sqlite-vec 扩展已加载: {}", path);
            return true;
        } catch (SQLException e) {
            log.warn("加载 sqlite-vec 扩展失败，向量检索将使用 Java 回退实现: {}", e.getMessage());
            return false;
        }
    }

    public static String toVecJson(float[] vector) {
        String body = java.util.Arrays.stream(toDoubleArray(vector))
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        return "[" + body + "]";
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static float[] parseVecJson(String json) {
        String trimmed = json.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("非法向量 JSON");
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        if (inner.isEmpty()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private static String resolveExtensionPath(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path path = Path.of(configuredPath);
            if (Files.exists(path)) {
                return path.toAbsolutePath().toString().replace('\\', '/');
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String resourceName = extensionResourceName(os, arch);
        if (resourceName == null) {
            return null;
        }
        try (InputStream in = SqliteVecSupport.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                return null;
            }
            Path temp = Files.createTempFile("sqlite-vec-", extensionSuffix(os));
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp.toAbsolutePath().toString().replace('\\', '/');
        } catch (IOException e) {
            log.warn("解压 sqlite-vec 扩展失败: {}", e.getMessage());
            return null;
        }
    }

    private static String extensionResourceName(String os, String arch) {
        if (os.contains("linux")) {
            if (arch.contains("64")) {
                return "/native/sqlite-vec/linux-x86_64/vec0.so";
            }
        }
        if (os.contains("mac") || os.contains("darwin")) {
            if (arch.contains("aarch64") || arch.contains("arm")) {
                return "/native/sqlite-vec/macos-aarch64/vec0.dylib";
            }
            return "/native/sqlite-vec/macos-x86_64/vec0.dylib";
        }
        if (os.contains("win")) {
            return "/native/sqlite-vec/windows-x86_64/vec0.dll";
        }
        return null;
    }

    private static String extensionSuffix(String os) {
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return ".dylib";
        }
        return ".so";
    }

    private static double[] toDoubleArray(float[] vector) {
        double[] result = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i];
        }
        return result;
    }
}
