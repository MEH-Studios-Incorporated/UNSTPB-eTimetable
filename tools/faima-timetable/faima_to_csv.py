# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
"""
Converts FAIMA's timetable workbooks (one per study year, one tab per
programme or series, the grid layout the faculty publishes) into the CSV the
dashboard imports (docs/admin.md).

Development aid only: it reads the faculty's current layout with heuristics
and prints every cell it could not understand. In production an administrator
imports the timetable through the dashboard.

What the sheets do that it has to undo:
- the same subject under several names ("Programarea" and "Programarea
  calculatoarelor", spelling mistakes): merged by prefix, initials and ALIASES;
- two entries in one cell, after " / " or a blank line, sometimes with their
  own hours ("MA (s) even week 14-18");
- rooms written "BN 212 b", "local Polizu L201" or only at the end of the
  cell; written here as "BN212b" and "L201 Polizu";
- teachers with different titles and spellings; the fullest one wins, with
  diacritics restored;
- old cedilla letters (ş, ţ) and "si", "in" without diacritics in Romanian
  names. Lab and seminar cells name no assistant; those rows stay without one.

Usage: faima_to_csv.py OUT.csv WORKBOOK.xlsx [WORKBOOK.xlsx ...]
"""
import csv
import re
import sys
import unicodedata

import openpyxl

COLUMNS = ["id", "programme", "programme_name", "year", "series", "audience", "subject_code", "subject_ro",
           "subject_en", "type", "day", "start", "end", "weeks", "professor", "professor_email", "room", "erasmus"]
DAYS = ["luni", "marti", "miercuri", "joi", "vineri", "sambata"]
ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4}
PROGRAMMES = {
    "IMA": "Ingineria și Managementul Afacerilor",
    "BEM": "Business Engineering and Management",
    "IEDEEE": "Inginerie Economică în Domeniul Electric, Energetic și Electronic",
    "IEICM": "Inginerie Economică în Industria Chimică și de Materiale",
    "IBM": "Ingenieurwesen und Business Management",
}
GROUP = re.compile(r"^Grupa\s*:?\s*(\d{4})\s*([A-Za-z]*)\s*$")
TIME = re.compile(r"^(\d{1,2})\s*-\s*(\d{1,2})$")
# "Management Strategic (MS)": a name followed by its abbreviation
ABBR = re.compile(r"^(.*?)\(([A-ZĂÂÎȘȚ][A-Za-z0-9&]{0,9}(?:\s+[IVX]{1,3})?)\)")
PLACES = {"POLIZU", "LEU", "RECTORAT", "PRECIS"}
# where a teacher's name starts, with or without parentheses
# a title only counts when a name follows it: "asist. Ionescu" is a person, "Grafică asist. IV" is not
TITLE = re.compile(
    r"(?:^|[\s(\-])((?:prof|conf|lect|ș\.?\s?l|ş\.?\s?l|s\.\s?l|sl|asist|as|jr|dr)\b\.?)"
    r"(?=(?:\s?(?:dr|ing|ec|univ|habil|[IVX]{1,2})\.)*\s?(?:[A-ZĂÂÎȘȚ][a-zăâîșț]{2,}|[A-ZĂÂÎȘȚ]\.?\s?[A-ZĂÂÎȘȚ][a-zăâîșț]))",
    re.I,
)
TYPE_MARKERS = {"LP", "L", "LAB", "S", "SEM", "P", "PR", "C", "PROIECT"}
TYPES = [
    (re.compile(r"\b(?:curs|kurs)\b|\(c\)|-\s*c\s*-", re.I), "curs"),
    (re.compile(r"\((?:lp|l|lab)\)|-\s*(?:lab|l|lp)\s*-|\blabor\b", re.I), "laborator"),
    (re.compile(r"\((?:s|sem)\)|-\s*(?:sem|s|seminar)\b\s*-?|\bseminar\b", re.I), "seminar"),
    (re.compile(r"\((?:p|pr|proiect)\)|-\s*(?:p|pr|proiect)\b\s*-?", re.I), "proiect"),
]
# what ends the subject's name inside a cell
NAME_END = re.compile(
    r"\((?:lp|l|lab|s|sem|p|pr|c|proiect)\)|-\s*(?:c|sem|s|lab|l|lp|p|pr)\s*-|\bcurs\b|\*\*|\s-\s|\bsala\b|\((?:s[ăa]pt|sg)|\bsgr?\.?\s*\d|\bsg\.",
    re.I,
)
# cells that only say where (a campus or a floor), never what
PLACE_ONLY = re.compile(r"^(?:polizu|leu|rectorat|precis|sala sport|et\.?\s*\d.*|[A-Z]{1,4}\s*\d{1,4}\s*[a-z]?(?:\s*,.*)?)$", re.I)
# notes about weeks, type or teacher that the faculty writes into a subject's cell
NOISE = [
    re.compile(r"^(?:even|odd)\s+weeks?\s*|^(?:par|impar)[ăae]?\s+", re.I),
    re.compile(r"[,\s]*-\s*(?:seminar|proiect|curs|laborator)\b\s*-?", re.I),
    re.compile(r"[,\s]*\bs[ăa]pt\.?\s*(?:pare|impare|\d+\s*-\s*\d+).*$", re.I),
    re.compile(r"\s*:?\s*\b(?:kurs|seminar|labor)\b.*$", re.I),
    re.compile(r"\s+(?:un)?gerade\s+woche.*$", re.I),
    re.compile(r"(?<=\.)\s+[A-Z]{1,3}$"),
    re.compile(r"\s+[A-Z]\.\s?[A-Za-z]\.?$"),
]
# spelling slips in the faculty's sheets, mapped to the subject they mean (folded)
ALIASES = {
    "grafica asistata de laborator": "Grafică asistată de calculator",
    "fundamentele pietei financiare": "Fundamentele piețelor financiare",
    # spelling mistakes in the sheets
    "statistica econimica": "Statistică economică",
    "mathematical analisys": "Mathematical Analysis",
}
SMALL_WORDS = {"de", "si", "și", "şi", "in", "în", "pentru", "a", "al", "ale", "cu", "la", "din", "pe", "of", "and", "the", "in", "und", "der", "die", "das", "im", "mit"}
ROOM = re.compile(r"^(?:sala\s+)?(?!sala\b)([A-Za-zĂÂÎȘȚăâîșț]{1,4})\s*(\d{1,4})\s*([a-zA-Z]{1,2})?(?![\w])\s*(.*)$", re.I)


def fold(text):
    return unicodedata.normalize("NFD", text).encode("ascii", "ignore").decode().lower()


CEDILLA = str.maketrans("şţŞŢ", "șțȘȚ")
ENGLISH_OR_GERMAN = {"BEM", "IBM"}


def romanian(text):
    """Comma-below ș and ț instead of the old cedilla letters, "și" and "în" with their diacritics."""
    text = text.translate(CEDILLA)
    text = re.sub(r"\bsi\b", "și", text)
    return re.sub(r"\bin\b", "în", text)


def title_case(name):
    """"DREPT COMERCIAL" -> "Drept Comercial"; names already in mixed case stay as written."""
    letters = [ch for ch in name if ch.isalpha()]
    if not letters or not all(ch.isupper() for ch in letters) or (len(name) <= 4 and " " not in name):
        return name
    words = name.lower().split(" ")
    out = []
    for i, word in enumerate(words):
        if re.fullmatch(r"[ivx]+", word) or any(ch.isdigit() for ch in word):
            out.append(word.upper())
        elif i > 0 and fold(word) in SMALL_WORDS:
            out.append(word)
        else:
            out.append(word[:1].upper() + word[1:])
    return " ".join(out)


def initials(name):
    words = [w for w in re.split(r"[\s,\-]+", fold(name)) if w and w not in SMALL_WORDS]
    return "".join(w[0] for w in words if w[0].isalpha() and not re.fullmatch(r"[ivx]+", w)).upper()


def clean_phrase(phrase):
    """The subject's name without the notes around it; "Lb străină: Germană" reads as "Limba Germană"."""
    for pattern in NOISE:
        phrase = pattern.sub("", phrase)
    phrase = re.sub(r"^lb\.?\s+(?:str[ăa]in[ăa]\s*:?\s*)?", "Limba ", phrase, flags=re.I)
    phrase = re.sub(r"^limba\s+str[ăa]in[ăa]\s*:?\s*", "Limba ", phrase, flags=re.I)
    return re.sub(r"\s+", " ", phrase).strip(" -,:")


def normalize_room(text):
    """"BN 212 b" -> "BN212b", "A 139 (Local Polizu)" -> "A139 Polizu", "EB 212 / 213" -> "EB212 / EB213"."""
    text = re.sub(r"\s*\(\s*(?:local\s+)?([^)]*)\)", r" \1", text, flags=re.I)
    text = re.sub(r"\blocal\s+", "", text, flags=re.I)
    # "Polizu L201": the campus comes after the room, as in "A139 Polizu"
    text = re.sub(r"^(Polizu|Leu|Rectorat|Precis)\s+([A-Z]{1,4}\s*\d.*)$", r"\2 \1", text.strip(), flags=re.I)
    if fold(text) == "sala sport":
        return "Sala Sport"
    text = re.sub(r",?\s*\bet\.?\s*\d+\b,?", " ", text, flags=re.I)
    rooms = []
    for part in re.split(r"\s*/\s*|\s+(?=[A-Z]{1,4}\s*\d)", text.strip()):
        m = ROOM.match(part.strip())
        if not m and rooms and re.fullmatch(r"\d{1,4}\s*[a-zA-Z]?", part.strip()):
            # "212 / 213": the second room is in the same building
            code = re.match(r"^[A-Z]+", rooms[-1])
            m = ROOM.match(f"{code.group(0) if code else ''}{part.strip()}")
        if m:
            code, number, letter, rest = m.groups()
            rooms.append(f"{code.upper()}{number}{(letter or '').lower()}" + (f" {rest.strip().title()}" if rest.strip() else ""))
        elif part.strip():
            rooms.append(title_case(part.strip()) if part.strip().isupper() else part.strip()[:1].upper() + part.strip()[1:])
    return " / ".join(dict.fromkeys(rooms))


def group_code(number, letters):
    letters = letters.upper()
    return number + ("B" if letters == "BEM" else letters)


def programme_of(code, tab):
    suffix = re.sub(r"^\d+", "", code)
    return {"E": "IEDEEE", "C": "IEICM", "B": "BEM", "G": "IBM"}.get(suffix, "IMA" if "IM" in tab.upper() or suffix in ("", "A", "R") else tab.upper())


def weeks_of(text):
    t = fold(text)
    if re.search(r"impar|odd week|ungerade woche", t):
        return "impare"
    if re.search(r"\bpar[ae]?\b|even week|\bgerade woche", t):
        return "pare"
    for span in ("1-7", "8-14"):
        if re.search(r"sapt\.?\s*" + span, t):
            return span
    return "toate"


TITLE_FORMS = [
    (re.compile(r"^(?:ș|ş|s)\.?\s*l\.?|^sl\.?", re.I), "Ș.l."),
    (re.compile(r"^prof\.?", re.I), "Prof."),
    (re.compile(r"^conf\.?", re.I), "Conf."),
    (re.compile(r"^lect(?:or)?\.?", re.I), "Lect."),
    (re.compile(r"^asist\.?|^as\.", re.I), "As."),
    (re.compile(r"^jr\.?", re.I), "Jr."),
]


def clean_person(name):
    """"sl. dr. ing Ionut Radu Even Weeks" -> "Ș.l. dr. ing. Ionut Radu"."""
    name = name.split("/")[0].split(")")[0]
    name = re.sub(r"\b(?:odd|even)\s+weeks?\b.*$|\bs[ăa]pt\.?.*$", "", name, flags=re.I)
    name = re.sub(r"\s+[A-Z]{1,3}\s?\d{1,4}.*$", "", name)
    name = re.sub(r"\.+", ".", name).strip(" -,")
    for pattern, form in TITLE_FORMS:
        if pattern.match(name):
            name = pattern.sub(form, name, count=1)
            break
    name = re.sub(r"\b(dr|ing|ec)\b\.?", r"\1.", name)
    name = re.sub(r"\.(?=\S)", ". ", name).replace("Ș. l.", "Ș.l.")
    # "Monica NIȚĂ" -> "Monica Niță"
    return " ".join(w[:1] + w[1:].lower() if len(w) > 2 and w.isupper() and not re.fullmatch(r"[IVX]+", w) else w for w in name.split())


def person_key(name):
    """Surname and first initial, so "conf. P. Stanescu" and "Conf. P. Stănescu" are one person."""
    words = [w for w in name.split() if not w.endswith(".") or len(w) == 2]
    words = [w for w in words if fold(w).rstrip(".") not in {"dr", "ing", "ec", "prof", "conf", "lect", "as", "jr", "s.l"}]
    if not words:
        return fold(name)
    return f"{fold(words[-1])}|{fold(words[0])[:1]}"


HOURS = re.compile(r"\b(\d{1,2})\s*-\s*(\d{1,2})\b")
WEEK_WORD = re.compile(r"(?:s[ăa]pt\w*\.?|weeks?|wochen?)\s*$", re.I)


def in_cell_hours(text):
    """"MA (s) even week 14-18 BN 211": hours written in the cell, not week numbers ("săpt 8-14")."""
    for m in HOURS.finditer(text):
        start, end = int(m.group(1)), int(m.group(2))
        if 7 <= start < end <= 22 and not WEEK_WORD.search(text[:m.start()]):
            return m
    return None


def parse_cell(text):
    """What one timetable cell says: subject phrase, abbreviation, type, weeks, teacher, room."""
    teacher = ""
    m = re.search(r"\(([^()]*\b(?:prof|conf|lect|asist|sl|ș\.?\s?l|s\.\s?l|dr)\b[^()]*)\)", text, re.I)
    if m:
        teacher = m.group(1)
    else:
        t = TITLE.search(text)
        if t and t.start(1) > 2:
            rest = text[t.start(1):]
            teacher = re.split(r"\s-\s|\s+-\s*(?:c|sem|s|lab|l|p)\s*-|\bsala\b|\(", rest, maxsplit=1, flags=re.I)[0]
    room = ""
    r = re.search(r"\bsala\s+(.+)$", text, re.I) or re.search(r"\)\s*([A-Z]{1,4}\s*\d{1,4}\s*[a-z]?(?:\s+\w+)?)\s*$", text)
    if r:
        room = r.group(1)
    elif polizu := re.search(r"\bpolizu\b\W*([A-Z]\s?\d{3}[a-z]?)\b", text, re.I):
        # "local Polizu - L121": Polizu rooms have a one-letter building
        room = f"{polizu.group(1)} Polizu"
    else:
        # "... Razvan Dobrescu CD 006", "FPM (s) odd weeks BN205"
        r = re.search(r"\b([A-Z]{2}\s?\d{3}[a-z]?)\b(?!.*\b[A-Z]{2}\s?\d{3})", text)
        if r:
            room = r.group(1)

    abbr = ""
    a = ABBR.match(text)
    if a and a.group(2).upper() not in TYPE_MARKERS | PLACES and len(a.group(1).strip()) > len(a.group(2)):
        phrase, abbr = a.group(1), a.group(2).upper()
    else:
        cut = len(text)
        for pattern in (NAME_END, TITLE):
            found = pattern.search(text)
            if found and found.start() > 0:
                cut = min(cut, found.start())
        phrase = text[:cut]
    phrase = clean_phrase(phrase)
    kind = next((name for pattern, name in TYPES if pattern.search(text)), None)
    return {"phrase": phrase, "abbr": abbr, "kind": kind, "weeks": weeks_of(text), "teacher": clean_person(teacher), "room": room}


def is_abbreviation(phrase):
    """"PCLP", "Mk", "B II": short codes, not names."""
    m = re.fullmatch(r"([A-Za-z]{1,6})(?:\s+[IVX]{1,3})?", phrase)
    return bool(m) and (m.group(1).isupper() or len(m.group(1)) <= 2)


def abbreviation_key(phrase):
    """"PCLP I" and "PCLP" are the same code."""
    return re.sub(r"\s+[IVX]{1,3}$", "", phrase).upper()


class Subjects:
    """The subjects of one programme and year, gathered from every tab before naming any of them."""

    def __init__(self):
        self.names = {}  # folded name -> name as written
        self.by_abbr = {}  # abbreviation -> folded name
        self.merged = {}  # folded name -> the longer name it is short for

    def learn(self, cell):
        phrase, abbr = cell["phrase"], cell["abbr"]
        if not phrase or (is_abbreviation(phrase) and not abbr):
            return
        if fold(phrase) in ALIASES:
            phrase = ALIASES[fold(phrase)]
        key = fold(phrase)
        current = self.names.get(key)
        own = True
        if current is None or (own and ((current.isupper() and not phrase.isupper()) or len(phrase) > len(current))):
            self.names[key] = phrase
        if abbr:
            self.by_abbr.setdefault(abbreviation_key(abbr), key)

    def settle(self):
        """Joins a name to the one longer name it starts, so "Programarea calculatoarelor" and
        "Programarea calculatoarelor și limbaje de programare I" become one subject."""
        def starts(short, long):
            a, b = short.split(), long.split()
            return len(a) < len(b) and all(y == x or (x.endswith(".") and y.startswith(x[:-1])) for x, y in zip(a, b))
        for key in self.names:
            longer = [k for k in self.names if starts(key, k)]
            if len(longer) == 1:
                self.merged[key] = longer[0]

    def canonical(self, key):
        while key in self.merged:
            key = self.merged[key]
        return key

    def resolve(self, cell):
        """(code, name) for a cell; abbreviations nobody spells out stay as their own subject."""
        phrase, abbr = cell["phrase"], cell["abbr"]
        key = fold(ALIASES.get(fold(phrase), phrase))
        if not abbr and is_abbreviation(phrase) and key not in self.names:
            code = abbreviation_key(phrase)
            key = self.by_abbr.get(code)
            if key is None:
                matches = {self.canonical(k) for k in self.names if initials(self.names[k]) == code}
                key = matches.pop() if len(matches) == 1 else None
            if key is None:
                return code, phrase.upper()
        key = self.canonical(key)
        if key not in self.names:
            # a short name that starts exactly one known name
            longer = {self.canonical(k) for k in self.names if k.startswith(key + " ")}
            key = longer.pop() if len(longer) == 1 else key
        name = self.names.get(key, phrase)
        known = set(self.by_abbr)
        shown = name if (" " not in name and name.upper() in known) else title_case(name)
        code = next((a for a, k in self.by_abbr.items() if self.canonical(k) == key), None) or initials(name) or "X"
        return code, shown


class Sheet:
    def __init__(self, ws, year, problems):
        self.ws = ws
        self.year = year
        self.problems = problems
        self.merged = {}
        for r in ws.merged_cells.ranges:
            for row in range(r.min_row, r.max_row + 1):
                for col in range(r.min_col, r.max_col + 1):
                    self.merged[(row, col)] = r

    def span(self, row, col):
        r = self.merged.get((row, col))
        return (r.min_row, r.min_col, r.max_row, r.max_col) if r else (row, col, row, col)

    def value(self, row, col):
        r0, c0, _, _ = self.span(row, col)
        v = self.ws.cell(r0, c0).value
        return re.sub(r"\s+", " ", str(v)).strip() if v is not None else ""


def read_sheet(ws, problems):
    sheet = Sheet(ws, None, problems)
    text = {}
    for row in ws.iter_rows():
        for c in row:
            if c.value is not None and str(c.value).strip():
                # a blank line inside a cell separates two entries; it survives as "\n"
                parts = re.split(r"\n\s*\n", str(c.value).strip())
                text[(c.row, c.column)] = "\n".join(re.sub(r"\s+", " ", p).strip() for p in parts)

    year = series = None
    for v in text.values():
        if v.startswith("Anul de studii"):
            year = ROMAN.get(v.split(":", 1)[1].strip().split()[0].upper())
        if v.startswith("Seria"):
            series = v.split(":", 1)[1].strip()
    m = re.search(r"SERIA\s+([A-Z])", ws.title.upper())
    series = series or (m.group(1) if m else None)
    if year is None:
        problems.append(f"{ws.title}: no 'Anul de studii', skipped")
        return []

    groups = []
    for (row, col), v in text.items():
        m = GROUP.match(v)
        if not m:
            continue
        _, c0, _, c1 = sheet.span(row, col)
        room_col = next((c for c in range(c0, c1 + 1) if fold(sheet.value(row + 1, c)) == "sala"), c1)
        subject_cols = [c for c in range(c0, c1 + 1) if c != room_col]
        groups.append({"code": group_code(*m.groups()), "cols": subject_cols, "room": room_col, "header": row})
    if not groups:
        problems.append(f"{ws.title}: no groups found, skipped")
        return []

    # each "08 - 10" label covers its rows; the hours restart at every new day
    slots = sorted((sheet.span(r, c), TIME.match(v)) for (r, c), v in text.items() if TIME.match(v))
    hours = {}
    day, previous = -1, 99
    for (r0, _, r1, _), m in slots:
        start, end = int(m.group(1)), int(m.group(2))
        if start <= previous:
            day += 1
        previous = start
        per_row = (end - start) / (r1 - r0 + 1)
        for i, row in enumerate(range(r0, r1 + 1)):
            hours[row] = (day, start + i * per_row)
    data_rows = set(hours)
    subject_cols = {c for g in groups for c in g["cols"]}
    room_cols = {g["room"] for g in groups}

    units = []
    for (r, c), v in text.items():
        if r not in data_rows or c not in subject_cols or c in room_cols:
            continue
        if PLACE_ONLY.match(v):
            # a campus or room spilled into the subject columns
            continue
        r0, c0, r1, c1 = sheet.span(r, c)
        covered = []
        for g in groups:
            cols = [x for x in g["cols"] if c0 <= x <= c1]
            if not cols:
                continue
            if len(cols) == len(g["cols"]):
                covered.append(g["code"])
            else:
                half = len(g["cols"]) // 2
                label = sheet.value(g["header"] + 2, cols[0])
                suffix = re.sub(r"^Sgr\.?\s*", "", label, flags=re.I) if label.lower().startswith("sgr") else ("A" if cols[-1] < g["cols"][half] else "B")
                covered.append(f"{g['code']}/{suffix}")
        if not covered or r1 not in hours:
            problems.append(f"{ws.title} {ws.cell(r0, c0).coordinate}: outside the grid: {v}")
            continue
        day, start = hours[r0]
        _, last = hours[r1]
        per_row = (hours.get(r0 + 1, (day, start + 1))[1] - start) or 1
        end = last + per_row
        if hours[r1][0] != day or day >= len(DAYS):
            problems.append(f"{ws.title} {ws.cell(r0, c0).coordinate}: spans two days: {v}")
            continue

        for entry in v.split("\n"):
            span = in_cell_hours(entry)
            entry_start, entry_end = (int(span.group(1)), int(span.group(2))) if span else (start, end)
            cell = parse_cell(entry[:span.start()] + entry[span.end():] if span else entry)
            if not cell["phrase"]:
                problems.append(f"{ws.title} {ws.cell(r0, c0).coordinate}: no subject in: {entry}")
                continue
            cell["kind"] = cell["kind"] or ("curs" if len(covered) > 1 else "seminar")
            if len({x.split("/")[0] for x in covered}) == 1:
                g = next(g for g in groups if g["code"] == covered[0].split("/")[0])
                column_room = sheet.value(r0, g["room"])
                # the room column sometimes holds a neighbouring subject instead of a room
                looks_like_text = any(p.search(column_room) for p, _ in TYPES) or TITLE.search(column_room) or weeks_of(column_room) != "toate"
                if column_room and len(column_room) <= 30 and not looks_like_text:
                    cell["room"] = column_room
            cell["room"] = normalize_room(cell["room"]) if len(cell["room"]) <= 30 else ""

            by_programme = {}
            for code in covered:
                by_programme.setdefault(programme_of(code.split("/")[0], ws.title), []).append(code)
            for programme, audience in by_programme.items():
                units.append({
                    "cell": cell, "programme": programme, "year": year, "day": day, "start": entry_start, "end": entry_end,
                    "audience": sorted(audience), "series": series or re.sub(r"^\d+", "", audience[0].split("/")[0]) or "A",
                })
    return units


def clock(hours):
    return f"{int(hours):02d}:{int(round((hours % 1) * 60)):02d}"


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out, workbooks = sys.argv[1], sys.argv[2:]
    problems, units = [], []
    for path in workbooks:
        wb = openpyxl.load_workbook(path, data_only=True)
        for ws in wb.worksheets:
            units += read_sheet(ws, problems)

    # every name of a programme and year is known before any cell is named
    # "Etica in afaceri / Tehnologia materialelor plastice": two subjects in one cell
    split = []
    for u in units:
        parts = [p.strip() for p in u["cell"]["phrase"].split(" / ")]
        if len(parts) > 1 and all(len(p) > 6 for p in parts):
            split += [{**u, "cell": {**u["cell"], "phrase": p, "abbr": ""}} for p in parts]
        else:
            split.append(u)
    units = split

    subjects = {}
    for u in units:
        subjects.setdefault((u["programme"], u["year"]), Subjects()).learn(u["cell"])
    for s in subjects.values():
        s.settle()

    rows, seen = [], set()
    for u in units:
        cell = u["cell"]
        code, name = subjects[(u["programme"], u["year"])].resolve(cell)
        key = (tuple(u["audience"]), u["day"], u["start"], code, cell["kind"])
        if key in seen:
            continue
        seen.add(key)
        rows.append({
            "id": "", "programme": u["programme"], "programme_name": PROGRAMMES.get(u["programme"], u["programme"]),
            "year": u["year"], "series": u["series"], "audience": " ".join(u["audience"]),
            "subject_code": f"{u['programme']}-{u['year']}-{code}", "subject_ro": name, "subject_en": name,
            "type": cell["kind"], "day": DAYS[u["day"]], "start": clock(u["start"]), "end": clock(u["end"]),
            "weeks": cell["weeks"], "professor": cell["teacher"], "professor_email": "", "room": cell["room"], "erasmus": "nu",
        })

    # one spelling per teacher: the longest seen for the same surname and first initial,
    # and with diacritics when two are as long ("Răzvan" over "Razvan")
    def completeness(name):
        return len(name), sum(not ch.isascii() for ch in name)

    fullest, spellings = {}, {}
    for row in rows:
        if row["professor"]:
            key = person_key(row["professor"])
            spellings.setdefault(key, set()).add(row["professor"])
            if completeness(row["professor"]) > completeness(fullest.get(key, "")):
                fullest[key] = row["professor"]
    for key, name in fullest.items():
        # "Razvan" in the fullest spelling, "Răzvan" in a shorter one
        words = {fold(w): w for other in spellings[key] for w in other.split() if not w.isascii()}
        fullest[key] = " ".join(words.get(fold(w), w) for w in name.split())
    for row in rows:
        if row["professor"]:
            row["professor"] = fullest[person_key(row["professor"])]

    # "08 - 09" and "09 - 10" of the same class in two cells are one class
    joined = []
    for row in sorted(rows, key=lambda r: (r["programme"], r["audience"], r["day"], r["start"])):
        last = joined[-1] if joined else None
        same = last is not None and all(
            last[k] == row[k] for k in ("programme", "audience", "day", "subject_code", "type", "weeks", "professor", "room")
        )
        if same and last["end"] == row["start"]:
            last["end"] = row["end"]
        else:
            joined.append(row)
    rows = joined

    for row in rows:
        for column in ("subject_ro", "subject_en", "professor", "room", "programme_name"):
            row[column] = row[column].translate(CEDILLA)
        if row["programme"] not in ENGLISH_OR_GERMAN:
            row["subject_ro"] = romanian(row["subject_ro"])
            row["subject_en"] = romanian(row["subject_en"])

    with open(out, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows)} activities written to {out}")
    for p in problems:
        print("  not read:", p)


if __name__ == "__main__":
    main()
