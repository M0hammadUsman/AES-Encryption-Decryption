package org.aes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@RequiredArgsConstructor
@EnableAsync
@SpringBootApplication
public class AesApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(AesApplication.class, args);
	}
	
	@Bean
	public CommandLineRunner commandLineRunner() {
		
		return runner -> {
		
		};
		
	}
	
}
