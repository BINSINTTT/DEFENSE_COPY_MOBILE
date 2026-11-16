"""Export TomatoInsight data dictionary to a Word .docx file."""
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from docx.shared import Inches, Pt, RGBColor

OUT = Path(r"Z:\USER_GUIDE\TomatoInsight_Data_Dictionary.docx")
OUT_FALLBACK = Path(r"Z:\USER_GUIDE\TomatoInsight_Data_Dictionary_White.docx")

TABLES = [
    {
        "name": "1. User",
        "desc": (
            "The User table stores the main grower account used to sign in and manage TomatoInsight. "
            "It keeps personal profile details such as the user’s first name, middle initial, last name, "
            "email address, and hashed password. It also stores farm-related profile data, including the "
            "farm name, profile image, and the currently selected farm week. Creation and update timestamps "
            "are saved so the system can track when the account was created and when the profile was last changed."
        ),
        "rows": [
            ("userId", "VARCHAR / UID", "PK", "Yes", "Unique identifier of the user account"),
            ("userFName", "VARCHAR(50)", "", "Yes", "User first name"),
            ("userMiddleInitial", "CHAR(1)", "", "No", "User middle initial"),
            ("userLName", "VARCHAR(50)", "", "Yes", "User last name"),
            ("userEmail", "VARCHAR(100)", "Unique", "Yes", "Email used for sign-in"),
            ("userPass", "VARCHAR(255)", "", "Yes", "Hashed account password"),
            ("userFarmName", "VARCHAR(100)", "", "No", "Name of the user's farm"),
            ("userProfileImage", "VARCHAR / URL", "", "No", "Profile photo path or URL"),
            ("userSelectedFWeek", "INT", "", "No", "Selected farm week (4–12)"),
            ("userCreatedAt", "DATETIME", "", "Yes", "Account creation timestamp"),
            ("userUpdAt", "DATETIME", "", "Yes", "Last profile update timestamp"),
        ],
    },
    {
        "name": "2. Farm Layout",
        "desc": (
            "The Farm Layout table stores the user’s farm blueprint configuration that defines how the "
            "tomato farm is arranged on the Home screen. It records the number of rows and columns used "
            "to build the plant tile grid, which determines the total number of plant positions available "
            "for scanning and tracking. Each layout belongs to one user and can be updated whenever the "
            "grower edits the farm blueprint."
        ),
        "rows": [
            ("layId", "VARCHAR / INT", "PK", "Yes", "Unique farm layout identifier"),
            ("User_userId", "VARCHAR / UID", "FK", "Yes", "References User.userId (1:1)"),
            ("layrows", "INT", "", "Yes", "Number of rows in the farm blueprint (1–10)"),
            ("layColumns", "INT", "", "Yes", "Number of columns in the farm blueprint (1–10)"),
            ("layUpAt", "DATETIME", "", "Yes", "Last layout update timestamp"),
        ],
    },
    {
        "name": "3. Plant",
        "desc": (
            "The Plant table stores each plant or tile placed on the farm blueprint. It identifies the "
            "plant’s position through the tile number and keeps optional details such as the custom plant "
            "name entered by the grower and the current plant status. This table is the center of plant "
            "monitoring because scans, notifications, and deletions are linked to each plant record. "
            "Creation and update timestamps help track when the plant was added and last modified."
        ),
        "rows": [
            ("plantId", "VARCHAR / INT", "PK", "Yes", "Unique plant identifier"),
            ("Farm_layoutId", "VARCHAR / INT", "FK", "Yes", "References Farm Layout.layId (M:1)"),
            ("plantTileNum", "INT", "", "Yes", "Tile number on the blueprint"),
            ("plantPlantName", "VARCHAR(100)", "", "No", "Custom plant name entered by the user"),
            ("plantStatus", "VARCHAR(50)", "", "No", "Current plant status (e.g., Healthy, Issue)"),
            ("plantUpAt", "DATETIME", "", "Yes", "Last plant update timestamp"),
            ("plantCreatedAt", "DATETIME", "", "Yes", "Plant creation timestamp"),
        ],
    },
    {
        "name": "4. Scan",
        "desc": (
            "The Scan table stores the leaf scan results captured for a plant during a selected farm week. "
            "It keeps the scanned leaf image reference, the top predicted disease or class, and the "
            "classification percentages for Healthy, TLCV, Early Blight, and Spotted Wilt. It also records "
            "whether the scan was processed offline on the device through scanIsOffline, which helps the "
            "system distinguish on-device TFLite results from online or synced scans. The saved timestamp "
            "shows when the scan result was stored for later monitoring and history review."
        ),
        "rows": [
            ("scanId", "VARCHAR / INT", "PK", "Yes", "Unique scan identifier"),
            ("plant_plantId", "VARCHAR / INT", "FK", "Yes", "References Plant.plantId (M:1)"),
            ("scanWeekNum", "INT", "", "Yes", "Farm week number of the scan (4–12)"),
            ("scanImgPath", "VARCHAR / URL", "", "No", "Path or URL of the scanned leaf image"),
            ("scanTopBarKey", "VARCHAR(50)", "", "No", "Top predicted disease/class key"),
            ("scanTopPct", "DECIMAL(5,2)", "", "No", "Top prediction confidence percentage"),
            ("scanHealthyPct", "DECIMAL(5,2)", "", "No", "Healthy classification percentage"),
            ("scanTcvPct", "DECIMAL(5,2)", "", "No", "TLCV classification percentage"),
            ("scanEarlyBlightPct", "DECIMAL(5,2)", "", "No", "Early blight classification percentage"),
            ("scanSpottedWiltPct", "DECIMAL(5,2)", "", "No", "Spotted wilt classification percentage"),
            ("scanIsOffline", "BOOLEAN", "", "Yes", "True if the scan was classified offline on the device"),
            ("scanSavedAt", "DATETIME", "", "Yes", "Scan saved timestamp"),
        ],
    },
    {
        "name": "5. Recommendation",
        "desc": (
            "The Recommendation table stores the care recommendation generated for one completed scan. "
            "It contains the main disease name, urgency note, summary of advice, recommended action steps, "
            "and prevention tips that help the grower respond to the detected condition. It also records "
            "whether critical removal is advised and where the recommendation came from, such as AI or "
            "rule-based guidance. Even when more than one disease is detected, the tips remain inside this "
            "single recommendation record linked to the scan."
        ),
        "rows": [
            ("recId", "VARCHAR / INT", "PK", "Yes", "Unique recommendation identifier"),
            ("Scan_scanId", "VARCHAR / INT", "FK", "Yes", "References Scan.scanId (1:1)"),
            ("recDiseaseName", "VARCHAR(100)", "", "No", "Main detected disease name"),
            ("recUrgencyNote", "VARCHAR(255)", "", "No", "Urgency message for the grower"),
            ("recSummary", "TEXT", "", "No", "Short summary of the care advice"),
            ("recActionSteps", "TEXT / JSON", "", "No", "Recommended action steps"),
            ("recPreventTips", "TEXT / JSON", "", "No", "Prevention tips"),
            ("recIsCritRemove", "BOOLEAN", "", "No", "True if critical leaf/plant removal is advised"),
            ("recSource", "VARCHAR(50)", "", "No", "Source of recommendation (e.g., AI, offline)"),
            ("recSavedAt", "DATETIME", "", "Yes", "Recommendation saved timestamp"),
        ],
    },
    {
        "name": "6. Credits",
        "desc": (
            "The Credits table stores the user’s credit account used for leaf scanning in TomatoInsight. "
            "It keeps the current available balance and tracks whether free signup credits and profile-setup "
            "bonus credits have already been granted. Because one user has only one credits account, this "
            "table holds the running balance, while purchase history is recorded separately in the Payment "
            "table. The update timestamp shows when the credit balance was last changed."
        ),
        "rows": [
            ("credId", "VARCHAR / INT", "PK", "Yes", "Unique credits account identifier"),
            ("User_userId", "VARCHAR / UID", "FK", "Yes", "References User.userId (1:1)"),
            ("credBal", "INT", "", "Yes", "Current available scan credit balance"),
            ("credSignupGrant", "BOOLEAN", "", "Yes", "True if signup credits were already granted"),
            ("credProfBonGrant", "BOOLEAN", "", "Yes", "True if profile-setup bonus was already granted"),
            ("credUpdAt", "DATETIME", "", "Yes", "Last credits update timestamp"),
        ],
    },
    {
        "name": "7. Payment",
        "desc": (
            "The Payment table stores each credit pack purchase or top-up transaction made by the grower. "
            "It records the selected pack, number of credits added, listed price, amount charged, currency, "
            "payment status, and payment method such as GCash. It also saves the credit balance before and "
            "after the transaction so the system can keep a clear payment history for the user’s credits "
            "account. Each successful payment increases the related Credits balance."
        ),
        "rows": [
            ("payId", "VARCHAR / INT", "PK", "Yes", "Unique payment identifier"),
            ("Credits_credId", "VARCHAR / INT", "FK", "Yes", "References Credits.credId (M:1)"),
            ("payEmail", "VARCHAR(100)", "", "Yes", "Email used for the payment"),
            ("payPackId", "VARCHAR(50)", "", "Yes", "Credit pack ID (starter, grower, season)"),
            ("payPackName", "VARCHAR(50)", "", "Yes", "Credit pack name"),
            ("payCred", "INT", "", "Yes", "Number of credits added by this payment"),
            ("payListPrice", "DECIMAL(10,2)", "", "Yes", "Listed pack price"),
            ("payCharge", "DECIMAL(10,2)", "", "Yes", "Amount charged"),
            ("payCurrency", "CHAR(3)", "", "Yes", "Currency code (PHP)"),
            ("payStats", "VARCHAR(50)", "", "Yes", "Payment status (e.g., success, pending)"),
            ("payMethod", "VARCHAR(50)", "", "Yes", "Payment method (e.g., GCash)"),
            ("payBalBef", "INT", "", "Yes", "Credit balance before payment"),
            ("payBalAft", "INT", "", "Yes", "Credit balance after payment"),
            ("payCreatedAt", "DATETIME", "", "Yes", "Payment created timestamp"),
        ],
    },
    {
        "name": "8. Deletion",
        "desc": (
            "The Deletion table stores the record created when a plant is removed from the farm blueprint. "
            "It keeps the link to the deleted plant and the exact date and time the deletion happened. "
            "This helps TomatoInsight track removed plants for history and sync purposes, even after the "
            "plant is no longer shown on the active farm layout."
        ),
        "rows": [
            ("delId", "VARCHAR / INT", "PK", "Yes", "Unique deletion identifier"),
            ("Plant_plantId", "VARCHAR / INT", "FK", "Yes", "References Plant.plantId (1:1)"),
            ("delDeldAt", "DATETIME", "", "Yes", "Date and time the plant was deleted"),
        ],
    },
    {
        "name": "9. Notification",
        "desc": (
            "The Notification table stores alerts related to a specific plant so the grower can see important "
            "updates on the farm dashboard. These alerts may include disease warnings, urgent care reminders, "
            "or notices that a plant week needs to be scanned again. Each notification contains a title, "
            "message, optional type, read status, and creation timestamp so the system can show unread plant "
            "alerts and keep a history of notifications."
        ),
        "rows": [
            ("notifId", "VARCHAR / INT", "PK", "Yes", "Unique notification identifier"),
            ("Plant_plantId", "VARCHAR / INT", "FK", "Yes", "References Plant.plantId (M:1)"),
            ("notifTitle", "VARCHAR(100)", "", "Yes", "Notification title"),
            ("notifMessage", "TEXT", "", "Yes", "Notification message body"),
            ("notifType", "VARCHAR(50)", "", "No", "Notification type/category"),
            ("notifIsRead", "BOOLEAN", "", "Yes", "True if the user already read the notification"),
            ("notifCreatedAt", "DATETIME", "", "Yes", "Notification created timestamp"),
        ],
    },
]


def set_run_font(run, size=11, bold=False, color=None):
    run.font.name = "Times New Roman"
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
    run.font.size = Pt(size)
    run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def shade_cell(cell, hex_color: str):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), hex_color)
    shd.set(qn("w:val"), "clear")
    tcPr.append(shd)


def add_table(doc: Document, rows: list[tuple[str, str, str, str, str]]):
    table = doc.add_table(rows=1 + len(rows), cols=5)
    table.style = "Table Grid"
    headers = ["Field Name", "Data Type", "Key", "Required", "Description"]
    for i, header in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        run = p.add_run(header)
        set_run_font(run, size=10, bold=True, color=RGBColor(0, 0, 0))
        shade_cell(cell, "FFFFFF")

    for r_idx, row in enumerate(rows, start=1):
        for c_idx, value in enumerate(row):
            cell = table.rows[r_idx].cells[c_idx]
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(value)
            set_run_font(run, size=10, color=RGBColor(0, 0, 0))
            shade_cell(cell, "FFFFFF")

    widths = [Inches(1.5), Inches(1.3), Inches(0.7), Inches(0.8), Inches(2.7)]
    for row in table.rows:
        for idx, cell in enumerate(row.cells):
            cell.width = widths[idx]
    return table


def main():
    doc = Document()

    section = doc.sections[0]
    section.top_margin = Inches(0.8)
    section.bottom_margin = Inches(0.8)
    section.left_margin = Inches(0.8)
    section.right_margin = Inches(0.8)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("TomatoInsight Data Dictionary")
    set_run_font(run, size=18, bold=True, color=RGBColor(183, 28, 28))

    intro = doc.add_paragraph()
    run = intro.add_run(
        "This data dictionary describes the entities, attributes, keys, and relationships "
        "used in the TomatoInsight system based on the project ERD."
    )
    set_run_font(run, size=11)

    rel_heading = doc.add_paragraph()
    run = rel_heading.add_run("Relationship Summary")
    set_run_font(run, size=14, bold=True, color=RGBColor(183, 28, 28))

    relationships = [
        "User — 1:1 must have → Farm Layout",
        "User — 1:1 must have → Credits",
        "Farm Layout — 1:M contains → Plant",
        "Plant — 1:M may have → Scan",
        "Plant — 1:M may have → Notification",
        "Plant — 1:1 may have → Deletion",
        "Credits — 1:M may have → Payment",
        "Scan — 1:1 must have → Recommendation",
    ]
    for item in relationships:
        p = doc.add_paragraph(style="List Bullet")
        run = p.add_run(item)
        set_run_font(run, size=11)

    for entity in TABLES:
        heading = doc.add_paragraph()
        run = heading.add_run(entity["name"])
        set_run_font(run, size=14, bold=True, color=RGBColor(183, 28, 28))

        desc = doc.add_paragraph()
        run = desc.add_run(entity["desc"])
        set_run_font(run, size=11)

        add_table(doc, entity["rows"])
        doc.add_paragraph()

    notes_h = doc.add_paragraph()
    run = notes_h.add_run("Notes")
    set_run_font(run, size=14, bold=True, color=RGBColor(183, 28, 28))

    notes = [
        "PK = Primary Key; FK = Foreign Key.",
        "1:1 = one-to-one; 1:M = one-to-many.",
        "Farm week range used by TomatoInsight is Week 4 to Week 12.",
        "Multiple disease tips are stored inside one Recommendation record, so Scan → Recommendation remains 1:1.",
        "One Credits account stores the balance (credBal). Multiple purchases are stored in Payment, so Credits → Payment is 1:M.",
        "scanIsOffline marks whether a Scan was classified offline on the device using on-device TFLite.",
    ]
    for note in notes:
        p = doc.add_paragraph(style="List Number")
        run = p.add_run(note)
        set_run_font(run, size=11)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        doc.save(OUT)
        print(f"Saved: {OUT}")
    except PermissionError:
        doc.save(OUT_FALLBACK)
        print(f"Original file is open. Saved: {OUT_FALLBACK}")


if __name__ == "__main__":
    main()
