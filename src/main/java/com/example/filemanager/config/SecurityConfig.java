package com.example.filemanager.config;

import com.example.filemanager.security.ApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Two independent filter chains:
 *
 * <ul>
 * <li>{@code /api/**} — stateless HTTP Basic for programmatic clients. No
 * session cookie is issued and the 401 carries no {@code WWW-Authenticate:
 * Basic} challenge, so browsers never cache credentials for this realm and
 * cannot be made to replay them from a third-party page. That is what makes
 * disabling CSRF on this chain safe: there is no ambient credential to ride.</li>
 * <li>everything else — session based form login for the Thymeleaf UI, with
 * CSRF protection enabled.</li>
 * </ul>
 *
 * Endpoints consumed by the browser UI live under {@code /web/api/**} so that
 * they authenticate with the session, not with Basic.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Roles are derived from group names; the administrator group is "admins". */
    public static final String ADMIN_ROLE = "ADMINS";

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Safe here only because this chain is stateless and issues no
                // browser-cacheable Basic challenge (see class javadoc).
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/users/**", "/api/groups/**").hasRole(ADMIN_ROLE)
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.authenticationEntryPoint(new ApiAuthenticationEntryPoint()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new ApiAuthenticationEntryPoint()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/favicon.svg")
                        .permitAll()
                        .requestMatchers("/admin/**", "/h2-console/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .hasRole(ADMIN_ROLE)
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, denied) -> response
                        .sendError(HttpStatus.FORBIDDEN.value(), "この操作を行う権限がありません。")))
                // The H2 console renders inside frames and posts its own forms.
                // It is admin-only above and disabled outside the h2 profile.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
