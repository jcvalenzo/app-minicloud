package dev.minicloud.config;

import java.time.Clock;
import java.time.Duration;

import dev.minicloud.security.BoundedPasswordEncoder;
import dev.minicloud.security.LoginAttemptService;
import dev.minicloud.security.RejectUnauthenticatedMultipartFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'none'; object-src 'none'; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";
    private static final int MAX_CONCURRENT_PASSWORD_CHECKS = 2;
    private static final Duration PASSWORD_CHECK_WAIT = Duration.ofSeconds(1);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/app.css", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/files", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .addFilterBefore(new RejectUnauthenticatedMultipartFilter(), CsrfFilter.class)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder(MiniCloudProperties properties) {
        return new BoundedPasswordEncoder(new BCryptPasswordEncoder(properties.maxBcryptCost()),
                MAX_CONCURRENT_PASSWORD_CHECKS, PASSWORD_CHECK_WAIT);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LoginAttemptService loginAttemptService(MiniCloudProperties properties, Clock clock) {
        return new LoginAttemptService(properties, clock);
    }
}
