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
import org.aes.dto.FileUploadResponseDto;
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
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
	private static final int UPLOAD_CHUNK_SIZE = 2048; //KBs
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
		
		var googleClientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
			new StringReader(decodedJsonCredentials));
		
		flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, googleClientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(credentialsFolderPath.getFile()))
			//Setting setAccessType("offline") indicates that you want to receive a refresh token, and
			// setApprovalPrompt("force") ensures that the user is prompted to grant consent, even if
			// they have previously granted access to your application, in case your CredentialStore gets deleted
			.setAccessType("offline")
			.setApprovalPrompt("force")
			.build();
		
	}
	
	@SneakyThrows
	public boolean isDriveAuthorized(String email) {
/*		The documentation says:
		Call AuthorizationCodeFlow.loadCredential(String)) based on the user ID to check if the end-user's
		credentials are already known If so, we're done. So, we use refreshToken() method to Request a new access
		token from the authorization endpoint and returning true else if no refresh token exists returns false*/
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
	public void initiateUpload(String userId, MultipartFile multipartFile) {
		
		var userCredentials = flow.loadCredential(userId);
		var resumableUploadUri = getResumableUploadUrl(userCredentials, multipartFile);
		
		var tempFilePath = TEMP_DIR_PATH + "/" + UUID.randomUUID() + multipartFile.getOriginalFilename();
		var tempFile = new File(tempFilePath);
		multipartFile.transferTo(tempFile);
		
		if (resumableUploadUri == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
			"Unauthorized while  resumable uri for file upload for this user");
		
		var fileUploadEventDto = new FileUploadEventDto(tempFile, resumableUploadUri);
		
		applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto));
		
	}
	
	@SneakyThrows
	@Async("simpleAsyncTaskExecutor")
	public void uploadFileChunks(FileUploadEventDto fileUploadEventDto) {
		
		while (!InternetConnectivityChecker.isDriveApiAccessible()) Thread.sleep(Duration.ofSeconds(10));
		
		var fileChunks = FileChunkProvider.getChunks(fileUploadEventDto.file(), UPLOAD_CHUNK_SIZE);
		var totalBytesInFileChunks = fileChunks.stream().mapToInt(chunk -> chunk.array().length).sum();
		
		log.info("Starting file chunk upload of size {} * {} * 1024 = Bytes {}", fileChunks.size(), UPLOAD_CHUNK_SIZE,
			totalBytesInFileChunks);
		
//		uploadFile(fileUploadEventDto.resumableUrl(), Files.readAllBytes(fileUploadEventDto.file().toPath()));
		
		for (int i = 0 ; i < fileChunks.size() ; i++) {

			var chunk = fileChunks.get(i).array();
			int contentRangeA = (i > 0) ? fileChunks.get(i-1).array().length * i : 0;
			int contentRangeB = contentRangeA + chunk.length - 1;
			
			var uploadedFileId = uploadChunk(fileUploadEventDto,
				totalBytesInFileChunks,
				chunk, contentRangeA,
				contentRangeB);
			
			// TODO: Generates an event or whatever and if user has set that some users must have access for this
			//  file you have to set this
			uploadedFileId.ifPresent(System.out::println);

		}
		
	}
	
	private String getResumableUploadUrl(Credential userCredentials, MultipartFile file) {
		
		String requestBody = """
			{"name" : "%s"}
			""".formatted(file.getOriginalFilename());
		
		var resumableUri =  RestClient.create(gDriveUplaodUrl)
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
			.toBodilessEntity()
			.getHeaders()
			.getLocation();

		return String.valueOf(resumableUri);
		
	}
	
	
	
	@SneakyThrows
	public Optional<String> uploadChunk(FileUploadEventDto fileUploadEventDto,
	                                    long totalBytesInFile,
	                                    byte[] chunk,
	                                    int contentRangeA,
	                                    int contentRangeB) {
		// Use AtomicReference to hold the uploadedFileId
		final var uploadedFileId = new AtomicReference<String>();
		
		RestClient.create(fileUploadEventDto.resumableUrl())
			.method(HttpMethod.PUT)
			.body(chunk)
			.headers(h -> {
				// Content-Length: Set to the number of bytes in the current chunk.
				h.setContentLength(chunk.length);
				//Content-Range: Set to show which bytes in the file you upload
				// For example, Content-Range: bytes 0-524287/2000000 shows that you upload the first 524,288 bytes
				// (256 x 1024 x 2) in a 2,000,000 byte file.
				h.set(HttpHeaders.CONTENT_RANGE,
					"bytes " + contentRangeA + "-" + (contentRangeB) + "/" + totalBytesInFile);
				
				log.info("Uploading File Chunks Content Range: Bytes {}-{}/{}",
					contentRangeA, contentRangeB, totalBytesInFile);
			})
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
				log.info("{}", req);
				log.info("{}", res);
			})
			.onStatus(HttpStatusCode::is4xxClientError, (req, res) ->
				// A 404 Not Found response indicates the upload session has expired and
				// the upload must be restarted from the beginning.
				// So, publishing a new FileUploadEvent to initiate upload from beginning
				applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto))
			)
			.onStatus(HttpStatusCode::is3xxRedirection, (req, res) -> {
				
				var uploadedBytes = Integer.parseInt(Objects.requireNonNull(res.getHeaders()
					.getFirst("range"))
					.split("-")[1]);
				
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(), uploadedBytes, res.getHeaders().getFirst("date"));
				// If you received a 308 Resume Incomplete response, process the Range header of the response
				// to determine which bytes the server has received. If the response doesn't have a Range header,
				// no bytes have been received. For example, a Range header of bytes=0-42 indicates that the
				// first 43 bytes of the file were received and that the next chunk to upload would start with byte 44.
				if (res.getHeaders().getFirst("range") == null || uploadedBytes == 0) {
					uploadChunk(fileUploadEventDto, totalBytesInFile, chunk, contentRangeA, contentRangeB);
				} else if (uploadedBytes != contentRangeB) { // Meaning less bytes are received by the Google Drive server
					
					var unUploadedChunk = Arrays.copyOfRange(chunk,
						uploadedBytes - contentRangeA + 1,
						chunk.length);
					
					uploadChunk(fileUploadEventDto,
						totalBytesInFile,
						unUploadedChunk,
						uploadedBytes + 1,
						contentRangeB);
					
				}
			})
			.onStatus(HttpStatusCode::is2xxSuccessful, (req, res) -> {
				
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(),
					contentRangeB,
					res.getHeaders().getFirst("date"));
				log.info("ResponseStatusCode: {}, File Uploaded Successfully!", res.getStatusCode());
				
				var responseDto = new ObjectMapper()
					.readValue(res.getBody(), FileUploadResponseDto.class);
				
				uploadedFileId.set(responseDto.getMappedResponse().get("id"));
			})
			.toBodilessEntity();
		
		return Optional.ofNullable(uploadedFileId.get());
		
	}
	
	// Uploads the file in single request not good for large files
	@SneakyThrows
	public void uploadFile(String resumableUrl, byte[] fileBytes) {
		
		var response = RestClient.create(resumableUrl)
			.method(HttpMethod.PUT)
			.body(fileBytes)
			.contentLength(fileBytes.length)
			.retrieve()
			.toEntity(String.class);
		
		var responseDto = new ObjectMapper().readValue(response.getBody(), FileUploadResponseDto.class);
		log.info("Uploaded file id: {}", responseDto.getMappedResponse().get("id"));
		
	}
	
	private void initiateRetryMechanism() {
	
	}
	
	/*@SneakyThrows
	public void uploadChunk(FileUploadEventDto fileUploadEventDto, long totalBytesInFile, byte[] chunk, int contentRangeA,
	                        int contentRangeB) {

//		while (!InternetConnectivityChecker.isDriveApiAccessible()) Thread.sleep(Duration.ofSeconds(10));
		
		int noOfBytesInChunk = chunk.length;
		
		var alteredChunk = Arrays.copyOfRange(chunk, 0, noOfBytesInChunk - 5120);
		
		RestClient.create(fileUploadEventDto.resumableUrl())
			.method(HttpMethod.PUT)
			.body(alteredChunk)
			.headers(h -> {
				// Content-Length: Set to the number of bytes in the current chunk.
				h.setContentLength(alteredChunk.length);
				//Content-Range: Set to show which bytes in the file you upload
				// For example, Content-Range: bytes 0-524287/2000000 shows that you upload the first 524,288 bytes
				// (256 x 1024 x 2) in a 2,000,000 byte file.
				h.set(HttpHeaders.CONTENT_RANGE,
					"bytes " + contentRangeA + "-" + (contentRangeB - 5120) + "/" + totalBytesInFile);
				log.info("Uploading File Chunks Content Range: Bytes {}-{}/{}", contentRangeA, contentRangeB, totalBytesInFile);
			})
			.retrieve()
			.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
				log.info("{}", req);
				log.info("{}", res);
			})
			.onStatus(HttpStatusCode::is4xxClientError, (req, res) ->
				// A 404 Not Found response indicates the upload session has expired and
				// the upload must be restarted from the beginning.
				// So, publishing a new FileUploadEvent to initiate upload from beginning
				applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto))
			)
			.onStatus(HttpStatusCode::is3xxRedirection, (req, res) -> {
				
				var uploadedBytes = Integer.parseInt(Objects.requireNonNull(res.getHeaders().getFirst("range")).split("-")[1]);
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(), uploadedBytes, res.getHeaders().getFirst("date"));
				
				if (uploadedBytes != contentRangeB) {
					var unUploadedAlteredChunk = Arrays.copyOfRange(chunk, uploadedBytes - contentRangeA + 1, chunk.length);
					uploadC(fileUploadEventDto, totalBytesInFile, unUploadedAlteredChunk, uploadedBytes + 1, contentRangeB);
				}
				
			})
			.onStatus(HttpStatusCode::is2xxSuccessful, (req, res) -> {
				log.info("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
					res.getStatusCode(),
					contentRangeB,
					res.getHeaders().getFirst("date"));
				log.info("ResponseStatusCode: {}, File Uploaded Successfully!", res.getStatusCode());
			})
			.toBodilessEntity();
		
	}*/
	
}
