package org.aes.eventlistner;

import lombok.RequiredArgsConstructor;
import org.aes.dto.FileUploadEventDto;
import org.aes.event.FileUploadEvent;
import org.aes.service.GoogleDriveService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class FileUploadEventListener {
	
	private final GoogleDriveService googleDriveService;
	
	@EventListener
	public void onFileUploadEvent(FileUploadEvent fileUploadEvent) {
		googleDriveService.uploadFileChunks(fileUploadEvent.getFileUploadEventDto());
	}
	
}
