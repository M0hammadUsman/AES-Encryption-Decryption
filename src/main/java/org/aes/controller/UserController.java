package org.aes.controller;

import lombok.RequiredArgsConstructor;
import org.aes.dto.OnboardRequestDto;
import org.aes.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/user")
public class UserController {
	
	private final UserService userService;
	
	@PostMapping("/onboard")
	public void onboardUser(@RequestBody OnboardRequestDto onboardRequestDto) {
		userService.onboardUser(onboardRequestDto);
	}
	
}
