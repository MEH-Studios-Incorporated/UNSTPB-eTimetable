# Copyright (c) 2026 Multimedia Entertainment Hub LLC
# SPDX-License-Identifier: LicenseRef-Proprietary
"""
Converts FAIMA's timetable workbooks (one per study year, one tab per
programme or series, the grid layout the faculty publishes) into the CSV the
dashboard imports (docs/admin.md).

Development aid only: it reads the faculty's current layout with heuristics
and prints every cell it could not understand. In production an administrator
imports the timetable through the dashboard.

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
ABBR = re.compile(r"^(.*?)\(([A-Za-z][A-Za-z0-9&]{0,9})\)")
PROFESSOR = re.compile(r"\(([^()]*?\b(?:Prof|Conf|Lect|Asist|As|Sl|Ș\.?l|S\.l|dr)\b[^()]*)\)", re.I)
# markers in parentheses that say the type, never a subject's abbreviation
TYPE_MARKERS = {"LP", "L", "LAB", "S", "SEM", "P", "PR", "C", "PROIECT"}
TYPES = [
    (re.compile(r"\bcurs\b|\(c\)", re.I), "curs"),
    (re.compile(r"\((?:lp|l|lab)\)", re.I), "laborator"),
    (re.compile(r"\((?:s|sem)\)", re.I), "seminar"),
    (re.compile(r"\((?:p|pr|proiect)\)", re.I), "proiect"),
]


def fold(text):
    return unicodedata.normalize("NFD", text).encode("ascii", "ignore").decode().lower()


def group_code(number, letters):
    letters = letters.upper()
    return number + ("B" if letters == "BEM" else letters)


def programme_of(code, tab):
    suffix = re.sub(r"^\d+", "", code)
    return {"E": "IEDEEE", "C": "IEICM", "B": "BEM", "G": "IBM"}.get(suffix, "IMA" if "IM" in tab.upper() or suffix in ("", "A", "R") else tab.upper())


def weeks_of(text):
    t = fold(text)
    if re.search(r"impar|odd week", t):
        return "impare"
    if re.search(r"\bpar[ae]?\b|even week", t):
        return "pare"
    for span in ("1-7", "8-14"):
        if re.search(r"sapt\.?\s*" + span, t):
            return span
    return "toate"


def full_name(text):
    """("Management Strategic", "MS") for "Management Strategic (MS) curs ...", else None."""
    m = ABBR.match(text)
    if m and m.group(2).upper() not in TYPE_MARKERS and len(m.group(1).strip()) > len(m.group(2)) + 2:
        return m.group(1).strip(), m.group(2).upper()
    return None


def professor_of(text):
    m = PROFESSOR.search(text)
    if not m:
        return ""
    # a cell sometimes names two professors; the first one teaches the course
    name = re.sub(r"\.+", ".", m.group(1).split("/")[0]).strip()
    return re.sub(r"\.(?=\S)", ". ", name)


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


def read_sheet(ws, problems, seen):
    sheet = Sheet(ws, None, problems)
    text = {}
    for row in ws.iter_rows():
        for c in row:
            if c.value is not None and str(c.value).strip():
                text[(c.row, c.column)] = re.sub(r"\s+", " ", str(c.value)).strip()

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

    # abbreviation -> full name, from the cells that spell the subject out
    names = {}
    for (r, c), v in text.items():
        if r in data_rows and c in subject_cols:
            found = full_name(v)
            if found:
                names[found[1]] = found[0]

    rows = []
    for (r, c), v in text.items():
        if r not in data_rows or c not in subject_cols or c in room_cols:
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

        kind = next((name for pattern, name in TYPES if pattern.search(v)), "curs" if len(covered) > 1 else "seminar")
        found = full_name(v)
        if found:
            subject, abbr = found
        else:
            first = re.split(r"[\s(*]", v, maxsplit=1)[0]
            abbr = re.sub(r"[^A-Za-z0-9&]", "", first).upper() or "X"
            # "Chemistry (lp)" belongs to "Chemistry (CH) curs": match on the first word
            spelled = next((a for a, n in names.items() if fold(n).split()[0] == fold(first)), None)
            abbr = abbr if abbr in names else spelled or abbr
            subject = names.get(abbr, first if len(first) > 4 else abbr)
        subject = re.sub(r"\s*-\s*$", "", subject)

        room = ""
        if len({x.split("/")[0] for x in covered}) == 1:
            g = next(g for g in groups if g["code"] == covered[0].split("/")[0])
            room = sheet.value(r0, g["room"])
        else:
            tail = re.split(r"\)", v)[-1].strip(" -")
            room = tail if 0 < len(tail) <= 30 else ""

        by_programme = {}
        for code in covered:
            by_programme.setdefault(programme_of(code.split("/")[0], ws.title), []).append(code)
        for programme, audience in by_programme.items():
            key = (tuple(sorted(audience)), day, start, abbr, kind)
            if key in seen:
                continue
            seen.add(key)
            group_series = series or re.sub(r"^\d+", "", audience[0].split("/")[0]) or "A"
            rows.append({
                "id": "", "programme": programme, "programme_name": PROGRAMMES.get(programme, programme),
                "year": year, "series": group_series, "audience": " ".join(sorted(audience)),
                "subject_code": f"{programme}-{year}-{abbr}", "subject_ro": subject, "subject_en": subject,
                "type": kind, "day": DAYS[day], "start": f"{int(start):02d}:{int(round((start % 1) * 60)):02d}",
                "end": f"{int(end):02d}:{int(round((end % 1) * 60)):02d}", "weeks": weeks_of(v),
                "professor": professor_of(v), "professor_email": "", "room": room, "erasmus": "nu",
            })
    return rows


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out, workbooks = sys.argv[1], sys.argv[2:]
    problems, seen, rows = [], set(), []
    for path in workbooks:
        wb = openpyxl.load_workbook(path, data_only=True)
        for ws in wb.worksheets:
            rows += read_sheet(ws, problems, seen)
    # one name per subject code: the longest spelling seen
    canonical = {}
    for row in rows:
        name = canonical.get(row["subject_code"], "")
        if len(row["subject_ro"]) > len(name):
            canonical[row["subject_code"]] = row["subject_ro"]
    for row in rows:
        row["subject_ro"] = row["subject_en"] = canonical[row["subject_code"]]
    with open(out, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS, delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows)} activities written to {out}")
    for p in problems:
        print("  not read:", p)


if __name__ == "__main__":
    main()
