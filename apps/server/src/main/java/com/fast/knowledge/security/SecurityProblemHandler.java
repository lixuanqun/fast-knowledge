package com.fast.knowledge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fast.knowledge.common.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private volatile byte[] indexHtml;

    public SecurityProblemHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // SPA 深链接支持：JWT 存于 localStorage，地址栏导航不带 Authorization 头，
        // 必然走到此入口点。浏览器文档导航直接回 index.html，由前端路由接管；
        // fetch/XHR（Sec-Fetch-Dest: empty）与 curl（无该头）仍返回 401 JSON。
        // 注：内嵌 Tomcat 下 getRequestDispatcher("/index.html") 返回 null，故直接回写文件内容。
        if (isSpaDocumentNavigation(request)) {
            byte[] html = indexHtml();
            if (html != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(MediaType.TEXT_HTML_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentLength(html.length);
                try (OutputStream out = response.getOutputStream()) {
                    out.write(html);
                }
                return;
            }
        }
        write(response, 401, "未登录或 Token 无效");
    }

    private byte[] indexHtml() {
        byte[] cached = indexHtml;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (indexHtml == null) {
                try (InputStream in = new ClassPathResource("static/index.html").getInputStream()) {
                    byte[] buf = in.readAllBytes();
                    // 去除可能的 BOM，避免部分浏览器渲染异常
                    if (buf.length > 3 && (buf[0] & 0xFF) == 0xEF && (buf[1] & 0xFF) == 0xBB && (buf[2] & 0xFF) == 0xBF) {
                        buf = Arrays.copyOfRange(buf, 3, buf.length);
                    }
                    indexHtml = buf;
                } catch (IOException e) {
                    return null;
                }
            }
            return indexHtml;
        }
    }

    private boolean isSpaDocumentNavigation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/assets/") || path.equals("/index.html")
                || path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")) {
            return false;
        }
        // 现代浏览器导航携带 Sec-Fetch-Dest: document；缺失时回退 Accept 头判断
        String dest = request.getHeader("Sec-Fetch-Dest");
        if (dest != null) {
            return "document".equals(dest);
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "无权限访问");
    }

    private void write(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
