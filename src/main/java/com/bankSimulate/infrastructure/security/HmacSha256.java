package com.bankSimulate.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacSha256 {
    private HmacSha256(){

    }

    public static String hex(String data, String secret){
        return HexFormat.of().formatHex(bytes(data, secret));
    }

    public static byte[] bytes(String data, String secret){
        if(data == null ) throw new IllegalArgumentException("HMAC data is missing");
        if(secret == null || secret.isBlank()) throw new IllegalArgumentException("HMAC secret is missing");
        try{
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        }catch(GeneralSecurityException ex){
            throw new IllegalStateException("Cannot calculate HMAC-SHA256", ex);
        }
    } 
    public static boolean matches(String data, String secret, String suppliedHex)
    {
        if(suppliedHex == null || !suppliedHex.matches("[0-9a-fA-F]{64}")) return false;
        byte[] supplied = HexFormat.of().parseHex(suppliedHex);
        return MessageDigest.isEqual(bytes(data, secret), supplied);
    }
}
