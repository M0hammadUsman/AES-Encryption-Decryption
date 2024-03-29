package org.aes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aes.dto.FileUploadEventDto;
import org.aes.event.FileUploadEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j(topic = "GoogleDriveService")
@RequiredArgsConstructor
@PropertySource("classpath:messages.properties")
@Service
public class GoogleDriveService {
	
	
	private final ThreadPoolTaskExecutor taskExecutor;
	
	private final ApplicationEventPublisher applicationEventPublisher;
	@Getter
	private static  GoogleAuthorizationCodeFlow flow;
	private static final String APPLICATION_NAME = "AES Encryption Decryption (KFUEIT)";
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
	private static final Path TEMP_DIR_PATH = Paths.get(System.getProperty("java.io.tmpdir"));
	
	@Value("${google.oauth.callback.uri}")
	private String callbackUri;
	
	@Value("${google.oauth.client.credentials}")
	private String oauthCredentials;
	
	@Value("${google.oauth.credentials.folder.path}")
	private Resource credentialsFolderPath;
	
	@Value("${gdrive.resumable.upload.url}")
	private String gDriveUplaodUrl;
	
	
	@SneakyThrows
	@PostConstruct
	public synchronized void init() {
		
		var decodedCredentials = Base64.getDecoder().decode(oauthCredentials);
		var decodedJsonCredentials = new ObjectMapper().readValue(decodedCredentials, Object.class).toString();
		
		var googleClientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new StringReader(decodedJsonCredentials));
		flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, googleClientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(credentialsFolderPath.getFile()))
			.setAccessType("offline")
			.build();
		
	}
	
	@SneakyThrows
	public boolean isDriveAuthorized(String email) {
		/*
		The documentation says:
		Call AuthorizationCodeFlow.loadCredential(String)) based on the user ID to check if the end-user's
		credentials are already known If so, we're done. So, we use refreshToken() method to Request a new access
		token from the authorization endpoint and returning true else if no refresh token exists returns false
		*/
		var credentials = flow.loadCredential(email);
		return credentials.refreshToken();
	}
	
	@SneakyThrows
	public void authorizeDrive(HttpServletResponse response) {
		var redirectUrl = flow.newAuthorizationUrl().setRedirectUri(callbackUri).build();
		response.sendRedirect(redirectUrl);
	}
	
	@SneakyThrows
	public void exchangeCodeForAccessToken(String code, String userId) {
		var googleTokenResponse = flow.newTokenRequest(code).setRedirectUri(callbackUri).execute();
		flow.createAndStoreCredential(googleTokenResponse, userId);
	}
	
	@SneakyThrows
	public void createFile(String userId, MultipartFile multipartFile) {
		
		var credentials = flow.loadCredential(userId);
		
		var tempFilePath = TEMP_DIR_PATH + "/" + UUID.randomUUID() + multipartFile.getOriginalFilename();
		var tempFile = new File(tempFilePath);
		multipartFile.transferTo(tempFile);
		
		var resumableUploadUrl = getResumableUploadUrl(credentials, multipartFile.getOriginalFilename());
		var fileUploadEventDto = new FileUploadEventDto(tempFile, resumableUploadUrl);
		
		applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto));
		
	}
	
	@SneakyThrows
	public void uploadFile() {
		taskExecutor.execute(() -> {
			for (int i = 0 ; i < 20 ; i++) {
				log.info("Upload file is setting thread to sleep for 60 sec");
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	private String getResumableUploadUrl(Credential credentials, String filename) {
		
		String requestBody = """
			{"name" : "%s"}
			""".formatted(filename);
		
		var response = RestClient.create(gDriveUplaodUrl)
			.method(HttpMethod.POST)
			.body(requestBody)
			.headers(h -> {
				h.setBearerAuth(credentials.getAccessToken());
		 		h.setContentType(MediaType.APPLICATION_JSON);
				h.setContentLanguage(Locale.ENGLISH);
				h.setContentLength(requestBody.getBytes().length);
				h.set("X-Upload-Content-Type", "file/mimetype");
			})
			.retrieve()
			.toBodilessEntity();
		
		return response.getStatusCode().isSameCodeAs(HttpStatus.OK)
		 ? response.getHeaders().getFirst(HttpHeaders.LOCATION)
		 : HttpStatus.UNAUTHORIZED.getReasonPhrase();

	}
}
