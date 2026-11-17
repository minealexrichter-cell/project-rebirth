#!/usr/bin/env bash
# Synthesises the sounds of version 1.7.0 (garrison) with ffmpeg; nothing is downloaded.
# Usage: bash assets_src/sounds/garrison/make_garrison_sounds.sh [path to ffmpeg]
# Writes mono .ogg files into src/main/resources/assets/projectrebirth/sounds/garrison/.
set -e
FF="${1:-ffmpeg}"
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projectrebirth/sounds/garrison"
mkdir -p "$OUT"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 4"

# Welding, 1.4 s: an arc's frying crackle (white noise gated by fast random-like pulses) over a 120 Hz buzz.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.8:d=1.4:seed=71" \
  -f lavfi -i "aevalsrc='0.15*sin(2*PI*120*t)+0.07*sin(2*PI*240*t)':s=44100:d=1.4" \
  -filter_complex "[0]highpass=f=1800,volume='0.35*gt(sin(2*PI*37*t)*sin(2*PI*53*t)+0.3*sin(2*PI*91*t),0.05)':eval=frame[arc];[arc][1]amix=inputs=2:normalize=0,afade=t=in:d=0.04,afade=t=out:st=1.2:d=0.2" \
  $ENC "$OUT/welding.ogg"

# Manipulator, 1.0 s: a servo whine that rises and falls as the arm moves, and a clank at the end.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.22*sin(2*PI*(320+260*sin(PI*min(t,0.8)/0.8))*t)*lt(t,0.8)+0.3*sin(2*PI*180*t)*exp(-(t-0.82)*25)*gte(t,0.82)':s=44100:d=1.0" \
  -f lavfi -i "anoisesrc=color=pink:amplitude=0.6:d=1.0:seed=73" \
  -filter_complex "[1]bandpass=f=2500:width_type=h:w=1500,volume='0.25*exp(-(t-0.82)*30)*gte(t,0.82)':eval=frame[clank];[0][clank]amix=inputs=2:normalize=0,afade=t=in:d=0.03" \
  $ENC "$OUT/manipulator.ogg"

# Electronics, 0.9 s: quick data chirps, like boards being tested.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.22*sin(2*PI*(1400+600*floor(mod(t*12,4)))*t)*lt(mod(t,0.083),0.05)*lt(t,0.75)':s=44100:d=0.9" \
  $ENC "$OUT/electronics.ogg"

# Calibration, 1.0 s: three rising beeps and a long confirming tone.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*sin(2*PI*if(lt(t,0.15),700,if(lt(t,0.3),900,if(lt(t,0.45),1100,1320)))*t)*if(lt(t,0.45),lt(mod(t,0.15),0.09),lt(t,0.95))*min(1,(1-t)*10)':s=44100:d=1.0" \
  $ENC "$OUT/calibration.ogg"

# Core boot, 1.6 s: a deep power-up sweep, a hum settling and two soft chimes (the AI core comes to life).
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.3*sin(2*PI*(60+240*min(t,0.8)/0.8)*t)*min(1,t*4)*exp(-max(0,t-0.9)*3)+0.18*sin(2*PI*880*t)*exp(-(t-0.9)*6)*gte(t,0.9)+0.15*sin(2*PI*1320*t)*exp(-(t-1.15)*6)*gte(t,1.15)':s=44100:d=1.6" \
  $ENC "$OUT/core_boot.ogg"

# Assembly done, 0.9 s: a pneumatic release hiss and a bright two-tone chime.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.6:d=0.9:seed=79" \
  -f lavfi -i "aevalsrc='0.22*sin(2*PI*988*t)*gte(t,0.3)*exp(-(t-0.3)*5)+0.2*sin(2*PI*1319*t)*gte(t,0.45)*exp(-(t-0.45)*5)':s=44100:d=0.9" \
  -filter_complex "[0]highpass=f=3000,volume='0.3*exp(-t*8)':eval=frame[hiss];[hiss][1]amix=inputs=2:normalize=0" \
  $ENC "$OUT/assembly_done.ogg"

# Machine beep, 0.35 s: the short two-note chirp of a machine answering an order (no speech).
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*sin(2*PI*if(lt(t,0.12),1500,2000)*t)*lt(mod(t,0.17),0.11)*lt(t,0.3)':s=44100:d=0.35" \
  $ENC "$OUT/machine_beep.ogg"

# Target lock, 0.5 s: a fast rising chirp and a hard double tick.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*sin(2*PI*(900+2400*t)*t)*lt(t,0.3)+0.35*sin(2*PI*2600*t)*(exp(-(t-0.33)*120)*gte(t,0.33)+exp(-(t-0.41)*120)*gte(t,0.41))':s=44100:d=0.5" \
  $ENC "$OUT/target_lock.ogg"

# Machine shutdown, 1.2 s: a falling power-down sweep with a last electric sputter.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.3*sin(2*PI*(500-420*min(t,1)/1)*t)*exp(-t*1.5)+0.12*sin(2*PI*100*t)*gt(sin(2*PI*13*t),0.3)*gte(t,0.8)*exp(-(t-0.8)*4)':s=44100:d=1.2" \
  $ENC "$OUT/machine_shutdown.ogg"

# Console alarm, 1.2 s: a two-tone klaxon, three cycles.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.24*(sin(2*PI*if(lt(mod(t,0.4),0.2),660,880)*t)+0.25*sin(2*PI*3*if(lt(mod(t,0.4),0.2),660,880)*t))*min(1,min(mod(t,0.2)/0.01,(0.2-mod(t,0.2))/0.01))':s=44100:d=1.2" \
  $ENC "$OUT/console_alarm.ogg"
ls -la "$OUT"
