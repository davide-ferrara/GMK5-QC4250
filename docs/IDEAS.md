# Idee per il launcher

Questa pagina raccoglie possibili evoluzioni del launcher. Le priorità restano
avvio affidabile, leggibilità su 1024×600 e compatibilità con retrocamera, CAN
bus e applicazioni OEM.

## Miglioramenti immediati

- Aggiungere una dissolvenza breve tra splash, animazione 3D e fotogramma
  statico, verificando che non compaiano frame neri.
- Evidenziare la pressione dei pulsanti con una leggera scala, un alone blu e
  un feedback aptico discreto.
- Aggiungere indicatori di pagina all'app drawer e uno scorrimento che si
  agganci a gruppi di 18 applicazioni.
- Permettere di fissare le applicazioni preferite all'inizio del drawer con una
  pressione prolungata.
- Mostrare un piccolo badge quando è disponibile una nuova versione del
  launcher.

## Home più utile

- Aggiungere un widget multimediale compatto con brano, artista, copertina e
  comandi precedente, play/pausa e successivo.
- Mostrare lo stato della connessione Android Auto, Bluetooth e rete senza
  riempire la schermata di indicatori.
- Integrare una scorciatoia configurabile nel dock, per esempio navigazione,
  telefono o musica.
- Offrire due layout selezionabili: automobile al centro oppure automobile a
  destra con widget informativi sulla sinistra.
- Inserire un saluto contestuale molto discreto durante l'avvio, seguito
  immediatamente dall'orologio.

## Aspetto e animazioni

- Adottare una modalità giorno/notte automatica, legata all'orario o, solo se
  disponibile in modo affidabile, allo stato delle luci del veicolo.
- Preparare varianti colore OEM sobrie: blu Volkswagen, rosso GTI e una
  modalità monocromatica.
- Applicare un leggero effetto di profondità all'automobile in risposta allo
  scorrimento tra le schermate, senza animazioni continue distraenti.
- Creare una variante del render con fari di posizione accesi per la modalità
  notte.
- Usare una breve animazione coordinata per l'apertura del drawer: dock che
  scende, titolo che appare e icone che entrano con pochi millisecondi di
  ritardo progressivo.

## Suono di benvenuto

- Creare un chime originale e sobrio di circa 0,5–1,5 secondi, più adatto di
  una musica completa all'avvio di un'auto moderna.
- Riprodurlo una sola volta per avvio del launcher o ciclo ACC, con
  un'impostazione visibile per disattivarlo.
- Non interrompere radio, navigazione o Android Auto: verificare lo stato
  dell'audio e richiedere il focus solo se il comportamento OEM lo consente.
- Provare volume, equalizzazione e tempi sul QC4250 reale; in caso di audio già
  attivo o avvio durante la retromarcia, non riprodurre alcun suono.

## App drawer

- Aggiungere cartelle opzionali come Media, Navigazione e Strumenti.
- Consentire di nascondere altre applicazioni dalla schermata informazioni,
  mantenendo sempre protetta la blacklist tecnica.
- Memorizzare la pagina corrente quando si apre un'app e ripristinarla al
  ritorno.
- Aggiungere una modalità di riordino manuale, mantenendo l'ordine alfabetico
  come impostazione predefinita.
- Visualizzare una piccola iniziale o un'icona generata quando un APK OEM non
  fornisce un'icona valida.

## Integrazione con l'auto

- Collegare il pulsante Radio all'activity OEM corretta dopo averla identificata
  e verificata sul dispositivo.
- Valutare widget di sola lettura per temperatura esterna, porte aperte e stato
  luci, usando esclusivamente API già esposte dai componenti OEM.
- Esplorare il CAN bus inizialmente in sola lettura: censire broadcast, service,
  provider e interfacce Binder esposti da `com.kyhero.car.myhost`,
  `com.kyhero.car.myhost2` e dagli adapter vendor, senza inviare comandi.
- Registrare e correlare pochi segnali innocui e verificabili — porte, luci,
  temperatura esterna, retromarcia e quadro acceso — prima di mostrare dati nel
  launcher; documentare sorgente, formato, frequenza e comportamento in caso di
  dato assente.
- Tenere ogni lettura CAN separata dalla UI principale e disattivabile, così un
  errore o un cambio del firmware OEM non può compromettere il launcher.
- Sospendere animazioni e interazioni non necessarie durante la retromarcia,
  lasciando piena priorità alla finestra della retrocamera.
- Verificare una modalità semplificata in movimento con bersagli tattili più
  grandi e meno funzioni secondarie.

## Aggiornamenti e manutenzione

- Collegare “Controlla aggiornamenti” alle release GitHub e mostrare versione,
  note e dimensione del download.
- Verificare firma e checksum dell'APK prima di proporre l'installazione.
- Conservare sempre un percorso visibile per ripristinare il launcher OEM.
- Aggiungere una pagina diagnostica esportabile con versione Android, modello,
  risoluzione, package target e risultato degli intent principali.
- Registrare solo errori tecnici essenziali in un log locale cancellabile,
  senza telemetria remota predefinita.

## Principi da mantenere

- Nessuna modifica o disattivazione automatica dei package CAN, retrocamera,
  MCU, audio o alimentazione.
- Tutte le funzioni principali devono restare utilizzabili se video, rete o
  applicazioni esterne non sono disponibili.
- Testare ogni release a 1024×600 su Android 11 prima della prova in auto.
- Preferire schermate semplici, leggibili con un colpo d'occhio e utilizzabili
  con tocchi grandi.
