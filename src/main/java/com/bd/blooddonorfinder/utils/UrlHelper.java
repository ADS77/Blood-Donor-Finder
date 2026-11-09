package com.bd.blooddonorfinder.utils;

public class UrlHelper {
    public UrlHelper(){

    }
    public static final String LOGIN = "/login";
    public static final String LOGIN_FAILURE = "/login-failure";
    public static final String ACCESS_DENIED = "/access-denied";
    public static final String LOGOUT = "/logout";
    public static final String HOME = "/home";

    public static String process(String url) {
        return url + "/process";
    }

    public static String all(String url){
        return url + "/**";
    }
}
