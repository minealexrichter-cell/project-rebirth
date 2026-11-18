#!/usr/bin/env bash
# Synthesises the interface sounds of version 1.8.0 with ffmpeg; nothing is downloaded.
# Usage: bash assets_src/sounds/ui/make_ui_sounds.sh [path to ffmpeg]
# Writes short mono .ogg files into src/main/resources/assets/projectrebirth/sounds/ui/. All are short and mechanical,
# in the spirit of the style "Industrial terminal": relays, switches, a terminal's beeps.
set -e
FF="${1:-ffmpeg}"
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projectrebirth/sounds/ui"
mkdir -p "$OUT"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 4"

# Click of a button, 0.06 s: a dry relay tick (a burst of filtered noise) with a short low knock.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.9:d=0.06:seed=3" \
  -f lavfi -i "aevalsrc=0.5*sin(2*PI*180*t)*exp(-t*90):s=44100:d=0.06" \
  -filter_complex "[0]bandpass=f=3200:width_type=h:w=2500,volume='exp(-t*120)':eval=frame[tick];[tick][1]amix=inputs=2:normalize=0,volume=0.8" \
  $ENC "$OUT/click.ogg"

# Hover, 0.03 s: a very quiet high tick.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.5:d=0.03:seed=5" \
  -af "highpass=f=5000,volume='0.35*exp(-t*200)':eval=frame" \
  $ENC "$OUT/hover.ogg"

# Switch, 0.12 s: two ticks of a toggle (the lever, then its stop).
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.9:d=0.12:seed=9" \
  -af "bandpass=f=2400:width_type=h:w=1800,volume='if(lt(t,0.05),exp(-t*140),0.7*exp(-(t-0.05)*140))':eval=frame" \
  $ENC "$OUT/toggle.ogg"

# Error, 0.28 s: two low buzzes of a terminal (square-ish wave).
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.22*(sin(2*PI*196*t)+0.4*sin(2*PI*588*t)+0.2*sin(2*PI*980*t))*if(lt(mod(t,0.14),0.11),1,0)*min(1,mod(t,0.14)/0.005)':s=44100:d=0.28" \
  $ENC "$OUT/error.ogg"

# Start of work, 0.35 s: a rising two-tone beep.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*sin(2*PI*if(lt(t,0.15),660,990)*t)*min(1,min(mod(t,0.175)/0.006,(0.175-mod(t,0.175))/0.02))':s=44100:d=0.35" \
  $ENC "$OUT/work_start.ogg"

# End of work, 0.45 s: a falling two-tone beep and a soft relay click.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*sin(2*PI*if(lt(t,0.15),990,660)*t)*min(1,min(mod(t,0.175)/0.006,(0.175-mod(t,0.175))/0.02))*if(lt(t,0.35),1,0)':s=44100:d=0.45" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.6:d=0.45:seed=13" \
  -filter_complex "[1]bandpass=f=2800:width_type=h:w=2000,volume='if(gt(t,0.37),exp(-(t-0.37)*120),0)':eval=frame[tick];[0][tick]amix=inputs=2:normalize=0" \
  $ENC "$OUT/work_end.ogg"

# Alarm, 0.8 s: three quick high pulses of a terminal alarm.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.3*(sin(2*PI*1320*t)+0.3*sin(2*PI*2640*t))*if(lt(mod(t,0.2),0.12),1,0)*if(lt(t,0.6),1,0)*min(1,mod(t,0.2)/0.004)':s=44100:d=0.8" \
  $ENC "$OUT/alarm.ogg"
ls -la "$OUT"
