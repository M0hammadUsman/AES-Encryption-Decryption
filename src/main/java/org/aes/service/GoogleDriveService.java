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
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aes.dto.FileUploadEventDto;
import org.aes.event.FileUploadEvent;
import org.aes.helper.FileChunkProvider;
import org.aes.helper.InternetConnectivityChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.*;

@Slf4j(topic = "GoogleDriveService")
@RequiredArgsConstructor
@PropertySource("classpath:messages.properties")
@Service
public class GoogleDriveService {
	
	
	private final SimpleAsyncTaskExecutor simpleAsyncTaskExecutor;
	
	private final ApplicationEventPublisher applicationEventPublisher;
	@Getter
	private static  GoogleAuthorizationCodeFlow flow;
	private static final String APPLICATION_NAME = "AES Encryption Decryption (KFUEIT)";
	private static final int UPLOAD_CHUNK_SIZE = 5120; //KBs
	private static final int MAX_BACKOFF_TIME = 64; //Sec
	private static final int MAX_UPLOAD_RETRIES = 10;
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
		
		try {
			return credentials != null && credentials.refreshToken();
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
				"Unable to complete authorization sequence due to some network issues");
		}
		
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
	public void initiateUpload(String userId, MultipartFile file) {
		
		var userCredentials = flow.loadCredential(userId);
		
//		var tempFilePath = TEMP_DIR_PATH + "/" + UUID.randomUUID() + multipartFile.getOriginalFilename();
//		var tempFile = new File(tempFilePath);
//		multipartFile.transferTo(tempFile);
		
		var resumableUploadUrl = getResumableUploadUrl(userCredentials, file);
		var fileUploadEventDto = new FileUploadEventDto(file, resumableUploadUrl);
		
		applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto));
		
	}
	
	@SneakyThrows
	@Async("threadPoolTaskExecutor")
	public void uploadFileChunks(FileUploadEventDto fileUploadEventDto) {
		
		var fileChunks = FileChunkProvider.getChunks(fileUploadEventDto.file(), UPLOAD_CHUNK_SIZE);
		var totalBytesInFileChunks = fileChunks.stream().mapToInt(chunk -> chunk.array().length).sum();
		
		log.info("Starting file chunk upload of size {} * {} * 1024 = {}", fileChunks.size(), UPLOAD_CHUNK_SIZE, totalBytesInFileChunks);
		
		for (int i = 0 ; i < fileChunks.size() ; i++) {
			
			var chunk = fileChunks.get(i);
			int contentRangeA = (i > 0) ? fileChunks.get(i-1).array().length * i : 0;
			int contentRangeB = contentRangeA + chunk.array().length - 1;
			
			log.info("Uploading File Chunks Content Range: Bytes {}-{}/{}", contentRangeA, contentRangeB, totalBytesInFileChunks - 1);
			uploadChunk(fileUploadEventDto.resumableUrl(), totalBytesInFileChunks, chunk, contentRangeA, contentRangeB);

		}
		
	}
	
	private String getResumableUploadUrl(Credential userCredentials, MultipartFile file) {
		
		if (!InternetConnectivityChecker.isDriveApiAccessible()) {
			// TODO: generate an noInterneConnectionEvent and manages this upload
			return null;
		}
		
		String requestBody = """
			{"name" : "%s"}
			""".formatted(file.getOriginalFilename());
		
		var response = RestClient.create(gDriveUplaodUrl)
			.method(HttpMethod.POST)
			.body(requestBody)
			.headers(h -> {
				h.setBearerAuth(userCredentials.getAccessToken());
				h.setContentType(MediaType.APPLICATION_JSON);
				h.setContentLanguage(Locale.ENGLISH);
				h.setContentLength(requestBody.getBytes().length);
				h.set("X-Upload-Content-Type", file.getContentType());
			})
			.retrieve()
			.toBodilessEntity();
		
		return (response.getStatusCode().isSameCodeAs(HttpStatus.OK))
			? response.getHeaders().getFirst(HttpHeaders.LOCATION)
			: HttpStatus.UNAUTHORIZED.getReasonPhrase();

	}
	
	@SneakyThrows
	public void uploadChunk(String resumableUrl, long totalBytesInFile, ByteBuffer chunk, int contentRangeA, int contentRangeB) {
		
//		while (!InternetConnectivityChecker.isDriveApiAccessible()) Thread.sleep(Duration.ofSeconds(10));
		
		int noOfBytesInChunk = chunk.array().length;
		
		RestClient.create(resumableUrl)
			.method(HttpMethod.PUT)
			.body(chunk.array())
			.headers(h -> {
				// Content-Length: Set to the number of bytes in the current chunk.
				h.setContentLength(noOfBytesInChunk);
				//Content-Range: Set to show which bytes in the file you upload
				// For example, Content-Range: bytes 0-524287/2000000 shows that you upload the first 524,288 bytes
				// (256 x 1024 x 2) in a 2,000,000 byte file.
				h.set(HttpHeaders.CONTENT_RANGE, "bytes " + contentRangeA + "-" + contentRangeB + "/" + totalBytesInFile);
			})
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
				log.info("{}", req);
				log.info("{}", res);
			})
			.onStatus(HttpStatusCode::is3xxRedirection, (req, res) -> {
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(),
					Objects.requireNonNull(res.getHeaders().getFirst("range")).split("-")[1],
					res.getHeaders().getFirst("date"));
			})
			.onStatus(HttpStatusCode::is2xxSuccessful, (req, res) -> {
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(),
					contentRangeB,
					res.getHeaders().getFirst("date"));
				log.info("ResponseStatusCode: {}, File Uploaded Successfully!", res.getStatusCode());
			})
			.toBodilessEntity();
		
	}
	
	
	@SneakyThrows
	private void uploadChunk(FileUploadEventDto fileUploadEventDto, int waitDuration) {
		
		if (!InternetConnectivityChecker.isDriveApiAccessible()) {
			// TODO: generate an noInterneConnectiontEvent and manages this upload
			return;
		}
		
		Thread.sleep(Duration.ofSeconds(waitDuration));

//		var fileBytes = Files.readAllBytes(Paths.get(fileUploadEventDto.file().toURI()));
		var fileBytes = fileUploadEventDto.file().getBytes();
		
		RestClient.create(fileUploadEventDto.resumableUrl())
			.method(HttpMethod.PUT)
			.body(fileBytes)
			.headers(h -> h.setContentLength(fileBytes.length))
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
				log.info("{}", req);
				log.info("{}", res);
			})
			.onStatus(HttpStatusCode::is3xxRedirection, (req, res) -> {
				log.info("ResponseStatusCode: {}, Uploaded Content-Range: {}",
					res.getStatusCode(),
					res.getBody());
			})
			.onStatus(HttpStatusCode::is2xxSuccessful, (req, res) -> {
				log.info("ResponseStatusCode: {}, File Uploaded Successfully, ResponseBody: {}",
					res.getStatusCode(),
					res.getBody());
			})
			.toBodilessEntity();
		
	}
	
	private void initiateRetryMechanism() {
	
	}
	
	@SneakyThrows
	public Drive getDrive() {
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, flow.loadCredential("usmannadeem3344@gmail.com"))
			.setApplicationName(APPLICATION_NAME)
			.build();
	}
	
}
