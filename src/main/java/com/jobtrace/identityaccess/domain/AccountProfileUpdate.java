package com.jobtrace.identityaccess.domain;

/** Normalized display-profile values accepted by the owner-scoped command. */
public record AccountProfileUpdate(String ownerId, String displayName, String image) {
}
