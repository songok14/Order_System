package be16.ordersystem.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenFilter extends GenericFilterBean {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Key key;

    @PostConstruct
    public void init() {
        byte[] keyBytes = secretKeyAt.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Value("${jwt.secretKeyAt}")
    private String secretKeyAt;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String bearerToken = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearerToken == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = bearerToken.substring(7);
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            List<GrantedAuthority> authorityList = new ArrayList<>();
            authorityList.add(new SimpleGrantedAuthority("ROLE_" + claims.get("roleCode")));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), "", authorityList);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {           // 토큰 만료 예외는 별도로 처리
            log.error("Expired JWT Token: {}", e.getMessage());
            setErrorResponse(httpResponse, HttpStatus.UNAUTHORIZED, "만료된 토큰입니다.");
        } catch (JwtException | IllegalArgumentException e) { // 서명 불일치, 토큰 형식 오류 등 모든 JWT 관련 예외를 한번에 처리
            log.error("Invalid JWT Token: {}", e.getMessage());
            setErrorResponse(httpResponse, HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
        } catch (Exception e) {
            log.error("JWT Filter Error: {}", e.getMessage());
            setErrorResponse(httpResponse, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void setErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("status", status.value());
        errorDetails.put("message", message);

        response.getWriter().write(objectMapper.writeValueAsString(errorDetails));
    }
}
