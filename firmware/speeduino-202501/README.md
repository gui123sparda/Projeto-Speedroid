# Entrada GPS VSS para Speeduino 202501

Esta extensão permite receber a velocidade GPS do aplicativo Android pela mesma conexão USB ou Bluetooth usada pela telemetria. Não usa um quinto valor no campo `vssMode`, que só tem dois bits. A fonte GPS é negociada em tempo de execução e utiliza o modo existente **CAN/Serial/Analog (`vssMode = 1`)**.

O firmware Speeduino 202501 original não implementa esse comando na serial principal. O aplicativo verifica a capacidade antes de transmitir dados; uma ECU original responde que não oferece a extensão e continua disponível para telemetria. O aplicativo informa que a extensão é necessária, em vez de aparentar que a velocidade foi enviada.

## Arquivos

- `gps-vss.patch`: alterações sobre a tag oficial `202501`, incluindo o novo cabeçalho.
- `gps_vss.h`: estado da leitura e timeout, sem dependência do Android.
- `gps_vss_test.cpp`: testes executáveis no computador, incluindo ausência de sinal, velocidade zero, limites e rollover de `millis()`.
- `build/speeduino-202501-mega2560-gps-vss.hex`: firmware compilado para o alvo PlatformIO **megaatmega2560**, com a extensão aplicada.
- `build/build.log`: saída da compilação AVR.

O `.hex` corresponde ao alvo Mega 2560 padrão. Para outras placas ou variantes do Speeduino, compile o alvo correspondente; o aplicativo não grava firmware automaticamente.

## Compilar

No checkout do firmware oficial `202501`, aplique o patch e compile:

```bash
patch -p1 --dry-run < /caminho/TunerStudioAndroid/firmware/speeduino-202501/gps-vss.patch
patch -p1 < /caminho/TunerStudioAndroid/firmware/speeduino-202501/gps-vss.patch
pio run -e megaatmega2560
```

Teste o estado do sensor isoladamente nesta pasta:

```bash
g++ -std=c++11 -Wall -Wextra -Werror gps_vss_test.cpp -o /tmp/gps_vss_test
/tmp/gps_vss_test
```

Após instalar o firmware apropriado na ECU, selecione **Ajustes → VSS Mode e GPS → GPS do celular → ECU**, autorize localização precisa e grave o ajuste local para aplicar `vssMode = 1` na ECU. O estado do envio e a velocidade devolvida pela ECU aparecem abaixo do painel. Selecione qualquer modo VSS nativo para interromper o envio GPS.

## Protocolo v1

Todos os comandos usam o envelope Speeduino existente: comprimento big endian, payload e CRC32 big endian. Não há mistura com comandos sem enquadramento.

| Operação | Payload | Resposta de dados, após status |
| --- | --- | --- |
| Capacidade | `67 01 00` | `47 50 53 56 53 53 01` (`GPSVSS` e versão binária 1) |
| Atualização | `67 01 01 flags lo hi` | somente confirmação de status |

Os valores da tabela são hexadecimais. `flags` tem bit 0 para fonte GPS habilitada e bit 1 para fix válido. A velocidade é um U16 little endian, em décimos de km/h, limitada a 500,0 km/h. O canal VSS nativo da ECU continua inteiro em km/h, com arredondamento. Exemplos:

- 123,4 km/h: `67 01 01 03 D2 04`.
- GPS habilitado sem fix válido: `67 01 01 01 00 00`.
- Desabilitar a fonte GPS: `67 01 01 00 00 00`.

O aplicativo envia no máximo cinco atualizações por segundo, serializadas junto à telemetria. A transação completa de gravação/readback/burn tem prioridade e suspende os envios GPS até terminar.

O firmware só aceita habilitar GPS quando `vssMode = 1`. Pacotes inválidos ou modo incompatível retornam `0x84`. O aplicativo identifica essa resposta como configuração pendente. O firmware sem extensão retorna `0x83` à consulta; nenhuma atualização GPS é enviada a ele.

Sem fix válido ou sem atualização por mais de **1.500 ms**, a velocidade GPS usada pela ECU passa a **zero**. A fonte continua reservada ao GPS até receber o comando de desabilitação ou reiniciar a ECU, evitando reutilizar uma leitura auxiliar antiga. O aplicativo rejeita fixes antigos e não envia a predição do acelerômetro como se fosse uma medição GPS. No encerramento normal da conexão, envia desabilitação; na perda do cabo ou Bluetooth, o timeout atua na própria ECU.

## Validação

O patch foi aplicado sobre a tag oficial e a compilação completa `pio run -e megaatmega2560` passou. O teste C++ do estado do sensor passou com `-Werror`. Os testes Android verificam payloads, CRC, negociação/rejeição, frequência, perda de fix e retorno de VSS por um controlador simulado. A validação em ECU física permanece necessária; nenhum firmware foi instalado ou gravado automaticamente.

Referência: [Speeduino 202501](https://github.com/speeduino/speeduino/tree/202501), [serial principal](https://github.com/speeduino/speeduino/blob/202501/speeduino/comms.cpp), [leitura VSS](https://github.com/speeduino/speeduino/blob/202501/speeduino/sensors.cpp). A integração com o firmware segue sua licença GPL-3.0-or-later.
