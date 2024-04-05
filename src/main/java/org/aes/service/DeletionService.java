package org.aes.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class DeletionService {
	
	@Value("${delete.files.after}")
	long deleteFilesAfter;
	
	@Async
	@SneakyThrows
	public void deleteTempFiles(List<Path> deletionFilesList) {
		Thread.sleep(deleteFilesAfter);
		for (var path : deletionFilesList) {
			if (Files.exists(path)) Files.delete(path);
			log.debug("File Deleted: {} @{}", path.getFileName(), new Date());
		}
	}
	
}
