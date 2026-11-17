# Copy this file to secrets.py on the Pico and fill in your own values.
# secrets.py is in .gitignore and must never be committed.
#
# The Pico tries the networks in this order and uses the first one that connects.
# Only 2.4 GHz networks work. For a phone hotspot, set the hotspot band to 2.4 GHz.

NETWORKS = [
    ("home-wifi-name", "home-wifi-password"),
    ("my-phone-hotspot-name", "my-phone-hotspot-password"),
]

# Last resort: if no network above connects, the Pico starts its own network
# "PicoMeteo" at 192.168.4.1 with this password (8 characters or more).
AP_PASSWORD = "choose-a-password"
