package org.aes.controller;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aes.dto.DecryptedFileDto;
import org.aes.model.User;
import org.aes.service.AesEncryptionDecryptionService;
import org.aes.service.FileUploadService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class AesEncryptionDecryptionController {
	
	private final AesEncryptionDecryptionService aesEncryptionDecryptionService;
	private final FileUploadService fileUploadService;
	
	@SneakyThrows
	@PostMapping(value = "/encrypt", produces = "application/zip")
	public ResponseEntity<Resource> encrypt(@AuthenticationPrincipal User user,
	                                        @RequestPart MultipartFile file,
	                                        @RequestParam int keySize,
											@RequestParam boolean uploadToDrive,
	                                        @RequestParam(required = false) List<String> shareToList) {
		
		Resource fileResource = aesEncryptionDecryptionService.encrypt(file, keySize, uploadToDrive);
		
		if (uploadToDrive) fileUploadService.initiateUpload(user.getUsername(), fileResource.getFile(), shareToList);
		
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileResource.getFilename())
			.body(fileResource);
		
	}
	
	@PostMapping("/decrypt")
	public ResponseEntity<Resource> decrypt(@RequestPart MultipartFile file) {
		
		DecryptedFileDto fileResource = aesEncryptionDecryptionService.decrypt(file);
		
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileResource.resource().getFilename())
			.header(HttpHeaders.CONTENT_TYPE, fileResource.contentType())
			.body(fileResource.resource());
		
	}
	
}
