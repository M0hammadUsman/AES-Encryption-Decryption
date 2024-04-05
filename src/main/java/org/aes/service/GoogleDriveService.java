package org.aes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.StringReader;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Slf4j(topic = "GoogleDriveService")
@RequiredArgsConstructor
@Service
public class GoogleDriveService {
	
	@Getter
	private static GoogleAuthorizationCodeFlow flow;
	private static final String APPLICATION_NAME = "AES Encryption Decryption (KFUEIT)";
	private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
	
	@Value("${google.oauth.callback.uri}")
	private String callbackUri;
	
	@Value("${google.oauth.client.credentials}")
	private String oauthCredentials;
	
	@Value("${google.oauth.credentials.folder.path}")
	private Resource credentialsFolderPath;
	
	
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
	public Drive getDrive() {
		var authenticatedUserId = SecurityContextHolder.getContext().getAuthentication().getName();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, flow.loadCredential(authenticatedUserId))
			.setApplicationName(APPLICATION_NAME)
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
	
}
