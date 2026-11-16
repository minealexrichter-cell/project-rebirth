# Synthesizes the sounds of 1.6.0, block G: nothing is downloaded. Needs ffmpeg (winget install Gyan.FFmpeg).
# Mono, 44.1 kHz, .ogg, into assets/projectrebirth/sounds/scouting/.
O="E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/sounds/scouting"
mkdir -p "$O"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 5"

# The hum of a radiation source: a low electric drone (50 Hz and its harmonics) with a faint crackle, fading in and out,
# heard now and then near radioactive debris and leaking barrels.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "sine=f=50:d=2.5" -f lavfi -i "sine=f=100:d=2.5" -f lavfi -i "sine=f=150:d=2.5" \
  -f lavfi -i "anoisesrc=d=2.5:c=pink:a=0.6:seed=11" \
  -filter_complex "[0]volume=0.7[a];[1]volume=0.45[b];[2]volume=0.25[c];[3]highpass=f=2500,lowpass=f=7000,volume=0.12[n];[a][b][c][n]amix=inputs=4:normalize=0,tremolo=f=6:d=0.25,afade=t=in:d=0.5,afade=t=out:st=1.8:d=0.7,volume=4dB,alimiter=limit=0.9:level=disabled" \
  $ENC "$O/source_hum.ogg"

# The beep of the field analyzer: two short high blips, as a hand-held instrument does when it reads contamination.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "sine=f=1760:d=0.07" -f lavfi -i "anullsrc=r=44100:cl=mono:d=0.05" -f lavfi -i "sine=f=2350:d=0.07" \
  -filter_complex "[0]afade=t=out:st=0.05:d=0.02[a];[2]afade=t=out:st=0.05:d=0.02[c];[a][1][c]concat=n=3:v=0:a=1,volume=-4dB" \
  $ENC "$O/analyzer_beep.ogg"

# The monitoring station printing a map: a dot-matrix chatter (noise bursts eight times a second) over a feed motor's whir.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "anoisesrc=d=1.2:c=white:a=0.8:seed=5" -f lavfi -i "sine=f=180:d=1.2" \
  -filter_complex "[0]bandpass=f=3200:width_type=h:w=2500,tremolo=f=16:d=0.95[n];[1]volume=0.25,tremolo=f=8:d=0.4[m];[n][m]amix=inputs=2:normalize=0,afade=t=in:d=0.05,afade=t=out:st=1.0:d=0.2,volume=2dB,alimiter=limit=0.9:level=disabled" \
  $ENC "$O/map_print.ogg"

for f in "$O"/*.ogg; do echo "$(basename "$f"): $(ffprobe -v error -show_entries stream=channels:format=duration -of csv=p=0 "$f" | tr '\n' ' ')"; done
