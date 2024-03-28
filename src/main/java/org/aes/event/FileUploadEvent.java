package org.aes.event;

import lombok.Getter;
import lombok.Setter;
import org.aes.dto.FileUploadEventDto;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class FileUploadEvent extends ApplicationEvent {
	
	private final FileUploadEventDto fileUploadEventDto;
	
	public FileUploadEvent(FileUploadEventDto fileUploadEventDto) {
		super(fileUploadEventDto);
		this.fileUploadEventDto = fileUploadEventDto;
	}
	
}
