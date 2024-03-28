package org.aes;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
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
