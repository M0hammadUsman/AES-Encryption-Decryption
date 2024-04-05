package org.aes.event;

import org.aes.dto.FilePermissionGrantDto;
import org.springframework.context.ApplicationEvent;

public class FileUploadSuccessEvent extends ApplicationEvent {
	
	public FileUploadSuccessEvent(FilePermissionGrantDto shareToList) {
		// Map will have fileId as key and list of emails to share file with
		super(shareToList);
	}
	
}
