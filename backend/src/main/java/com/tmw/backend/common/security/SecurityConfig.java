package com.tmw.backend.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // (Nếu TWM-8 có khai báo JwtAuthenticationFilter hay AuthenticationProvider ở đây thì bé Long giữ nguyên nhé)

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Tắt bảo vệ CSRF vì hệ thống sử dụng token JWT (Stateless)
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. Cấu hình phân quyền truy cập cho các API
            .authorizeHttpRequests(auth -> auth
                // Cấp quyền permitAll() cho các API xác thực (auth) VÀ API test lỗi của chúng ta
                .requestMatchers("/api/auth/**", "/api/test-error").permitAll()
                
                // Tất cả các request tới đường dẫn khác đều phải có token xác thực
                .anyRequest().authenticated()
            )
            
            // 3. Cấu hình quản lý phiên (Session) ở chế độ Không lưu trạng thái (Stateless)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 4. Cấu hình Filter JWT của task TWM-8 (Nếu có thì bé Long bỏ dấu // ở dòng dưới đi nhé)
        // http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}