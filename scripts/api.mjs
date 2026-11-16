// Roda o Maven Wrapper da marketing-api com os argumentos recebidos, no Windows (mvnw.cmd)
// ou no Linux/macOS (./mvnw). Ex.: node scripts/api.mjs spring-boot:run
//
// No Windows, o .cmd precisa do shell e vai pelo caminho completo, entre aspas: o cmd.exe nem
// sempre procura na pasta atual (NoDefaultCurrentDirectoryInExePath), e o caminho do repositório
// pode ter espaço. A linha vai inteira numa string, sem a lista de argumentos que o Node recusa
// com shell (DEP0190); os argumentos aqui são objetivos do Maven, sem espaço.
import { spawn } from 'node:child_process';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const pastaApi = fileURLToPath(new URL('../marketing-api/', import.meta.url));
const argumentos = process.argv.slice(2);
const opcoes = { cwd: pastaApi, stdio: 'inherit' };

const processo =
  process.platform === 'win32'
    ? spawn([`"${join(pastaApi, 'mvnw.cmd')}"`, ...argumentos].join(' '), { ...opcoes, shell: true })
    : spawn('./mvnw', argumentos, opcoes);
processo.on('exit', (codigo) => process.exit(codigo ?? 1));
