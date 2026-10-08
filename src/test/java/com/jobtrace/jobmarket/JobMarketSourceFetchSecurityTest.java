package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.infrastructure.SafeSourceAddressPolicy;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMarketSourceFetchSecurityTest {
    @Test
    void requiresAnApprovedHttpsHostForEveryHop() {
        var policy = new SafeSourceAddressPolicy();
        assertThat(policy.validateUrl("https://jobs.example.com/roles",
                List.of("jobs.example.com")).getHost()).isEqualTo("jobs.example.com");
        for (String url : List.of("http://jobs.example.com/roles",
                "https://user:secret@jobs.example.com/roles", "https://127.0.0.1/",
                "https://jobs.example.com:8443/", "https://evil.example.com/")) {
            assertThatThrownBy(() -> policy.validateUrl(url,
                    List.of("jobs.example.com"))).as(url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> policy.validateUrl(
                "https://internal.example.com/", List.of("jobs.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(policy.validateUrl("https://jobs.example.com:443/path",
                List.of("jobs.example.com")).getPath()).isEqualTo("/path");
        for (String url : List.of("https://jobs.example.com/path#fragment",
                "https://localhost/", "https://sub.localhost/", "https://0.0.0.0/",
                "https://[::1]/", "not-a-url")) {
            assertThatThrownBy(() -> policy.validateUrl(url,
                    List.of("jobs.example.com"))).as(url)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> policy.validateUrl(null, List.of("jobs.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateUrl("https://jobs.example.com/", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateReservedMixedAndMappedDnsAnswers() throws Exception {
        var policy = new SafeSourceAddressPolicy();
        for (String address : List.of("127.0.0.1", "10.1.2.3", "172.20.0.1",
                "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1",
                "192.0.2.1", "203.0.113.1", "::1", "fd00::1", "fe80::1",
                "2001:db8::1", "::ffff:127.0.0.1")) {
            assertThat(policy.isPublic(InetAddress.getByName(address))).as(address).isFalse();
        }
        assertThat(policy.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(policy.isPublic(InetAddress.getByName("2001:4860:4860::8888"))).isTrue();
        assertThatThrownBy(() -> policy.requirePublic(List.of(
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requirePublic(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        policy.requirePublic(List.of(InetAddress.getByName("8.8.8.8")));
        for (String address : List.of("0.0.0.0", "192.0.0.1", "192.0.0.9",
                "192.0.0.170", "192.0.0.171", "192.0.0.255", "192.0.2.200",
                "192.88.99.1", "198.51.100.2", "224.0.0.1", "255.255.255.255",
                "ff02::1", "::ffff:192.168.1.1")) {
            assertThat(policy.isPublic(InetAddress.getByName(address))).as(address).isFalse();
        }
        assertThat(policy.isPublic(null)).isFalse();
    }
}
