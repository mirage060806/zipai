package com.onrender.zipai.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private final SocialLoginSuccessHandler socialLoginSuccessHandler;

    public SecurityConfig(SocialLoginSuccessHandler socialLoginSuccessHandler) {
        this.socialLoginSuccessHandler = socialLoginSuccessHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable());

        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                .loginPage("/member/login")
                .successHandler(socialLoginSuccessHandler)
                .failureUrl("/member/login?oauthError=failed")
            );
        }

        return http.build();
    }
}
