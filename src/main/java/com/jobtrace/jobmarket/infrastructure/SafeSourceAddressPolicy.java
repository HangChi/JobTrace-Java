package com.jobtrace.jobmarket.infrastructure;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/** Validation for every source URL and resolved address, including redirects. */
public final class SafeSourceAddressPolicy {
    public URI validateUrl(String value, List<String> approvedHosts) {
        if (value == null || approvedHosts == null) {
            throw new IllegalArgumentException("来源网址不属于已批准的 HTTPS 主机。");
        }
        try {
            URI uri = new URI(value).normalize();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() != -1 && uri.getPort() != 443
                    || host.equalsIgnoreCase("localhost")
                    || host.toLowerCase(Locale.ROOT).endsWith(".localhost")
                    || host.matches("[0-9.]+")
                    || host.contains(":")
                    || approvedHosts.stream().noneMatch(
                            allowed -> host.equalsIgnoreCase(allowed))) {
                throw new IllegalArgumentException("来源网址不属于已批准的 HTTPS 主机。");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("来源网址无效。", exception);
        }
    }

    public void requirePublic(List<InetAddress> addresses) {
        if (addresses == null || addresses.isEmpty()
                || addresses.stream().anyMatch(address -> !isPublic(address))) {
            throw new IllegalArgumentException("来源主机不能解析到私有或保留地址。");
        }
    }

    public boolean isPublic(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            int c = bytes[2] & 0xff;
            return !(a == 0 || a == 10 || a == 100 && b >= 64 && b <= 127
                    || a == 127 || a == 169 && b == 254
                    || a == 172 && b >= 16 && b <= 31
                    || a == 192 && b == 0 && c == 0
                    || a == 192 && b == 0 && c == 2
                    || a == 192 && b == 88 && c == 99
                    || a == 192 && b == 168
                    || a == 198 && b >= 18 && b <= 19
                    || a == 198 && b == 51 && c == 100
                    || a == 203 && b == 0 && c == 113
                    || a >= 224);
        }
        if (bytes.length != 16) {
            return false;
        }
        int a = bytes[0] & 0xff;
        int b = bytes[1] & 0xff;
        int c = bytes[2] & 0xff;
        int d = bytes[3] & 0xff;
        if ((a & 0xfe) == 0xfc || a == 0xfe && (b & 0xc0) == 0x80
                || a == 0xff || a == 0x20 && b == 0x01 && c == 0x0d && d == 0xb8) {
            return false;
        }
        boolean mapped = true;
        for (int index = 0; index < 10; index++) {
            mapped &= bytes[index] == 0;
        }
        if (mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
            return isPublic(ipv4Tail(bytes));
        }
        return true;
    }

    private static InetAddress ipv4Tail(byte[] bytes) {
        try {
            return InetAddress.getByAddress(new byte[] {
                    bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (java.net.UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
