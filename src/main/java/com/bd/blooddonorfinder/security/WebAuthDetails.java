package com.bd.blooddonorfinder.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.*;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
@Data
@EqualsAndHashCode(callSuper = true)
public class WebAuthDetails extends WebAuthenticationDetails {
    private String username;
    private String password;

    public WebAuthDetails(HttpServletRequest request){
        super(request);
        this.username = request.getParameter("username").trim().toLowerCase();
        this.password = request.getParameter("password");
    }
}
