package com.jobtrace.jobmarket.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public final class JobMarketCatalog {
    private JobMarketCatalog() {}

    public enum ListingKind {
        SYNCED_JOBS("synced_jobs"), RECRUITMENT_DIRECTORY("recruitment_directory");
        private final String wire;

        ListingKind(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }

        public static ListingKind fromWire(String value) {
            for (var item : values()) {
                if (item.wire.equals(value)) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Unknown listing kind");
        }
    }

    public enum PostStatus {
        OPEN("open"), STALE("stale"), CLOSED("closed");
        private final String wire;

        PostStatus(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }

        public static PostStatus fromWire(String value) {
            for (var item : values()) {
                if (item.wire.equals(value)) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Unknown post status");
        }
    }

    public enum ApplyMode {
        SINGLE("single"), SELECT("select"), UNAVAILABLE("unavailable");
        private final String wire;

        ApplyMode(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }
    }
}
