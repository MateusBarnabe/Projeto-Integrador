<#
.SYNOPSIS
  Sobe o Marketing de ponta a ponta contra a infraestrutura da plataforma (Postgres, RabbitMQ e
  identity do infra-integrador-2026), com o placeholder do Landing e o stub do CRM.

.DESCRIPTION
  O identity nunca e simulado: a tela precisa de um token de usuario e a leitura do envio no Landing
  precisa do token de servico. Por isso o banco, o broker e o identity vem do compose da plataforma,
  e os quatro apps deste repositorio rodam no host apontando para eles. Passos:

    1. confere o .env do infra (cria com scripts/gerar-env.sh, pelo Git Bash, se faltar);
    2. para a infraestrutura do compose deste repositorio, se estiver de pe (as portas conflitam);
    3. sobe postgres, rabbitmq, rabbitmq-init, mailpit, identity e gateway da plataforma;
    4. faz login como marketing@empresa-a.dev e grava o token em marketing-web/.env.local;
    5. abre API, front, placeholder do Landing e stub do CRM, cada um com as senhas do .env do infra;
    6. espera a API e aplica infra/demo/prazo-descarte.sql (prazo de descarte de 2 min).

  Roteiro da demonstracao: Docs/demo-iot.md.

.PARAMETER Infra
  Pasta do infra-integrador-2026. Padrao: a variavel INFRA_INTEGRADOR ou ..\infra-integrador-2026.

.PARAMETER SoToken
  So renova o token do front (ele dura 15 minutos). O Vite recarrega sozinho; recarregue a pagina.

.PARAMETER Ocultos
  Roda os apps sem abrir janelas, com o log em %TEMP%\marketing-dev\*.log.

.PARAMETER SemInfra
  Nao mexe nos containers da plataforma (ja estao de pe).

.PARAMETER ComCasca
  Sobe tambem a casca da plataforma (porta 3000, imagem IMAGEM_CASCA do .env do infra) e aponta o
  gateway para o front, a API e o stub do CRM que rodam no host. Abra http://localhost:8080/ , entre com
  marketing@empresa-a.dev e abra o Marketing no menu lateral: o modulo roda no iframe da casca, com a
  sessao, o tema e os itens do submenu (Painel, Leads, Configuracoes) vindos dela. O token do
  .env.local so vale no modo sozinho (http://localhost:3007/modulos/marketing/).

.PARAMETER Parar
  Encerra os apps abertos por este script. Os containers da plataforma continuam de pe.

.EXAMPLE
  .\scripts\dev-plataforma.ps1 -Infra 'C:\repos\infra-integrador-2026'
.EXAMPLE
  .\scripts\dev-plataforma.ps1 -SoToken
#>
[CmdletBinding()]
param(
    [string]$Infra = $(if ($env:INFRA_INTEGRADOR) { $env:INFRA_INTEGRADOR } else { Join-Path $PSScriptRoot '..\..\infra-integrador-2026' }),
    [switch]$SoToken,
    [switch]$Ocultos,
    [switch]$SemInfra,
    [switch]$ComCasca,
    [switch]$Parar
)

# Continue: no PowerShell 5.1, o stderr do docker e do npm viraria erro fatal com 'Stop'. As chamadas
# que precisam parar o script usam -ErrorAction Stop ou conferem o $LASTEXITCODE.
$ErrorActionPreference = 'Continue'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..') -ErrorAction Stop).Path
$pastaLogs = Join-Path $env:TEMP 'marketing-dev'
$arquivoPids = Join-Path $pastaLogs 'pids.txt'
$usuario = 'marketing@empresa-a.dev'
$senhaUsuario = 'Plataforma2026'

function Passo($texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }

function Parar-Apps {
    if (-not (Test-Path $arquivoPids)) { Write-Host 'Nenhum app aberto por este script.'; return }
    foreach ($id in Get-Content $arquivoPids) {
        if ($id -and (Get-Process -Id $id -ErrorAction SilentlyContinue)) {
            & taskkill.exe /T /F /PID $id *> $null
        }
    }
    Remove-Item $arquivoPids -Force
    Write-Host 'Apps encerrados. Os containers da plataforma continuam de pe (docker compose stop, no infra, para parar).'
}

function Obter-Token {
    $corpo = @{ email = $usuario; senha = $senhaUsuario } | ConvertTo-Json
    $limite = (Get-Date).AddMinutes(4)
    while ($true) {
        try {
            $resposta = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/identity/auth/login' `
                -ContentType 'application/json' -Body $corpo -ErrorAction Stop
            return $resposta.data.accessToken
        } catch {
            if ((Get-Date) -gt $limite) { throw "O identity nao respondeu ao login em 4 minutos: $($_.Exception.Message)" }
            Start-Sleep -Seconds 5
        }
    }
}

function Gravar-Token($token) {
    $arquivo = Join-Path $raiz 'marketing-web\.env.local'
    $linhas = @()
    if (Test-Path $arquivo) { $linhas = @(Get-Content $arquivo | Where-Object { $_ -notmatch '^VITE_TOKEN_DEV=' }) }
    $linhas += "VITE_TOKEN_DEV=$token"
    [IO.File]::WriteAllLines($arquivo, $linhas, (New-Object Text.UTF8Encoding $false))
    Write-Host "Token de $usuario gravado em marketing-web\.env.local (vale 15 minutos)."
}

if ($Parar) { Parar-Apps; return }

if ($SoToken) {
    Passo 'Renovando o token do front'
    Gravar-Token (Obter-Token)
    Write-Host 'Recarregue a pagina do Marketing.'
    return
}

# --- 1. .env do infra -------------------------------------------------------------------------
if (-not (Test-Path (Join-Path $Infra 'docker-compose.yml'))) {
    throw "Infra nao encontrado em '$Infra'. Passe -Infra <pasta do infra-integrador-2026> ou defina INFRA_INTEGRADOR."
}
$Infra = (Resolve-Path $Infra).Path
$arquivoEnv = Join-Path $Infra '.env'
if (-not (Test-Path $arquivoEnv)) {
    Passo 'Criando o .env do infra (scripts/gerar-env.sh)'
    $bash = Get-Command bash -ErrorAction SilentlyContinue
    if (-not $bash) { throw 'Sem o .env do infra e sem o Git Bash para gera-lo. Rode scripts/gerar-env.sh no infra pelo Git Bash.' }
    Push-Location $Infra
    try { & $bash.Source scripts/gerar-env.sh } finally { Pop-Location }
}
$envInfra = @{}
Get-Content $arquivoEnv | Where-Object { $_ -match '^\w+=' } | ForEach-Object {
    $chave, $valor = $_ -split '=', 2
    $envInfra[$chave] = $valor
}
foreach ($chave in 'DB_APP_MARKETING_SENHA', 'DB_OWN_MARKETING_SENHA', 'MQ_MARKETING_SENHA', 'MQ_LANDING_SENHA', 'MQ_CRM_SENHA', 'SVC_MARKETING_SEGREDO') {
    if (-not $envInfra[$chave]) { throw "O .env do infra nao tem $chave." }
}

# --- 2 e 3. Containers --------------------------------------------------------------------------
if (-not $SemInfra) {
    Passo 'Parando a infraestrutura deste repositorio, se estiver de pe (as portas conflitam)'
    & docker compose -f (Join-Path $raiz 'docker-compose.yml') --project-directory $raiz stop postgres rabbitmq mailpit marketing marketing-front landing crm 2>&1 | Out-Null

    $arquivosCompose = @('-f', (Join-Path $Infra 'docker-compose.yml'))
    $servicos = @('postgres', 'rabbitmq', 'rabbitmq-init', 'mailpit', 'identity', 'gateway')
    if ($ComCasca) {
        # O gateway do infra roteia /modulos/marketing/, /api/marketing e /api/crm para containers
        # (marketing-front, marketing e crm). Aqui os tres rodam no host (o CRM e o stub): o arquivo abaixo
        # troca so essas tres rotas. O front chama /api/crm para os resumos do detalhe do lead. Fica numa
        # pasta temporaria e nao altera o repositorio do infra.
        New-Item -ItemType Directory -Force $pastaLogs | Out-Null
        $sobreposicao = Join-Path $pastaLogs 'compose-casca.yml'
        @(
            'services:',
            '  gateway:',
            '    environment:',
            '      ROTA_MARKETING_FRONT: http://host.docker.internal:3007',
            '      ROTA_MARKETING_API: http://host.docker.internal:8087',
            '      ROTA_CRM_API: http://host.docker.internal:8082',
            '    extra_hosts:',
            '      - "host.docker.internal:host-gateway"'
        ) | Set-Content -Path $sobreposicao -Encoding ascii
        $arquivosCompose += @('-f', $sobreposicao)
        $servicos += 'casca'
    }
    Passo ("Subindo " + ($servicos -join ', ') + " da plataforma")
    & docker compose @arquivosCompose --project-directory $Infra --profile plataforma up -d @servicos
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up falhou (rede lenta? rode de novo).' }
}

# --- 4. Token ------------------------------------------------------------------------------------
Passo "Login no identity como $usuario"
Gravar-Token (Obter-Token)

# --- 5. Apps -------------------------------------------------------------------------------------
foreach ($pasta in 'marketing-web', 'landing-web', 'crm-stub') {
    if (-not (Test-Path (Join-Path $raiz "$pasta\node_modules"))) {
        Passo "Instalando as dependencias de $pasta"
        & npm ci --prefix (Join-Path $raiz $pasta)
    }
}
if (Test-Path $arquivoPids) { Parar-Apps }
New-Item -ItemType Directory -Force $pastaLogs | Out-Null

function Url-Rabbit($usuarioMq, $senha) {
    "amqp://${usuarioMq}:$([uri]::EscapeDataString($senha))@localhost:5672/plataforma"
}

# Cada app herda o ambiente deste processo no momento em que abre: as variaveis mudam entre um e outro
function Abrir($titulo, $script, [hashtable]$variaveis, [string]$argumentos = '') {
    foreach ($nome in $variaveis.Keys) { Set-Item "env:$nome" $variaveis[$nome] }
    if ($Ocultos) {
        # Sem dois-pontos: no NTFS, "dev:api.log" viraria um fluxo alternativo do arquivo "dev"
        $log = Join-Path $pastaLogs "$($script.Replace(':', '-')).log"
        $processo = Start-Process cmd.exe -ArgumentList '/c', "npm run $script $argumentos > `"$log`" 2>&1" `
            -WorkingDirectory $raiz -WindowStyle Hidden -PassThru -ErrorAction Stop
        Write-Host "  $titulo (log em $log)"
    } else {
        $comando = "`$host.UI.RawUI.WindowTitle = '$titulo'; npm run $script $argumentos"
        $processo = Start-Process powershell.exe -ArgumentList '-NoExit', '-Command', $comando `
            -WorkingDirectory $raiz -PassThru -ErrorAction Stop
        Write-Host "  $titulo (janela propria)"
    }
    Add-Content $arquivoPids $processo.Id
    foreach ($nome in $variaveis.Keys) { Remove-Item "env:$nome" -ErrorAction SilentlyContinue }
}

# A porta do Postgres no host e do compose do infra e ja mudou (5433, depois 5432): pergunta ao docker
# em vez de fixar. Se nao responder, fica a 5432 (a do compose atual).
$portaPostgres = '5432'
$publicada = & docker compose -f (Join-Path $Infra 'docker-compose.yml') --project-directory $Infra port postgres 5432 2>$null
if ($publicada -match ':(\d+)\s*$') { $portaPostgres = $Matches[1] }
Write-Host "Postgres da plataforma em localhost:$portaPostgres"

Passo 'Abrindo os apps'
Abrir 'Marketing API (8087)' 'dev:api' @{
    DB_URL            = "jdbc:postgresql://localhost:$portaPostgres/plataforma"
    DB_APP_PASSWORD   = $envInfra['DB_APP_MARKETING_SENHA']
    DB_OWNER_PASSWORD = $envInfra['DB_OWN_MARKETING_SENHA']
    RABBITMQ_PASSWORD = $envInfra['MQ_MARKETING_SENHA']
    SVC_CLIENT_SECRET = $envInfra['SVC_MARKETING_SEGREDO']
}
if ($ComCasca) {
    # O gateway (em container) chega ao Vite pelo host.docker.internal: o Vite precisa ouvir em todas as
    # interfaces e aceitar esse nome de host. O segundo "--" leva o argumento do npm da raiz ao do front.
    Abrir 'Marketing front (3007, para a casca)' 'dev:web' @{ __VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS = 'host.docker.internal' } '-- -- --host 0.0.0.0'
} else {
    Abrir 'Marketing front (3007)' 'dev:web' @{}
}
Abrir 'Landing placeholder (3008/8088)' 'dev:landing' @{ RABBITMQ_URL = (Url-Rabbit 'mq_landing' $envInfra['MQ_LANDING_SENHA']) }
Abrir 'CRM stub (8082)' 'dev:crm' @{ RABBITMQ_URL = (Url-Rabbit 'mq_crm' $envInfra['MQ_CRM_SENHA']) }

# --- 6. Regras da demo -----------------------------------------------------------------------------
Passo 'Esperando a API (as migrations criam as tabelas)'
$limite = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 5
    try { $saude = Invoke-RestMethod 'http://localhost:8087/api/marketing/health' -TimeoutSec 3 -ErrorAction Stop } catch { $saude = $null }
} until ($saude -or (Get-Date) -gt $limite)
if (-not $saude) { throw 'A API nao subiu em 5 minutos. Veja a janela (ou o log) da Marketing API.' }

Passo 'Aplicando infra/demo/prazo-descarte.sql (prazo de descarte de 2 minutos)'
# O arquivo vai para o container com cp: o pipe do PowerShell 5.1 para o docker troca os acentos por "?"
$compose = @('compose', '-f', (Join-Path $Infra 'docker-compose.yml'), '--project-directory', $Infra)
& docker @compose cp (Join-Path $raiz 'infra\demo\prazo-descarte.sql') postgres:/tmp/prazo-descarte.sql
& docker @compose exec -T postgres psql -q -U postgres -d plataforma -v ON_ERROR_STOP=1 -f /tmp/prazo-descarte.sql
if ($LASTEXITCODE -ne 0) { throw 'O SQL da demo falhou.' }

Write-Host @"

Pronto. Siga o roteiro de Docs/demo-iot.md:
  Marketing     http://localhost:3007/modulos/marketing/configuracoes
$(if ($ComCasca) { "  Casca         http://localhost:8080/  (marketing@empresa-a.dev, Plataforma2026; menu lateral > Marketing)" })
  Landing       http://localhost:3008/
  RabbitMQ      http://localhost:15672 (admin e senha RABBITMQ_ADMIN_SENHA do .env do infra)
  Token expira em 15 min: .\scripts\dev-plataforma.ps1 -SoToken
  Encerrar os apps:       .\scripts\dev-plataforma.ps1 -Parar
"@ -ForegroundColor Green
