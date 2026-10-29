# Tuner Speeduino para Android

Versão Android do projeto `/home/guisparda/desenvolvimento/projetos/java/TunerSpeeduino`, com interface nativa em Kotlin e núcleo de interpretação INI, modelo ECU, MSQ e protocolo reaproveitados em Java. O projeto de origem permanece intacto. Android 8.0/API 26 ou superior.

## Funcionalidades

- Painel com RPM, MAP, TPS, AFR, temperaturas, bateria, avanço, tempo de injeção e mínimos/máximos. Os painéis personalizados **Rally**, **Ford Escort Zetec 1.8** e **Ford Mondeo 2.0 (1997)** também estão no seletor, com seus desenhos vetoriais portados do projeto Java. Painéis originais Speeduino Sweep, LED e LED Amber carregados dos próprios arquivos `.dash`, com fontes e imagens embutidas, zoom e tela cheia.
- Diálogos e pesquisa de parâmetros gerados a partir do INI; campos escalares, opções, painéis e comandos definidos no arquivo.
- Mapas 2D e curvas completos na tela, com células e eixos editáveis ao toque. Os valores crescem de baixo para cima e, nos mapas, da esquerda para a direita; a ordenação visual preserva os offsets da ECU.
- Leitura completa de páginas e gravação explícita das alterações, com CRC, conferência de assinatura do firmware, readback e confirmação de burn. Telemetria não intercala requisições durante a transação de gravação.
- Importação/exportação MSQ, importação de outra definição INI, calculadora de combustível requerido e assessor de combustível para CSV.
- Bluetooth Classic SPP/RFCOMM e USB serial via OTG, incluindo RS232 por conversor apropriado.
- Opção **GPS do celular → ECU** em **VSS Mode**, com negociação de suporte, envio a 5 Hz e timeout na ECU. Inclui extensão de firmware compilada para Mega 2560; veja [integração GPS VSS](firmware/speeduino-202501/README.md).
- Velocidade GPS independente da conexão com a ECU e aceleração linear do celular. Predição breve entre leituras de GPS válidas.
- Log CSV a 5 Hz, histórico de comunicação e demonstração claramente identificada.
- **Tooth / Composite Logger** em **Logs**, com intervalos entre dentes e trilhas digitais de rotação, fase e sincronismo, captura contínua, congelamento, cursor, zoom e CSV. Veja [uso e protocolo](docs/trigger-logger/README.md).
- Sessão de ajuste persistida localmente; alterações pendentes continuam pendentes após reiniciar. Rotação da tela preserva a sessão.

## Compilar e instalar

Abra esta pasta no Android Studio ou execute com JDK 25, conforme a configuração do Gradle já existente:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O `local.properties` informa o SDK Android da máquina. Não é necessário manter o projeto Java de origem instalado: o núcleo e o INI estão incluídos nesta pasta. O APK de debug é destinado a desenvolvimento; uma distribuição pública precisa de chave de assinatura própria.

## Primeira utilização

1. Abra **Conexão → Iniciar demonstração** para explorar a interface sem hardware. O modo de demonstração simula somente a ECU, nunca o GPS.
2. Em **Painel**, toque em **Autorizar GPS e sensores**, conceda localização **precisa** e ative o GPS. Use o aparelho em local com boa recepção de satélites.
3. Para a ECU real, escolha o transporte abaixo. O INI incluído tem assinatura `speeduino 202501`. A assinatura da ECU precisa corresponder exatamente, ignorando maiúsculas/minúsculas. Use **Carregar definição INI** para outro firmware; salve o MSQ antes, pois a troca reinicia o ajuste local.
4. Modifique parâmetros em **Ajustes** ou células em **Mapas**. As alterações ficam locais até **Ajustes → Gravar na ECU**. Essa ação pede confirmação na própria interface. Sem alterações locais pendentes, conectar baixa as páginas da ECU; com alterações pendentes, preserva a cópia local. **Ler novamente da ECU** substitui os ajustes locais.
5. Em **Logs**, inicie e pare a gravação; depois use **Exportar CSV**. Os arquivos são escolhidos pelo seletor de documentos do Android, sem permissão geral de armazenamento.

### Bluetooth serial / RS232

Pareie previamente um adaptador Bluetooth **Classic SPP**, como um módulo HC-05/HC-06 configurado para a serial da ECU, nas configurações do Android. Em **Conexão → Bluetooth serial (SPP)**, autorize acesso aos dispositivos próximos e escolha o dispositivo pareado. Não há busca ativa nem suporte a BLE/GATT nesta versão. A velocidade UART é configurada no módulo Bluetooth, não no RFCOMM.

Uma porta RS232 exige ponte Bluetooth–RS232 ou conversor USB–RS232 com os níveis elétricos apropriados. Não conecte sinal RS232 diretamente a UART TTL.

### USB serial / RS232

Use celular com USB host/OTG e cabo/adaptador OTG, conecte a ECU USB ou adaptador USB–serial e abra **Conexão → USB / RS232 via OTG**. Escolha dispositivo e porta, aceite a autorização USB do Android e selecione o baud rate. Configuração padrão: **115200, 8 bits, sem paridade, 1 stop bit**.

Os drivers vêm da biblioteca `usb-serial-for-android` (CDC/ACM, FTDI, CP210x, CH34x e Prolific, conforme suporte do driver). Adaptadores com várias portas permitem escolher a porta. O aplicativo não alterna DTR automaticamente. Uma falha de telemetria encerra a conexão e remove os valores antigos; reconecte pelo menu após corrigir o cabo ou adaptador.

## Velocidade e acelerômetro

A velocidade absoluta vem de `Location.speed` do GPS, convertida de m/s para km/h. O aplicativo rejeita amostras antigas, coordenadas com precisão pior que 30 m e incerteza de velocidade acima de 3 m/s. Sem informação de precisão de velocidade, aceita apenas fixes com precisão de posição de até 15 m, usando uma estimativa conservadora de qualidade.

A aceleração usa `TYPE_LINEAR_ACCELERATION`, com a gravidade removida pelo Android. Quando também existe vetor de rotação e direção GPS confiável, a aceleração é projetada na direção de deslocamento, corrigindo a declinação magnética. O celular deve estar fixo. A integração usa intervalos de até 300 ms e corrige a referência a cada leitura válida do GPS; a predição é limitada a 2 s. Sem GPS recente, a leitura fica indisponível após 6 s. Em baixa velocidade, sem direção válida ou sem sensores de orientação, permanece a velocidade do GPS, sem integração inercial.

Em **Ajustes → VSS Mode e GPS**, a escolha **GPS do celular → ECU** preserva os quatro modos originais e configura a cópia local do VSS para CAN/Serial/Analog. A escolha adicional pertence ao aplicativo; não é um valor inválido na EEPROM. O envio só começa após confirmar a extensão GPS VSS do firmware. O firmware original não oferece essa entrada na serial principal. O patch e a imagem compilada, com instruções, estão em [firmware/speeduino-202501](firmware/speeduino-202501/README.md). A ECU deixa de usar a última velocidade em 1,5 s sem fix válido/atualizações; o painel informa o estado do envio e o VSS retornado pela ECU.

O acelerômetro sozinho não fornece velocidade absoluta estável. A magnitude da aceleração é exibida separadamente em m/s². Sensores ausentes e localização negada não impedem usar as funções da ECU. Toda aquisição ocorre com o aplicativo visível; sair da tela do aplicativo encerra a gravação e desconecta a ECU. Não há serviço nem localização em segundo plano.

## Logs e análise de combustível

O CSV contém `Time (s),RPM,MAP,TPS,CLT,AFR,AFR Target,Gego,GPS speed (km/h),Acceleration (m/s2),Estimated speed (km/h)`. CLT é exportada em Celsius independentemente da preferência de unidade do INI. Números usam ponto decimal. Campos ausentes ficam vazios. GPS e estimativa inercial são colunas distintas.

Cada registro suporta até 60.000 amostras (aproximadamente 3h20 a 5 Hz) e para ao atingir o limite. O CSV permanece em memória durante a sessão: exporte antes de encerrar o aplicativo. A pausa e retomada preserva os intervalos reais de tempo. O assessor usa os critérios do projeto original: CLT ≥70 °C, pelo menos 20 amostras, trechos estáveis e sugestões limitadas a ±5%. A análise é consultiva e não escreve na ECU.

## Correspondência com o desktop

O núcleo em `org.clonetuner.ini`, `org.clonetuner.model` e parte de `org.clonetuner.comm` vem do projeto Java informado. `StreamLink` generaliza o transporte serial original, substituindo jSerialComm por `SerialIo`. A interface e os transportes Android estão em `com.example.tunerstudioandroid`.

As telas Android foram adaptadas para toque e rolagem. Os editores de mapas e curvas ocupam a tela disponível, ajustando células e fontes sem rolagem; os eixos X dos mapas aparecem na base. Os arquivos **Speeduino_Sweep.dash**, **Speeduino_LED.dash** e **Speeduino_LED_Amber.dash** da instalação de referência estão incluídos sem alterações. São 24, 49 e 49 componentes, respectivamente. O renderizador Android usa a geometria relativa, ordem de desenho, cores ARGB com transparência, fontes TTF e imagens embutidas dos documentos. Implementa os seis tipos de pintor presentes nesses arquivos, aliases dos canais antigos, graus Celsius/Fahrenheit explícitos, escalas invertidas, avisos, suavização e histórico. A fonte GPS real alimenta os instrumentos `gpsSpeedMPH` e o indicador `gpsActive`; dados indisponíveis não recebem números fictícios.

Use o seletor na aba Painel para trocar o arquivo. **Tela cheia** preserva a proporção do desenho e mantém o modo de conexão visível. Toque no ícone de logging para iniciar/parar o CSV, segure um instrumento para inspecionar e use dois dedos para ampliar. O histórico e os extremos ficam na sessão, independentes da recriação da View. **Importar painel .dash** aceita documentos XML de formato 3.0 com recursos embutidos. Pintores ou ações de arquivos externos sem implementação específica são identificados nos detalhes do painel; não há promessa de compatibilidade universal com todas as versões e plugins do TunerStudio.

Os desenhos personalizados **Rally**, **Escort Zetec 1.8** e **Mondeo 2.0 (1997)** foram portados dos vetores JavaFX para Canvas Android, preservando as coordenadas 1200 × 620, carcaças, mostradores, agulhas, símbolos e fontes DejaVu. Estão no mesmo seletor dos arquivos `.dash`, funcionam em tela cheia e guardam seleção e extremos na sessão. O velocímetro usa a fonte VSS escolhida, incluindo GPS; combustível, hodômetro e sensores não disponíveis continuam sem leitura. O painel nativo genérico permanece como alternativa, com paletas Verde/Azul/Âmbar distintas desses três desenhos. Veja as [capturas comparativas](docs/vehicle-review/index.html). A edição de mapas é uma grade 2D; a superfície 3D, o desfazer de células e plugins/ferramentas proprietários do TunerStudio não foram portados. Widgets INI sem editor Android específico aparecem como informação. Subpainéis ficam agrupados no diálogo principal; a busca também encontra os painéis internos. Campos e painéis condicionais são reavaliados após cada edição. Painéis de mapas/curvas abrem seus editores e o comando de combustível requerido abre a calculadora local.

## Verificação

Capturas dos três painéis, comparação visual e evidências estão em [docs/dashboard-review](docs/dashboard-review/README.md).

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleDebug
# Com emulador ou aparelho conectado:
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew connectedDebugAndroidTest
```

Os testes incluem regressões do projeto de origem para INI, MSQ, protocolo, cálculo de combustível, análise CSV e transações de burn; testes adicionais verificam serial fragmentada, CRC inválido, liberação de recursos, desconexão após falha, estimativa de velocidade e limites do CSV. Testes instrumentados verificam a interface de demonstração, escolha GPS no VSS, preservação dos bits do pino, envio/expiração por controlador simulado, interação com logging/tela cheia, renderização dos três `.dash` com seus recursos e persistência MSQ no parser real do Android. O firmware AVR também foi compilado, e o estado do sensor tem testes C++ independentes. A análise estática também verifica chamadas incompatíveis com a API mínima.

Os testes de comunicação usam pares simulados. É necessário validar em bancada o modelo real de ECU, firmware, adaptador USB/Bluetooth e celular, além de medir a velocidade em campo. Nenhuma ECU física foi gravada durante o desenvolvimento.

Referências das APIs: [Bluetooth RFCOMM](https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices), [USB host](https://developer.android.com/develop/connectivity/usb/host), [Location](https://developer.android.com/reference/android/location/Location), [drivers USB](https://github.com/mik3y/usb-serial-for-android).
