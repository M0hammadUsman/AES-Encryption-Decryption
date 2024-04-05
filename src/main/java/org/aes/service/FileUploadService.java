package org.aes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aes.dto.FilePermissionGrantDto;
import org.aes.dto.FileUploadResponseDto;
import org.aes.dto.GDriveFileDto;
import org.aes.event.FileDeletionEvent;
import org.aes.event.FileUploadEvent;
import org.aes.event.FileUploadSuccessEvent;
import org.aes.helper.FileChunkProvider;
import org.aes.helper.InternetConnectivityChecker;
import org.aes.helper.TimeCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileUploadService {
	
	private final ApplicationEventPublisher applicationEventPublisher;
	private static final int UPLOAD_CHUNK_SIZE = 2048; //KBs
	private static final int MAX_BACKOFF_TIME = 64000; //64 Sec
	private static final int MAX_UPLOAD_RETRIES = 16; // 17 retries -> 0 based
	
	@Value("${gdrive.resumable.upload.url}")
	private String gDriveUploadUrl;
	
	@SneakyThrows
	public void initiateUpload(String userId, File file, List<String> shareToList) {
		
		var userCredentials = GoogleDriveService.getFlow().loadCredential(userId);
		var resumableUploadUri = getResumableUploadUrl(userCredentials, file);
		
		if (resumableUploadUri == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
			"Unauthorized while resumable uri for file upload for this user");
		
		var fileUploadEventDto = new GDriveFileDto(file, resumableUploadUri, shareToList);
		
		applicationEventPublisher.publishEvent(new FileUploadEvent(fileUploadEventDto));
		
	}
	
	@SneakyThrows
	@Async
	public void uploadFileChunks(GDriveFileDto gDriveFileDto) {
		
		while (!InternetConnectivityChecker.isDriveApiAccessible()) Thread.sleep(Duration.ofSeconds(10));
		
		var fileChunks = FileChunkProvider.getChunks(gDriveFileDto.file(), UPLOAD_CHUNK_SIZE);
		var totalBytesInFileChunks = fileChunks.stream().mapToInt(chunk -> chunk.array().length).sum();
		Optional<String> uploadedFileId = Optional.empty();
		
		log.debug("Starting file chunk upload of size {} * {} * 1024 = Bytes {}", fileChunks.size(), UPLOAD_CHUNK_SIZE,
			totalBytesInFileChunks);
		
		for (int i = 0 ; i < fileChunks.size() ; i++) {
			
			var chunk = fileChunks.get(i).array();
			int contentRangeA = (i > 0) ? fileChunks.get(i-1).array().length * i : 0;
			int contentRangeB = contentRangeA + chunk.length - 1;
			
			uploadedFileId = uploadChunk(gDriveFileDto,
				chunk,
				0, // It's not a retry attempt
				contentRangeA,
				contentRangeB);
			
		}
		
		if (uploadedFileId.isEmpty()) return;
		
		var fileDeletionEvent = new FileDeletionEvent(List.of(gDriveFileDto.file().toPath()));
		var filePermissionGrantDto = new FilePermissionGrantDto(uploadedFileId.get(), gDriveFileDto.shareToUsersList());
		
		applicationEventPublisher.publishEvent(fileDeletionEvent);
		applicationEventPublisher.publishEvent(new FileUploadSuccessEvent(filePermissionGrantDto));
		
	}
	
	@SneakyThrows
	private String getResumableUploadUrl(Credential userCredentials, File file) {
		
		var requestBody = """
			{"name" : "%s"}
			""".formatted(file.getName());
		var contentType = Files.probeContentType(file.toPath());
		
		var resumableUri =  RestClient.create(gDriveUploadUrl)
			.method(HttpMethod.POST)
			.body(requestBody)
			.headers(h -> {
				h.setBearerAuth(userCredentials.getAccessToken());
				h.setContentType(MediaType.APPLICATION_JSON);
				h.setContentLanguage(Locale.ENGLISH);
				h.setContentLength(requestBody.getBytes().length);
				h.set("X-Upload-Content-Type", contentType);
			})
			.retrieve()
			.toBodilessEntity()
			.getHeaders()
			.getLocation();
		
		return String.valueOf(resumableUri);
		
	}
	
	@SneakyThrows
	private Optional<String> uploadChunk(GDriveFileDto gDriveFileDto,
	                                     byte[] chunk,
	                                     int retryIndex,
	                                     int contentRangeA,
	                                     int contentRangeB) {
		// Use AtomicReference to hold the uploadedFileId as its in lambda context
		var uploadedFileId = new AtomicReference<String>();
		
		try {
			
			RestClient.create(gDriveFileDto.resumableUrl())
				.method(HttpMethod.PUT)
				.body(chunk)
				.headers(h -> {
					// Content-Length: Set to the number of bytes in the current chunk.
					h.setContentLength(chunk.length);
					//Content-Range: Set to show which bytes in the file you upload
					// For example, Content-Range: bytes 0-524287/2000000 shows that you upload the first 524,288 bytes
					// (256 x 1024 x 2) in a 2,000,000 byte file.
					h.set(HttpHeaders.CONTENT_RANGE,
						"bytes " + contentRangeA + "-" + (contentRangeB) + "/" + gDriveFileDto.file().length());
					
					log.debug("Uploading File Chunks Content Range: Bytes {}-{}/{}",
						contentRangeA, contentRangeB, gDriveFileDto.file().length());
				})
				.retrieve()
				.onStatus(HttpStatusCode::is5xxServerError, (req, res) ->
					// To fix 5xxServerErrors, use exponential backoff to retry the request
					initiateRetryAttempt(gDriveFileDto, chunk, retryIndex, contentRangeA, contentRangeB)
				)
				.onStatus(HttpStatusCode::is4xxClientError, (req, res) ->
					// For any 4xx errors (including 403) during a resumable upload, restart the upload.
					// These errors indicate the upload session has expired and must be restarted
					// by requesting a new session URI. Upload sessions also expire after one week of inactivity.
					// So, publishing a new FileUploadEvent to initiate upload from beginning
					applicationEventPublisher.publishEvent(new FileUploadEvent(gDriveFileDto))
				)
				.onStatus(HttpStatusCode::is3xxRedirection, (req, res) -> {
					
					var uploadedBytes = Integer.parseInt(Objects.requireNonNull(res.getHeaders()
							.getFirst("range"))
						.split("-")[1]);
					
					log.debug("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
						res.getStatusCode(), uploadedBytes, res.getHeaders().getFirst("date"));
					// If you received a 308 Resume Incomplete response, process the Range header of the response
					// to determine which bytes the server has received. If the response doesn't have a Range header,
					// no bytes have been received. For example, a Range header of bytes=0-42 indicates that the
					// first 43 bytes of the file were received and that the next chunk to upload would start with byte 44.
					handleUploadedBytesResponse(gDriveFileDto, chunk, contentRangeA, contentRangeB, res, uploadedBytes);
				})
				.onStatus(HttpStatusCode::is2xxSuccessful, (req, res) -> {
					
					log.debug("ResponseStatusCode: {}, Uploaded Bytes: {} | @{}",
						res.getStatusCode(),
						contentRangeB,
						res.getHeaders().getFirst("date"));
					log.debug("ResponseStatusCode: {}, File Uploaded Successfully!", res.getStatusCode());
					
					var responseDto = new ObjectMapper()
						.readValue(res.getBody(), FileUploadResponseDto.class);
					
					uploadedFileId.set(responseDto.getMappedResponse().get("id"));
				})
				.toBodilessEntity();
			
		} catch (Exception e) {
			log.debug("Exception while uploading file: {}", e.getMessage());
			initiateRetryAttempt(gDriveFileDto, chunk, retryIndex, contentRangeA, contentRangeB);
		}
		
		return Optional.ofNullable(uploadedFileId.get());
		
	}
	
	private void handleUploadedBytesResponse(GDriveFileDto gDriveFileDto,
	                                         byte[] chunk,
	                                         int contentRangeA,
	                                         int contentRangeB,
	                                         ClientHttpResponse res,
	                                         int uploadedBytes) {
		
		if (res.getHeaders().getFirst("range") == null || uploadedBytes == 0) {
			uploadChunk(gDriveFileDto, chunk, 0, contentRangeA, contentRangeB);
		} else if (uploadedBytes < contentRangeB) { // Meaning less bytes are received by the Google Drive Server
			
			var unUploadedChunk = Arrays.copyOfRange(chunk,
				uploadedBytes - contentRangeA + 1,
				chunk.length);
			
			// Sending again those bytes that are not received by the Google Drive Server
			uploadChunk(gDriveFileDto,
				unUploadedChunk,
				0,
				uploadedBytes + 1,
				contentRangeB);
			
		}
		
	}
	
	@SneakyThrows
	private void initiateRetryAttempt(GDriveFileDto gDriveFileDto,
	                                  byte[] chunk,
	                                  int retryIndex,
	                                  int contentRangeA,
	                                  int contentRangeB) {
		
		if (retryIndex < MAX_UPLOAD_RETRIES) {
			
			long waitTillRetryAttempt = TimeCalculator.calculateRetryDelay(retryIndex);
			waitTillRetryAttempt = Math.min(waitTillRetryAttempt,
				MAX_BACKOFF_TIME + SecureRandom.getInstanceStrong().nextLong(1001));
			
			Thread.sleep(waitTillRetryAttempt);
			
			uploadChunk(gDriveFileDto,
				chunk,
				retryIndex + 1,
				contentRangeA,
				contentRangeB);
			
		} else { // After MAX_UPLOAD_RETRIES retrying upload from start
			applicationEventPublisher.publishEvent(new FileUploadEvent(gDriveFileDto));
		}
		
	}
	
}
