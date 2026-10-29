# Revisão de funcionalidades e tabelas

Correções desta revisão:

- Ajustes deixa de apresentar subpainéis internos como funcionalidades independentes. A busca continua encontrando esses painéis e não corta resultados em 100 itens.
- Subpainéis de mapas e curvas abrem o editor correspondente, em vez de aparecerem como indisponíveis.
- O parser reconhece condições de visibilidade de painéis com ou sem posição de layout. Edições reconstroem os campos condicionais do diálogo aberto.
- O botão `reqFuelCalc` do INI abre a calculadora local. Links de ajuda são encaminhados ao navegador.
- Comandos desconectados, desconhecidos ou com falha de transmissão retornam erro ao chamador, chegando ao aviso da sessão. Não são apresentados como enviados com sucesso.
- Mapas e curvas usam a mesma estrutura de tela e células adaptáveis. O mapa inteiro ocupa a área disponível, com Y crescente para cima e X crescente para a direita, na base. As curvas mostram pares com X crescente para cima.
- Os nomes dos eixos definidos por `xyLabels` são exibidos. Sem rótulos explícitos, os eixos usam os nomes dos respectivos bins. Os números respeitam as casas decimais do INI.
- Células e eixos continuam vinculados aos índices originais da ECU, inclusive após ordenação visual e edição.
- O início/parada de CSV no painel e em Logs compartilha a mesma função.

## Verificação

```sh
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest
```

As regressões novas estão em `MapAndSettingsTest`, `PanelVisibilityTest`, `TableLabelsTest` e `CommandFailureTest`. Os testes de interface verificam as 256 células de um mapa 16×16 sem recortes, sua origem inferior esquerda, edição do endereço correspondente, exibição completa de curva, agrupamento de ajustes e abertura da calculadora pelo INI.

A suíte existente cobre interpretação INI, MSQ, protocolo/CRC, gravação e readback, serial fragmentada e desconexão, CSV e análise de combustível, cálculo de combustível, velocidade e GPS/VSS, persistência e painéis.

A comunicação é exercitada com controladores simulados. Adaptadores USB/Bluetooth, aquisição GPS em campo e gravação em ECU física precisam de validação em bancada. Widgets externos do INI sem editor específico continuam identificados como informação, conforme documentado no README principal.

Capturas de mapas completos: [retrato](map-portrait.png) e [paisagem](map-landscape.png). Os valores das capturas são de uma sessão simulada de teste.

Resultado final: 40 testes unitários e 15 testes de interface aprovados no emulador Pixel 7/API 37, sem falhas ou testes ignorados. `assembleDebug` concluído; lint com 0 erros e 29 avisos. Veja [saída completa do Gradle](gradle-verification.log).
