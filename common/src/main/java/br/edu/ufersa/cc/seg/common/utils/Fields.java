package br.edu.ufersa.cc.seg.common.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class Fields {

    public static final String SERVER_TYPE = "serverType";
    public static final String CONNECTION_TYPE = "connectionType";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String PUBLIC_KEY = "publicEncriptionKey";
    public static final String ENCRYPTION_KEY = "encryptionKey";
    public static final String HMAC_KEY = "hmacKey";
    public static final String TOKEN = "token";

}
