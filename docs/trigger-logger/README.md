# Tooth e Composite Logger

Abra **Logs → Tooth / Composite Logger** e conecte a ECU por USB ou Bluetooth. A demonstração também produz blocos de teste, sempre marcados como **DEMONSTRAÇÃO** no gráfico e no CSV.

- **Rotação + fase** usa o Composite Logger: rotação em verde, fase em âmbar e sincronismo em azul, com níveis digitais 0/1 e tempo em milissegundos.
- **Intervalos dos dentes** usa o Tooth Logger: cada barra representa o intervalo entre dentes reconhecidos pelo decoder, em microssegundos. Intervalos de pelo menos 1,8 vezes a mediana ficam em âmbar para facilitar a identificação da referência de dentes ausentes.
- **Iniciar captura** atualiza a visualização com blocos independentes de 127 registros, até quatro vezes por segundo, conforme a ECU disponibiliza os dados. Sem um bloco pronto, o aplicativo continua aguardando; não inventa pulsos nem solicita um bloco vazio.
- **Congelar / parar** encerra o logger na ECU e mantém o último bloco para inspeção.
- Toque no gráfico para selecionar um evento e ler tempo, intervalo e níveis. Use pinça ou os botões de zoom; arraste na horizontal para navegar. **Ajustar** mostra o bloco inteiro novamente.
- **Exportar CSV** salva o bloco visível. A captura é copiada antes de abrir o seletor de documentos. As colunas informam fonte, modo, índice, tempo e intervalo em µs; no modo composto também incluem rotação, fase, sincronismo, origem secundária e ciclo.

Os sinais são os níveis digitais reconhecidos pela ECU, depois do condicionamento de entrada. O aplicativo não mede amplitude, polaridade ou forma de onda analógica do sensor VR/Hall. Ruído que não chega a produzir uma borda digital não é visível aqui.

O espaçamento maior pode ser a falha de dentes prevista na roda; não é um diagnóstico automático de defeito. Compare o padrão com a roda e o decoder configurados. A fase pode não transicionar dentro de um único bloco de 127 eventos. Observe vários blocos, sobretudo em sensores que pulsam uma vez a cada duas voltas. As contagens de transições e quedas de sincronismo se referem somente ao bloco exibido; não há interpolação entre blocos.

## Protocolo e ciclo de vida

Os comandos vêm da seção `[LoggerDefinition]` do INI ativo. Esta implementação reconhece os formatos Speeduino correspondentes a **H/h** (tooth) e **J/j** (composite), com leitura **T**, 127 registros, tempos uint32 em big endian e um byte de flags depois de cada timestamp composto. As leituras passam pela mesma conferência de envelope, status, tamanho e CRC usada pelas demais operações seriais. O contador `micros()` de 32 bits é convertido em tempo relativo, incluindo rollover.

O transporte compartilha seu lock serial entre telemetria, GPS/VSS e logger. Leitura de páginas, burn e comandos manuais pedem que a captura seja parada antes. Fechar a janela, desconectar ou sair do aplicativo enfileira o encerramento do logger, restaurando os comandos de interrupção normais do firmware. Na perda física do enlace, só é possível tentar o comando de parada; o aplicativo encerra a conexão e informa a falha. Uma nova conexão descarta o bloco anterior para não confundir fontes.

A rotação da tela preserva sessão, modo e bloco; a janela é reaberta. O início de uma captura limpa o bloco antigo. Modos sem definição compatível permanecem indisponíveis.

Referências primárias da tag **202501**: [comandos e transmissão](https://github.com/speeduino/speeduino/blob/202501/speeduino/comms.cpp), [ativação e parada dos loggers](https://github.com/speeduino/speeduino/blob/202501/speeduino/logger.cpp), [bordas e flags dos sensores](https://github.com/speeduino/speeduino/blob/202501/speeduino/decoders.cpp). Não foi necessário modificar o firmware.

## Verificação

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
```

`TriggerLoggerTest` verifica decodificação, rollover, flags, intervalos longos, ausência de bloco pronto, exclusão de gravações durante captura e limpeza após falhas. `TriggerLoggerWireTest` exercita o StreamLink real contra um par serial fragmentado, verificando comandos e CRC. `TriggerLoggerUiTest` usa a sessão e o simulador completos para iniciar, congelar, inspecionar, ampliar, recriar a atividade, alternar para tooth e fechar a captura.

As verificações não substituem ensaio em bancada com a ECU, adaptador e sensores reais. A implementação foi validada com o simulador e um par serial de teste.

[Modo composto](composite.png) e [Tooth Logger](tooth.png), em paisagem; [modo composto em retrato](composite-portrait.png). A disposição dos controles em retrato também foi conferida manualmente.

Resultado da verificação: **49 testes unitários e 17 testes de interface aprovados**, sem falhas, erros ou testes ignorados. `assembleDebug` concluído; lint com 0 erros e 34 avisos. Veja [saída completa do Gradle](gradle-verification.log).
