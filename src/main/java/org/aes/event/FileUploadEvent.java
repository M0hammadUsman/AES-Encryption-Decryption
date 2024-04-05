package org.aes.event;

import lombok.Getter;
import lombok.Setter;
import org.aes.dto.GDriveFileDto;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class FileUploadEvent extends ApplicationEvent {
	
	public FileUploadEvent(GDriveFileDto gDriveFileDto) {
		super(gDriveFileDto);
	}
	
}
