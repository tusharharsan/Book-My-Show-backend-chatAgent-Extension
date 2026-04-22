package com.example.bookmyshowoct24.services;

import com.example.bookmyshowoct24.dtos.ResponseStatus;
import com.example.bookmyshowoct24.exceptions.UserAlreadyExistsException;
import com.example.bookmyshowoct24.exceptions.UserNotFoundException;
import com.example.bookmyshowoct24.models.User;
import com.example.bookmyshowoct24.repositories.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private UserRepository userRepository;
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    public UserService(UserRepository userRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    public User signUp(String name, String email, String password) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            throw new UserAlreadyExistsException("User with email " + email + " already exists. Please login.");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(bCryptPasswordEncoder.encode(password));

        return userRepository.save(user);
    }

    public ResponseStatus login(String email, String password) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            throw new UserNotFoundException("No account found for email: " + email + ". Please sign up first.");
        }

        User user = optionalUser.get();

        if (bCryptPasswordEncoder.matches(password, user.getPassword())) {
            return ResponseStatus.SUCCESS;
        }
        return ResponseStatus.FAILURE;
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + userId));
    }
}

/*

encoding + salting

=> BCryptPasswordEncoder.

 */
