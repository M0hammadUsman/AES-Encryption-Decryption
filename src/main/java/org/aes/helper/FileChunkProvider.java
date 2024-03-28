package org.aes.helper;

import lombok.SneakyThrows;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileChunkProvider {

	@SneakyThrows
	public static List<ByteBuffer> getChunks(File file, int chunkSize) {
		
		var chunks = new ArrayList<ByteBuffer>();
		
		try (var fis = new FileInputStream(file);
		     var channel = fis.getChannel()) {
			
			var buffer = ByteBuffer.allocate(chunkSize * 1024);
			int bytesRead;
			
			while ((bytesRead = channel.read(buffer)) != -1) {
				buffer.flip();
				chunks.add(ByteBuffer.allocate(bytesRead).put(buffer));
				buffer.clear();
			}
		}
		
		return chunks;
		
	}
	
}
