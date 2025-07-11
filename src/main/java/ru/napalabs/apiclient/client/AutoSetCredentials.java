package ru.napalabs.apiclient.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoSetCredentials {
    boolean token() default true;
    boolean username() default false;
    String tokenHeader() default "Authorization";
    String usernameParam() default "X-Auth-Username";
}