package br.edu.ufersa.cc.seg;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import br.edu.ufersa.cc.seg.common.messengers.Messenger;
import br.edu.ufersa.cc.seg.common.utils.ConnectionType;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FilterFirewall {

    @Getter
    @Setter(value = AccessLevel.PRIVATE)
    @Accessors(chain = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @EqualsAndHashCode
    public static class Rule {
        private final ConnectionType connectionType;
        private final InetAddress host;
        private final int port;

        private boolean allowed;
    }

    private Set<Rule> rules = new HashSet<>();

    public FilterFirewall addRule(final ConnectionType connectionType, final InetAddress host, final int port,
            final boolean allowed) {
        final var rule = new Rule(connectionType, host, port).setAllowed(allowed);
        rules.add(rule);

        return this;
    }

    public FilterFirewall addRule(final Messenger messenger, final boolean allowed) {
        return addRule(messenger.getConnectionType(), messenger.getHost(), messenger.getPort(), allowed);
    }

    public FilterFirewall permit(final ConnectionType connectionType, final InetAddress host, final int port) {
        return addRule(connectionType, host, port, true);
    }

    public FilterFirewall permit(final Messenger messenger) {
        return addRule(messenger, true);
    }

    public FilterFirewall deny(final ConnectionType connectionType, final InetAddress host, final int port) {
        return addRule(connectionType, host, port, false);
    }

    public FilterFirewall deny(final Messenger messenger) {
        return addRule(messenger, false);
    }

    public Optional<Rule> findRule(final ConnectionType connectionType, final InetAddress host, final int port) {
        return rules.stream()
                .filter(rule -> rule.connectionType.equals(connectionType) &&
                        rule.host.equals(host) &&
                        rule.port == port)
                .findFirst();
    }

    public Optional<Rule> findRule(final Messenger messenger) {
        return findRule(messenger.getConnectionType(), messenger.getHost(), messenger.getPort());
    }

    public boolean isAllowed(final ConnectionType connectionType, final InetAddress host, final int port) {
        final var isAllowed = findRule(connectionType, host, port).map(rule -> rule.allowed).orElse(false);

        if (isAllowed) {
            log.info("✅ O firewall permitiu o acesso a {}/{}:{} via {}",
                    host.getHostName(), host.getHostAddress(), port, connectionType);
        } else {
            log.error("🛑 O firewall bloqueou o acesso a {}/{}:{} via {}",
                    host.getHostName(), host.getHostAddress(), port, connectionType);
        }

        return isAllowed;
    }

    public boolean isAllowed(final Messenger messenger) {
        return isAllowed(messenger.getConnectionType(), messenger.getHost(), messenger.getPort());
    }

    public FilterFirewall removeRule(final ConnectionType connectionType, final InetAddress host, final int port) {
        findRule(connectionType, host, port)
                .ifPresent(rules::remove);

        return this;
    }

    public FilterFirewall removeRule(final Messenger messenger) {
        return removeRule(messenger.getConnectionType(), messenger.getHost(), messenger.getPort());
    }

    public FilterFirewall printRules() {
        final var text = new StringBuilder("\n\nREGRAS DO FIREWALL:\n\n");

        rules.forEach(rule -> {
            if (rule.allowed) {
                text.append("✅ ");
            } else {
                text.append("🛑 ");
            }

            text.append(rule.getConnectionType()).append("\t| ")
                    .append(rule.getHost().getHostName()).append("/").append(rule.getHost().getHostAddress())
                    .append(":").append(rule.getPort())
                    .append("\n");
        });

        log.info(text.toString());

        return this;
    }

}
