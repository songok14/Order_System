package be16.ordersystem.common.auth;

import be16.ordersystem.member.domain.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value("${jwt.expirationAt}")
    private int expirationAt;
    @Value("${jwt.secretKeyAt}")
    private String secretKeyAt;

    private Key key;

    @PostConstruct
    public void init() {
        // JwtTokenFilter와 동일한 방식으로 키 생성
        byte[] keyBytes = secretKeyAt.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAtToken(Member member) {
        String email = member.getEmail();
        String roleCode = member.getRole().toString();

        Claims claims = Jwts.claims().setSubject(email);
        claims.put("roleCode", roleCode);
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationAt * 60 * 1000L))   // 밀리초 단위로 30분 세팅
                .signWith(this.key, SignatureAlgorithm.HS512)
                .compact();
    }

    public String createRtToken(Member member){
        // 유효기간이 긴 rt 토큰 생성

        // rt토큰을 radis에 저장

        return null;
    }
}
