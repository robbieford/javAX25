/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * 
 * Robert Campbell and Michael Harriman 2014 Adapted from S. Toledo
 * 
 *      This program is free software; you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation; either version 2 of the License, or
 *      (at your option) any later version.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with this program; if not, write to the Free Software
 *      Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package sivantoledo.ax25;

import java.util.ArrayList;

public class WindowedZeroCrossingDemodulator extends PacketDemodulator // implements
// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String WINDOWEDZERO = "WindowedZero";

	public String getDemodulatorName() {
		return WINDOWEDZERO + "-w_emphasis_" + emphasis;
	}

	// New Variables for Zero Crossing (migrate old ones here as we realize we
	// need them
	// -----------------------------------------------------------------------------------vvv
	private static final int DEBUG = -1;

	// Variables
	private long samplesReceived;
	private float samplesPerBit;

	private static final int _1200CrossingsInWindow = 2;
	private int samplesInWindow;
	private ArrayList<Float> window;
	private int samplesSinceCrossingRecount;
	private int crossingRecountInterval;
	private int samplesSinceFreqTransition;
	private int minimumZeroXingSamples;

	private Frequency lastFrequencySeen;
	ArrayList<Float> sampleArray;
	float[] td_filter;

	private int filter_index;

	private int rate_index;


	public WindowedZeroCrossingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public WindowedZeroCrossingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		this.samplesPerBit = (float) sample_rate / 1200.0f;

		minimumZeroXingSamples = 4;
		samplesReceived = 0;
		samplesSinceFreqTransition = 0;
		// samplesInWindow = Math.round(sample_rate / 1200.0f) - 2;
		samplesInWindow = Math.round(samplesPerBit - 4); // Math.round(samplesPerBit
															// *
															// WINDOW_SIZE_IN_PERCENTAGE);
		window = new ArrayList<Float>(samplesInWindow);
		crossingRecountInterval = 3;// Math.round(samplesPer2200ZeroXing/2.0f);
		sampleArray = new ArrayList<Float>();
	}

	protected boolean isZeroCrossing(float firstSample, float secondSample) {
		return ((firstSample <= 0 && secondSample > 0) || // Going from below
															// zero to above OR
		(firstSample >= 0 && secondSample < 0)); // Going from above zer to
													// below
	}

	protected boolean isBelowZero(float sample) {
		return sample <= 0;
	}

	protected boolean isAboveZero(float sample) {
		return sample >= 0;
	}

	protected void addSamplePrivate(float s) {
		Frequency freq = null;

		window.add(s);

		samplesReceived++;
		samplesSinceFreqTransition++;
		samplesSinceCrossingRecount++;

		// We have enough samples, so lets do this!
		if (window.size() == samplesInWindow) {
			if (samplesSinceCrossingRecount > crossingRecountInterval) {
				int crossings = calculateCrossingsInWindow();
				samplesSinceCrossingRecount = 0;

				if (crossings == _1200CrossingsInWindow) {
					freq = Frequency.f_1200;
				} else if (crossings > _1200CrossingsInWindow) {
					freq = Frequency.f_2200;
				} else {
					// who knows?
				}

				if (freq != null && lastFrequencySeen != freq) {
					int bits = Math.round(samplesSinceFreqTransition
							/ samplesPerBit);

					if (bits > 0) {
						if (DEBUG > 1) {
							System.out.println("\t" + samplesReceived + " "
									+ bits + " -Switched Freq from "
									+ lastFrequencySeen);
						}
						// System.out.println(bits + "     " + samplesReceived);
						handleDemodulatedBits(bits);
						lastFrequencySeen = freq;
						samplesSinceFreqTransition = 0;
					} else {
						if (DEBUG > 2)
							System.out
									.println("\t\tGot 0 bits, wait for one more sample - "
											+ samplesReceived);
						samplesSinceCrossingRecount = crossingRecountInterval + 1;
					}
				}
			}

			// Remove the last sample to get ready for the next one.
			window.remove(0);
		}
	}

	private int calculateCrossingsInWindow() {
		int crossings = 0;
		boolean isHigh = window.get(0) > 0;

		// Set this to something big
		int samplesSinceLastXing = samplesInWindow;

		for (int i = 0; i < window.size(); i++) {
			samplesSinceLastXing++;
			if (samplesSinceLastXing >= minimumZeroXingSamples) {
				if ((isHigh && isBelowZero(window.get(i)))
						|| (!isHigh && isAboveZero(window.get(i)))) {
					crossings++;
					isHigh = !isHigh;
					samplesSinceLastXing = 0;
				}
			}
		}
		return crossings;
	}

}
