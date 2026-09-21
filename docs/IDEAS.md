# Idee per il launcher

Queste proposte partono esclusivamente dai segnali CAN già verificati su questa
Golf Mk5 / QC4250: velocità, quattro sportelli, freno a mano, luci esterne e
retromarcia. Il launcher deve restare sempre in sola lettura e non interferire
con retrocamera, MCU o servizi OEM.

## Render della Golf vivo

- Creare un render della Golf coerente con la vista attuale, con fari spenti e
  accesi.
- Usare 16 render completi dall’alto per le combinazioni dei quattro sportelli,
  senza livelli separati per le porte. I bit 1, 2, 4, 8 indicano rispettivamente
  anteriore guidatore, anteriore passeggero, posteriore guidatore e posteriore
  passeggero; selezionano `golf_top_00.webp` … `golf_top_15.webp`.
- Con almeno uno sportello aperto mostrare la vista dall’alto; con tutti chiusi
  tornare alla vista attuale senza ripetere l’intro. La velocità in movimento
  mantiene la precedenza. In Info è disponibile una simulazione solo visiva,
  compresa la vista dall’alto con tutti chiusi, e il ritorno ad Automatico CAN.
- [x] Renderizzato l’overlay dei fari dalla stessa camera ortografica dei 16
  render, con bloom e dissolvenza collegati al CAN e all’anteprima luci in Info.
  Le luci rimangono l’unico overlay: nessuna duplicazione delle 16 immagini
  per luci on/off. L’overlay segue scala e centratura della vista dall’alto.
- Tenere una variante di fallback già composta per i dispositivi su cui la
  composizione o il video 3D risultassero troppo pesanti.
- Quando luci e sportelli cambiano insieme, applicare una breve dissolvenza
  invece di un cambio secco; la transizione deve restare leggibile e non deve
  introdurre frame neri.

## Modalità in movimento

- Quando il valore assoluto della velocità CAN è maggiore di zero, nascondere
  il render e mostrare al centro soltanto la velocità, in caratteri molto
  grandi e ad alto contrasto.
- In retromarcia mantenere il valore come stato interno firmato, ma trattare
  comunque l'auto come in movimento; la retrocamera OEM conserva sempre la
  priorità visiva.
- Verificare sul veicolo che l'aggiornamento sia continuo. Se il bridge CAN
  invia eventi solo al cambio di valore, non azzerare troppo presto la velocità
  visualizzata e conservare l'ultimo valore diagnostico nella pagina Info.
- Ridurre l'interfaccia a velocità non nulla: nessuna nuova animazione, dock
  invariato e bersagli tattili già grandi.

## Stati utili da mostrare

- Aggiungere alla pagina Info una diagnostica compatta: velocità CAN ricevuta,
  stato dei quattro sportelli, luci e freno a mano.
- Mostrare nel render un indicatore discreto del freno a mano inserito quando
  l'auto è ferma; non trasformarlo in un avviso di sicurezza o in un comando.
- Usare lo stato luci per scegliere render diurno/notturno e per attenuare
  leggermente lo sfondo, senza affidarsi all'orario.
- Quando una porta è aperta, evidenziarla sul render e mostrare il suo numero
  (1 guidatore, 2 passeggero anteriore, 3 posteriore guidatore, 4 posteriore
  passeggero) nella pagina Info.

## Dati derivati, con etichetta chiara

- Calcolare una distanza di sessione integrando la velocità CAN solo mentre il
  launcher è attivo. Va chiamata *distanza sessione*, non odometro né trip MFA:
  il quadro installato non dispone di MFA e non abbiamo un segnale CAN
  confermato per chilometri totali o viaggio.
- Conservare opzionalmente l'ultima velocità ricevuta e l'ora dell'ultimo frame
  per rendere immediati i test dopo essersi fermati.
- Non stimare marce, carburante, temperatura, chiusura centralizzata o RPM
  finché non esiste una mappa CAN ripetuta e calibrata. Le prove attuali non li
  rendono dati affidabili.

## Affidabilità e prove

- Definire una tabella di test manuale per ogni variante visiva: tutte le
  porte, luci on/off, freno a mano e passaggio fermo/in movimento.
- Se un callback CAN scompare o un frame è sconosciuto, mantenere il render
  normale e l'app utilizzabile; mai bloccare HOME o tentare una ritrasmissione.
- Tenere aggiornata [CANBUS.md](./CANBUS.md) con frame, scala, test svolto e
  livello di confidenza prima di collegare un nuovo segnale alla UI.
