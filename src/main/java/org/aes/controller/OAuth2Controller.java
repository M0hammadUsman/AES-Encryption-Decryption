package org.aes.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aes.model.User;
import org.aes.service.GoogleDriveService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RequiredArgsConstructor
@RequestMapping("/api/v1/oauth2")
@RestController
public class OAuth2Controller {
	
	private final GoogleDriveService googleDriveService;
	
	@GetMapping("/authorize-drive")
	public void authorizeDrive(@AuthenticationPrincipal User user, HttpServletResponse response) {
		if (user != null && !googleDriveService.isDriveAuthorized(user.getUsername())) {
			googleDriveService.authorizeDrive(response);
		}
	}
	
	@GetMapping("/handle-redirect")
	public RedirectView handleGoogleOauth2Redirect(@RequestParam String code) {
		googleDriveService.exchangeCodeForAccessToken(code, "usmannadeem3344@gmail.com");
		return new RedirectView("/index.html");
	}
	
}
