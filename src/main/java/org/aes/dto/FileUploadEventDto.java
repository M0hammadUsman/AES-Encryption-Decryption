package org.aes.dto;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadEventDto(
	MultipartFile file,
	String resumableUrl
) {}
