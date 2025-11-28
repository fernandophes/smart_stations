package br.edu.ufersa.cc.seg.common.crypto;

import java.security.PublicKey;

import lombok.Data;

@Data
public class CryptoServicePair {

    private final CryptoService publicSide;
    private final CryptoService privateSide;
    private final PublicKey publicKey;

}
