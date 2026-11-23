# Synthesizes the sounds of the machine mind of 1.9.0, block F: nothing is downloaded. Needs ffmpeg.
# Mono, 44.1 kHz, .ogg, into assets/projectrebirth/sounds/mind/.
O="E:/Projects/Project-Rebirth-mod/src/main/resources/assets/projectrebirth/sounds/mind"
mkdir -p "$O"
ENC="-ac 1 -ar 44100 -c:a libvorbis -q:a 5"
LIM="alimiter=limit=0.9:level=disabled"
FF="ffmpeg -y -hide_banner -loglevel error"

# The hum of the computer: two low detuned voices with a slow pulse and a faint data chirp.
$FF -f lavfi -i "aevalsrc='0.5*sin(2*PI*55*t)+0.4*sin(2*PI*55.7*t)+0.15*sin(2*PI*110*t)*(0.5+0.5*sin(2*PI*0.5*t))+0.04*sin(2*PI*(1800+600*floor(mod(t*9,4)))*t)*(lt(mod(t*9,1),0.3))':d=5:s=44100" \
  -filter_complex "[0]afade=t=in:d=0.4,afade=t=out:st=4.5:d=0.5,volume=-1dB,$LIM" $ENC "$O/hum.ogg"

# The factory: a press and a ratchet over a motor.
$FF -f lavfi -i "aevalsrc='0.3*sin(2*PI*80*t)*(0.7+0.3*sin(2*PI*6*t))+0.8*sin(2*PI*52*t)*exp(-9*mod(t,0.8))':d=2.4:s=44100" -f lavfi -i "anoisesrc=d=2.4:c=white:a=0.5:seed=31" \
  -filter_complex "[1]bandpass=f=2600:width_type=h:w=1800,tremolo=f=7.5:d=1,volume=0.35[n];[0][n]amix=inputs=2:normalize=0,afade=t=in:d=0.1,afade=t=out:st=2.1:d=0.3,$LIM" $ENC "$O/factory.ogg"

# Hurt: a metallic crack with an electric fizz.
$FF -f lavfi -i "aevalsrc='(0.6*sin(2*PI*730*t)+0.5*sin(2*PI*1170*t)+0.4*sin(2*PI*1910*t))*exp(-9*t)':d=0.6:s=44100" -f lavfi -i "anoisesrc=d=0.6:c=white:a=0.8:seed=32" \
  -filter_complex "[1]highpass=f=3000,tremolo=f=45:d=0.9,afade=t=out:st=0.1:d=0.4,volume=0.5[n];[0][n]amix=inputs=2:normalize=0,volume=1dB,$LIM" $ENC "$O/hurt.ogg"

# A new stage: a rising chord of square voices over a deep swell.
$FF -f lavfi -i "aevalsrc='0.35*sgn(sin(2*PI*(60+25*t)*t))*min(1,t)+0.3*sin(2*PI*(120+50*t)*t)+0.25*sin(2*PI*(181+75*t)*t)':d=3.2:s=44100" \
  -filter_complex "[0]lowpass=f=1400,aecho=0.7:0.6:180|340:0.35|0.2,afade=t=in:d=0.3,afade=t=out:st=2.6:d=0.8,volume=2dB,$LIM" $ENC "$O/stage.ogg"

# The death of a node: voices sinking down, breaking up.
$FF -f lavfi -i "aevalsrc='0.45*sgn(sin(2*PI*(240*t-42*t*t)))+0.35*sin(2*PI*(363*t-63*t*t))':d=2.8:s=44100" -f lavfi -i "anoisesrc=d=2.8:c=brown:a=0.9:seed=33" \
  -filter_complex "[0]lowpass=f=1800,tremolo=f=13:d=0.7[v];[1]lowpass=f=500,volume=0.7[n];[v][n]amix=inputs=2:normalize=0,acrusher=bits=6:mix=0.4,afade=t=out:st=1.8:d=1.0,volume=2dB,$LIM" $ENC "$O/death.ogg"

# The drone: a thin whir of four rotors.
$FF -f lavfi -i "aevalsrc='0.3*sin(2*PI*210*t)+0.25*sin(2*PI*217*t)+0.2*sin(2*PI*426*t)':d=1.6:s=44100" -f lavfi -i "anoisesrc=d=1.6:c=pink:a=0.5:seed=34" \
  -filter_complex "[0]vibrato=f=7:d=0.25,tremolo=f=32:d=0.5[v];[1]bandpass=f=1400:width_type=h:w=900,tremolo=f=32:d=0.8,volume=0.4[n];[v][n]amix=inputs=2:normalize=0,afade=t=in:d=0.2,afade=t=out:st=1.3:d=0.3,$LIM" $ENC "$O/drone.ogg"

# The drone at work: a welding arc.
$FF -f lavfi -i "anoisesrc=d=0.9:c=white:a=0.9:seed=35" -f lavfi -i "aevalsrc='0.3*sin(2*PI*100*t)*sgn(sin(2*PI*50*t))':d=0.9:s=44100" \
  -filter_complex "[0]bandpass=f=3800:width_type=h:w=3200,tremolo=f=50:d=0.9,tremolo=f=9:d=0.5[n];[n][1]amix=inputs=2:normalize=0,afade=t=in:d=0.03,afade=t=out:st=0.7:d=0.2,$LIM" $ENC "$O/drone_work.ogg"
