package org.aes.dto;

import java.io.File;

public record FileUploadEventDto(
	File file,
	String resumableUrl
) {}
