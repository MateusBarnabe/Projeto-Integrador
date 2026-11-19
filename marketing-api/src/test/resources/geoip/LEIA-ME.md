# Base GeoIP de teste

`GeoLite2-City-Test.mmdb` é a base **de teste** (sintética, 16 KB) publicada pela MaxMind no repositório
[maxmind/MaxMind-DB](https://github.com/maxmind/MaxMind-DB), em `test-data/`, copiada sem alteração em
19/11/2026. Ela só serve para provar que a `LocalizacaoPorGeoIp` lê uma base `.mmdb` City; os IPs dela
são os do repositório da MaxMind (por exemplo, `81.2.69.142` é Londres, ENG, GB).

Licença: o repositório `MaxMind-DB` é distribuído sob **Apache-2.0 ou MIT**, à escolha de quem usa
(`LICENSE-APACHE` e `LICENSE-MIT` na raiz dele), e cobre esse arquivo de teste.

**Isto não é a base GeoLite2 de produção.** A GeoLite2 City de verdade tem licença própria da MaxMind
(conta gratuita, termos de uso e atualização periódica) e **nunca deve ser commitada**: cada ambiente baixa
a sua e aponta para ela em `RASTREAMENTO_GEOIP_ARQUIVO`.
