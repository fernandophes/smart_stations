package br.edu.ufersa.cc.seg.common.crypto;

public interface CryptoService {

    SecureMessage encrypt(final byte[] message);

    byte[] decrypt(final SecureMessage secureMessage);

}
