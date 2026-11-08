# Ghid de administrare

Pentru secretariatul și administratorii FAIMA care pun orarul și anunțurile în
aplicație. Panoul de administrare este la adresa serverului urmată de
`/admin`, de exemplu `https://orar.faima.upb.ro/admin`.

## Prezentare

Prima pagină după conectare arată săptămâna de azi (numărul și paritatea),
câte activități, grupe, materii, profesori și conturi de studenți are
semestrul curent și ce mai e de făcut: activități fără profesor sau fără
sală, săli sau profesori cu două activități în același timp, profesori fără
email. Fiecare rând trimite unde se rezolvă.

Adresa serverului fără `/admin` este pagina publică a aplicației, pentru
studenți: de unde se descarcă și ce face. Are un link spre panou.

## Conturi

Primul cont se creează la instalarea serverului (vezi `docs/deploy.md`).
Din pagina **Administratori** acesta adaugă celelalte conturi:

- Administrator: tot, inclusiv conturile.
- Editor: semestre, orar și anunțuri.

Fiecare își schimbă parola din pagina **Schimbă parola**. O sesiune durează
12 ore.

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

3. **Importă orarul** din fișierul descris mai jos.
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
- **Săli.** Sălile sunt grupate pe corpuri; numele și adresa corpului se
  schimbă de aici. Pagina unei săli arată săptămâna ei, deci și orele în
  care e liberă.
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
se salvează nimic și pagina spune ce rând și ce anume. O sală sau un profesor
în două locuri în același timp apar ca avertismente; orarul se salvează
oricum.

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
pentru zi și activități fără sală sau profesor. Bifează activitățile de
modificat (cel mult 200), alege sala sau profesorul și, opțional, deplasarea
în minute. **Previzualizează modificările** arată noile ore și suprapunerile
noi. Numai **Confirmă și salvează** le publică. Dacă alt editor a modificat
orarul între timp, reîncarcă pagina și verifică selecția. Linkul fiecărei
activități deschide editorul complet.

## Publicarea anunțurilor

**Anunțuri** are un formular cu titlu, text și semnătură. Textul acceptă
Markdown simplu: `**îngroșat**`, `*cursiv*`, `[link](https://...)`, liste cu
`- ` urmat de text, de exemplu `- Primul element`, fiecare element pe un rând
nou. **Previzualizează** arată anunțul așa cum apare în aplicație, fără să-l
publice. Un anunț fixat apare primul în aplicație. Anunțurile ajung la toți
studenții. Lista are câte 20 de anunțuri pe pagină, cu acces la cele mai
vechi.

Un anunț publicat se poate **edita**: textul nou ajunge în aplicații la
următoarea sincronizare, iar data publicării rămâne aceeași.
