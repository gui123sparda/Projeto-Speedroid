# Painéis personalizados — versão 1.2

Os três desenhos do projeto Java agora aparecem na aba **Painel**, no mesmo seletor dos painéis Speeduino:

- **Ford Escort Zetec 1.8**: carcaça grafite, mostradores claros, conta-giros até 7000 RPM, velocímetro até 220 km/h e face compartilhada de combustível/temperatura.
- **Ford Mondeo 2.0 (1997)**: carcaça escura com base reta, conta-giros até 8000 RPM, velocímetro até 240 km/h e instrumentos auxiliares nas extremidades.
- **Rally**: escala de 40 segmentos até 8000 RPM, marcha central, indicadores de estado e oito células de sensores.

São os desenhos originais de `TunerSpeeduino/src/main/java/org/clonetuner/ui/InstrumentPanelView.java`, portados para uma lista de comandos vetoriais executada pelo Canvas Android. As coordenadas, caminhos, agulhas, cores, gradientes e escalas foram preservados. Não são fotografias estáticas nem variações de cor do painel genérico. As fontes DejaVu Sans e DejaVu Sans Mono Bold estão incluídas no APK, com a licença correspondente.

O seletor guarda a opção escolhida; a tela cheia e a recriação da Activity usam o mesmo modelo e os mesmos extremos da sessão. O velocímetro segue a fonte VSS configurada, incluindo GPS. Leituras ausentes permanecem `--`, com agulhas no estado de indisponibilidade, conforme o projeto original. Hodômetros, combustível e sensores adicionais não recebem dados fictícios.

[Abra a comparação visual](index.html). As capturas Android `*-live.png` e `*-offline.png` são produzidas em 2400 × 1240 pelos testes instrumentados. A fixture usa 3500 RPM, 110 km/h, 50% de combustível, motor a 90 °C, terceira marcha e bateria 13,8 V. Os demais sensores ficam sem leitura. As imagens `desktop-*` vêm da verificação do projeto Java, com os mesmos valores principais. Antialiasing e alinhamento vertical de texto podem variar entre as plataformas.

Os testes verificam as quatro agulhas dos painéis analógicos, posições esperadas a 110 km/h, resposta ao RPM, as três faces claras do Escort e as quatro faces escuras do Mondeo, campos do Rally e estados sem dados. No Android também exercitam a seleção dos três modelos, persistência após recriação, tela cheia e a rasterização com as fontes reais.

## Verificação

A compilação do APK 1.2, a análise estática e os **34 testes unitários** passaram. Os **9 testes instrumentados** passaram no emulador isolado TunerDashReview, Android API 37. Consulte [o log do build](build-verification.log) e [o log Android](android-verification.log).
