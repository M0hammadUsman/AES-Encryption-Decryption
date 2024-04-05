package org.aes.eventlistner;

import lombok.RequiredArgsConstructor;
import org.aes.dto.GDriveFileDto;
import org.aes.event.FileUploadEvent;
import org.aes.service.FileUploadService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FileUploadEventListener {
	
	private final FileUploadService fileUploadService;
	
	@EventListener
	public void onFileUploadEvent(FileUploadEvent fileUploadEvent) {
		fileUploadService.uploadFileChunks((GDriveFileDto) fileUploadEvent.getSource());
	}
	
}
