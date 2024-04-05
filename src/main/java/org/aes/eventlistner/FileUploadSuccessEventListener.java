package org.aes.eventlistner;

import lombok.RequiredArgsConstructor;
import org.aes.dto.FilePermissionGrantDto;
import org.aes.event.FileUploadSuccessEvent;
import org.aes.service.PermissionGrantService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FileUploadSuccessEventListener {
	
	private final PermissionGrantService permissionGrantService;
	
	@EventListener
	public void onFileUploadSuccessEvent(FileUploadSuccessEvent fileUploadSuccessEvent) {
		
		var  filePermissionGrantDto = (FilePermissionGrantDto) fileUploadSuccessEvent.getSource();
		var shareToUserList = filePermissionGrantDto.shareToUsersList();
		
		if (shareToUserList != null && !shareToUserList.isEmpty()) {
			permissionGrantService.grantDriveFilePermission(filePermissionGrantDto);
		}
		
	}
	
}
