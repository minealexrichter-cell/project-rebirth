# Prepares the sounds of the environment (1.3.0): mono .ogg, cut, level. Sources are listed in docs/CREDITS.md.
# Needs ffmpeg (winget install Gyan.FFmpeg).
# Folder with the downloaded sources: siren_0.mp3 and steam/*.wav (unpacked steam_hisses.zip).
B="${1:?folder with the sources}"
O="E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/sounds/environment"
mkdir -p "$O"
MONO="aresample=44100,pan=mono|c0=0.5*c0+0.5*c1"

# level <in filter chain> <peak dB>: the gain that brings the peak of a cut to the given level
gain_for() {
  local max
  max=$(ffmpeg -hide_banner -nostats "${@:1:$#-1}" -af "$MONO,volumedetect" -f null - 2>&1 | grep -oE 'max_volume: [-0-9.]+' | cut -d' ' -f2)
  awk -v m="$max" -v p="${@: -1}" 'BEGIN{printf "%.2f", p-m}'
}

# The warning siren: the first wail of the recording.
g=$(gain_for -ss 0 -t 8 -i "$B/siren_0.mp3" -2)
ffmpeg -y -hide_banner -loglevel error -ss 0 -t 8 -i "$B/siren_0.mp3" \
  -af "$MONO,volume=${g}dB,afade=t=in:d=0.3,afade=t=out:st=6.8:d=1.2" -c:a libvorbis -q:a 5 "$O/siren.ogg"

# The hiss of acid rain: five steam hisses, played now and then around the player.
for i in 1 2 3 4 5; do
  src="$B/steam/steam hisses - Marker #$i.wav"
  g=$(gain_for -i "$src" -3)
  ffmpeg -y -hide_banner -loglevel error -i "$src" -af "$MONO,volume=${g}dB,afade=t=in:d=0.15,afade=t=out:st=1.35:d=0.5" -c:a libvorbis -q:a 5 "$O/acid_hiss$i.ogg"
done

# The clicks of the dosimeter are made here from noise, nothing is downloaded: a few milliseconds of white noise
# with a sharp attack and a fast decay, three slightly different ones.
n=1
for f in 1800 2600 3400; do
  ffmpeg -y -hide_banner -loglevel error -f lavfi -i "anoisesrc=d=0.03:c=white:a=1:seed=$((n * 7))" \
    -af "highpass=f=$f,lowpass=f=9000,afade=t=out:st=0.002:d=0.02:curve=exp,volume=6dB,alimiter=limit=0.95:level=disabled" -ac 1 -ar 44100 -c:a libvorbis -q:a 5 "$O/geiger_click$n.ogg"
  n=$((n + 1))
done

for f in "$O"/*.ogg; do echo "$(basename "$f"): $(ffprobe -v error -show_entries stream=channels:format=duration -of csv=p=0 "$f" | tr '\n' ' ')"; done
