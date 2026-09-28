package com.eventease.config;

import com.eventease.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtCookieAuthenticationFilter jwtFilter;
    private final RequestOriginFilter originFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtCookieAuthenticationFilter jwtFilter,
            RequestOriginFilter originFilter,
            ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.originFilter = originFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                handleUnauthenticated(request, response))
                        .accessDeniedHandler((request, response, exception) ->
                                handleAccessDenied(request, response)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/student/register",
                                "/api/auth/login",
                                "/api/auth/student/login",
                                "/api/auth/college-admin/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/*").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("COLLEGE_ADMIN")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/events/*/register").hasRole("STUDENT")
                        .requestMatchers("/api/registrations/**").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/events/**").hasRole("COLLEGE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/events/**").hasRole("COLLEGE_ADMIN")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/admin/**").hasRole("COLLEGE_ADMIN")
                        .requestMatchers("/", "/index", "/index.html", "/login", "/signup", "/auth/**",
                                "/organizer/**", "/css/**", "/js/**", "/images/**", "/assets/**",
                                "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(originFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, RequestOriginFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<JwtCookieAuthenticationFilter> jwtFilterServletRegistration(
            JwtCookieAuthenticationFilter filter) {
        FilterRegistrationBean<JwtCookieAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestOriginFilter> originFilterServletRegistration(RequestOriginFilter filter) {
        FilterRegistrationBean<RequestOriginFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${eventease.cors.allowed-origins:http://localhost:8080}") String origins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.failure(message, null));
    }

    private void handleUnauthenticated(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) throws java.io.IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required.");
        } else {
            response.sendRedirect("/#how-it-works");
        }
    }

    private void handleAccessDenied(
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) throws java.io.IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "You do not have permission to perform this action.");
            return;
        }
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COLLEGE_ADMIN"));
        response.sendRedirect(admin ? "/admin/dashboard" : "/student/dashboard");
    }
}
