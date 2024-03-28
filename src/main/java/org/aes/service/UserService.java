package org.aes.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.aes.dto.OnboardRequestDto;
import org.aes.model.Role;
import org.aes.model.User;
import org.aes.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class UserService implements UserDetailsService {
	
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository
			.findById(username)
			.orElseThrow(() -> new UsernameNotFoundException("User with this email do not exist"));
	}
	
	@Transactional
	public void onboardUser(OnboardRequestDto onboardRequestDto) {
		
		var userExists = userRepository.findById(onboardRequestDto.username()).isPresent();
		
		if (!userExists) {
			userRepository.save(new User(onboardRequestDto.username(),
				passwordEncoder.encode(onboardRequestDto.password()),
				Role.USER));
		} else {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "User with this username already exist, try logging in");
		}
		
	}
}
