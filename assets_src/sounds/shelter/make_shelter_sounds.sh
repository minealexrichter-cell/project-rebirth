#!/usr/bin/env bash
# Synthesises the sounds of the shelter blocks (version 1.4.0) with ffmpeg; nothing is downloaded.
# Usage: bash assets_src/sounds/shelter/make_shelter_sounds.sh [path to ffmpeg]
# Writes mono .ogg files into src/main/resources/assets/projectrebirth/sounds/shelter/.
set -e
FF="${1:-ffmpeg}"
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projectrebirth/sounds/shelter"
mkdir -p "$OUT"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 4"

# Air filtration unit, a 4 s loop: a low motor hum and the whoosh of the fan blades.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc=0.20*sin(2*PI*90*t)+0.10*sin(2*PI*180*t)+0.05*sin(2*PI*270*t):s=44100:d=4" \
  -f lavfi -i "anoisesrc=color=pink:amplitude=0.5:d=4:seed=7" \
  -filter_complex "[1]bandpass=f=700:width_type=h:w=500,volume='0.35+0.15*sin(2*PI*8*t)':eval=frame[fan];[0][fan]amix=inputs=2:normalize=0,volume=0.9" \
  $ENC "$OUT/filtration_hum.ogg"

# Airlock door, 1.3 s: a thud of the bolts, then the seal lets the air go.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc=0.6*sin(2*PI*70*t)*exp(-t*18):s=44100:d=1.3" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.6:d=1.3:seed=11" \
  -filter_complex "[1]highpass=f=2500,lowpass=f=9000,volume='if(lt(t,0.06),t/0.06,exp(-(t-0.06)*2.6))':eval=frame[air];[0][air]amix=inputs=2:normalize=0" \
  $ENC "$OUT/airlock_hiss.ogg"

# Decontamination shower, a 3 s loop: spray on tiles and a low rush of water in the pipes.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.5:d=3:seed=23" \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.6:d=3:seed=29" \
  -filter_complex "[0]bandpass=f=4000:width_type=h:w=5000,volume='0.45+0.08*sin(2*PI*5*t)':eval=frame[spray];[1]lowpass=f=300,volume=0.5[rush];[spray][rush]amix=inputs=2:normalize=0" \
  $ENC "$OUT/decontamination_shower.ogg"

# Leak alarm, a 2 s loop: two tones, a quarter of a second each.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.32*(sin(2*PI*if(lt(mod(t,0.5),0.25),880,660)*t)+0.35*sin(2*PI*3*if(lt(mod(t,0.5),0.25),880,660)*t))*min(1,min(mod(t,0.25)/0.01,(0.25-mod(t,0.25))/0.01))':s=44100:d=2" \
  $ENC "$OUT/leak_alarm.ogg"
ls -la "$OUT"
