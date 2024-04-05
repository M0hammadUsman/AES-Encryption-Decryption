package org.aes.service;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aes.dto.DecryptedFileDto;
import org.aes.event.FileDeletionEvent;
import org.aes.helper.FileZipper;
import org.aes.model.AesEncryptionMetaInfo;
import org.aes.repository.AesEncryptionMetaInfoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;

@RequiredArgsConstructor
@Service
public class AesEncryptionDecryptionService {
	
	private final AesEncryptionMetaInfoRepository aesEncryptionMetaInfoRepository;
	
	private final ApplicationEventPublisher applicationEventPublisher;
	private SecretKey secretKey;
	private static final int TAG_LENGTH = 128;
	private static final Path TEMP_FILE_PATH = Path.of(System.getProperty("java.io.tmpdir"));
	private static final String TEMP_META_INFO_FILE_PATH = TEMP_FILE_PATH+"/EncryptionMetaInfo"+"(DO_NOT_DELETE).txt";
	
	
	@SneakyThrows
	public void init(int keySize) {
		
		var keyGenerator = KeyGenerator.getInstance("AES");
		try {
			keyGenerator.init(keySize);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		this.secretKey = keyGenerator.generateKey();
		
	}
	
	@SneakyThrows
	public Resource encrypt(MultipartFile file, int keySize, boolean uploadToDrive) { // If set to false also delete zipped file
		
		init(keySize);
		
		var encryptionCipher = Cipher.getInstance("AES/GCM/NoPadding");
		var iv = generateIV();
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH, iv);
		encryptionCipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
		
		// Saving Encryption Meta Info
		var metaInfo = aesEncryptionMetaInfoRepository
			.save(new AesEncryptionMetaInfo(UUID.randomUUID().toString(), secretKey, iv, TAG_LENGTH));
		var uniqueMetaInfoFilePath = Paths.get(FileZipper.generateUniqueFileName(TEMP_META_INFO_FILE_PATH));
		Files.write(uniqueMetaInfoFilePath, Base64.getEncoder().encode(metaInfo.getId().getBytes()));
		
		var encryptedFileBytes = encryptionCipher.doFinal(file.getBytes());
		
		var uniqueFileName = FileZipper.generateUniqueFileName(file.getOriginalFilename());
		var filePath = Paths.get(TEMP_FILE_PATH + "/Encrypted-" + uniqueFileName);
		try (var fos = new FileOutputStream(filePath.toFile())) {
			fos.write(encryptedFileBytes);
		}
		
		var zippedFilePath = FileZipper.zip(filePath, uniqueMetaInfoFilePath);
		
		// Publishing Temp File Deletion Event to Clean Up Resources
		FileDeletionEvent fileDeletionEvent;
		
		if (uploadToDrive) fileDeletionEvent = new FileDeletionEvent(List.of(filePath, uniqueMetaInfoFilePath));
		else fileDeletionEvent = new FileDeletionEvent(List.of(filePath, uniqueMetaInfoFilePath, zippedFilePath));
		
		applicationEventPublisher.publishEvent(fileDeletionEvent);
		
		return new FileSystemResource(zippedFilePath);
		
	}
	
	@SneakyThrows
	public DecryptedFileDto decrypt(MultipartFile file) {
		
		var filePaths = FileZipper.unzip(file);
		String metaInfoId;
		AesEncryptionMetaInfo metaInfo = null;
		
		if (filePaths.size() == 2) {
			var metaInfoFilePath = filePaths.stream()
				.filter(path -> path.equals(Paths.get(TEMP_META_INFO_FILE_PATH)))
				.findFirst().orElse(null);
			metaInfoId = new String(Base64.getDecoder().decode(Files.readAllBytes(Objects.requireNonNull(metaInfoFilePath))));
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The structure of the encrypted file has been tampered with");
		}
		
		Path encryptedFilePath = filePaths.stream()
			.filter(path -> !path.equals(TEMP_META_INFO_FILE_PATH))
			.findFirst()
			.orElse(null);
		
		Optional<AesEncryptionMetaInfo> metaInfoOptional = aesEncryptionMetaInfoRepository.findById(metaInfoId);
		if (metaInfoOptional.isPresent()) metaInfo = metaInfoOptional.get();
		
		var decryptionCipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(Objects.requireNonNull(metaInfo).getTagLength(), metaInfo.getIv());
		decryptionCipher.init(Cipher.DECRYPT_MODE, metaInfo.getSecretKey(), gcmParameterSpec);
		
		var decryptedFileBytes = decryptionCipher.doFinal(Files.readAllBytes(Objects.requireNonNull(encryptedFilePath)));
		
		var decryptedFile = TEMP_FILE_PATH + "/" + encryptedFilePath.getFileName();
		decryptedFile = decryptedFile.replace("Encrypted", "Decrypted");
		var decryptedFilePath = Path.of(decryptedFile);
		
		filePaths.add(decryptedFilePath); // Adding it into this list then passing this list for deletion
		
		try (var fos = new FileOutputStream(decryptedFile)) {
			fos.write(decryptedFileBytes);
		}
		
		// Publishing Temp File Deletion Event
		applicationEventPublisher.publishEvent(new FileDeletionEvent(filePaths));
		
		
		var fileContentType = Files.probeContentType(decryptedFilePath);
		
		return new DecryptedFileDto(new FileSystemResource(decryptedFile), fileContentType);
		
	}
	
	
	private static byte[] generateIV() {
		var iv = new byte[12];
		new SecureRandom().nextBytes(iv);
		return iv;
	}
	
}
