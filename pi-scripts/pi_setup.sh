#!/usr/bin/env bash
# ============================================================================
# Pi Glass Terminal — Raspberry Pi Setup Script
# Configures: Firewall, VNC (localhost-only), Websockify, XFCE touch UI,
#             mDNS/Avahi discovery, power savings, Wi-Fi hotspot autoconnect
# Target OS:  Kali Linux (ARM) on Raspberry Pi Zero W
# Usage:      chmod +x pi_setup.sh && sudo ./pi_setup.sh
# ============================================================================
set -euo pipefail

# ── Color helpers ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
err()   { echo -e "${RED}[ERR]${NC}   $*"; exit 1; }

# ── Pre-flight ────────────────────────────────────────────────────────────────
[[ $EUID -ne 0 ]] && err "This script must be run as root (sudo)."

info "Updating package lists…"
apt-get update -qq

# ============================================================================
# 1. FIREWALL — ufw: allow SSH from hotspot subnet ONLY
# ============================================================================
info "Installing and configuring UFW firewall…"
apt-get install -y -qq ufw > /dev/null

ufw --force reset > /dev/null 2>&1
ufw default deny incoming
ufw default allow outgoing

# Allow SSH only from Android hotspot subnet (192.168.43.0/24)
# Also allow 192.168.49.0/24 (Wi-Fi Direct) and 10.42.0.0/24 (some phones)
ufw allow from 192.168.43.0/24 to any port 22 proto tcp comment "SSH from hotspot"
ufw allow from 192.168.49.0/24 to any port 22 proto tcp comment "SSH from Wi-Fi Direct"
ufw allow from 10.42.0.0/24    to any port 22 proto tcp comment "SSH from alt hotspot"

ufw --force enable
ok "Firewall: SSH (22) allowed from hotspot subnets only. All else denied."

# ============================================================================
# 2. SSH HARDENING
# ============================================================================
info "Hardening SSH daemon…"
SSHD_CONF="/etc/ssh/sshd_config"
cp "$SSHD_CONF" "${SSHD_CONF}.bak.$(date +%s)"

# Disable password auth — key-only
sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/'       "$SSHD_CONF"
sed -i 's/^#\?ChallengeResponseAuthentication.*/ChallengeResponseAuthentication no/' "$SSHD_CONF"
sed -i 's/^#\?UsePAM.*/UsePAM no/'                                       "$SSHD_CONF"
sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/'       "$SSHD_CONF"

# Only allow key-based auth methods
sed -i 's/^#\?PubkeyAuthentication.*/PubkeyAuthentication yes/'           "$SSHD_CONF"

# Restrict to the hotspot interfaces
# (We don't restrict ListenAddress because the IP is dynamic via DHCP)

systemctl restart sshd
ok "SSH hardened: password auth disabled, key-only login."

# ============================================================================
# 3. VNC SERVER (TigerVNC) — bound to localhost:5900
# ============================================================================
info "Installing TigerVNC…"
apt-get install -y -qq tigervnc-standalone-server tigervnc-common > /dev/null

VNC_USER="${SUDO_USER:-kali}"
VNC_HOME=$(eval echo "~$VNC_USER")

# Create VNC password (user will be prompted)
info "Setting VNC password for user '$VNC_USER'…"
sudo -u "$VNC_USER" mkdir -p "$VNC_HOME/.vnc"

if [ ! -f "$VNC_HOME/.vnc/passwd" ]; then
    echo ""
    echo -e "${CYAN}Enter a VNC password (you'll use this in the app):${NC}"
    sudo -u "$VNC_USER" vncpasswd "$VNC_HOME/.vnc/passwd"
fi

# VNC startup script — launches XFCE
cat > "$VNC_HOME/.vnc/xstartup" << 'XSTARTUP'
#!/bin/sh
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export XDG_SESSION_TYPE=x11
exec startxfce4
XSTARTUP
chmod +x "$VNC_HOME/.vnc/xstartup"
chown -R "$VNC_USER":"$VNC_USER" "$VNC_HOME/.vnc"

# Systemd service — VNC on localhost:5900 ONLY
cat > /etc/systemd/system/vncserver@.service << VNCUNIT
[Unit]
Description=TigerVNC Server (display %i)
After=syslog.target network.target

[Service]
Type=forking
User=$VNC_USER
Group=$VNC_USER
WorkingDirectory=$VNC_HOME

ExecStartPre=-/usr/bin/vncserver -kill :%i > /dev/null 2>&1
ExecStart=/usr/bin/vncserver :%i \\
    -geometry 1280x720 \\
    -depth 24 \\
    -localhost yes \\
    -SecurityTypes VncAuth
ExecStop=/usr/bin/vncserver -kill :%i
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
VNCUNIT

systemctl daemon-reload
systemctl enable vncserver@1.service
systemctl restart vncserver@1.service || true
ok "VNC: TigerVNC bound to 127.0.0.1:5901 (display :1), auto-start on boot."

# ============================================================================
# 4. WEBSOCKIFY — bridges WebSocket (localhost:6080) → VNC (localhost:5901)
# ============================================================================
info "Installing websockify and noVNC…"
apt-get install -y -qq python3-pip python3-numpy novnc > /dev/null 2>&1 || true
pip3 install websockify --break-system-packages 2>/dev/null || pip3 install websockify

# Our Android app bundles its own noVNC HTML, so websockify only needs to do
# the WebSocket-to-TCP bridge. We skip --web since /usr/share/novnc may not
# exist if the novnc package wasn't available.
NOVNC_WEB=""
if [ -d /usr/share/novnc ]; then
    NOVNC_WEB="--web /usr/share/novnc/"
fi

cat > /etc/systemd/system/websockify.service << WSUNIT
[Unit]
Description=Websockify VNC Bridge
After=vncserver@1.service
Requires=vncserver@1.service

[Service]
Type=simple
ExecStart=/usr/local/bin/websockify $NOVNC_WEB 127.0.0.1:6080 127.0.0.1:5901
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
WSUNIT

systemctl daemon-reload
systemctl enable websockify.service
systemctl restart websockify.service || true
ok "Websockify: ws://127.0.0.1:6080 → VNC :5901, localhost only."

# ============================================================================
# 5. XFCE TOUCH OPTIMIZATION
# ============================================================================
info "Optimizing XFCE for touch / phone screen…"

XFCE_DIR="$VNC_HOME/.config/xfce4/xfconf/xfce-perchannel-xml"
sudo -u "$VNC_USER" mkdir -p "$XFCE_DIR"

# 5a. DPI / Scaling — 192 DPI (200% of 96)
cat > "$XFCE_DIR/xsettings.xml" << 'XSETTINGS'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Xft" type="empty">
    <property name="DPI" type="int" value="192"/>
    <property name="Antialias" type="int" value="1"/>
    <property name="HintStyle" type="string" value="hintslight"/>
    <property name="RGBA" type="string" value="rgb"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="CursorThemeSize" type="int" value="48"/>
    <property name="FontName" type="string" value="Noto Sans 12"/>
  </property>
</channel>
XSETTINGS

# 5b. Panel — 48px tall, bottom position
cat > "$XFCE_DIR/xfce4-panel.xml" << 'XPANEL'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-panel" version="1.0">
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=8;x=640;y=672"/>
      <property name="size" type="uint" value="48"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="length" type="uint" value="100"/>
      <property name="length-adjust" type="bool" value="false"/>
      <property name="icon-size" type="uint" value="32"/>
    </property>
  </property>
</channel>
XPANEL

# 5c. Thunar File Manager — Single-Click to open
THUNAR_DIR="$VNC_HOME/.config/Thunar"
sudo -u "$VNC_USER" mkdir -p "$THUNAR_DIR"
cat > "$THUNAR_DIR/thunarrc" << 'THUNAR'
[Configuration]
LastSingleClickOpenItems=TRUE
MiscSingleClickOpenItems=TRUE
MiscShowBarrelRollOnCopy=FALSE
MiscTextBesideIcons=FALSE
ShortcutsIconSize=THUNAR_ICON_SIZE_48
THUNAR

# 5d. Window Manager — larger title bar for touch
cat > "$XFCE_DIR/xfwm4.xml" << 'XFWM'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="title_font" type="string" value="Noto Sans Bold 12"/>
    <property name="button_offset" type="int" value="4"/>
    <property name="button_spacing" type="int" value="4"/>
    <property name="easy_click" type="string" value="None"/>
    <property name="theme" type="string" value="Default-xhdpi"/>
  </property>
</channel>
XFWM

chown -R "$VNC_USER":"$VNC_USER" "$VNC_HOME/.config"
ok "XFCE: DPI=192, panel=48px, Thunar single-click, large title bars."

# ============================================================================
# 6. ONBOARD VIRTUAL KEYBOARD
# ============================================================================
info "Installing onboard virtual keyboard…"
apt-get install -y -qq onboard > /dev/null 2>&1 || info "onboard not available, skipping."
ok "Onboard virtual keyboard installed."

# ============================================================================
# 7. AVAHI / mDNS — broadcast Pi for zero-conf discovery
# ============================================================================
info "Configuring Avahi for mDNS discovery…"
apt-get install -y -qq avahi-daemon avahi-utils > /dev/null

cat > /etc/avahi/services/glassterminal.service << 'AVAHI'
<?xml version="1.0" standalone='no'?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">GlassTerminal on %h</name>
  <service>
    <type>_ssh._tcp</type>
    <port>22</port>
    <txt-record>device=piterm</txt-record>
  </service>
  <service>
    <type>_glassterminal._tcp</type>
    <port>22</port>
    <txt-record>version=1.0</txt-record>
  </service>
</service-group>
AVAHI

systemctl enable avahi-daemon
systemctl restart avahi-daemon
ok "Avahi: broadcasting _glassterminal._tcp and _ssh._tcp via mDNS."

# ============================================================================
# 8. POWER SAVINGS — disable HDMI + Bluetooth
# ============================================================================
info "Disabling HDMI and Bluetooth for power saving…"

# Disable HDMI
if command -v tvservice &>/dev/null; then
    tvservice -o 2>/dev/null || true
fi

# Add to rc.local for persistence
RC_LOCAL="/etc/rc.local"
if [ ! -f "$RC_LOCAL" ]; then
    echo '#!/bin/sh -e' > "$RC_LOCAL"
    echo 'exit 0' >> "$RC_LOCAL"
    chmod +x "$RC_LOCAL"
fi
grep -q "tvservice" "$RC_LOCAL" || sed -i '/^exit 0/i tvservice -o 2>/dev/null || true' "$RC_LOCAL"

# Disable Bluetooth
if ! grep -q "dtoverlay=disable-bt" /boot/config.txt 2>/dev/null; then
    echo "dtoverlay=disable-bt" >> /boot/config.txt
fi
systemctl disable bluetooth 2>/dev/null || true
systemctl disable hciuart 2>/dev/null || true

ok "HDMI disabled, Bluetooth disabled. ~40mA saved."

# ============================================================================
# 9. WI-FI HOTSPOT AUTOCONNECT (NetworkManager)
# ============================================================================
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║   Wi-Fi Hotspot Auto-Connect Configuration      ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════╝${NC}"
echo ""

read -rp "Enter your Android hotspot SSID:     " HOTSPOT_SSID
read -rp "Enter your Android hotspot password:  " HOTSPOT_PASS

if [ ${#HOTSPOT_PASS} -lt 8 ]; then
    err "Password must be at least 8 characters for WPA2."
fi

# Delete existing connection with same name if any
nmcli connection delete "GlassTerminal-Hotspot" 2>/dev/null || true

nmcli connection add \
    type wifi \
    con-name "GlassTerminal-Hotspot" \
    ifname wlan0 \
    ssid "$HOTSPOT_SSID" \
    wifi-sec.key-mgmt wpa-psk \
    wifi-sec.psk "$HOTSPOT_PASS" \
    connection.autoconnect yes \
    connection.autoconnect-priority 100

nmcli connection up "GlassTerminal-Hotspot" 2>/dev/null || \
    info "Could not connect now (hotspot may be off). Will auto-connect on boot."

ok "Wi-Fi: will auto-connect to '$HOTSPOT_SSID' on boot."

# ============================================================================
# 10. CREATE authorized_keys FILE (for the app's public key)
# ============================================================================
AUTH_KEYS="$VNC_HOME/.ssh/authorized_keys"
sudo -u "$VNC_USER" mkdir -p "$VNC_HOME/.ssh"
chmod 700 "$VNC_HOME/.ssh"
touch "$AUTH_KEYS"
chmod 600 "$AUTH_KEYS"
chown -R "$VNC_USER":"$VNC_USER" "$VNC_HOME/.ssh"

# ============================================================================
# DONE
# ============================================================================
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║           Pi Glass Terminal Setup Complete!              ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║                                                          ║${NC}"
echo -e "${GREEN}║  Firewall:   SSH only from hotspot subnet                ║${NC}"
echo -e "${GREEN}║  VNC:        127.0.0.1:5901 (localhost only)             ║${NC}"
echo -e "${GREEN}║  Websockify: 127.0.0.1:6080 → VNC                       ║${NC}"
echo -e "${GREEN}║  mDNS:       Broadcasting _glassterminal._tcp            ║${NC}"
echo -e "${GREEN}║  XFCE:       Touch-optimized (DPI 192, 48px panel)       ║${NC}"
echo -e "${GREEN}║  Wi-Fi:      Auto-connects to '$HOTSPOT_SSID'           ║${NC}"
echo -e "${GREEN}║                                                          ║${NC}"
echo -e "${GREEN}║  NEXT STEP:                                              ║${NC}"
echo -e "${GREEN}║  Copy your app's public key into:                        ║${NC}"
echo -e "${GREEN}║    $AUTH_KEYS                                            ║${NC}"
echo -e "${GREEN}║                                                          ║${NC}"
echo -e "${GREEN}║  Then reboot:  sudo reboot                               ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════╝${NC}"
