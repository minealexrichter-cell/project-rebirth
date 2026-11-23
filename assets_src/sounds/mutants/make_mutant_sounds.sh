# Synthesizes the sounds of the new mutants of 1.9.0, block D: nothing is downloaded. Needs ffmpeg (winget install Gyan.FFmpeg).
# Mono, 44.1 kHz, .ogg, into assets/projectrebirth/sounds/mutant/.
O="E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/sounds/mutant"
mkdir -p "$O"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 5"
LIM="alimiter=limit=0.9:level=disabled"

# The crawler: a dry chitinous chitter, fast clicks of mandibles over a faint scraping.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "anoisesrc=d=0.9:c=white:a=0.9:seed=21" -f lavfi -i "anoisesrc=d=0.9:c=brown:a=0.5:seed=22" \
  -filter_complex "[0]bandpass=f=4200:width_type=h:w=3000,tremolo=f=28:d=1.0,tremolo=f=5:d=0.6[c];[1]bandpass=f=600:width_type=h:w=500,volume=0.4[s];[c][s]amix=inputs=2:normalize=0,afade=t=in:d=0.03,afade=t=out:st=0.7:d=0.2,volume=3dB,$LIM" \
  $ENC "$O/crawler_chitter.ogg"

# The screamer's scream: a rising, tearing shriek of two detuned voices with a rasp, long, then breaking off.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*(520*t+260*t*t))+0.35*sin(2*PI*(533*t+270*t*t))':d=1.8:s=44100" \
  -f lavfi -i "anoisesrc=d=1.8:c=pink:a=0.7:seed=23" \
  -filter_complex "[0]vibrato=f=11:d=0.6,highpass=f=300[v];[1]bandpass=f=2400:width_type=h:w=1800,volume=0.35[n];[v][n]amix=inputs=2:normalize=0,acrusher=bits=8:mix=0.25,afade=t=in:d=0.15,afade=t=out:st=1.4:d=0.4,volume=4dB,$LIM" \
  $ENC "$O/screamer_scream.ogg"

# The screamer's moan: a low wet groan wavering in pitch.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "aevalsrc='0.6*sin(2*PI*(140*t-12*t*t))+0.25*sin(2*PI*(281*t-24*t*t))':d=1.4:s=44100" \
  -f lavfi -i "anoisesrc=d=1.4:c=brown:a=0.5:seed=24" \
  -filter_complex "[0]vibrato=f=6:d=0.4[v];[1]lowpass=f=900,volume=0.3[n];[v][n]amix=inputs=2:normalize=0,afade=t=in:d=0.2,afade=t=out:st=1.0:d=0.4,volume=2dB,$LIM" \
  $ENC "$O/screamer_moan.ogg"

# The rat: three short high squeaks.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "aevalsrc='0.6*sin(2*PI*(2600*t+3000*t*t))*(lt(mod(t,0.11),0.06))':d=0.33:s=44100" \
  -filter_complex "[0]vibrato=f=40:d=0.3,afade=t=out:st=0.25:d=0.08,volume=-2dB,$LIM" \
  $ENC "$O/rat_squeak.ogg"

# The hunter: a low rattling growl, breathy, close.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "aevalsrc='0.7*sin(2*PI*70*t)*(0.6+0.4*sin(2*PI*23*t))':d=1.2:s=44100" \
  -f lavfi -i "anoisesrc=d=1.2:c=pink:a=0.6:seed=25" \
  -filter_complex "[1]bandpass=f=350:width_type=h:w=300,tremolo=f=23:d=0.8,volume=0.6[n];[0][n]amix=inputs=2:normalize=0,afade=t=in:d=0.1,afade=t=out:st=0.9:d=0.3,volume=5dB,$LIM" \
  $ENC "$O/hunter_growl.ogg"

# The drowner: a gurgle of water in a throat, bubbles bursting.
ffmpeg -y -hide_banner -loglevel error \
  -f lavfi -i "aevalsrc='0.5*sin(2*PI*(180+120*sin(2*PI*9*t))*t)':d=1.1:s=44100" \
  -f lavfi -i "anoisesrc=d=1.1:c=brown:a=0.6:seed=26" \
  -filter_complex "[0]tremolo=f=13:d=0.9[g];[1]lowpass=f=700,tremolo=f=7:d=0.7,volume=0.5[n];[g][n]amix=inputs=2:normalize=0,afade=t=in:d=0.1,afade=t=out:st=0.8:d=0.3,volume=3dB,$LIM" \
  $ENC "$O/drowner_gurgle.ogg"

for f in "$O"/*.ogg; do echo "$(basename "$f"): $(ffprobe -v error -show_entries stream=channels:format=duration -of csv=p=0 "$f" | tr '\n' ' ')"; done
