#!/usr/bin/env bash
# Synthesises the sounds of version 1.5.0 (technical limits) with ffmpeg; nothing is downloaded.
# Usage: bash assets_src/sounds/power/make_power_sounds.sh [path to ffmpeg]
# Writes mono .ogg files into src/main/resources/assets/projectrebirth/sounds/power/.
set -e
FF="${1:-ffmpeg}"
OUT="$(cd "$(dirname "$0")/../../.." && pwd)/src/main/resources/assets/projectrebirth/sounds/power"
mkdir -p "$OUT"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 4"

# Dust storm wind, an 8 s loop: a low roar of brown noise and a hissing of sand, both rising and falling in gusts.
# The gusts are whole periods of the 8 s loop, so it loops without a seam.
"$FF" -y -v error \
  -f lavfi -i "anoisesrc=color=brown:amplitude=0.7:d=8:seed=41" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.5:d=8:seed=43" \
  -filter_complex "[0]lowpass=f=500,volume='0.55+0.3*sin(2*PI*t/8)+0.15*sin(2*PI*3*t/8)':eval=frame[roar];[1]bandpass=f=3500:width_type=h:w=3000,volume='0.12+0.1*sin(2*PI*2*t/8+1)':eval=frame[sand];[roar][sand]amix=inputs=2:normalize=0,afade=t=in:d=0.05,afade=t=out:st=7.95:d=0.05" \
  $ENC "$OUT/dust_wind.ogg"

# EMI crackle, 1.2 s: a 50 Hz electric buzz broken into bursts, and sharp clicks of static.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.25*(sin(2*PI*50*t)+0.6*sin(2*PI*150*t)+0.4*sin(2*PI*250*t))*gt(sin(2*PI*7*t)+sin(2*PI*11*t),0.4)*exp(-t*1.5)':s=44100:d=1.2" \
  -f lavfi -i "anoisesrc=color=white:amplitude=0.9:d=1.2:seed=47" \
  -filter_complex "[1]highpass=f=1500,volume='gt(sin(2*PI*31*t)*sin(2*PI*17*t),0.55)*0.35':eval=frame[clicks];[0][clicks]amix=inputs=2:normalize=0,lowpass=f=7000,volume=0.9,afade=t=out:st=1.0:d=0.2" \
  $ENC "$OUT/emi_crackle.ogg"

# Electronics fault, 0.7 s: a falling glitchy tone chopped by a fast gate, ending in a short buzz.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.35*sin(2*PI*(900-700*t)*t)*gt(sin(2*PI*28*t),0)*exp(-t*2)+0.2*sin(2*PI*100*t)*gte(t,0.45)*exp(-(t-0.45)*8)':s=44100:d=0.7" \
  $ENC "$OUT/emi_fault.ogg"

# Low charge, 0.6 s: two short falling beeps, like a device whose battery runs out.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.3*sin(2*PI*if(lt(t,0.3),1100,780)*t)*min(1,min(mod(t,0.3)/0.01,(0.18-mod(t,0.3))/0.01))*lt(mod(t,0.3),0.18)':s=44100:d=0.6" \
  $ENC "$OUT/low_charge.ogg"

# Overheat alarm, 1.2 s: three quick high pulses over a rising tone.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.28*(sin(2*PI*(1300+300*t)*t)+0.3*sin(2*PI*3*(1300+300*t)*t))*lt(mod(t,0.4),0.22)*min(1,min(mod(t,0.4)/0.01,(0.22-mod(t,0.4))/0.01))':s=44100:d=1.2" \
  $ENC "$OUT/overheat_alarm.ogg"

# Charging hum, a 3 s loop: a 100 Hz transformer hum with a soft rising whine that pulses once a second.
"$FF" -y -v error \
  -f lavfi -i "aevalsrc='0.18*sin(2*PI*100*t)+0.09*sin(2*PI*200*t)+0.04*sin(2*PI*300*t)+0.05*sin(2*PI*1800*t)*(0.5+0.5*sin(2*PI*t))':s=44100:d=3" \
  $ENC "$OUT/charging_hum.ogg"
ls -la "$OUT"
