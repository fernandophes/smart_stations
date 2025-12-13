package br.edu.ufersa.cc.seg.utils;

import br.edu.ufersa.cc.seg.FilterFirewall;
import br.edu.ufersa.cc.seg.common.messengers.Message;
import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
public class MessengerWithFirewall extends Messenger {

    @Delegate
    private final Messenger messenger;

    private final FilterFirewall firewall;

    @Override
    public void send(final Message message) {
        if (firewall.isAllowed(messenger)) {
            messenger.send(message);
        }
    }

}
