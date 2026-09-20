# Radio OEM e possibile interfaccia sostitutiva

## Obiettivo

Valutare la realizzazione di un'app radio minimale per il QC4250, con le sole
funzioni essenziali:

- visualizzazione della frequenza corrente;
- ricerca automatica avanti e indietro (`seek`);
- cambio della stazione/frequenza;
- eventuali preferiti, solo in una fase successiva.

L'obiettivo iniziale non e riscrivere il driver del tuner. La soluzione piu
sicura e sostituire l'interfaccia grafica mantenendo, se necessario, i servizi
OEM che controllano tuner, MCU e percorso audio.

## Stato attuale

Nel censimento del dispositivo compaiono almeno tre componenti relativi alla
radio:

| Package | Nome/percorso noto | Stato |
|---|---|---|
| `com.acloud.stub.extradio` | GalaRadio | Candidato principale: il launcher lo avvia come radio OEM. |
| `com.caf.fmradio` | da verificare | Potenziale componente FM Qualcomm/AOSP o backend secondario. |
| `com.ex.dabplayer.pad` | `/vendor/app/DabPlayer/DabPlayer.apk` | Lettore DAB separato; non e l'obiettivo iniziale. |

`com.acloud.stub.extradio` e il target radio usato dal launcher ed e stato
confermato come frontend/backend OEM durante la prova del prototipo. Non sono
ancora verificati completamente:

- tutte le action oltre a frequenza e seek;
- l'interfaccia e il formato usati per lo stato RDS;
- eventuali permessi di sistema o `signature`;
- le dipendenze da MCU, CAN bus, servizi audio o altri package OEM.

Quindi non bisogna ancora disabilitare, sostituire o rimuovere
`com.acloud.stub.extradio`.

## Prototipo verificato sul dispositivo

Il modulo separato [`radio-app`](../radio-app/) implementa una prima interfaccia
minimale senza sostituire GalaRadio. La prova sul QC4250 ha verificato che:

- l'activity OEM e `com.acloud.stub.extradio/.QtActivity`;
- l'APK OEM si trova in
  `/odmdir/system/app/GalaRadio/GalaRadio.apk`;
- il servizio esportato e
  `com.acloud.stub.extradio/com.radio.service.RadioService`;
- `xy.android.fmradio.frd` avvia la ricerca in avanti;
- `xy.android.fmradio.rev` e l'action corrispondente per la ricerca indietro,
  individuata nel codice OEM e disponibile nel prototipo;
- il servizio pubblica `xy.update.freq` con l'extra intero `freq`;
- il valore verificato `87900` corrisponde a `87,9 MHz`;
- il servizio accetta il comando da una normale app utente: durante la prova
  non sono comparse `SecurityException`;
- GalaRadio resta installata, abilitata e fornisce il backend nativo.

Il test di ricerca avanti ha aperto correttamente il device radio, richiesto
l'audio focus, sintonizzato `87,9 MHz` e aggiornato la UI del prototipo. Il
package installato per la prova e `com.golfv.radio` versione `0.3.0`.

La versione 0.2 legge inoltre il Program Service RDS dalla diagnostica
`McuRadio`: sul dispositivo e stato verificato il nome `SPORTIVA` alla
frequenza `88,5 MHz`. Offre otto preset locali, richiamabili con un tocco e
memorizzabili con una pressione lunga. Il richiamo usa l'action OEM
`com.android.xygala.ACTION_CONTROL_RADIO`, metodo `method_setFreq` ed extra
`param_freq`. Il pomello fisico continua ad aggiornare l'interfaccia tramite
`xy.update.freq`.

## Valutazione di fattibilita

La UI richiesta e semplice da implementare. La fattibilita complessiva dipende
invece dall'interfaccia tra l'app OEM e l'hardware radio.

### Scenario A: API OEM accessibile

L'app GalaRadio potrebbe comandare un servizio esportato o inviare broadcast
espliciti per operazioni come accensione, spegnimento, seek e selezione della
frequenza. In questo caso una nuova app installabile normalmente potrebbe usare
la stessa interfaccia.

Questo e lo scenario migliore: GalaRadio rimarrebbe installata come backend, o
potrebbe persino non essere necessaria se il servizio risiede in un altro
package.

### Scenario B: backend riutilizzabile ma protetto

Il tuner potrebbe essere esposto tramite un servizio Binder, provider o API
vendor con permessi privilegiati. Sarebbe ancora possibile creare una nuova
interfaccia, ma potrebbe servire una delle seguenti condizioni:

- firma compatibile con quella del firmware;
- installazione come app di sistema/privilegiata;
- modifica controllata dell'APK OEM;
- un piccolo bridge eseguito con privilegi maggiori.

Una normale app utente potrebbe non essere autorizzata a invocare direttamente
queste API.

### Scenario C: logica integrata nell'APK o in librerie native

GalaRadio potrebbe comunicare direttamente con una libreria JNI, un device
node, una porta seriale o la MCU. In tal caso una riscrittura completa sarebbe
piu rischiosa: bisognerebbe ricostruire il protocollo e riprodurre anche la
gestione della sorgente audio, dello stato ACC e dei pulsanti fisici.

Prima di arrivare a questo scenario conviene verificare se sia possibile
modificare soltanto la UI dell'APK OEM oppure pilotarne le funzioni esistenti.

## Perche conservare inizialmente l'app OEM

Su una head unit la radio non coincide necessariamente con una normale app
Android. Il package OEM puo occuparsi anche di:

- selezione della sorgente audio radio;
- audio focus, mute e ripristino del volume;
- accensione e spegnimento con ACC;
- ripristino dell'ultima frequenza dopo il boot;
- tasto RADIO/MODE e comandi al volante;
- scansione e memorizzazione delle stazioni;
- RDS, AF, TA e scelta della banda;
- comunicazione con MCU o servizi vendor.

Disabilitarlo prima di avere identificato il backend potrebbe lasciare il tuner
acceso senza controllo, interrompere l'audio o rompere i pulsanti hardware. La
prima versione della nuova app dovrebbe quindi convivere con GalaRadio.

## Indagine ADB proposta

Tutte le operazioni di questa sezione sono di sola lettura, ad eccezione della
copia dell'APK dal dispositivo al computer.

### 1. Identificare package, APK e componenti

```sh
adb shell pm path com.acloud.stub.extradio
adb shell dumpsys package com.acloud.stub.extradio
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  com.acloud.stub.extradio
adb shell pm list packages -f | grep -i -E 'radio|dab'
```

Ripetere le verifiche almeno per `com.caf.fmradio` e
`com.ex.dabplayer.pad`, senza avviarli o disabilitarli alla cieca.

### 2. Osservare la radio durante l'uso

Aprire GalaRadio e provare manualmente seek avanti, seek indietro e cambio
stazione, raccogliendo contemporaneamente:

```sh
adb shell dumpsys activity activities
adb shell dumpsys activity services | grep -i -A 8 -B 8 radio
adb shell dumpsys media_session
adb shell dumpsys audio
adb logcat -c
adb logcat
```

Nel log cercare package, action di broadcast, nomi di servizi, frequenze e
parole come `radio`, `tuner`, `seek`, `freq`, `band`, `mcu`, `source`, `rds` e
`acloud`. E utile annotare l'ora esatta di ogni pressione per correlare gli
eventi.

Se `logcat` e troppo rumoroso, prima identificare il PID:

```sh
adb shell pidof com.acloud.stub.extradio
adb logcat --pid=PID
```

Il filtro per PID puo pero nascondere messaggi emessi da un servizio residente
in un altro processo; va confrontato con almeno una cattura globale.

### 3. Copiare e analizzare GalaRadio

`pm path` restituisce una o piu righe nel formato `package:/percorso/app.apk`.
Copiare ogni APK indicato, inclusi eventuali split:

```sh
adb pull /percorso/restituito/GalaRadio.apk GalaRadio.apk
sha256sum GalaRadio.apk
```

L'analisi statica deve controllare:

- manifest, activity, service, receiver e provider;
- componenti `exported` e relativi permessi;
- intent action e broadcast registrati nel codice;
- chiamate `bindService`, Binder/AIDL e content provider;
- riferimenti a frequenze, bande FM/AM, seek, scan e RDS;
- librerie `.so`, JNI e classi vendor;
- accessi a device node, socket, porte seriali o servizi di sistema;
- dipendenze verso `com.xyauto.services`, CAN bus, MCU o DSP;
- controlli sulla firma o sull'UID del chiamante.

Gli APK OEM sono proprietari e non devono essere aggiunti al repository. Nel
repository vanno salvati soltanto package ID, componenti, hash, percorsi sul
dispositivo e risultati dell'analisi.

### 4. Osservare IPC e broadcast

Se l'analisi individua action o servizi plausibili, verificarli prima in modo
passivo. Non inviare comandi non compresi a MCU, CAN bus o servizi audio.

Per ciascuna interfaccia candidata bisogna documentare:

| Operazione | Comando/API | Risultato osservato | Reversibile |
|---|---|---|---|
| Leggere la frequenza | da scoprire | — | si |
| Seek avanti | da scoprire | — | si |
| Seek indietro | da scoprire | — | si |
| Impostare frequenza | da scoprire | — | si |
| Attivare sorgente radio | da scoprire | — | da verificare |
| Uscire dalla radio | da scoprire | — | da verificare |

## Architettura consigliata

La soluzione preferita e una nuova app separata, per esempio con package
`com.golfv.radio`, composta da:

1. una UI minimale ottimizzata per 1024x600 landscape;
2. un'interfaccia interna `RadioController` indipendente dalla UI;
3. un'implementazione OEM che usa soltanto le API verificate;
4. uno stato osservabile con frequenza, ricezione, ricerca in corso ed errori;
5. un controller finto per sviluppo e test sul PC/emulatore.

Separare UI e backend evita di legare il progetto a ipotesi non ancora
confermate. Permette inoltre di realizzare e testare subito la schermata, mentre
il controller reale viene aggiunto dopo l'analisi del firmware.

La prima UI dovrebbe contenere soltanto:

- frequenza corrente molto leggibile;
- nome RDS, se disponibile;
- pulsante seek precedente;
- pulsante seek successivo;
- pulsante scansione;
- indicatore chiaro quando il backend non e disponibile.

L'inserimento manuale della frequenza e i preferiti possono essere aggiunti
dopo che lettura e cambio stazione funzionano in modo affidabile. I controlli
devono essere grandi e utilizzabili in auto senza menu profondi.

## Piano incrementale

1. Identificare con certezza APK, activity e processo di GalaRadio.
2. Raccogliere log correlati alle sole operazioni di lettura e seek.
3. Analizzare manifest e codice senza modificare il dispositivo.
4. Classificare l'accesso al tuner secondo gli scenari A, B o C.
5. Creare un piccolo proof of concept che legga lo stato senza comandare
   l'hardware.
6. Provare un solo comando reversibile, inizialmente seek avanti.
7. Verificare audio, ACC, tasti fisici, comandi al volante, ZLink e retromarcia.
8. Implementare l'app minimale e collegarla al pulsante Radio del launcher.
9. Conservare GalaRadio finche tutti i comportamenti integrati non sono stati
   verificati.

## Criteri di accettazione

La nuova app puo essere considerata utilizzabile quando:

- mostra la frequenza reale e non uno stato locale simulato;
- seek avanti e indietro comandano il tuner una sola volta per pressione;
- l'audio passa correttamente alla sorgente radio;
- uscire e rientrare non lascia audio o tuner in uno stato incoerente;
- ACC off/on ripristina uno stato valido;
- i tasti fisici e al volante continuano a funzionare oppure la limitazione e
  esplicitamente accettata;
- retromarcia, CAN bus e Android Auto non subiscono regressioni;
- un backend assente o non autorizzato produce un errore visibile senza crash;
- il rollback consiste semplicemente nel riaprire GalaRadio e ripristinare il
  target del launcher.

## Conclusione provvisoria

La sostituzione della sola interfaccia e fattibile: il proof of concept comanda
gia il seek e riceve la frequenza reale riutilizzando il backend OEM. La
strategia con il rischio minore resta mantenere GalaRadio e i componenti
originali installati, aggiungendo soltanto le operazioni OEM osservate e
verificate una alla volta.
