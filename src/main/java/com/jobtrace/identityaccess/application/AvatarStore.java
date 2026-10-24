package com.jobtrace.identityaccess.application;

public interface AvatarStore {
    String upload(String key, String contentType, byte[] body);
}
