package org.aes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ApplicationConfig {
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
	
	@Bean
	public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService) {
		
		var daoAuthProvider = new DaoAuthenticationProvider();
		daoAuthProvider.setPasswordEncoder(passwordEncoder());
		daoAuthProvider.setUserDetailsService(userDetailsService);
		
		return daoAuthProvider;
		
	}
	
	/*@Bean
	public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
		
		var executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(10);
		executor.setAllowCoreThreadTimeOut(true); // Default keep-alive seconds are set to 60
		executor.setWaitForTasksToCompleteOnShutdown(true); // To my understanding allows graceful shutdown
		executor.setThreadNamePrefix("ThreadPoolExecutor-");
		executor.initialize();
		
		return executor;
		
	}
	
	@Bean
	public SimpleAsyncTaskExecutor simpleAsyncTaskExecutor() {
		
		var executor = new SimpleAsyncTaskExecutor("SimpleAsyncExecutor-");
		executor.setVirtualThreads(true);
		
		return executor;
		
	}*/

}
