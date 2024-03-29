package org.aes;

import lombok.RequiredArgsConstructor;
import org.aes.service.GoogleDriveService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@RequiredArgsConstructor
@EnableAsync
@SpringBootApplication
public class AesApplication {
	
	private final GoogleDriveService googleDriveService;
	
	public static void main(String[] args) {
		SpringApplication.run(AesApplication.class, args);
	}
	
	@Bean
	public CommandLineRunner commandLineRunner() {
		
		return runner -> {
			googleDriveService.uploadFile();
		};
		
	}
	
}
