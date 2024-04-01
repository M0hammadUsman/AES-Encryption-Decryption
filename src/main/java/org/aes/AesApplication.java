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
			
			/*var jsonCredentials = """
				{
				  "web": {
				    "client_id": "461180740526-0ccbo0tt3u5m7fro58ou3gfud4l818me.apps.googleusercontent.com",
				    "project_id": "aes-encryption-decryption",
				    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
				    "token_uri": "https://oauth2.googleapis.com/token",
				    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
				    "client_secret": "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",
				    "redirect_uris": [
				      "http://localhost:8080/api/v1/oauth2/handle-redirect"
				    ],
				    "javascript_origins": [
				      "http://localhost:8080",
				      "https://localhost:8080"
				    ]
				  }
				}
				""";
			
			var object = new ObjectMapper().writeValueAsString(jsonCredentials);
			System.out.println(Base64.getEncoder().encodeToString(object.getBytes()));*/
			
		};
		
	}
	
}
