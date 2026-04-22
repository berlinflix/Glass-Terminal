from reportlab.lib.pagesizes import LETTER
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.lib.enums import TA_LEFT, TA_CENTER
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, HRFlowable, ListFlowable, ListItem
)
from reportlab.lib.colors import HexColor, black
from datetime import datetime
import shutil

_ts = datetime.now().strftime("%Y-%m-%d_%H%M%S")
OUTPUTS = [
    rf"C:\Users\suyas\Desktop\Suyash_Singh_Resume_{_ts}.pdf",
    rf"C:\Users\suyas\skill\~\resumes\Suyash_Singh_VIT_SRIP_Resume_{_ts}.pdf",
]
PRIMARY = OUTPUTS[0]

NAVY = HexColor("#0B2545")
ACCENT = HexColor("#13315C")
RULE = HexColor("#8DA9C4")

styles = getSampleStyleSheet()

name_style = ParagraphStyle(
    "Name", parent=styles["Title"], fontName="Helvetica-Bold",
    fontSize=22, textColor=NAVY, alignment=TA_CENTER, spaceAfter=2, leading=26,
)
contact_style = ParagraphStyle(
    "Contact", parent=styles["Normal"], fontName="Helvetica",
    fontSize=9.5, alignment=TA_CENTER, textColor=black, spaceAfter=6, leading=12,
)
section_style = ParagraphStyle(
    "Section", parent=styles["Heading2"], fontName="Helvetica-Bold",
    fontSize=11.5, textColor=NAVY, spaceBefore=8, spaceAfter=2, leading=14,
)
body_style = ParagraphStyle(
    "Body", parent=styles["Normal"], fontName="Helvetica",
    fontSize=9.8, leading=12.6, alignment=TA_LEFT, spaceAfter=2,
)
sub_style = ParagraphStyle(
    "Sub", parent=body_style, fontName="Helvetica-Bold", textColor=ACCENT, spaceAfter=1,
)
small_style = ParagraphStyle(
    "Small", parent=body_style, fontSize=9, leading=11.5, textColor=HexColor("#333333"),
)

def rule():
    return HRFlowable(width="100%", thickness=0.6, color=RULE,
                      spaceBefore=1, spaceAfter=4)

def bullets(items):
    return ListFlowable(
        [ListItem(Paragraph(i, body_style), leftIndent=10, bulletColor=NAVY)
         for i in items],
        bulletType="bullet", start="•", leftIndent=12, bulletFontSize=8,
        bulletFontName="Helvetica-Bold",
    )

doc = SimpleDocTemplate(
    PRIMARY, pagesize=LETTER,
    leftMargin=0.55*inch, rightMargin=0.55*inch,
    topMargin=0.4*inch, bottomMargin=0.4*inch,
    title="Suyash Singh — Resume", author="Suyash Singh",
)

story = []

# Header
story.append(Paragraph("SUYASH SINGH", name_style))
story.append(Paragraph(
    "B.Tech CSE (Year 1) &nbsp;•&nbsp; Vellore Institute of Technology, Chennai<br/>"
    "suyashsinghdtg@gmail.com &nbsp;|&nbsp; "
    '<a href="https://github.com/berlinflix" color="#13315C">github.com/berlinflix</a>',
    contact_style))
story.append(rule())

# Profile
story.append(Paragraph("PROFILE", section_style))
story.append(Paragraph(
    "First-year B.Tech CSE student with demonstrated technical output: an "
    "article on Hyperledger Fabric forthcoming in the May 2026 edition of "
    "<i>Open Source For You</i> magazine, and a portfolio of "
    "production-quality systems built end-to-end — a zero-trust Android "
    "control-plane for a headless Kali Pi, an end-to-end encrypted "
    "file-sharing app, and a gamified cleanup platform. Seeking a summer "
    "research internship in cybersecurity, applied AI (including AI "
    "Security and Neuro AI), or secure distributed systems.",
    body_style))

# Research interests
story.append(Paragraph("RESEARCH INTERESTS", section_style))
story.append(rule())
story.append(Paragraph(
    "Cybersecurity and Applied Cryptography (application &amp; systems "
    "security, end-to-end encryption, secure communications); Artificial "
    "Intelligence / Machine Learning — with interest in AI/ML Security, "
    "AI applied to Neuroscience (Neuro AI — EEG, brain–computer "
    "interfaces), and applied biomedical AI; Blockchain and Distributed "
    "Systems; Quantum Computing with interest in Quantum Machine Learning.",
    body_style))

# Education
story.append(Paragraph("EDUCATION", section_style))
story.append(rule())
story.append(Paragraph(
    "<b>Vellore Institute of Technology (VIT), Chennai</b><br/>"
    "B.Tech, Computer Science and Engineering &nbsp;—&nbsp; Year 1 (2025–2026) "
    "&nbsp;·&nbsp; <b>CGPA: 8.8 / 10</b>",
    body_style))

# Publication
story.append(Paragraph("PUBLICATION", section_style))
story.append(rule())
story.append(Paragraph(
    "<b>“Hyperledger Fabric Experiment”</b> — "
    "<i>Open Source For You (OSFY)</i>, <b>forthcoming in the May 2026 "
    "edition</b>. Walkthrough of deploying a permissioned blockchain on "
    "Hyperledger Fabric: chaincode deployment, peer / orderer configuration, "
    "channel setup, and end-to-end transaction flow. "
    'Companion code: <a href="https://github.com/berlinflix/hyperledger-fabric-experiment" '
    'color="#13315C">github.com/berlinflix/hyperledger-fabric-experiment</a>',
    body_style))

# Projects & Technical Work
story.append(Paragraph("PROJECTS &amp; TECHNICAL WORK", section_style))
story.append(rule())

story.append(Paragraph(
    "<b>Neo Browser — Security Testing with IamNeo.ai &amp; VIT Chennai "
    "Faculty</b> &nbsp;·&nbsp; 2025", sub_style))
story.append(bullets([
    "Identified security vulnerabilities in the <b>Neo Browser</b> used for "
    "proctored academic assessments.",
    "Carried out coordinated testing alongside the IamNeo.ai team and VIT "
    "Chennai faculty to reproduce and scope the findings.",
    "Submitted a structured disclosure report to IamNeo.ai.",
]))

story.append(Paragraph(
    "<b>Pi Glass Terminal — Zero-Trust Mobile Control Plane for a Headless "
    "Kali Pi</b> &nbsp;·&nbsp; 2025", sub_style))
story.append(bullets([
    "Zero-trust architecture: Android phone (Kotlin / Jetpack Compose) drives "
    "a headless Raspberry Pi Zero W running Kali Linux with <b>no physical "
    "data link</b> — communication only over the phone’s Wi-Fi hotspot.",
    "Authentication strictly via <b>Ed25519</b> SSH keys stored in the "
    "Android Keystore; <b>TOFU</b> host-key pinning prevents MITM on first "
    "connection.",
    "VNC (5901) and websockify (6080) bound to <i>localhost</i> on the Pi and "
    "reached via SSH <b>local port forwarding</b>; <b>ufw</b> restricts the "
    "Pi to port 22 from the hotspot subnet only.",
    "Discovery via <b>mDNS</b> (<i>_glassterminal._tcp</i> / Avahi) with a "
    "<b>parallel TCP-22 sweep</b> fallback; foreground service keeps SSH "
    "tunnels alive through long-running tasks.",
]))

story.append(Paragraph(
    "<b>Syncro — End-to-End Encrypted File Sharing App</b> &nbsp;·&nbsp; 2025",
    sub_style))
story.append(bullets([
    "E2EE architecture for device-to-device file transfer — server cannot "
    "decrypt content in transit or at rest. "
    'Code: <a href="https://github.com/berlinflix/SyncroFileSharingApp" '
    'color="#13315C">github.com/berlinflix/SyncroFileSharingApp</a>',
]))

story.append(Paragraph(
    "<b>EcoQuest — Gamified Environmental Cleanup Platform</b> &nbsp;·&nbsp; 2025",
    sub_style))
story.append(bullets([
    "Quests-and-points system incentivising local cleanup actions. "
    'Code: <a href="https://github.com/berlinflix/ecoquest" color="#13315C">'
    "github.com/berlinflix/ecoquest</a>",
]))

# Leadership
story.append(Paragraph("LEADERSHIP &amp; POSITIONS", section_style))
story.append(rule())
story.append(bullets([
    "<b>Cybersecurity Lead</b> — Microsoft Innovations Club (MIC), VIT Chennai",
    "<b>Campus Ambassador</b> — GeeksforGeeks, VIT Chennai",
]))

# Skills
story.append(Paragraph("TECHNICAL SKILLS", section_style))
story.append(rule())
story.append(Paragraph(
    "<b>Languages:</b> Python, Kotlin, JavaScript, C / C++, Java<br/>"
    "<b>Android:</b> Jetpack Compose, Material 3, Coroutines / Flow, "
    "Foreground Services, Android Keystore, WebView / JavascriptInterface<br/>"
    "<b>Security &amp; Systems:</b> Ed25519 / SSH key authentication, SSH "
    "tunneling &amp; port forwarding, TOFU host-key pinning, E2EE, UFW; "
    "Linux (Kali, Debian), Raspberry Pi, VNC / noVNC / websockify, mDNS / "
    "Avahi<br/>"
    "<b>Blockchain:</b> Hyperledger Fabric (chaincode, peers, orderers)<br/>"
    "<b>Tooling:</b> Git &amp; GitHub, Gradle (Kotlin DSL)",
    small_style))

doc.build(story)

for out in OUTPUTS[1:]:
    shutil.copyfile(PRIMARY, out)
    print(f"Copied to {out}")

print(f"Wrote {PRIMARY}")
