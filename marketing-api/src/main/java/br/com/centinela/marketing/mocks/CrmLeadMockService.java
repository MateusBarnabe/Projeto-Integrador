package br.com.centinela.marketing.mocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Mock removível do CRM; o cliente real será implementado na tarefa C1/F5. */
@Service
public class CrmLeadMockService {

    private static final Logger log = LoggerFactory.getLogger(CrmLeadMockService.class);

    public void registrarRecebimento(String email) {
        log.info("[MOCK CRM] lead recebido: {}", email);
    }
}