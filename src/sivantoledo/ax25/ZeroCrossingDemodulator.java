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

public class ZeroCrossingDemodulator extends PacketDemodulator // implements
// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String ZEROCROSSING = "ZeroCrossing";

	public String getDemodulatorName() {
		return ZEROCROSSING + "-w_emphasis_" + emphasis;
	}

	// New Variables for Zero Crossing (migrate old ones here as we realize we
	// need them
	// -----------------------------------------------------------------------------------vvv
	private static final int DEBUG = -1;

	// Variables
	private long samplesReceived;
	private float samplesPerBit;
	private boolean isSignalHigh;

	private static final float SAMPLE_BUFFER_AMOUNT = 2;
	private float samplesPer1200ZeroXing;
	private float samplesPer2200ZeroXing;
	private int samplesSinceLastXing;
	private int samplesSinceFreqTransition;

	private Frequency lastFrequencySeen;

	private float sampleLength;
	private ArrayList<Float> samples = new ArrayList<Float>();
	private static final int SAMPLES_BETWEEN_HISTORY_STATS_RECALC = 5;
	private static final int BIT_PERIODS_IN_HISTORY = 2;
	private int sampleHistoryLength;
	private int samplesSinceLastRecalc;
	private float monotonicThreshold;
	private static final float ZERO_CROSSING_THRESHOLD_PERCENTAGE = 0.5f;
	private static final float MONOTONIC_THRESHOLD_PERCENTAGE = 0.05f;
	private float averageValueInHistory;
	private float zeroCrossingThreshold;
	private ArrayList<Float> sampleHistory = new ArrayList<Float>();

	// -----------------------------------------------------------------------------------^^^

	public ZeroCrossingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public ZeroCrossingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		this.samplesPerBit = (float) sample_rate / 1200.0f;

		samplesPer1200ZeroXing = samplesPerBit / 2.0f;
		samplesPer2200ZeroXing = (float) sample_rate / 2200.0f / 2.0f;
		samplesSinceLastXing = 0;
		samplesSinceLastRecalc = 0;
		sampleHistoryLength = (int) (Math.round(samplesPerBit))
				* BIT_PERIODS_IN_HISTORY;
		sampleLength = samplesPer2200ZeroXing / 2;
		samplesReceived = 0;
		isSignalHigh = false;
		samplesSinceFreqTransition = 0;
	}

	protected void addSamplePrivate(float s) {
		samplesSinceLastRecalc++;
		samplesSinceLastXing++;
		samplesReceived++;
		samplesSinceFreqTransition++;

		if (DEBUG > 9) {
			System.out.println("Sample number: " + samplesReceived
					+ " /Value: " + s);
		}

		samples.add(s);
		sampleHistory.add(s);
		// If we have enough data, go ahead
		if (sampleHistory.size() > sampleHistoryLength) {
			// Take the first entry out (we added the most recent one to the
			// end;
			samples.remove(0);
			sampleHistory.remove(0);

			if (samplesSinceLastRecalc > SAMPLES_BETWEEN_HISTORY_STATS_RECALC) {
				if (DEBUG > 8) {
					System.out.println("Recalculating History...");
				}
				historyStatisticsRecalculation();
				if (DEBUG > 8) {
					System.out.println("Average: " + averageValueInHistory
							+ " zThreshold: " + zeroCrossingThreshold
							+ " monotonicThreshold: " + monotonicThreshold);
				}
			}

			// if (isSamplesIncreasing()){
			if (!isSignalHigh
					&& samples.get(samples.size() - 1) > averageValueInHistory
							+ zeroCrossingThreshold) {
				// Its going high!
				isSignalHigh = true;
				if (DEBUG > 7) {
					System.out
							.println("We had a zero crossing going HIGH at sample "
									+ samplesReceived);
				}
				handleZeroCrossing();
			}
			// }
			// else {
			if (isSignalHigh
					&& samples.get(samples.size() - 1) < averageValueInHistory
							- zeroCrossingThreshold) {
				// Its going low!!
				isSignalHigh = false;
				if (DEBUG > 7) {
					System.out
							.println("We had a zero crossing going LOW at sample "
									+ samplesReceived);
				}
				handleZeroCrossing();
			}

			// }
		}
		// Otherwise, just start collecting data into the arrays
		else {
			// If we already have enough samples start picking them off from
			// the front.
			if (samples.size() > sampleLength) {
				samples.remove(0);
			}
		}
	}

	private void handleZeroCrossing() {

		Frequency freq;

		// The last zero crossing is semi-recently then lets change to the
		// processing stage...
		if (samplesSinceLastXing < (samplesPer1200ZeroXing + SAMPLE_BUFFER_AMOUNT)) {
			// Presumably we are in the decoding phase...
			if (samplesSinceLastXing > ((samplesPer2200ZeroXing + samplesPer1200ZeroXing) / 2 - SAMPLE_BUFFER_AMOUNT)) { // round
																															// down
																															// slightly
				freq = Frequency.f_1200;
			} else {
				freq = Frequency.f_2200;
			}

			if (DEBUG > 2) {
				System.out.println("Frequency is:" + freq);
			}

			// transition!
			if (freq != lastFrequencySeen) {

				int bits = (int) Math.round((double) samplesSinceFreqTransition
						/ (double) samplesPerBit);

				if (DEBUG > 1) {
					System.out.println(samplesReceived + " " + bits
							+ " -Switched Freq from " + lastFrequencySeen
							+ " to: " + freq);
				}

				handleDemodulatedBits(bits);
				samplesSinceFreqTransition = 0;
			}
			lastFrequencySeen = freq;
		}
		samplesSinceLastXing = 0;
	}

	private void historyStatisticsRecalculation() {
		samplesSinceLastRecalc = 0;
		float max = -100, min = 100, sum = 0;
		for (int i = 0; i < sampleHistory.size(); i++) {
			if (sampleHistory.get(i) < min) {
				min = sampleHistory.get(i);
			}
			if (sampleHistory.get(i) > max) {
				max = sampleHistory.get(i);
			}
			sum += sampleHistory.get(i);
		}

		averageValueInHistory = sum / sampleHistory.size();
		zeroCrossingThreshold = ((Math.abs(max) + Math.abs(min)) / 2.0f)
				* ZERO_CROSSING_THRESHOLD_PERCENTAGE;
		monotonicThreshold = ((Math.abs(max) + Math.abs(min)) / 2.0f)
				* MONOTONIC_THRESHOLD_PERCENTAGE;
	}
}
