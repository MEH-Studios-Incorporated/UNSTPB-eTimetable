# Ghid de administrare

Pentru secretariatul și administratorii FAIMA care pun orarul și anunțurile în
aplicație. Panoul de administrare este la adresa serverului urmată de
`/admin`, de exemplu `https://orar.faima.upb.ro/admin`.

## Prezentare

Prima pagină după conectare arată:

- săptămâna de azi (numărul, paritatea, zilele ei, a câta din semestru și
  când începe următoarea vacanță) și semestrul curent, cu numărul de clase,
  grupe și conturi de studenți și ultima modificare a orarului;
- **Acum în facultate**: clasele care se țin acum, cu sala și cadrul
  didactic (o clasă fără sală apare cu roșu); când nu e nicio clasă, cele
  care urmează azi;
- problemele de orar deschise, cele mai noi primele;
- **De făcut**: clase fără profesor sau fără sală, săli sau profesori cu
  două clase în același timp, profesori fără email; fiecare rând trimite
  unde se rezolvă;
- evenimentele următoare;
- pentru administratori, ultimele modificări din jurnal și un rezumat de
  securitate: ultimele autentificări, sesiunile tale, câte conturi au
  autentificarea în doi pași și câte parole greșite au fost în ultima zi.

Bara de sus arată săptămâna de predare, ca în aplicație, și semestrul
curent. Butonul **Temă** trece panoul între tema luminoasă și cea
întunecată; până îl apeși, panoul urmează setarea calculatorului. În meniu,
lângă **Probleme de orar**, apare câte probleme așteaptă verificarea.

Listele lungi (materii, profesori, studenți, probleme de orar) au pagini:
jos se vede ce rânduri sunt afișate, de exemplu "11-20 din 243", câte
rânduri să arate o pagină (10, 25 sau 50) și linkurile spre celelalte
pagini. Căutarea și filtrele se păstrează când schimbi pagina.

Adresa serverului fără `/admin` este pagina publică a aplicației, pentru
studenți: de unde se descarcă și ce face. Are un link spre panou.

## Conturi

Primul cont se creează la instalarea serverului (vezi `docs/deploy.md`).
Din pagina **Administratori** acesta adaugă celelalte conturi:

- Administrator: tot, inclusiv conturile și jurnalul.
- Editor: semestre, orar, anunțuri, evenimente și problemele de orar.

Un cont nou primește o parolă temporară, afișată o singură dată celui care
îl creează. Dă-o persoanei în persoană sau la telefon. La prima conectare,
panoul îi cere o parolă a ei, de cel puțin 12 caractere și fără adresa de
email în ea.

Pe pagina unui cont, un administrator îi poate schimba rolul, îi poate da o
parolă temporară nouă, îi poate închide sesiunile, îi poate opri
autentificarea în doi pași (dacă și-a pierdut telefonul), îl poate
dezactiva sau șterge. Ultimul administrator activ nu poate fi retrogradat,
dezactivat sau șters, iar nimeni nu își face asta singur.

### Contul meu

Din linkul **Contul meu** de jos din meniu fiecare:

- își schimbă parola (cu parola actuală); celelalte sesiuni se închid;
- vede sesiunile deschise (browser, adresă, de când) și le poate închide;
- vede ultimele autentificări, inclusiv cele greșite;
- pornește autentificarea în doi pași: scanează codul QR cu o aplicație
  de autentificare (Google Authenticator, Microsoft Authenticator, Aegis),
  scrie codul de 6 cifre și păstrează cele 10 coduri de rezervă. De atunci,
  după parolă, panoul cere codul din aplicație.

O sesiune durează 12 ore. Cinci parole sau coduri greșite în 15 minute
blochează contul 15 minute.

Pe pagina **Administratori**, un administrator care folosește el însuși
autentificarea în doi pași o poate cere pentru toate conturile. Atunci un
cont fără ea ajunge, după parolă, la pagina unde o pornește și nu poate
deschide altceva până nu termină; cine o are nu o mai poate opri. Pagina
arată câte conturi active nu au pornit-o încă.

### Jurnal

Pagina **Jurnal** (doar pentru administratori) arată cine a schimbat ce și
când: conturi, orar, anunțuri, evenimente, probleme de orar și conturi de
studenți. Se poate filtra după categorie și persoană și se poate căuta în
text. Intrările nu se pot modifica sau șterge din panou.

### Tokenuri API

Un program (de exemplu un agent care pregătește orarul) poate trimite
fișierul cu orarul fără browser. Se identifică cu un token, pe care îl
creează un administrator pe pagina **Tokenuri API**:

1. **Token nou**: un nume după care îl recunoști ("Agentul de orar"),
   drepturile (deocamdată doar `timetable:write`, adică înlocuirea orarului
   unui semestru) și, opțional, ziua în care expiră.
2. Tokenul (începe cu `ett_`) apare o singură dată. Copiază-l atunci în
   programul care îl folosește; serverul păstrează doar amprenta lui
   SHA-256, așa că nu ți-l mai poate arăta.
3. Lista arată când a fost creat, de cine, când expiră și când a fost
   folosit ultima dată. **Revocă** îl oprește pe loc.

Crearea, revocarea și fiecare import făcut cu un token apar în **Jurnal**;
importul apare sub numele tokenului. Programul trimite același fișier ca la
import, cu aceeași verificare (vezi `docs/data-model.md`, "Admin API"):

```
curl -X POST \
  -H "Authorization: Bearer ett_..." \
  -H "Content-Type: text/csv" \
  --data-binary @orar.csv \
  "https://orar.faima.upb.ro/api/admin/v1/semesters/2026-1/timetable?dry_run=true"
```

Cu `dry_run=true` primește doar diferențele și erorile; fără el, orarul se
înlocuiește dacă fișierul nu are erori.

## Un semestru nou

1. **Semestre → Semestru nou.** Codul (de exemplu `2026-2`) rămâne același
   tot semestrul. Bifează "Copiază grupele, materiile și orarul semestrului
   curent" dacă orarul seamănă cu cel de acum; altfel semestrul pornește gol.
2. **Calendarul.** Prima și ultima zi de cursuri, apoi vacanțele, câte una pe
   rând:

   ```
   2026-12-21 2027-01-03 Vacanța de iarnă | Winter break
   2026-12-01 Ziua Națională | National Day
   ```

   O zi liberă are o singură dată. Textul de după `|` este numele în engleză,
   pentru studenții Erasmus.

   Săptămânile se numerotează de la prima săptămână a semestrului. O
   săptămână în întregime în vacanță nu primește număr, așa că după vacanța
   de iarnă numărătoarea continuă. Dacă facultatea schimbă paritatea unei
   săptămâni, se scrie o zi din acea săptămână și paritatea dorită:

   ```
   2026-11-30 pară
   ```

3. **Importă orarul** din fișierul descris mai jos, pas cu pas (vezi
   "Importul pas cu pas").
4. Verifică orarul unei grupe din lista **Grupe**.
5. **Fă-l curent** din lista de semestre. Abia atunci îl văd studenții, la
   următoarea sincronizare.

## Modificări fără import

Orarul se poate corecta direct în panou, fără să mai treci prin fișier.
Fiecare salvare ajunge în aplicații la următoarea sincronizare. Un import
ulterior înlocuiește tot semestrul, inclusiv aceste modificări, așa că
descarcă orarul actual înainte de a-l modifica în Excel.

- **Materii.** Lista se caută după nume sau cod și se filtrează după
  specializare și an. Pe pagina unei materii se schimbă numele (în română și
  în engleză) și codul, se văd toate activitățile ei și se adaugă una nouă.
  "Profesor pentru mai multe activități" trece același profesor la toate
  laboratoarele (sau seminarele, proiectele) materiei, doar acolo unde nu e
  trecut nimeni dacă lași bifa. Așa se completează asistenții pe care orarul
  facultății nu îi are.
- **O activitate.** Butonul "Editează" de lângă orice activitate (sau o
  activitate din săptămâna unei grupe) deschide formularul: materia, tipul,
  ziua, orele, săptămânile (`toate`, `pare`, `impare` sau numere ca în
  fișier), profesorul, sala, grupele și dacă e deschisă studenților Erasmus.
  Grupele se scriu cu codul lor, despărțite prin spațiu: `1521A 1522A` sau
  `1521A/A` pentru o semigrupă. O sală nouă se adaugă singură.
- **Profesori.** Lista arată cine nu are email. Pe pagina unui profesor se
  schimbă titlul, numele, emailul, biroul și consultațiile, câte una pe rând:

  ```
  Miercuri 14:00-16:00 EC210 impare
  Vineri 10:00-12:00
  ```

  Un profesor fără activități se poate șterge; unul nou se adaugă de pe
  listă.
- **Săli.** Fila **Săli** este un tabel cu toate sălile: corpul, locurile,
  câte activități are fiecare și câte ore pe săptămână e ocupată (o
  activitate din săptămânile pare sau impare contează pe jumătate). Se
  caută după cod și se filtrează după corp. Fila **Corpuri** listează
  corpurile cu adresa și numărul de săli; numele și adresa unui corp se
  schimbă pe pagina lui. Pagina unei săli arată săptămâna ei pe ore, deci și
  orele în care e liberă.
- **Studenți** (doar administratorii). Conturile, după email sau nume, cu
  grupa aleasă. **Schimbă** pune o parolă nouă pentru cine și-a pierdut-o și
  îl deconectează de pe toate telefoanele; spune-i parola pe altă cale.
  **Șterge** șterge contul de tot.

## Fișierul cu orarul

Un tabel (Excel, LibreOffice sau Google Sheets) salvat ca CSV, cu un rând pe
activitate. Cel mai simplu este să descarci orarul actual din pagina
semestrului ("Descarcă orarul actual"), să îl modifici și să îl imporți
înapoi. Un exemplu complet este în
[`examples/orar-exemplu.csv`](examples/orar-exemplu.csv).

Importul înlocuiește tot orarul semestrului. Dacă un rând are o greșeală, nu
se salvează nimic și previzualizarea spune ce rând și ce anume. O sală sau un
profesor în două locuri în același timp apar ca avertismente; orarul se
salvează oricum.

### Importul pas cu pas

Din pagina semestrului ("Importă orarul") sau de la adresa
`/admin/semesters/<cod>/import`:

1. **Încarcă.** Alegi fișierul CSV sau Excel (`.xlsx`). Din Excel se citește
   prima foaie; orele și datele formatate ca ore și date se citesc cum se
   văd (09:00, 2026-11-10). Fișierele `.xls` vechi trebuie salvate întâi ca
   `.xlsx` sau "CSV UTF-8".
2. **Coloane.** Panoul caută coloanele de mai jos după numele din primul
   rând al fișierului, fără să țină cont de litere mari și diacritice; merg
   și nume românești ca `zi`, `ora început`, `sală`, `materie`, `grupe`.
   Dacă le-a găsit pe toate cele obligatorii, un singur clic duce mai
   departe. Altfel alegi, pentru fiecare coloană, coloana din fișier care o
   conține. Sub listă se văd primele rânduri din fișier.
3. **Previzualizare.** Diferența dintre orarul de acum al semestrului și
   cel din fișier: câte activități se adaugă, se modifică și se elimină
   (cifrele sunt și filtre), apoi lista lor, cu valoarea veche tăiată și
   cea nouă evidențiată. Rândurile cu erori apar în aceeași listă, cu
   numărul lor din fișier. Nimic nu se salvează încă.

   Sub listă, **Pe orarul grupei** arată săptămâna unei grupe după import,
   pe aceeași grilă ca în Gestionare orar. Lista de grupe le cuprinde doar
   pe cele atinse de import, cu numărul de modificări; implicit e aleasă
   grupa cu cele mai multe. Activitățile adăugate au chenar verde și
   eticheta "Adăugată", cele modificate sunt galbene ("Modificată"), iar
   cele eliminate rămân pe grilă estompate, cu chenar punctat roșu
   ("Eliminată").

   **Studenți afectați** spune câte conturi de studenți ating
   modificările, fără nume sau adrese: studenții naționali a căror grupă
   sau semigrupă (cea aleasă în aplicație) are o activitate adăugată,
   modificată sau eliminată; studenții Erasmus care au ales o activitate
   modificată sau eliminată; studenții care au o activitate eliminată
   printre cele adăugate în plus sau printre cele la care au renunțat. Un
   student poate intra în mai multe categorii, dar în total se numără o
   singură dată. Numărătoarea folosește alegerile salvate acum în aplicație,
   chiar dacă importul este pentru alt semestru decât cel curent.
4. **Confirmă.** Bifezi "Am verificat diferențele" și apeși **Importă
   orarul**. Dacă între timp cineva a modificat orarul semestrului (de
   exemplu din Gestionare orar), panoul refuză și te trimite înapoi la
   previzualizare, ca să vezi diferențele față de orarul nou.

Un fișier cu erori nu se poate confirma. Fiecare rând cu erori are în
previzualizare linkul **Corectează**: se deschide un formular cu celulele
rândului, câte una pentru fiecare coloană potrivită la pasul 2. După
**Salvează rândul**, rândul se rescrie în fișierul importului (restul
fișierului și numele lui rămân cum erau) și previzualizarea se calculează
din nou. Doar cine a încărcat fișierul îl poate corecta, iar corecturile nu
apar separat în **Jurnal**: apare importul, la confirmare. Un rând nu se
poate goli de tot. Poți și să corectezi fișierul pe calculator și să îl
încarci din nou.

La confirmare, panoul spune și câți studenți aveau printre alegeri
(Erasmus sau activități adăugate în plus) o activitate eliminată. După
import aplicația nu le mai arată, iar studenții își aleg altele.
Fișierul încărcat se păstrează 24 de ore între pași; **Renunță la import**
îl șterge pe loc. Importul confirmat apare în **Jurnal**, cu câte activități
s-au adăugat, modificat și eliminat.

| Coloană | Ce conține | Exemplu |
| --- | --- | --- |
| `id` | Codul activității. Gol la rândurile noi; nu îl schimba la cele existente, alegerile studenților Erasmus îl folosesc. | `calit-l-1541A` |
| `programme` | Codul specializării | `IEDEEE` |
| `programme_name` | Numele specializării (o dată ajunge) | `Inginerie economică ...` |
| `year` | Anul de studiu | `4` |
| `series` | Seria | `E` |
| `audience` | Grupele sau semigrupele, separate prin spațiu. Gol pentru toată seria. | `1541E/A` sau `1541E 1542E` |
| `subject_code` | Codul materiei, același la curs, seminar și laborator | `CALIT` |
| `subject_ro`, `subject_en` | Numele materiei în română și engleză (o dată ajunge) | `Managementul calității` |
| `type` | `curs`, `seminar`, `laborator`, `proiect` sau `consultații` | `laborator` |
| `day` | Ziua | `miercuri` |
| `start`, `end` | Orele | `12:00`, `14:00` |
| `weeks` | `toate`, `impare`, `pare` sau numerele săptămânilor | `impare`, `1-7`, `2,4,6` |
| `professor` | Cine ține rândul: titularul la curs, asistentul la seminar și laborator | `Conf. dr. ing. Ioana Marinescu` |
| `professor_email` | Opțional | |
| `room` | Codul corpului lipit de număr; campusul după sală; sălile cu nume rămân cum sunt | `BN212b`, `L201 Polizu`, `Sala Sport` |
| `erasmus` | `da` dacă studenții Erasmus pot alege activitatea | `da` |

Rândurile de tip `consultații` au nevoie doar de zi, ore, săptămâni,
profesor și, opțional, sală. Ele apar la profesor, nu în orarul grupelor.

La import, spațiile dintre corp și număr se scot singure: `BN 212 b` devine
`BN212b`, iar `a 139 Polizu` devine `A139 Polizu`.

Excel în română salvează CSV cu punct și virgulă; serverul acceptă și virgulă.
Diacriticele se păstrează dacă fișierul este salvat ca "CSV UTF-8".

## Gestionare orar în browser

**Gestionare orar** caută după materie, profesor, sală sau grupă, cu filtre
pentru zi și activități fără sală sau profesor, în pagini de 10, 25 sau 50
de rânduri; căutarea cuprinde toate paginile. Bifează activitățile de
modificat de pe pagina deschisă (căsuța din capul tabelului le bifează pe
toate de pe pagină), alege sala sau profesorul și, opțional, deplasarea
în minute. **Previzualizează modificările** arată noile ore și suprapunerile
noi. Numai **Confirmă și salvează** le publică. Dacă alt editor a modificat
orarul între timp, reîncarcă pagina și verifică selecția. Linkul fiecărei
activități deschide editorul complet.

Fila **Săptămână** arată orarul ca în aplicație: zilele de luni până vineri,
orele de la 08:00 la 20:00 (mai mult dacă o activitate iese din interval) și
fiecare activitate ca un bloc în culoarea materiei, cu tipul, sala și ora.
Sus alegi o grupă sau o semigrupă, o sală sau un profesor. **Toate**,
**Impare** și **Pare** estompează activitățile care nu au loc în săptămânile
alese; cele marcate "Impară" sau "Pară" au loc doar o dată la două
săptămâni. Activitățile care se suprapun stau una lângă alta, ca
laboratoarele celor două semigrupe. Un clic pe un bloc deschide lângă
săptămână formularul activității, cu suprapunerile ei cu alte activități
ale aceleiași săli, ale aceluiași profesor sau ale acelorași grupe. După
**Salvează** rămâi în aceeași săptămână, cu activitatea selectată. Pe
telefon săptămâna se derulează în lateral, iar formularul apare deasupra ei.

Pagina unei grupe, a unei săli și a unui profesor arată aceeași săptămână;
la sală și la profesor apar și consultațiile, ca blocuri gri.

## Probleme de orar

Studenții raportează o problemă din aplicație, de pe pagina unei clase.
Pagina **Probleme de orar** are filele Deschise și Închise, o căutare după
materie, student sau text și, când sunt rapoarte din mai multe semestre,
un filtru de semestru. **Exportă CSV** descarcă lista filtrată.

Un clic pe un rând deschide alături clasa așa cum o vedea studentul când a
raportat, ce s-a schimbat de atunci la ea (ora, săptămânile, sala,
profesorul), ce a scris studentul și dacă mai există alte rapoarte deschise
pentru aceeași clasă. **Corectează clasa** deschide editorul clasei și
întoarce aici după salvare; apoi **Marchează corectată**. **Respinge**
închide problema fără schimbări.

## Publicarea anunțurilor

**Anunțuri** arată anunțurile publicate într-un tabel, cele mai noi primele,
câte 10, 25 sau 50 pe pagină. De acolo un anunț se editează, se fixează sau
se șterge. **Anunț nou** deschide pagina de scris: formularul în stânga și, în
dreapta, un telefon care arată anunțul așa cum apare în aplicație. **Card**
arată anunțul în lista din fila Anunțuri, **Pagină** îl arată deschis;
**Luminos** și **Întunecat** schimbă tema telefonului.

Formularul are titlu, text și semnătură. Textul acceptă Markdown simplu:
`**îngroșat**`, `*cursiv*`, `[link](https://...)`, liste cu `- ` urmat de
text, de exemplu `- Primul element`, fiecare element pe un rând nou. Un rând
liber începe un paragraf nou. Telefonul se actualizează în timp ce scrii;
dacă browserul nu rulează JavaScript, **Previzualizează** îl redesenează fără
să publice nimic. Un anunț fixat apare primul în aplicație. Anunțurile ajung
la toți studenții, cu o notificare la publicare.

Un anunț publicat se poate **edita** pe aceeași pagină: textul nou ajunge în
aplicații la următoarea sincronizare, fără notificare, iar data publicării
rămâne aceeași.

## Evenimente

**Evenimente** pune în fila cu același nume din aplicație carduri cu o
imagine și titlul dedesubt. Pagina din panou are un tabel cu două file,
**Următoare** (cele mai apropiate primele) și **Trecute** (cele mai recente
primele), cu o miniatură a imaginii. **Eveniment nou** deschide pagina de
scris, cu telefonul alături ca la anunțuri: **Card** arată cardul din listă,
**Pagină** arată pagina evenimentului, cu imaginea care se pierde în fundal
sub titlu. Formularul cere:

- titlul, care apare sub imagine;
- imaginea, JPEG sau PNG de cel mult 8 MB. Serverul o micșorează la cel mult
  1600 px și o salvează ca JPEG, fără datele camerei (inclusiv locul unde a
  fost făcută fotografia). Pe card, toate imaginile sunt tăiate la 16:9,
  ca lista să arate uniform; pagina evenimentului arată imaginea mai mare,
  pe fundal. Pentru un afiș, ține textul spre mijloc;
- data și ora de început; ora de sfârșit și, pentru un eveniment de mai multe
  zile, data de sfârșit, ambele opționale;
- locul, o descriere cu Markdown ca la anunțuri și un link (înscriere,
  program), toate opționale.

Telefonul se actualizează în timp ce scrii, iar o imagine aleasă apare pe
loc, înainte de trimitere. Fără JavaScript, **Previzualizează** desenează
cardul și pagina pe server. Imaginea se păstrează din prima trimitere, așa că
o greșeală în formular nu cere încărcarea ei din nou. **Trimite notificare pe
telefoane** este bifat pentru un eveniment nou; debifează-l pentru unul mai
puțin important. Editarea nu trimite notificări.

Aplicația arată evenimentele care urmează și, la **Trecute**, pe cele
terminate în ultimele 60 de zile. Un eveniment se poate edita sau șterge;
imaginea lui se șterge împreună cu el.

## Ciorne

Pe pagina unui anunț nou sau a unui eveniment nou, **Salvează ciorna**
păstrează formularul așa cum e, fără să-l publice. Ajunge un titlu sau un
text; restul (data, imaginea, semnătura) poate lipsi până la publicare.
Pagina rămâne deschisă pe aceeași ciornă, cu mesajul "Ciorna a fost
salvată" și, sus, ora salvării și cine a salvat-o. Adresa paginii se poate deschide oricând mai
târziu: formularul și telefonul arată ciorna. Un nou **Salvează ciorna**
schimbă aceeași ciornă. Imaginea unui eveniment rămâne cu ciorna cât timp
ciorna există. Dacă o imagine nouă nu se poate folosi, ciorna se salvează cu
imaginea de dinainte și pagina spune de ce.

**Anunțuri** și **Evenimente** au fila **Ciorne**, cu numărul lor: titlul
(sau "(fără titlu)"), pentru evenimente și miniatura și data, cine a scris
ciorna și ultima modificare. Ciornele se văd din toate conturile panoului,
editori și administratori, așa că un coleg poate termina ciorna altcuiva.
**Continuă** deschide ciorna; **Șterge** o șterge, după o confirmare.

**Publică** pe o ciornă publică anunțul sau evenimentul și șterge ciorna. Un
eveniment trimite notificare doar dacă **Trimite notificare pe telefoane**
este bifat; ciorna păstrează bifa așa cum ai lăsat-o. Ciornele nu ajung în
aplicații. Salvarea unei ciorne noi, publicarea dintr-o ciornă și ștergerea
unei ciorne apar în **Jurnal**. Editarea unui anunț sau eveniment deja
publicat nu are ciornă: **Salvează** schimbă direct ce văd studenții.
