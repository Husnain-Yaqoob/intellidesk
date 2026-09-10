"""Generates a synthetic corpus of resolved IT incidents.

WHY THIS EXISTS
---------------
Similarity search and resolution suggestion need a body of past incidents that carry
real resolution text. Public IT ticket datasets almost always stop at the category
label, and the anonymised ServiceNow-style event logs have no usable free text at all.

So this file fabricates a corpus, and the app says so everywhere it surfaces a result
(`corpus_is_synthetic` in /model-info). If you later get hold of a real export, drop it
in as data/corpus.csv with the same columns and delete this file — nothing else changes.

Columns: reference, title, description, category, subcategory, resolution,
         resolution_hours, impact, urgency, priority
"""

from __future__ import annotations

import csv
import random
from pathlib import Path

DATA_DIR = Path(__file__).parent / "data"
CORPUS = DATA_DIR / "corpus.csv"

# Each entry: category, subcategory, symptom phrasings, resolutions, typical hours.
TEMPLATES = [
    (
        "Network", "VPN",
        [
            "VPN disconnects every {n} minutes when I work from home",
            "VPN keeps dropping and I lose the shared drives",
            "Remote VPN connection unstable since {day}",
            "Cannot stay connected to the company network remotely",
            "VPN client drops the tunnel repeatedly during calls",
        ],
        [
            "Updated the VPN client to the current build and reissued the user certificate.",
            "Reinstalled the VPN client and cleared the cached connection profile.",
            "Refreshed the expired VPN certificate on the user's device.",
        ],
        5.5,
    ),
    (
        "Network", "Wi-Fi",
        [
            "Wi-Fi drops in the {floor} floor meeting rooms",
            "Wireless keeps disconnecting at my desk but the cable is fine",
            "Poor Wi-Fi signal since the office move",
            "Wi-Fi authentication fails on my laptop only",
        ],
        [
            "Access point had failed over to a congested channel. Rebooted the AP and pinned the channel.",
            "Re-provisioned the wireless profile and removed the stale saved network.",
            "Replaced the failing access point from stock.",
        ],
        6.0,
    ),
    (
        "Network", "Connectivity",
        [
            "Cannot reach the shared drive from my desk",
            "Network is very slow on my machine only",
            "No network connection after moving desks",
        ],
        [
            "Switch port had negotiated at 10Mbps and was dropping frames. Moved the user to a spare port.",
            "Patched the desk port through to the correct VLAN.",
            "Replaced a faulty patch lead.",
        ],
        3.5,
    ),
    (
        "Software", "Outlook",
        [
            "Outlook crashes whenever I open an attachment",
            "Outlook closes itself when I open a PDF",
            "Outlook freezes on startup since the update",
            "Outlook keeps asking for my password in a loop",
        ],
        [
            "Disabled the conflicting preview add-in and repaired the Office installation.",
            "Rebuilt the Outlook profile and cleared the cached credentials.",
            "Removed the stale credential from the credential manager and re-authenticated.",
        ],
        4.5,
    ),
    (
        "Software", "Office",
        [
            "Excel freezes on the monthly report workbook",
            "Word documents will not save to the shared folder",
            "PowerPoint crashes when I insert images",
        ],
        [
            "Workbook contained volatile array formulas over full columns. Rebuilt the ranges.",
            "Cleared the Office document cache and restored the file from version history.",
            "Repaired the Office installation and updated the graphics driver.",
        ],
        7.0,
    ),
    (
        "Software", "Teams",
        [
            "Nobody can hear me on Teams calls",
            "Teams will not share my screen",
            "Teams keeps signing me out",
        ],
        [
            "Teams was bound to the wrong capture device after a driver update. Reset device preferences.",
            "Granted the screen recording permission and restarted the client.",
            "Cleared the Teams cache and re-registered the device.",
        ],
        2.5,
    ),
    (
        "Hardware", "Laptop",
        [
            "Laptop will not power on this morning",
            "Laptop battery drains within an hour",
            "Laptop overheating and shutting down",
        ],
        [
            "Failed power adapter. Replaced the adapter and confirmed the battery charges.",
            "Replaced the swollen battery and disposed of the old unit.",
            "Cleared blocked vents and replaced the failing fan.",
        ],
        8.0,
    ),
    (
        "Hardware", "Printer",
        [
            "Printer on the {floor} floor jams on every job",
            "Printer will not pick up paper",
            "Print jobs disappear from the queue",
        ],
        [
            "Worn pickup roller replaced and the paper path cleared.",
            "Replaced the separation pad and calibrated the tray.",
            "Restarted the print spooler and reinstalled the queue on the server.",
        ],
        6.5,
    ),
    (
        "Hardware", "Peripherals",
        [
            "Second monitor is not detected",
            "Keyboard keys have stopped responding",
            "Docking station stopped working after the update",
        ],
        [
            "Docking station firmware was out of date. Updated firmware and reseated the cable.",
            "Replaced the keyboard from stock.",
            "Rolled back the dock driver and applied the vendor's current firmware.",
        ],
        3.0,
    ),
    (
        "Email", "Delivery",
        [
            "Not receiving external emails since {day}",
            "Emails to a client are bouncing back",
            "Messages sit in the outbox and never send",
        ],
        [
            "Messages were held by the mail filter after a rule change. Released the queue and corrected the rule.",
            "Recipient domain had a stale MX record cached. Flushed and confirmed delivery.",
            "Repaired the send connector and re-sent the queued items.",
        ],
        5.0,
    ),
    (
        "Email", "Mailbox",
        [
            "Mailbox is full and I cannot send",
            "Cannot open the shared mailbox",
            "Calendar invitations are not appearing",
        ],
        [
            "Increased the mailbox quota and archived mail older than two years.",
            "Granted full access to the shared mailbox and re-added the account.",
            "Rebuilt the calendar folder permissions.",
        ],
        3.0,
    ),
    (
        "Account / Access", "Password",
        [
            "My account is locked out again",
            "Password reset link is not arriving",
            "Cannot sign in after changing my password",
        ],
        [
            "Unlocked the account and reset the password. Stale credentials on the user's phone were causing lockouts.",
            "Re-sent the reset from the admin console and verified the recovery address.",
            "Cleared the cached credential on the workstation.",
        ],
        1.5,
    ),
    (
        "Account / Access", "Permissions",
        [
            "No access to the {team} shared folder",
            "Cannot open the reporting system since my move",
            "Read-only access where I need to edit",
        ],
        [
            "Added the user to the correct security group. Access confirmed after sign-out and sign-in.",
            "Corrected the group membership and removed the deny rule inherited from the old team.",
            "Granted contributor rights on the site and confirmed with the data owner.",
        ],
        4.0,
    ),
    (
        "Account / Access", "MFA",
        [
            "Multi-factor prompts are not arriving on my phone",
            "New phone and I cannot get authenticator codes",
            "Locked out of MFA after replacing my handset",
        ],
        [
            "Re-registered the authenticator app against the user's account.",
            "Issued a temporary access pass and re-enrolled the device.",
            "Cleared the old device registration and re-enrolled.",
        ],
        2.0,
    ),
    (
        "Security", "Phishing",
        [
            "Suspicious email asking me to confirm my password",
            "Received an email pretending to be from the finance director",
            "Reporting a phishing message received this morning",
        ],
        [
            "Confirmed as phishing. Blocked the sender domain, purged matching messages and briefed the floor.",
            "Quarantined the campaign across all mailboxes and reported the domain.",
            "Blocked the sender and reset the credentials of anyone who clicked.",
        ],
        3.5,
    ),
    (
        "Security", "Malware",
        [
            "Antivirus alert popped up on my machine",
            "Browser is redirecting to pages I did not open",
            "Warning about a blocked file download",
        ],
        [
            "Threat was quarantined on arrival. Ran a full scan, confirmed clean, removed the source download.",
            "Removed the malicious browser extension and reset the browser profile.",
            "Reimaged the workstation as a precaution and restored data from backup.",
        ],
        6.0,
    ),
]

FILLERS = {
    "n": ["five", "ten", "fifteen", "twenty"],
    "day": ["Monday", "yesterday", "last week", "Thursday"],
    "floor": ["first", "second", "third", "ground"],
    "team": ["finance", "HR", "operations", "marketing"],
}

# Incidents that genuinely sit between two categories. Real service desks are full of
# these, and a corpus without them trains a classifier that looks perfect on paper and
# falls over on the first ambiguous ticket. Each one is labelled with one of its two
# plausible categories at random, which puts an honest ceiling on achievable accuracy.
AMBIGUOUS = [
    ("Cannot log in to the VPN",
     ["Network", "Account / Access"], "VPN",
     "Reset the account password and reissued the VPN certificate.", 4.0),
    ("Email on my phone has stopped working",
     ["Email", "Account / Access"], "Mobile",
     "Re-authenticated the mail profile on the device.", 3.0),
    ("Laptop will not connect to the wireless network",
     ["Hardware", "Network"], "Wi-Fi",
     "Replaced the failing wireless card and re-provisioned the profile.", 6.0),
    ("Teams keeps signing me out several times a day",
     ["Software", "Account / Access"], "Teams",
     "Cleared the cached token and re-registered the device.", 2.5),
    ("Printer is asking me for a username and password",
     ["Hardware", "Account / Access"], "Printer",
     "Corrected the print server permissions and cleared the saved credential.", 3.5),
    ("Strange pop-up appeared and now the machine is slow",
     ["Security", "Software"], "Malware",
     "Removed the unwanted program and ran a full scan.", 5.0),
    ("Cannot open a document from a link in an email",
     ["Email", "Software"], "Attachments",
     "Corrected the file association and released the blocked attachment.", 2.5),
    ("Shared mailbox will not open on the new laptop",
     ["Email", "Hardware"], "Mailbox",
     "Re-added the shared mailbox after rebuilding the Outlook profile.", 3.0),
]

# Everyday noise: how people actually write tickets.
PREFIXES = ["", "", "", "hi, ", "sorry to bother you but ", "urgent - ", "quick one: ", "again - "]


def _noisify(text: str, rng: random.Random) -> str:
    """Roughens the text so the corpus is not a set of clean repeated strings.

    Without this the classifier scores 100% on held-out data purely because the same
    sentence appears in both splits, which is leakage dressed up as a result.
    """
    words = text.split()

    if rng.random() < 0.25 and len(words) > 4:  # people drop words
        words.pop(rng.randrange(len(words)))

    if rng.random() < 0.20:  # and make typos
        index = rng.randrange(len(words))
        word = words[index]
        if len(word) > 3:
            position = rng.randrange(len(word) - 1)
            words[index] = word[:position] + word[position + 1] + word[position] + word[position + 2:]

    result = " ".join(words)

    if rng.random() < 0.30:
        result = result.lower()

    return (rng.choice(PREFIXES) + result).strip()

DETAIL_SUFFIXES = [
    "It started this week and it is stopping me working.",
    "Nobody else on my team seems to have the same problem.",
    "I have tried restarting and it made no difference.",
    "This has happened twice before and was fixed at the time.",
    "It is intermittent but getting worse.",
    "",
]

IMPACTS = ["HIGH", "MEDIUM", "LOW"]
URGENCIES = ["HIGH", "MEDIUM", "LOW"]

PRIORITY = {
    ("HIGH", "HIGH"): "P1", ("HIGH", "MEDIUM"): "P2", ("HIGH", "LOW"): "P3",
    ("MEDIUM", "HIGH"): "P2", ("MEDIUM", "MEDIUM"): "P3", ("MEDIUM", "LOW"): "P4",
    ("LOW", "HIGH"): "P3", ("LOW", "MEDIUM"): "P4", ("LOW", "LOW"): "P4",
}

# Multipliers so priority genuinely affects resolution time — otherwise the
# regression has nothing to learn beyond the category mean.
PRIORITY_FACTOR = {"P1": 0.45, "P2": 0.7, "P3": 1.0, "P4": 1.6}


def fill(template: str, rng: random.Random) -> str:
    for key, options in FILLERS.items():
        token = "{" + key + "}"
        if token in template:
            template = template.replace(token, rng.choice(options))
    return template


def generate(rows: int = 1200, seed: int = 7, ambiguous_share: float = 0.14) -> list[dict]:
    """Builds the corpus.

    `ambiguous_share` of the rows are drawn from AMBIGUOUS and labelled with one of
    two plausible categories, so there is an honest ceiling on accuracy. Turning it
    to 0 produces a corpus a classifier can score ~100% on — which is exactly the
    result you should not believe.
    """
    rng = random.Random(seed)
    out = []
    for i in range(rows):
        if rng.random() < ambiguous_share:
            title_seed, categories, subcategory, resolution, base_hours = rng.choice(AMBIGUOUS)
            category = rng.choice(categories)
            title = _noisify(fill(title_seed, rng), rng)
            resolutions = [resolution]
        else:
            category, subcategory, symptoms, resolutions, base_hours = rng.choice(TEMPLATES)
            title = _noisify(fill(rng.choice(symptoms), rng), rng)

        description = (title + ". " + rng.choice(DETAIL_SUFFIXES)).strip()
        impact = rng.choice(IMPACTS)
        urgency = rng.choice(URGENCIES)
        priority = PRIORITY[(impact, urgency)]

        hours = base_hours * PRIORITY_FACTOR[priority] * rng.uniform(0.55, 1.65)

        out.append({
            "reference": f"INC-{100000 + i}",
            "title": title,
            "description": description,
            "category": category,
            "subcategory": subcategory,
            "resolution": rng.choice(resolutions),
            "resolution_hours": round(hours, 2),
            "impact": impact,
            "urgency": urgency,
            "priority": priority,
        })

    # Drop exact duplicate texts: the same sentence landing in both splits is the
    # leakage this generator exists to avoid.
    seen: set[str] = set()
    deduped = []
    for row in out:
        key = row["description"].lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(row)
    return deduped


def main() -> None:
    DATA_DIR.mkdir(exist_ok=True)
    rows = generate()
    with CORPUS.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {len(rows)} synthetic incidents to {CORPUS}")


if __name__ == "__main__":
    main()
