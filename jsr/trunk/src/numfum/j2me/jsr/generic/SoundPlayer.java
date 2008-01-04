package numfum.j2me.jsr.generic;

import java.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;

import numfum.j2me.jsr.Constants;

/**
 *	Plays midi files (and optionally controls midi channels used for effects).
 */
public final class SoundPlayer implements Constants {
	/**
	 *	Number of MIDI channels devoted to effects.
	 */
	private static final int MAX_EFFECTS = 8;
	
	/**
	 *	Singleton instance of this player.
	 */
	private static SoundPlayer instance = null;
	
	/**
	 *	Stream associated with the midi file. Its instance is held so it can be
	 *	closed after playing (most MMAPI implementations appear not to do this,
	 *	eventually failing or crashing as a result).
	 */
	private InputStream stream = null;
	
	/**
	 *	MMAPI <code>Player</code instance.
	 */
	private javax.microedition.media.Player player = null;
	
	/**
	 *	MMAPI <code>Conrol</code> giving access to the midi synth.
	 */
	private MIDIControl synth = null;
	
	/**
	 *	The current sound should loop.
	 */
	private boolean loop;
	
	/**
	 *	Whether the player should periodically ensure the sound is still
	 *	playing. Note: only applicable to looped sounds.
	 */
	private boolean ensurePlaying = false;
	
	/**
	 *	Number of ticks to enable each effect channel for.
	 */
	private final int[] effectTime = new int[MAX_EFFECTS];
	
	/**
	 *	How many ticks to maintain the tempo change effect.
	 */
	private int tempoTimer = 0;
	
	/**
	 *	Whether music is enabled.
	 */
	private boolean musicEnabled = true;
	
	/**
	 *	Whether sound effects are enabled.
	 */
	private boolean sndfxEnabled = true;
	
	/**
	 *	Filename of the previous file loaded.
	 */
	private String lastFile = null;
	
	/*
	 *	Only a singleton instance is available.
	 */
	private SoundPlayer() {}
	
	/**
	 *	Returns the singleton sound player instance.
	 */
	public static SoundPlayer getSoundPlayer() {
		if (instance == null) {
			instance = new SoundPlayer();
		}
		return instance;
	}
	
	/**
	 *	Initialises the midi synth without loading a midi file first.
	 *
	 *	@return whether initialisation was successful
	 */
	public boolean initMidiDevice() {
		try {
			player = Manager.createPlayer(Manager.MIDI_DEVICE_LOCATOR);
			player.prefetch();
			
			synth = (MIDIControl) player.getControl("MIDIControl");
			if (synth != null) {
				return true;
			}
		} catch (Throwable e) {
			System.err.println("Unable to access MIDI device: " + e);
		}
		return false;
	}
	
	/**
	 *	Enables the sound. Calls to any other methods always check whether
	 *	the sound is enabled before performing any actions.
	 */
	public void setEnabled(boolean musicEnabled, boolean sndfxEnabled) {
		this.musicEnabled = musicEnabled;
		this.sndfxEnabled = sndfxEnabled;
		if (!(musicEnabled || sndfxEnabled)) {
			stop(true);
		}
	}
	
	/**
	 *	Loads a midi file and prepares it to play (note: the file isn't loaded
	 *	if the music is disabled).
	 *
	 *	@return whether loading the file was successful
	 */
	public synchronized boolean load(String filename) {
		boolean success = false;
		if (player != null || stream != null) {
			stop(true);
		}
		if (musicEnabled) {
			lastFile = filename;
			try {
				stream = getClass().getResourceAsStream(filename);
				player = Manager.createPlayer(stream, "audio/midi");
				player.prefetch();
				
				success = true;
			} catch (Exception e) {
				stop(true);
				/*
				 *	Helps Motorola phones recover after failing to load,
				 *	in which case the sound won't play this call but will
				 *	the next time.
				 */
				System.gc();
			}
			if (ENABLE_SNDFX && player != null) {
				/*
				 *	It's possible that pure MIDP2.0 implementations without
				 *	MMAPI support will have problems with this. SE UIQ phones
				 *	certainly do.
				 */
				try {
					synth = (MIDIControl) player.getControl("MIDIControl");
				} catch (Throwable e) {}
			}
		}
		return success;
	}
	
	/**
	 *	Loads and plays a file, trying to work around as many MMAPI bugs as
	 *	possible (across various implementations).
	 *
	 *	@return whether playing the sound was successful
	 */
	public synchronized boolean play(String filename, boolean loop) {
		if (player != null && loop && filename.equals(lastFile)) {
			return resume();
		}
		if (!load(filename)) {
			/*
			 *	Attempts twice to load the file. Motorola implementations are
			 *	prone to failing after so many load/play cycles, but a retry
			 *	will often succeed.
			 */
			if (!load(filename)) {
				return false;
			}
		}
		return play(true, loop);
	}
	
	/**
	 *	Plays the currently loaded file.
	 */
	public synchronized boolean play(boolean rewind, boolean loop) {
		this.loop = loop;
		if (musicEnabled && player != null) {
			ensurePlaying = loop;
			if (rewind) {
				try {
					if (player.getState() == javax.microedition.media.Player.STARTED) {
						player.stop();
					}
				} catch (Exception e) {}
				try {
					/*
					 *	Some phones don't support setMediaTime() with midi.
					 */
					player.setMediaTime(0);
				} catch (Exception e) {}
			}
			try {
				if (player.getState() != javax.microedition.media.Player.STARTED) {
					if (loop) {
						player.setLoopCount(-1);
					}
					player.start();
				}
				return true;
			} catch (Exception e) {
				stop(true);
			}
		}
		return false;
	}
	
	/**
	 *	Resumes the playing sound. Only looped sounds are resumed, as it's
	 *	assumed any other sound is a one-shot. A new <code>Player</code> is
	 *	created if the existing one has been closed.
	 *
	 *	@return whether resuming the sound was successful
	 */
	public synchronized boolean resume() {
		if (musicEnabled && loop && lastFile != null) {
			ensurePlaying = true;
			try {
				if (player != null && player.getState() != javax.microedition.media.Player.CLOSED) {
					player.start();
					
					return true;
				} else {
					return play(lastFile, true);
				}
			} catch (Exception e) {
				stop(true);
				
				return false;
			}
		} else {
			return true;
		}
	}
	
	/**
	 *	Stops the current playing sound.
	 *
	 *	@param close whether to return all system resources after stopping
	 */
	public synchronized void stop(boolean close) {
		if (stream != null) {
			try {
				stream.close();
			} catch (Exception e) {}
			stream = null;
		}
		ensurePlaying = false;
		if (player != null) {
			try {
				player.stop();
			} catch (Exception e) {}
			if (close) {
				try {
					player.close();
				} catch (Exception e) {}
				
				player = null;
			}
		}
	}
	
	/**
	 *	Called per game tick to ensure the audio is still playing, updates the
	 *	effects timers and other timed settings.
	 */
	public synchronized void cycle() {
		if (musicEnabled) {
			if (ensurePlaying) {
				if (player == null || player.getState() != javax.microedition.media.Player.STARTED) {
					resume();
				}
				ensurePlaying = true;
			}
			if (sndfxEnabled) {
				for (int n = 0; n < MAX_EFFECTS; n++) {
					if (effectTime[n]-- == 0) {
						setVolume(n, 0);
					}
				}
			}
			if (tempoTimer > 0) {
				if (--tempoTimer == 0) {
					setTempoFactor(100000, 0);
				}
			}
		}
	}
	
	/**
	 *	Loads the sound effects. This is an empty implementation due to the
	 *	effects being tied into the music playback.
	 */
	public void loadEffects(String filename) {}
	
	/**
	 *	Plays a sound effect.
	 */
	public void playEffect(int index, int length, int volume) {
		if (musicEnabled && sndfxEnabled) {
			if (effectTime[index] <= 0) {
				setVolume(index, volume);
			}
			effectTime[index] = length;
		}
	}
	
	/**
	 *	Stops a sound effect.
	 */
	public void stopEffect(int index) {
		setVolume(index, 0);
	}
	
	/**
	 *	Sets the main volume of the midi instrument.
	 */
	public void setVolume(int volume) {
		if (player != null) {
			try {
				((VolumeControl) player.getControl("VolumeControl")).setLevel(volume);
			} catch (Exception e) {}
		}
	}
	
	/**
	 *	Sets the volume of a midi channel.
	 */
	public void setVolume(int channel, int volume) {
		if (ENABLE_SNDFX) {
			if (synth != null) {
				synth.setChannelVolume(channel, volume);
			}
		}
	}
	
	/**
	 *	Returns the volume of a midi channel.
	 */
	public int getVolume(int channel) {
		if (ENABLE_SNDFX) {
			if (synth != null) {
				return synth.getChannelVolume(channel);
			}
		}
		return -1;
	}
	
	/**
	 *	Adjusts the pitch bend controller of a particular midi channel.
	 */
	public void setPitchBend(int channel, int pitch) {
		if (ENABLE_SNDFX) {
			if (synth != null) {
				synth.shortMidiEvent(0xE0 | channel, 0, pitch);
			}
		}
	}
	
	/**
	 *	Adjusts the tempo of the currently playing midi file.
	 *
	 *	@param millirate percentage change multiplied by 1000
	 *	@param duration number of ticks the change should last
	 */
	public void setTempoFactor(int millirate, int duration) {
		if (ENABLE_SNDFX) {
			try {
				/*
				 *	As with the <code>MidiControl</code> this might also be
				 *	missing from the implementation.
				 */
				if (player != null && ((RateControl) player.getControl("RateControl")).setRate(millirate) != 100000) {
					tempoTimer = duration;
				}
			} catch (Throwable e) {}
		}
	}
}