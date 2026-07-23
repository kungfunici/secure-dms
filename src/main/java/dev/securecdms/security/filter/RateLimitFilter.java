package dev.securecdms.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private final int loginRequestsPerMinute;
    private final int apiRequestsPerMinute;

    public RateLimitFilter(
            @Value("${app.rate-limit.login-requests-per-minute:10}") int loginRequestsPerMinute,
            @Value("${app.rate-limit.api-requests-per-minute:100}") int apiRequestsPerMinute) {
        this.loginRequestsPerMinute = loginRequestsPerMinute;
        this.apiRequestsPerMinute = apiRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = getClientIp(request);
        boolean isLoginEndpoint = request.getRequestURI().equals("/api/auth/login");

        Bucket bucket = isLoginEndpoint
                ? loginBuckets.computeIfAbsent(ip, k -> buildLoginBucket())
                : buckets.computeIfAbsent(ip, k -> buildApiBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"status":429,"error":"Too many requests, please wait"}
                    """);
        }
    }

    private Bucket buildLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(loginRequestsPerMinute, Refill.greedy(loginRequestsPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket buildApiBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(apiRequestsPerMinute, Refill.greedy(apiRequestsPerMinute, Duration.ofMinutes(1))))
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
