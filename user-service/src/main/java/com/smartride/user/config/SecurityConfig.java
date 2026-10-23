package com.smartride.user.config;

import com.smartride.user.model.Role;
import com.smartride.user.model.User;
import com.smartride.user.repo.UserRepository;
import com.smartride.user.security.JwtAuthFilter;
import com.smartride.user.security.JwtUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtUtil jwt) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/users/register", "/api/users/login", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwt), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /** Seeds a default admin account for demos. Change the password in real deployments. */
    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder enc) {
        return args -> {
            if (!users.existsByEmail("admin@smartride.com")) {
                User a = new User();
                a.setName("Admin");
                a.setEmail("admin@smartride.com");
                a.setPassword(enc.encode("Admin@123"));
                a.setRole(Role.ADMIN);
                users.save(a);
            }
        };
    }
}
