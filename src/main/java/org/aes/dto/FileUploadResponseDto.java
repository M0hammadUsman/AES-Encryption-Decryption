package org.aes.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class FileUploadResponseDto {
	
	private final Map<String, String> mappedResponse = new LinkedHashMap<>();
	
	@JsonAnySetter
	private void setMappedResponse(String key, String value) {
		mappedResponse.put(key, value);
	}
	
}
