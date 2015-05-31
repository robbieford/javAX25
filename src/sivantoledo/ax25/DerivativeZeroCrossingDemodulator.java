/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * 
 * Robert Campbell 2015 Adapted from S. Toledo
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

public class DerivativeZeroCrossingDemodulator extends PacketDemodulator // implements
// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String DERIVATIVEZERO = "DerivativeZero";

	public String getDemodulatorName() {
		return DERIVATIVEZERO + "-w_emphasis_" + emphasis;
	}

	private static final int DEBUG = -1;

	// Variables
	private long samplesReceived;
	private float samplesPerBit;
	private boolean isSignalHigh;

	private static final float SAMPLE_BUFFER_AMOUNT = 2.0f;
	private float samplesPer1200ZeroXing;
	private float samplesPer2200ZeroXing;
	private int samplesSinceLastXing;
	private int samplesSinceFreqTransition;
	private int minimumZeroXingSamples;

	private Frequency lastFrequencySeen;

	private float previousSample;
	private float secPreviousSample;
	private float previousAverage;
	private float secPreviousAverage;
	private boolean foundFirstZeroCrossing = false;

	// -----------------------------------------------------------------------------------^^^

	public DerivativeZeroCrossingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public DerivativeZeroCrossingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		this.samplesPerBit = (float) sample_rate / 1200.0f;

		samplesPer1200ZeroXing = samplesPerBit / 2.0f;
		samplesPer2200ZeroXing = (float) sample_rate / 2200.0f / 2.0f;
		samplesSinceLastXing = 0;
		minimumZeroXingSamples = (int) (samplesPer2200ZeroXing - 1);
		previousSample = 0;
		secPreviousSample = 0;
		previousAverage = 0;
		secPreviousAverage = 0;
		samplesReceived = 0;
		isSignalHigh = false;
		samplesSinceFreqTransition = 0;
	}

	protected boolean isZeroCrossing(float firstSample, float secondSample) {
		return ((firstSample <= 0 && secondSample > 0) || // Going from below zero to above OR
		(firstSample >= 0 && secondSample < 0)); // Going from above zero to below
	}

	protected boolean isBelowZero(float sample) {
		return sample <= 0;
	}

	protected boolean isAboveZero(float sample) {
		return sample >= 0;
	}

	protected void addSamplePrivate(float s) {

		samplesSinceLastXing++;
		samplesReceived++;
		samplesSinceFreqTransition++;

//		float average = (s + previousAverage) / 2.0f;
//		float derivative = average - previousAverage; //166 F:-1
//		float average = (s + previousAverage + secPreviousAverage) / 3.0f;
//		float derivative = average - previousAverage; //346 f:-1
//		float average = (s + previousAverage);
//		float derivative = average - previousAverage; //541 f: 1
		float average = (s + previousAverage);
		float derivative = average - secPreviousAverage; //543 f:1
		
		if (DEBUG > 9) {
			System.out.println("Sample number: " + samplesReceived
					+ " /Value: " + derivative);
		}

		if (samplesSinceLastXing >= minimumZeroXingSamples) {

			if ((isSignalHigh && isBelowZero(derivative))
					|| (!isSignalHigh && isAboveZero(derivative))) {
				handleZeroCrossing();
				samplesSinceLastXing = 0;
				isSignalHigh = !isSignalHigh;
			}
		}

		secPreviousAverage = previousAverage;
		previousAverage = average;
		secPreviousSample = previousSample;
		previousSample = s;
	}

	private void handleZeroCrossing() {

		Frequency freq;

		// The last zero crossing is semi-recently then lets change to the processing stage...
		if (true) {
			// Presumably we are in the decoding phase...
			if (samplesSinceLastXing > ((samplesPer2200ZeroXing + samplesPer1200ZeroXing) / 2 - SAMPLE_BUFFER_AMOUNT)) { // round down slightly
				freq = Frequency.f_1200;
			} else {
				freq = Frequency.f_2200;
			}

			// System.out.println(sample_rate/samplesSinceLastXing/2);

			if (DEBUG > 2) {
				System.out.println("Frequency is:" + freq);
			}

			// transition!
			if (freq != lastFrequencySeen) {

				int bits = (int) Math.round((double) samplesSinceFreqTransition
						/ (double) samplesPerBit);

				if (DEBUG > 1) {
					System.out.println(bits);
				}

				handleDemodulatedBits(bits);

				samplesSinceFreqTransition = 0;
			}
			lastFrequencySeen = freq;
		}
		samplesSinceLastXing = 0;
	}

}
