package br.edu.ufersa.cc.seg.common.crypto;

import java.security.PublicKey;

public interface AsymmetricCryptoService extends CryptoService {

    PublicKey getPublicEncriptionKey();

    PublicKey getPublicHmacKey();

}
