package org.aes.dto;

import org.springframework.core.io.Resource;

public record DecryptedFileDto(
	Resource resource,
	String contentType
) {}
