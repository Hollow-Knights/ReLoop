package br.com.fiap.reloop.shared.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Monitor compartilhado pelas operações em memória que alteram pontos. */
@Component
@Profile("bootstrap")
public class InMemoryPointsLock {
}
