package org.aes.helper;

import lombok.SneakyThrows;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class FileChunkProvider {

	private FileChunkProvider() {
	
	}
	
	@SneakyThrows
	public static List<ByteBuffer> getChunks(MultipartFile file, int chunkSize) {
		
		var chunks = new ArrayList<ByteBuffer>();
		var buffer = new byte[chunkSize * 1024];
		int bytesRead;
		
		try (var fis = file.getInputStream()) {
			
			while ((bytesRead = fis.read(buffer)) != -1) {
				var byteBuffer = ByteBuffer.allocate(bytesRead);
				byteBuffer.put(buffer, 0, bytesRead);
				byteBuffer.flip(); // Prepare the buffer for reading
				chunks.add(byteBuffer);
			}
		}
		
		return chunks;
		
	}
	
}
