package com.jobtrace.identityaccess.application;

/** Durable owner-scoped session mutation boundary. */
@FunctionalInterface
public interface AccountSessionCommand {

    boolean revoke(String ownerId, String sessionId);
}
