# Validação da versão 1.1

Abra [a comparação visual](index.html) para alternar entre Sweep, LED e LED Amber, ver os estados com/sem telemetria e sobrepor as imagens às referências disponíveis do desktop.

## Evidências

| Verificação | Resultado | Evidência |
| --- | --- | --- |
| Núcleo, geometria, leitura .dash, canais e GPS VSS | 30 testes unitários, sem falhas | `app/build/reports/tests/testDebugUnitTest/` |
| Android API 37, instância isolada TunerDashReview | 7 testes instrumentados, sem falhas | [android-instrumentation.log](android-instrumentation.log) e [execução Gradle](android-gradle-verification.log) |
| APK, análise estática e testes unitários | Build concluído, lint sem erros | [build-verification.log](build-verification.log) |
| Estado do sensor no firmware | Teste C++ com `-Werror` passou | `firmware/speeduino-202501/gps_vss_test.cpp` |
| Firmware completo AVR Mega 2560 | Compilação e link passaram | [build do firmware](../../firmware/speeduino-202501/build/build.log) |
| Documentos .dash originais | 24 / 49 / 49 componentes, recursos embutidos reconhecidos | [checksums dos arquivos](../reference/dashboards.sha256) |

Os testes Android exercitam seleção de GPS no VSS preservando os bits do pino, envio ao controlador simulado, invalidação após perda de fix, logging pelo ícone do .dash em tela cheia, seleção após recriação da Activity, recursos/fontes reais e persistência de ajuste. A regressão de imagem do LED verifica que a leitura digital de RPM continua visível no mostrador de 180°.

As seis capturas `*-live.png` e `*-offline.png` foram produzidas pelo próprio renderizador Android, sem edição. A fixture tem RPM 3500, MAP 100 kPa, TPS 25%, CLT 90 °C, IAT 25 °C, AFR 14,7 e GPS 100 km/h; entradas não fornecidas ficam indisponíveis. A referência disponível usa outros valores em alguns canais e contém estados iniciais de animação diferentes. Diferenças de valores, barras e posição da agulha não são uma comparação do mesmo instante. Fontes não especificadas no documento usam a fonte de sistema do Android; a rasterização pode diferir do desktop.

A renderização preserva coordenadas relativas, proporção do desenho, ordem de sobreposição e recursos dos arquivos. Não se afirma equivalência universal de todos os formatos, pintores de terceiros ou rasterização pixel a pixel. A importação aceita os pintores implementados do formato XML 3.0 e apresenta detalhes para recursos/pintores/ações não reconhecidos.

O envio físico de velocidade depende do firmware GPS VSS instalado na ECU. A negociação e o enquadramento CRC foram verificados com pares de teste; o retorno VSS foi verificado com controlador simulado. O firmware real foi compilado para Mega 2560. Não houve gravação ou validação em ECU física.

O primeiro acesso à tela cheia pode apresentar a instrução nativa do Android sobre sair do modo imersivo. O teste de interação reconhece esse aviso do sistema. A execução final foi feita em instância isolada para evitar o processo Shadow Dash travado que já existia no emulador anterior.
