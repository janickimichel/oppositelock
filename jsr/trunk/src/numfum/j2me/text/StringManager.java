package numfum.j2me.text;

import java.io.*;

/**
 *	Handles string localisation. Each file contains one of more languages each
 *	with one or more strings.
 *
 *	TODO: remove string to char array conversion.
 */
public class StringManager {
	/**
	 *	An empty string (as chars).
	 */
	public static final char[] EMPTY = new char[] {};
	
	/**
	 *	Used for measuring the height of text.
	 */
	public static final char[] RULER = new char[] {'|'};
	
	/**
	 *	Name of the resource file containing the strings.
	 */
	private final String resource;
	
	/**
	 *	Each of the string entries
	 */
	private final char[][] line;
	
	/**
	 *	Number of language options to choose from.
	 */
	private final int opts;
	
	/**
	 *	Name for each language in the resource file (e.g.: English).
	 */
	private final char[][] optName;
	
	/**
	 *	Locale for each language in the resource file (e.g.: en_UK).
	 */
	private final char[][] optLang;
	
	/**
	 *	Currently loaded set of strings.
	 */
	private int optIdx = -1;
	
	/**
	 *	Creates a new string manager from the specified resource file.
	 */
	public StringManager(String resource) throws IOException {
		this.resource = resource;
		DataInputStream in = new DataInputStream(getClass().getResourceAsStream(resource));
		in.skipBytes(2); // header size
		line = new char[in.readUnsignedByte()][];
		opts = in.readUnsignedByte();
		optName = new char[opts][];
		optLang = new char[opts][];
		for (int n = 0; n < opts; n++) {
			optName[n] = in.readUTF().toCharArray();
			optLang[n] = in.readUTF().toCharArray();
		}
		in.close();
	}
	
	/**
	 *	Returns the number of locales in the resource file.
	 */
	public int getLocales() {
		return opts;
	}
	
	/**
	 *	Returns the name of the requested locale.
	 */
	public char[] getLocaleName(int index) {
		while (index < 0) {
			index += opts;
		}
		return optName[index % opts];
	}
	
	/**
	 *	Returns the index of the currently selected locale.
	 */
	public int getSelected() {
		return optIdx;
	}
	
	/**
	 *	Returns the index of the language matching the specified locale.
	 *	
	 *	@param locale match to search for
	 *	@param len number of chars to match (5 or 3 for ISO locales)
	 */
	private int matchLocale(char[] locale, int len) {
		int index = -1;
		for (int n = 0; n < opts; n++) {
			if (optLang[n].length >= len && locale.length >= len) {
				boolean matched = true;
				for (int i = 0; i < len; i++) {
					if (optLang[n][i] != locale[i]
						&&  locale[i] != '_'
						&&  locale[i] != '-') {
						matched = false;
						break;
					}
				}
				if (matched) {
					index = n;
				}
			}
		}
		return index;
	}
	
	/**
	 *	Loads the strings matching the specified locale (or the first if no
	 *	match can be found).
	 */
	public int use(char[] locale) {
		int index = matchLocale(locale, 5);
		if (index < 0) {
			index = matchLocale(locale, 2);
			if (index < 0) {
				index = 0;
			}
		}
		return use(index);
	}
	
	/**
	 *	Loads the strings matching the specified locale (or the first if no
	 *	match can be found).
	 */
	public int use(String locale) {
		if (locale != null) {
			return use(locale.toCharArray());
		} else {
			return use(0);
		}
	}
	
	/**
	 *	Loads the strings of the specified index.
	 */
	public int use(int index) {
		while (index < 0) {
			index += opts;
		}
		index %= opts;
		if (optIdx != index) {
			optIdx  = index;
			try {
				DataInputStream in = new DataInputStream(getClass().getResourceAsStream(resource));
				in.skipBytes(in.readUnsignedShort());
				for (int n = in.readUnsignedByte(); n > 0; n--) {
					line[in.readUnsignedByte()] = in.readUTF().toCharArray();
				}
				in.skipBytes(optIdx * 2);
				in.skipBytes(in.readUnsignedShort() + 2); // +2 is language size short
				for (int n = in.readUnsignedByte(); n > 0; n--) {
					line[in.readUnsignedByte()] = in.readUTF().toCharArray();
				}
				in.close();
			} catch (IOException e) {}
		}
		return optIdx;
	}
	
	/**
	 *	Returns the specified string (as chars) from the currently loaded
	 *	language.
	 */
	public char[] get(int index) {
		return line[index];
	}
	
	/**
	 *	Returns the specified string from the currently loaded language.
	 */
	public String getAsString(int index) {
		return new String(get(index));
	}
	
	/**
	 *	Returns the specified string from the currently loaded language. Where
	 *	content
	 */
	public String[] getAsStrings(int index) {
		int linelen = line[index].length;
		int numStrs = 1;
		for (int n = 0; n < linelen; n++) {
			if (line[index][n] == '\n') {
				numStrs++;
			}
		}
		String[] out = new String[numStrs];
		int start = 0;
		for (int n = 0; n < numStrs; n++) {
			int count = 0;
			while ((start + count) < linelen && line[index][start + count] != '\n') {
				count++;
			}
			out[n] = new String(line[index], start, count);
			start += count + 1;
		}
		return out;
	}
	
	/**
	 *	Prints all of the currently loaded strings to <code>stdout</code>
	 */
	public void dump() {
		for (int n = 0; n < line.length; n++) {
			System.out.println(n + ": " + getAsString(n));
		}
	}
	
	/**
	 *	Returns a single string from the specifed resource file.
	 */
	public static String getSingleString(String resource, String locale, int index) throws IOException {
		StringManager strings = new StringManager(resource);
		strings.use(locale);
		return strings.getAsString(index);
	}
}