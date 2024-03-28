package org.aes.dto;

public record OnboardRequestDto (
	String username, // email of the user
	String password
) {}
