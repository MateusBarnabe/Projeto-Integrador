package br.com.centinela.marketing.mocks;

import org.springframework.stereotype.Service;

/** Mock removível da geolocalização; não chama API externa e serve ao ambiente de demonstração. */
@Service
public class GeoLocationMockService {

    public String resolver(String localizacaoRecebida) {
        return localizacaoRecebida == null || localizacaoRecebida.isBlank()
                ? "Localização mock (desenvolvimento)" : localizacaoRecebida;
    }
}