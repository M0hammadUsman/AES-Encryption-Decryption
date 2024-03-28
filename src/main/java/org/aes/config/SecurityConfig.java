package org.aes.config;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aes.model.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;

@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
public class SecurityConfig {
	
	@Value("${remember.me.services.secret.key}")
	private String secretKey;
	
	private final AuthenticationProvider authenticationProvider;
	
	@SneakyThrows
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) {
		
		return http
			.authorizeHttpRequests(r ->
				r
					.requestMatchers("/error")
					.permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/user/onboard")
					.permitAll()
					.anyRequest()
					.hasAnyAuthority(Role.USER.name(), Role.ADMIN.name())
			)
			.sessionManagement(s ->
				s
					.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
			)
			.httpBasic(Customizer.withDefaults())
			.rememberMe(Customizer.withDefaults())
			.authenticationProvider(authenticationProvider)
			.csrf(AbstractHttpConfigurer::disable)
			.build();
		
	}
	
	@Bean
	RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
		var rememberMe = new TokenBasedRememberMeServices(secretKey, userDetailsService, RememberMeTokenAlgorithm.SHA256);
		rememberMe.setMatchingAlgorithm(RememberMeTokenAlgorithm.MD5);
		return rememberMe;
	}
	
}
