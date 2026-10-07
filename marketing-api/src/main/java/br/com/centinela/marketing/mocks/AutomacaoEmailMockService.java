package br.com.centinela.marketing.mocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Mock removível da automação de e-mail; a régua real pertence à E8/E9. */
@Service
public class AutomacaoEmailMockService {

    private static final Logger log = LoggerFactory.getLogger(AutomacaoEmailMockService.class);

    public void preparar(String email, String campanha) {
        log.info("[MOCK E-MAIL] lead preparado: email={}, campanha={}", email, campanha);
    }
}