package org.aes.dto;

import java.io.File;
import java.util.List;

public record GDriveFileDto(
	File file,
	String resumableUrl,
	List<String> shareToUsersList // Will contain the list of emails to share this file with
) {}
