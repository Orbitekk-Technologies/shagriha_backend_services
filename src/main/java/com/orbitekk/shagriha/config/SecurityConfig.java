package com.orbitekk.shagriha.config;

import com.orbitekk.shagriha.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import com.orbitekk.shagriha.auth.GoogleOAuthSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return login -> users.findByUsernameIgnoreCaseOrEmailIgnoreCase(login, login)
                .map(u -> User.withUsername(u.getId().toString()).password(u.getPasswordHash())
                        .roles(u.getRole().name()).disabled(!u.isEnabled()).build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Invalid credentials"));
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService details, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clients,
            GoogleOAuthSuccessHandler googleSuccessHandler,
            @org.springframework.beans.factory.annotation.Value("${app.frontend-url}") String frontendUrl) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/signup", "/auth/login", "/auth/refresh", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/properties", "/properties/**").permitAll()
                .requestMatchers("/managers/**").hasRole("MANAGER")
                .requestMatchers("/tenants/**").hasAnyRole("TENANT", "MANAGER")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        if (clients.getIfAvailable() != null) http.oauth2Login(login -> login
                .successHandler(googleSuccessHandler)
                .failureHandler((request, response, exception) ->
                        response.sendRedirect(frontendUrl + "/signin?oauthError=true")));
        return http.build();
    }
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaimAsStringList("roles").stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList());
        return converter;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.frontend-url}") String frontendUrl) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendUrl));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
