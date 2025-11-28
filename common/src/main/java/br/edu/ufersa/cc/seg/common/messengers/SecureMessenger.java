package br.edu.ufersa.cc.seg.common.messengers;

import br.edu.ufersa.cc.seg.common.crypto.AESService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interface comum para comunicação segura entre processos
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SecureMessenger extends Messenger {

    protected final AESService cryptoService;

}
