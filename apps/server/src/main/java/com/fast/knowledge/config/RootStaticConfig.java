package com.fast.knowledge.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * 干净 URL 支持：前端静态资源托管在根路径（/、/assets/**），API 挂载在 /api/v1
 * （spring.mvc.servlet.path）。DispatcherServlet 只映射 /api/v1，根路径请求会落到
 * Tomcat 默认 servlet（空 docBase）且被安全链拦截，故用此 Valve 在安全链之前
 * 直接回写 classpath:static/ 下的资源；未命中文件且无扩展名的路径回退 index.html
 * （SPA history 路由接管，如 /kbs、/qa 刷新与直开）。
 */
@Configuration
@Profile("bundle")
public class RootStaticConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextValves(new RootStaticValve());
    }

    static class RootStaticValve extends ValveBase {

        private static final String API_PREFIX = "/api/v1/";
        private static final Map<String, String> MIME = Map.ofEntries(
                Map.entry("html", "text/html;charset=UTF-8"),
                Map.entry("js", "text/javascript;charset=UTF-8"),
                Map.entry("mjs", "text/javascript;charset=UTF-8"),
                Map.entry("css", "text/css;charset=UTF-8"),
                Map.entry("map", "application/json"),
                Map.entry("json", "application/json"),
                Map.entry("svg", "image/svg+xml"),
                Map.entry("ico", "image/x-icon"),
                Map.entry("png", "image/png"),
                Map.entry("jpg", "image/jpeg"),
                Map.entry("jpeg", "image/jpeg"),
                Map.entry("webp", "image/webp"),
                Map.entry("gif", "image/gif"),
                Map.entry("woff", "font/woff"),
                Map.entry("woff2", "font/woff2"),
                Map.entry("ttf", "font/ttf"),
                Map.entry("txt", "text/plain;charset=UTF-8")
        );

        @Override
        public void invoke(org.apache.catalina.connector.Request request,
                           org.apache.catalina.connector.Response response)
                throws IOException, ServletException {
            HttpServletRequest req = request;
            String uri = req.getRequestURI();
            // API 与健康检查交给后续链路（DispatcherServlet + 安全链）
            if (uri.startsWith(API_PREFIX) || uri.startsWith("/actuator")) {
                getNext().invoke(request, response);
                return;
            }
            if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
                getNext().invoke(request, response);
                return;
            }

            String rel = uri.substring(1);
            if (rel.isEmpty() || rel.endsWith("/")) {
                rel = rel + "index.html";
            }
            ClassPathResource resource = new ClassPathResource("static/" + rel);
            String type = contentType(rel);
            if (!resource.exists()) {
                // 无扩展名视为 SPA 前端路由，回退 index.html；带扩展名的静态文件缺失返回 404
                if (hasExtension(rel)) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                resource = new ClassPathResource("static/index.html");
                type = MIME.get("html");
            }
            write(response, resource, type, "HEAD".equals(req.getMethod()));
        }

        private boolean hasExtension(String path) {
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');
            return dot > slash && dot < path.length() - 1;
        }

        private String contentType(String path) {
            int dot = path.lastIndexOf('.');
            String ext = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
            return MIME.getOrDefault(ext, "application/octet-stream");
        }

        private void write(HttpServletResponse response, ClassPathResource resource, String contentType,
                           boolean headOnly)
                throws IOException {
            byte[] body;
            try (InputStream in = resource.getInputStream()) {
                body = in.readAllBytes();
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(contentType);
            response.setContentLength(body.length);
            // index.html 不缓存，带 hash 的静态资源可被中间层缓存
            response.setHeader("Cache-Control", "no-cache");
            if (headOnly) {
                return;
            }
            try (OutputStream out = response.getOutputStream()) {
                out.write(body);
            }
        }
    }
}
