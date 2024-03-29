package org.aes.eventlistner;

import org.aes.dto.FileUploadEventDto;
import org.aes.event.FileUploadEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FileUploadEventListener {
	
	private List<FileUploadEventDto> fileUploadEventDtoList = new ArrayList<>();
	
	@EventListener
	public void onFileUploadEvent(FileUploadEvent fileUploadEvent) {
		fileUploadEventDtoList.add(fileUploadEvent.getFileUploadEventDto());
	}
	
}
