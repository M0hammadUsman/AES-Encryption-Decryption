package org.aes.helper;

import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class FileZipper {
	
	private static final Path TEMP_FILE_PATH = Path.of(System.getProperty("java.io.tmpdir"));
	
	private static final Path ENCRYPTED_TEMP_FILE = Path.of(TEMP_FILE_PATH + "/ZippedEncryptedFile.zip");
	
	private FileZipper() {
	
	}
	
	@SneakyThrows
	public static Path zip(Path...paths) {
		
		var filePath = paths[0].toString();
		var zipFilePath = getFilePathWithoutExtension(filePath) + ".zip";
		
		try (var zos = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
			for (Path path : paths) {
				zos.putNextEntry(new ZipEntry(path.getFileName().toString()));
				zos.write(Files.readAllBytes(path));
			}
			zos.closeEntry();
		}
		
		return Paths.get(zipFilePath);
		
	}
	
	@SneakyThrows
	public static List<Path> unzip(MultipartFile file) {
		
		var filePaths = new ArrayList<Path>();
		
		try (var zis = new ZipInputStream(file.getInputStream())) {
			
			ZipEntry zipEntry;
			
			while ((zipEntry = zis.getNextEntry()) != null) {
				
				Path outputPath = TEMP_FILE_PATH.resolve(zipEntry.getName());
				
				if (zipEntry.isDirectory()) {
					Files.createDirectories(outputPath);
				} else {
					
					filePaths.add(outputPath);
					Files.createDirectories(outputPath.getParent());
					
					try (var fos = new FileOutputStream(outputPath.toFile())) {
						var buffer = new byte[1024];
						int len;
						while((len = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, len);
						}
					}
				}
				zis.closeEntry();
				
			}
		}
		return filePaths;
		
	}
	
	public static String generateUniqueFileName(String fileName) {
		var fileNameWithoutExtension = getFilePathWithoutExtension(fileName);
		var randomAlphaNumericString = RandomStringUtils.randomAlphanumeric(9);
		return fileNameWithoutExtension + "-" + randomAlphaNumericString + getFileExtension(fileName);
	}
	
	public static String getFilePathWithoutExtension(String fileName) {
		return fileName.substring(0, fileName.lastIndexOf("."));
	}
	
	private static String getFileExtension(String fileName) {
		return fileName.substring(fileName.lastIndexOf("."));
	}
	
}
