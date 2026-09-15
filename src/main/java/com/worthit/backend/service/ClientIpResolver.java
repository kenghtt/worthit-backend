package com.worthit.backend.service;

import com.worthit.backend.config.ClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String CLIENT_IP_ATTRIBUTE = ClientIpResolver.class.getName() + ".clientIp";
    private static final String DIGITALOCEAN_CLIENT_IP_HEADER = "DO-Connecting-IP";

    private final ClientIpProperties properties;

    public String resolve(HttpServletRequest request) {
        Object cachedAddress = request.getAttribute(CLIENT_IP_ATTRIBUTE);
        if (cachedAddress instanceof String address) {
            return address;
        }

        String address = normalize(request.getRemoteAddr());
        if (properties.isTrustForwardedHeaders()) {
            String platformAddress = normalize(request.getHeader(DIGITALOCEAN_CLIENT_IP_HEADER));
            if (isIpAddress(platformAddress)) {
                address = platformAddress;
            }
        }

        request.setAttribute(CLIENT_IP_ATTRIBUTE, address);
        return address;
    }

    private static String normalize(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }

        String normalized = address.trim();
        if (normalized.startsWith("[") && normalized.contains("]")) {
            return normalized.substring(1, normalized.indexOf(']'));
        }

        int lastColon = normalized.lastIndexOf(':');
        if (normalized.contains(".") && lastColon > 0) {
            String port = normalized.substring(lastColon + 1);
            if (port.chars().allMatch(Character::isDigit)) {
                normalized = normalized.substring(0, lastColon);
            }
        }
        return normalized;
    }

    private static boolean isIpAddress(String address) {
        if ("unknown".equals(address) || !address.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(address).getHostAddress() != null;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }
}