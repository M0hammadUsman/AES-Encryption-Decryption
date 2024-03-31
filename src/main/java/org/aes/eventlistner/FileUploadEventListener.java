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
	private static final List<FileUploadEventDto> fileUploadEventDtoList = new ArrayList<>();
	
	@EventListener
	public void onFileUploadEvent(FileUploadEvent fileUploadEvent) {
		fileUploadEventDtoList.add(fileUploadEvent.getFileUploadEventDto());
		googleDriveService.uploadFileChunks(fileUploadEvent.getFileUploadEventDto());
		
	}
	
}
