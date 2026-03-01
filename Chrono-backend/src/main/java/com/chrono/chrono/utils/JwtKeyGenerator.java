package com.chrono.chrono.utils;

import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.nio.charset.StandardCharsets;

public class JwtKeyGenerator {

    private static final String SECRET = "MeinGeheimerSchluesselAmBestenLangeUndSicher";

    // Baut einen Key für JWT auf
    public static Key getKey() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),
                SignatureAlgorithm.HS256.getJcaName());
    }
}
