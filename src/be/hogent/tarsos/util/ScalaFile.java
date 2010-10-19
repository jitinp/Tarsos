package be.hogent.tarsos.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import be.hogent.tarsos.sampled.pitch.PitchConverter;
import be.hogent.tarsos.util.histogram.ToneScaleHistogram;

/**
 * A representation of a scala file.
 * 
 * @author Joren Six
 */
public final class ScalaFile {
	/**
	 * Log messages.
	 */
	private static final Logger LOG = Logger.getLogger(ScalaFile.class.getName());

	/**
	 * The octave is 1200 cents.
	 */
	private static final double OCTAVE_IN_CENTS = 1200.0;
	/**
	 * A description of the tone scale.
	 */
	private final transient String description;
	/**
	 * A list of pitches.
	 */
	private final transient double[] pitches;
	/**
	 * A list of names.
	 */
	private final transient String[] pitchNames;

	/**
	 * Create a new Scala file object.
	 * 
	 * @param desc
	 *            The description of the tone scale.
	 * @param notes
	 *            The pitches (notes) used in the scale.
	 * @param names
	 *            The names of the notes used (or null).
	 */
	public ScalaFile(final String desc, final double[] notes, final String[] names) {
		if (notes == null) {
			throw new IllegalArgumentException("The notes (pitches) should be defined");
		} else if (names != null && names.length != notes.length) {
			throw new AssertionError("Notes and names should have the same length.");
		}
		description = desc;
		pitches = notes.clone();
		if (names == null) {
			pitchNames = null;
		} else {
			pitchNames = names.clone();
		}
	}

	/**
	 * Create a new Scala file object.
	 * 
	 * @param desc
	 *            The description of the tone scale.
	 * @param notes
	 *            The pitches (notes) used in the scale.
	 */
	public ScalaFile(final String desc, final double[] notes) {
		this(desc, notes, null);
	}

	/**
	 * Reads a Scala file from disk and returns a new instance.
	 * <p>
	 * The <a href="http://www.huygens-fokker.org/scala/scl_format.html"> Scala
	 * scale file format</a>: <bufferCount>This file format for musical tunings
	 * is becoming a standard for exchange of scales, owing to the size of the
	 * scale archive of over 3700+ scales and the popularity of the Scala
	 * program.</bufferCount>
	 * </p>
	 * <p>
	 * Usually it has <code>.scl</code> as extension.
	 * </p>
	 * 
	 * @param scalaFile
	 *            The Scala file to read.
	 */
	public ScalaFile(final String scalaFile) {
		final String contents = FileUtils.readFile(scalaFile);
		final String[] lines = contents.split("\n");
		final List<String> validPitchRows = new ArrayList<String>();
		String descriptionLine = "";
		int numberOfDataLines = 0;
		for (final String line : lines) {
			final boolean isComment = line.trim().startsWith("!");
			// Skip comments.
			if (isComment) {
				continue;
			} // else {
			numberOfDataLines++;
			// The first data line is the description
			if (numberOfDataLines == 1) {
				descriptionLine = line;
				// The second data line is the number of notes.
				// The other data lines should be valid pitches.
			} else if (numberOfDataLines > 2) {
				final boolean isValidRatio = line.matches("\\s*[0-9]+(|/[0-9]+).*");
				final boolean isValidCent = line.matches("\\s*(-|\\+)?[0-9]+\\.[0-9]*.*");
				if (isValidRatio || isValidCent) {
					validPitchRows.add(line);
				}
			}
		}

		pitches = new double[validPitchRows.size()];
		pitchNames = new String[validPitchRows.size()];
		description = descriptionLine;
		String[] lineData;
		for (int i = 0; i < pitches.length; i++) {
			lineData = validPitchRows.get(i).trim().split("\\s", 2);
			final String pitchData = lineData[0];
			String nameData;
			if (lineData.length == 2) {
				nameData = lineData[1].trim();
			} else {
				nameData = "";
			}
			pitches[i] = parsePitch(pitchData);
			pitchNames[i] = nameData;
		}
	}

	/**
	 * Builds a tone scale histogram using the pitches defined in this scala
	 * file.
	 * 
	 * @return A ToneScaleHistogram using the correct pitches.
	 */
	public ToneScaleHistogram buildHistogram() {
		return ToneScaleHistogram.createToneScale(pitches);
	}

	/**
	 * Parses a row from a scala file and returns a double value representing
	 * cents.These lines are all valid pitch lines:
	 * 
	 * <pre>
	 * 81/64
	 * 408.0
	 * 408.
	 * 5
	 * -5.0
	 * 10/20
	 * 100.0 cents
	 * 100.0 C#
	 * 5/4   E\
	 * </pre>
	 * 
	 * @param row
	 *            The row to parse.
	 * @return The parsed pitch.
	 */
	private double parsePitch(final String row) {
		double parsedPitch;
		if (row.contains("/") || !row.contains(".")) {
			final String[] data = row.split("/");
			final double denominator = Double.parseDouble(data[0]);
			double quotient;
			if (data.length == 2) {
				quotient = Double.parseDouble(data[1]);
			} else {
				quotient = 1;
			}
			final double absCentDenom = PitchConverter.hertzToAbsoluteCent(denominator);
			final double absCentQuotient = PitchConverter.hertzToAbsoluteCent(quotient);
			parsedPitch = Math.abs(absCentDenom - absCentQuotient);
		} else {
			parsedPitch = Double.parseDouble(row);
		}
		return parsedPitch;
	}

	/**
	 * Writes a Scala file to disk. The peaks use cent values.
	 * <p>
	 * The <a href="http://www.huygens-fokker.org/scala/scl_format.html"> Scala
	 * scale file format</a>: <bufferCount>This file format for musical tunings
	 * is becoming a standard for exchange of scales, owing to the size of the
	 * scale archive of over 3700+ scales and the popularity of the Scala
	 * program.</bufferCount>
	 * </p>
	 * <p>
	 * Usually it has <code>.scl</code> as extension.
	 * </p>
	 * 
	 * @param scalaFile
	 *            The location to write to.
	 */
	public void write(final String scalaFile) {
		if (pitches.length > 0) {
			final StringBuilder contents = new StringBuilder();
			contents.append("! ").append(FileUtils.basename(scalaFile)).append(".scl \n");
			contents.append("!\n");
			contents.append(description).append("\n");
			contents.append(pitches.length).append("\n!\n");
			for (int i = 0; i < pitches.length; i++) {
				final double peakPosition = pitches[i];
				contents.append(peakPosition);
				if (pitchNames != null && pitchNames[i] != null) {
					contents.append(" ").append(pitchNames[i]);
				}
				contents.append("\n");
			}

			if (pitches[pitches.length - 1] != OCTAVE_IN_CENTS) {
				// contents.append(OCTAVE_IN_CENTS).append("\n");
			}

			FileUtils.writeFile(contents.toString(), scalaFile);
		} else {
			LOG.warning("No pitches defined, file: " + scalaFile + " not created.");
		}
	}

	/**
	 * @return The list of pitches.
	 */
	public double[] getPitches() {
		return pitches.clone();
	}

	/**
	 * @return The tone scale description.
	 */
	public String getDescription() {
		return this.description;
	}

	/**
	 * @return A list of pitch names. Or null.
	 */
	public String[] getPitchNames() {
		String[] names = null;
		if (hasNames()) {
			names = pitchNames.clone();
		}
		return names;
	}

	/**
	 * @return True if the pitch classes are named, false otherwise.
	 */
	public boolean hasNames() {
		return pitchNames != null;
	}

	/**
	 * @return The western scale.
	 */
	public static ScalaFile westernTuning() {
		final double[] notes = { 0, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, };
		final String[] names = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };
		return new ScalaFile("The western tone scale", notes, names);
	}
}
