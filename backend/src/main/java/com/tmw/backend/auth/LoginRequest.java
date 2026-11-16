package com.tmw.backend.auth;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    private String username;
    private String password;

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}