# Ghid de administrare

Pentru secretariatul și administratorii FAIMA care pun orarul și anunțurile în
aplicație. Panoul de administrare este la adresa serverului urmată de
`/admin`, de exemplu `https://orar.faima.upb.ro/admin`.

## Conturi

Primul cont se creează la instalarea serverului (vezi `docs/deploy.md`).
Din pagina **Administratori** acesta adaugă celelalte conturi:

- Administrator: tot, inclusiv conturile.
- Editor: semestre, orar și anunțuri.

Fiecare își schimbă parola din pagina **Schimbă parola**. O sesiune durează
12 ore.

## Un semestru nou

1. **Semestre → Semestru nou.** Codul (de exemplu `2026-2`) rămâne același
   tot semestrul. Bifează „Copiază grupele, materiile și orarul semestrului
   curent” dacă orarul seamănă cu cel de acum; altfel semestrul pornește gol.
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

## Fișierul cu orarul

Un tabel (Excel, LibreOffice sau Google Sheets) salvat ca CSV, cu un rând pe
activitate. Cel mai simplu este să descarci orarul actual din pagina
semestrului („Descarcă orarul actual”), să îl modifici și să îl imporți
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
| `professor` | Titlul și numele | `Conf. dr. ing. Ioana Marinescu` |
| `professor_email` | Opțional | |
| `room` | Sala, cu codul corpului la început | `BN 212 b` |
| `erasmus` | `da` dacă studenții Erasmus pot alege activitatea | `da` |

Rândurile de tip `consultații` au nevoie doar de zi, ore, săptămâni,
profesor și, opțional, sală. Ele apar la profesor, nu în orarul grupelor.

Excel în română salvează CSV cu punct și virgulă; serverul acceptă și virgulă.
Diacriticele se păstrează dacă fișierul este salvat ca „CSV UTF-8”.

## Anunțuri

**Anunțuri** are un formular cu titlu, text și semnătură. Textul acceptă
Markdown simplu: `**îngroșat**`, `*cursiv*`, `[link](https://...)`, liste cu
`-`. Un anunț fixat apare primul în aplicație. Anunțurile ajung la toți
studenții; notificările push vin într-o versiune următoare.
