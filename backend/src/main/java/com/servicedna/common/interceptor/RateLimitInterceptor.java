package com.servicedna.common.interceptor;

import com.servicedna.auth.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public RateLimitInterceptor(StringRedisTemplate redisTemplate, @org.springframework.beans.factory.annotation.Value("${rate-limit.max-requests:100}") int maxRequestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip rate limiting for internal actuator endpoints and websockets
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/ws")) {
            return true;
        }

        String clientIdentifier = getClientIdentifier(request);
        String redisKey = "rate_limit:" + clientIdentifier;

        Long requests = redisTemplate.opsForValue().increment(redisKey);
        if (requests != null && requests == 1L) {
            redisTemplate.expire(redisKey, Duration.ofMinutes(1));
        }
        
        long remaining = Math.max(0, maxRequestsPerMinute - (requests != null ? requests : 0));
        response.addHeader("X-RateLimit-Limit", String.valueOf(maxRequestsPerMinute));
        response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if (requests != null && requests > maxRequestsPerMinute) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
            return false;
        }

        return true;
    }

    private String getClientIdentifier(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return "user:" + userDetails.getUser().getId().toString();
        }
        
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return "ip:" + request.getRemoteAddr();
        }
        return "ip:" + xfHeader.split(",")[0];
    }
}
