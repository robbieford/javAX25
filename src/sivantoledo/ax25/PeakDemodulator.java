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

import sivantoledo.ax25.PacketDemodulator.Frequency;

public class PeakDemodulator extends PacketDemodulator // implements
// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String PEAK = "Peak";

	public String getDemodulatorName() {
		return PEAK + "-w_emphasis_" + emphasis;
	}

	// New Variables for Zero Crossing (migrate old ones here as we realize we
	// need them
	// -----------------------------------------------------------------------------------vvv
	private static final int DEBUG = -1;
	// Structure Declarations

	// Variables
	private long samplesReceived;
	private float samplesPerBit;
	private static final float SAMPLE_BUFFER_AMOUNT = 2.5f;
	private int samplesSinceLastPeak;
	private int samplesSinceTransition;
	private float previousSample;
	private float secPreviousSample;
	private float thirdPreviousSample;
	private float s, s1, s2, s3;
	private boolean isIncreasing = false;
	private float localPeak;
	private float localPeakSampleNum;
	private float prevLocalPeakSampleNum;
	private float prevSamplesBetweenPeaks;

	boolean wasLastATransition = true;

	public PeakDemodulator(int sample_rate, int filter_length) throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public PeakDemodulator(int sample_rate, int filter_length, int emphasis,
			PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		this.samplesPerBit = (float) sample_rate / 1200.0f;

		samplesSinceLastPeak = 0;
		previousSample = 0;
		secPreviousSample = 0;
		thirdPreviousSample = 0;
		s = s1 = s2 = s3 = 0;
		samplesReceived = 0;
		samplesSinceTransition = 0;
		localPeakSampleNum = 0;
		localPeak = 0;
		prevLocalPeakSampleNum = 0;
		prevSamplesBetweenPeaks = 0;

		if (DEBUG > 0)
			System.err.printf("samples per bit = %.3f\n", this.samplesPerBit);
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

	protected void addSamplePrivate(float rawSample) {
		s = Math.abs(rawSample);
		
		samplesSinceLastPeak++;
		samplesReceived++;
		samplesSinceTransition++;

		float sample = (s + s1 + s2) / 3.0f;
		//sample = (sample + previousSample + secPreviousSample + thirdPreviousSample) / 4.0f;
		//sample = (sample + previousSample + secPreviousSample) / 3.0f;
		// sample = (sample + previousSample)/2.0f;

		if (DEBUG > 9) {
			System.out.println("Sample number: " + samplesReceived
					+ " /Value: " + sample);
		}

		if (isIncreasing) {
			if (sample > localPeak) {
				localPeak = sample;
				localPeakSampleNum = samplesReceived;
			}
			if ((sample < previousSample) && (previousSample < secPreviousSample) && (secPreviousSample < thirdPreviousSample)) {
				isIncreasing = false;

				if (DEBUG > 2)
					System.out.println("Local Peak at " + samplesReceived
							+ " of " + localPeak + " Packets since last: "
							+ samplesSinceLastPeak);

				handlePeakDetection();
				prevLocalPeakSampleNum = localPeakSampleNum;
				localPeak = -1000;
				localPeakSampleNum = 0;
				samplesSinceLastPeak = 0;
			}
		} else {
			if ((sample > previousSample) && (previousSample > secPreviousSample) && (secPreviousSample > thirdPreviousSample)) {
				isIncreasing = true;
				localPeak = sample;
				localPeakSampleNum = samplesReceived;
			}
		}

		s3 = s2;
		s2 = s1;
		s1 = s;
		thirdPreviousSample = secPreviousSample;
		secPreviousSample = previousSample;
		previousSample = sample;

	}
	
	private void handlePeakDetection() {

		float samplesBetweenPeaks = localPeakSampleNum - prevLocalPeakSampleNum;

		if (!wasLastATransition)
		{
			float diff = Math.abs(samplesBetweenPeaks - prevSamplesBetweenPeaks);
	
			if (diff >= SAMPLE_BUFFER_AMOUNT && samplesSinceTransition > samplesPerBit/2.0f) {
	
				wasLastATransition = true;
	
				int bits = (int) Math.round((double) samplesSinceTransition / (double) samplesPerBit);
	
				if (DEBUG > 1) {
					System.out.println(samplesReceived + " " + bits+ " -Transition Occurred. Prev Tran occurred " + samplesSinceTransition + " diff: " + diff);
				}
	
				samplesSinceTransition = 0;
	
				handleDemodulatedBits(bits);
			} 
		} 
		else {
			wasLastATransition = false;
		}
		
		samplesSinceLastPeak = 0;
		prevSamplesBetweenPeaks = samplesBetweenPeaks;
	}

}
