package org.aes.service;

import com.google.api.services.drive.model.Permission;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aes.dto.FilePermissionGrantDto;
import org.aes.helper.InternetConnectivityChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PermissionGrantService {
	
	private final GoogleDriveService googleDriveService;
	
	@Value("${shared.file.email.message}")
	private String emailMessage;
	
	@SneakyThrows
	@Async
	public void grantDriveFilePermission(FilePermissionGrantDto filePermissionGrantDto) {
		
		while (InternetConnectivityChecker.isDriveApiNonAccessible()) Thread.sleep(Duration.ofSeconds(10));
		
		var permissionList = createPermission(filePermissionGrantDto.shareToUsersList());
		
		for (var permission : permissionList) {
			
			var executedPermission = googleDriveService.getDrive()
				.permissions()
				.create(filePermissionGrantDto.fileId(), permission)
				.setEmailMessage(emailMessage)
				.execute();
			
			log.debug("Permission Granted to: {}, with permission ID: {}",
				permission.getEmailAddress(),
				executedPermission.getId());
			
		}
		
	}
	
	private List<Permission> createPermission(List<String> shareToList) {
		
		var permissionList = new ArrayList<Permission>();
		
		shareToList.forEach(email -> {
			
			var permission = new Permission()
				.setType("user")
				.setRole("reader")
				.setEmailAddress(email);
			
			permissionList.add(permission);
			
		});
		
		return permissionList;
		
	}
	
}
