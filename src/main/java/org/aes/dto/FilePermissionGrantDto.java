package org.aes.dto;

import java.util.List;

public record FilePermissionGrantDto(
	String fileId,
	List<String> shareToUsersList
) {}
