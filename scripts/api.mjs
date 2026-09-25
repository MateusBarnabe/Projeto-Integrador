// Roda o Maven Wrapper da marketing-api com os argumentos recebidos, no Windows (mvnw.cmd)
// ou no Linux/macOS (./mvnw). Ex.: node scripts/api.mjs spring-boot:run
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const pastaApi = fileURLToPath(new URL('../marketing-api/', import.meta.url));
const windows = process.platform === 'win32';
const comando = windows ? 'mvnw.cmd' : './mvnw';

const processo = spawn(comando, process.argv.slice(2), { cwd: pastaApi, stdio: 'inherit', shell: windows });
processo.on('exit', (codigo) => process.exit(codigo ?? 1));
