package org.aes.helper;

import lombok.SneakyThrows;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileChunkProvider {

	private FileChunkProvider() {
	
	}
	
	@SneakyThrows
	public static List<ByteBuffer> getChunks(File file, int chunkSize) {
		
		var chunks = new ArrayList<ByteBuffer>();
		var buffer = new byte[chunkSize * 1024];
		int bytesRead;
		
		try (var fis = new FileInputStream(file)) {
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
