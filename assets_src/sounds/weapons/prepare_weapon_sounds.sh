# Prepares the weapon sounds: mono .ogg, cut, level, shortened tail. Sources are listed in docs/CREDITS.md.
# Needs ffmpeg (winget install Gyan.FFmpeg).
# Folder with the unpacked sources: x/Prepared SFX Library and extra/*.wav (see docs/CREDITS.md).
B="${1:?folder with the sources}"
L="$B/x/Prepared SFX Library"; X="$B/extra"
O="E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/sounds/weapon"
mkdir -p "$O"

# cut <source> <start> <length> <fade out seconds> <peak dB> <result>: mono, level measured on the cut itself, then BOOST dB louder with the peaks held down, so that it is loud without clipping
cut_sound() {
  local src="$1" start="$2" len="$3" fade="$4" peak="$5" out="$6"
  local base="aresample=44100,pan=mono|c0=0.5*c0+0.5*c1"
  [ "$(ffprobe -v error -show_entries stream=channels -of csv=p=0 "$src")" = "1" ] && base="aresample=44100"
  local max
  max=$(ffmpeg -hide_banner -nostats -ss "$start" -t "$len" -i "$src" -af "$base,volumedetect" -f null - 2>&1 | grep -oE 'max_volume: [-0-9.]+' | cut -d' ' -f2)
  local gain
  gain=$(awk -v m="$max" -v p="$peak" 'BEGIN{printf "%.2f", p-m}')
  local fadeStart
  fadeStart=$(awk -v l="$len" -v f="$fade" 'BEGIN{printf "%.3f", l-f}')
  ffmpeg -y -hide_banner -loglevel error -ss "$start" -t "$len" -i "$src" \
    -af "$base,volume=${gain}dB,volume=${BOOST}dB,alimiter=limit=0.95:attack=2:release=60:level=disabled,afade=t=out:st=${fadeStart}:d=${fade}" -c:a libvorbis -q:a 5 "$O/$out.ogg"
  echo "$out: gain ${gain} dB, $(ffprobe -v error -show_entries stream=channels:format=duration -of csv=p=0 "$O/$out.ogg" | tr '\n' ' ')"
}

BOOST=24
# Shots: a little before the attack; the tails are short so that a burst does not turn into a drone.
S1=0.69; S2=5.64; S3=3.57; S4=6.75; S5=0.42; S6=3.46
cut_sound "$L/AR-15/D_32P.wav"  "$S1" 0.40 0.20 -1 assault_rifle_shot1
cut_sound "$L/AR-15/D_32P.wav"  "$S2" 0.40 0.20 -1 assault_rifle_shot2
cut_sound "$L/SKS/U_14P.wav"    "$S3" 0.80 0.40 -1 battle_rifle_shot1
cut_sound "$L/SKS/U_14P.wav"    "$S4" 0.80 0.40 -1 battle_rifle_shot2
cut_sound "$L/Nova/O_21P.wav"   "$S5" 1.00 0.50 -1 combat_shotgun_shot1
cut_sound "$L/Nova/O_21P.wav"   "$S6" 1.00 0.50 -1 combat_shotgun_shot2

# Reloads
BOOST=12
cut_sound "$X/assaultriflereload1_0.wav" 0 1.55 0.05 -2 assault_rifle_reload
cut_sound "$X/gunreload1.wav"            0 1.57 0.05 -2 battle_rifle_reload
cut_sound "$X/singlebullet1.wav"         0 0.25 0.05 -2 combat_shotgun_reload

BOOST=4
# Clicks of an empty magazine: three different clicks of the same recording.
cut_sound "$X/equipment_clicks3.wav" 0.18 0.28 0.05 -2 assault_rifle_empty
cut_sound "$X/equipment_clicks3.wav" 1.42 0.30 0.05 -2 battle_rifle_empty
cut_sound "$X/equipment_clicks3.wav" 0.85 0.22 0.05 -2 combat_shotgun_empty
du -sh "$O"
