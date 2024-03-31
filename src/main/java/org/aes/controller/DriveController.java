package org.aes.controller;

import lombok.RequiredArgsConstructor;
import org.aes.model.User;
import org.aes.service.GoogleDriveService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/drive")
public class DriveController {
	
	private final GoogleDriveService googleDriveService;
	
	@PostMapping("/create")
	public void create(@AuthenticationPrincipal User user,
	                   @RequestPart MultipartFile file) {
		googleDriveService.initiateUpload(user.getUsername(), file);
	}
}
