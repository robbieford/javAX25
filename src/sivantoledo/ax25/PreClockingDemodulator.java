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

public class PreClockingDemodulator extends PacketDemodulator // implements
																// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String PRECLOCKING = "PreclockingDemodulator";
	private static final int DEBUG = -1;

	public String getDemodulatorName() {
		return PRECLOCKING + "-w_emphasis_" + emphasis;
	}

	/***/
	private float[] cd_filter;
	private float samplesPerBaud;
	private float[] c0_real, c0_imag, c1_real, c1_imag;
	private float[] diff;
	private float previous_fdiff;
	private long last_transition;
	private float phase_inc_f0, phase_inc_f1;
	private int samplesInFlag;
	float ZERO_CROSSING_THRESHOLD;
	private int sampleRate;

	public PreClockingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public PreClockingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		filteredSampleArray = new ArrayList<Float>(); // Needs to big enough for
														// the filter

		// transmit_controller = c;
		for (rate_index = 0; rate_index < Afsk1200Filters.sample_rates.length; rate_index++) {
			if (Afsk1200Filters.sample_rates[rate_index] == sample_rate) {
				break;
			}
		}
		if (rate_index == Afsk1200Filters.sample_rates.length) {
			throw new Exception("Sample rate " + sample_rate + " not supported");
		}

		this.sampleRate = sample_rate;
		this.samplesPerBaud = (float) sample_rate / 1200.0f;
		samplesInFlag = Math.round(samplesPerBaud * FLAG_LENGTH_IN_BAUD);
		if (DEBUG > 0)
			System.err.printf("samples per bit = %.3f\n", this.samplesPerBaud);

		cd_filter = Afsk1200Filters.corr_diff_filter[filter_index][rate_index];

		c0_real = new float[(int) Math.floor(samplesPerBaud)];
		c0_imag = new float[(int) Math.floor(samplesPerBaud)];
		c1_real = new float[(int) Math.floor(samplesPerBaud)];
		c1_imag = new float[(int) Math.floor(samplesPerBaud)];

		diff = new float[cd_filter.length];

		phase_inc_f0 = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
		phase_inc_f1 = (float) (2.0 * Math.PI * 2200.0 / sample_rate);

		// System.out.printf("Size of symbol sync filter is %d\n",
		// symbol_sync_filter.length);

		ZERO_CROSSING_THRESHOLD = sampleRate / 2200.0f / 4.0f;

	}

	private volatile boolean data_carrier = false;

	public boolean dcd() {
		return data_carrier;
	}

	private float sum(float[] x, int j) {
		float c = (float) 0.0;
		for (int i = 0; i < x.length; i++) {
			c += x[j];
			j--;
			if (j == -1) {
				j = x.length - 1;
			}
		}
		return c;
	}

	private int j_cd; // time domain index
	private int j_corr; // correlation index
	private float phase_f0, phase_f1;
	private long t; // running sample counter

	/**
	 * 1.) Filter the data with the "emphasis" filter 2.) Capture a whole packet
	 * (look for flags) 3.) Post process the captured packet... a.) Derivative
	 * to find vvv (Removes DC bias problem and emphasis problem) b.) Zero
	 * crossing to see transitions c.) Figure out which sample corresponds to
	 * clocking d.) Use clocking to do correlation. (per baud)
	 */

	/**
	 * Add sample to filter buffer and filter the sample Start looking for
	 * flags... If flag found store the location and look at distance since last
	 * flag If flags
	 */

	private ArrayList<Float> filteredSampleArray; // Slightly bigger than max
													// packet
	private static final int MINIMUM_PACKET_BYTES = 17;
	private static final int MAXIMUM_PACKET_BYTES = 256; // MTU for AX25
	private static final int FLAG_LENGTH_IN_BAUD = 9; // 0x7E

	private boolean containsStartFlag = false;

	private static final int BUFFER_SAMPLES_AFTER_FLAG = 10;

	protected void addSamplePrivate(float sample) {
		filteredSampleArray.add(sample);

		// Look for flags in samples
		boolean flagInSamples = containsFlag(sample);
		if (flagInSamples) {
			// something
			// System.out.println("Saw a flag! " + t);
			if (containsStartFlag) {
				int bytesBetweenFlags = (int) ((filteredSampleArray.size()
						- samplesInFlag * 2 - BUFFER_SAMPLES_AFTER_FLAG) / samplesPerBaud) / 8;

				if (bytesBetweenFlags >= MINIMUM_PACKET_BYTES
						&& bytesBetweenFlags <= MAXIMUM_PACKET_BYTES) {
					// System.out.println("Number of Potential Packtes: " +
					// ++numPotentialPackets + " with size: " +
					// bytesBetweenFlags);
					processPacket();
					shrinkFilteredSampleArray();
				} else {
					shrinkFilteredSampleArray();
				}

			} else {
				containsStartFlag = true;
				shrinkFilteredSampleArray();
			}
		}
	}

	int numPotentialPackets = 0;

	private void processPacket() {
		ArrayList<Float> derivativeOfSamples = getDerivativeOfFilteredData();
		ArrayList<Float> derivativeCrossingPositions = calulateZeroCrossings(derivativeOfSamples);
		ArrayList<Float> frequenciesFromDerivative = getFrequenciesSampleList(derivativeCrossingPositions);
		ArrayList<Float> detectedFreqTransitions = getTransitionsFromFreqData(frequenciesFromDerivative);
		int combOffset = getCombIndex(detectedFreqTransitions);
		ArrayList<Frequency> frequencySymbolList = getFrequencySymbolList(
				combOffset, frequenciesFromDerivative,
				derivativeCrossingPositions);
		generatePacket(frequencySymbolList);
	}

	private void generatePacket(ArrayList<Frequency> frequencySymbolList) {
		Frequency lastFreq = frequencySymbolList.get(0);
		int bauds = 0;

		for (int i = 0; i < frequencySymbolList.size(); i++) {
			bauds++;
			if (frequencySymbolList.get(i) != lastFreq) {
				// System.out.println(bauds);// + " " + i);
				// if(bauds == 8) bauds =7;
				handleDemodulatedBits(bauds);
				bauds = 0;
				lastFreq = frequencySymbolList.get(i);
			}
		}
	}

	private ArrayList<Frequency> getFrequencySymbolList(int combOffset,
			ArrayList<Float> frequenciesFromDerivative,
			ArrayList<Float> derivativeCrossingPositions) {
		ArrayList<Frequency> frequencies = new ArrayList<Frequency>();

		for (Float i = new Float(combOffset); i < filteredSampleArray.size(); i += samplesPerBaud) {
			Frequency freq = getFrequency(i + 1, i + samplesPerBaud,
					frequenciesFromDerivative, derivativeCrossingPositions);
			if (freq != null) {
				frequencies.add(freq);
			}
		}

		return frequencies;
	}

	private Frequency getFrequency(float start, float end,
			ArrayList<Float> frequenciesFromDerivative,
			ArrayList<Float> derivativeCrossingPositions) {
		int startIdx = -1, endIdx = -1;
		float sum = 0.0f;

		for (int i = 0; i < derivativeCrossingPositions.size(); i++) {
			if (startIdx < 0 && derivativeCrossingPositions.get(i) > start) {
				startIdx = i;
			}
			if (endIdx < 0 && derivativeCrossingPositions.get(i) > end) {
				endIdx = i - 1;
			}
		}

		if (endIdx == -1 || startIdx == -1) {
			return null;
		}

		for (int i = startIdx; i <= endIdx; i++) {
			sum += frequenciesFromDerivative.get(i);
		}

		Frequency freq = sum / (endIdx + 1 - startIdx) > 1700 ? Frequency.f_2200
				: Frequency.f_1200;
		return freq;
	}

	private int getCombIndex(ArrayList<Float> detectedFreqTransitions) {
		long minimumSquaredDistance = -1;
		int sampleCombPosition = 0;

		ArrayList<Integer> modulusValues = new ArrayList<Integer>(
				detectedFreqTransitions.size());

		for (int i = 0; i < detectedFreqTransitions.size(); i++) {
			modulusValues.add(Math.round(detectedFreqTransitions.get(i))
					% Math.round(samplesPerBaud));
		}

		for (int i = 0; i < samplesPerBaud; i++) {
			long squaredDistance = 0;
			for (int j = 0; j < detectedFreqTransitions.size(); j++) {
				squaredDistance += Math.pow(modulusValues.get(j) - i, 2);
			}
			if (minimumSquaredDistance == -1
					|| minimumSquaredDistance > squaredDistance) {
				minimumSquaredDistance = squaredDistance;
				sampleCombPosition = i;
			}
		}

		return sampleCombPosition;
	}

	private ArrayList<Float> getTransitionsFromFreqData(ArrayList<Float> freqs) {
		ArrayList<Float> sampleNumbers = new ArrayList<Float>();

		for (int i = 1; i < freqs.size(); i++) {
			if (isZeroCrossing(freqs.get(i - 1) - 1700.0f,
					freqs.get(i) - 1700.0f)) {
				sampleNumbers.add(i
						- 1
						+ zeroCrossingPercentage(freqs.get(i - 1) - 1700.0f,
								freqs.get(i) - 1700.0f));
			}
		}

		return sampleNumbers;
	}

	private ArrayList<Float> getFrequenciesSampleList(ArrayList<Float> data) {
		ArrayList<Float> freqs = new ArrayList<Float>();

		for (int i = 1; i < data.size(); i++) {
			freqs.add(sampleRate / (data.get(i) - data.get(i - 1)) / 2.0f);
		}

		return freqs;
	}

	private ArrayList<Float> calulateZeroCrossings(ArrayList<Float> samples) {
		// float ZERO_CROSSING_THRESHOLD = 5f;
		int sinceLastCrossing = Math.round(samplesPerBaud);
		ArrayList<Float> crossingList = new ArrayList<Float>();

		for (int i = 1; i < samples.size(); i++) {
			sinceLastCrossing++;
			if (sinceLastCrossing > ZERO_CROSSING_THRESHOLD
					&& isZeroCrossing(samples.get(i - 1), samples.get(i))) {
				crossingList.add(i
						- 1
						+ zeroCrossingPercentage(samples.get(i - 1),
								samples.get(i)));
				sinceLastCrossing = 0;
			}
		}

		return crossingList;
	}

	protected boolean isZeroCrossing(float firstSample, float secondSample) {
		return ((firstSample <= 0 && secondSample > 0) || // Going from below
															// zero to above OR
		(firstSample >= 0 && secondSample < 0)); // Going from above zer to
													// below
	}

	protected float zeroCrossingPercentage(float firstSample, float secondSample) {
		// x = x1 + (x2 - x1) * ((0-y1)/(y2-y1))
		return (-firstSample / (secondSample - firstSample));
	}

	private ArrayList<Float> getDerivativeOfFilteredData() {
		ArrayList<Float> derivative = new ArrayList<Float>(
				filteredSampleArray.size());

		for (int i = 1; i < filteredSampleArray.size() - 1; i++) {
			derivative.add(filteredSampleArray.get(i + 1)
					- filteredSampleArray.get(i - 1));
			if (derivative.size() == 1) {
				derivative.add(filteredSampleArray.get(i + 1)
						- filteredSampleArray.get(i - 1));
			}
		}
		derivative.add(derivative.get(derivative.size() - 1));

		return derivative;
	}

	private void shrinkFilteredSampleArray() {
		int index = filteredSampleArray.size() - 1 - samplesInFlag * 2
				- BUFFER_SAMPLES_AFTER_FLAG;
		if (index < 0)
			index = 0;
		filteredSampleArray = new ArrayList<Float>(filteredSampleArray.subList(
				index, filteredSampleArray.size() - 1));
	}

	private boolean containsFlag(float sample) {
		boolean retVal = false;

		c0_real[j_corr] = sample * (float) Math.cos(phase_f0);
		c0_imag[j_corr] = sample * (float) Math.sin(phase_f0);

		c1_real[j_corr] = sample * (float) Math.cos(phase_f1);
		c1_imag[j_corr] = sample * (float) Math.sin(phase_f1);

		phase_f0 += phase_inc_f0;
		if (phase_f0 > (float) 2.0 * Math.PI) {
			phase_f0 -= (float) 2.0 * Math.PI;
		}
		phase_f1 += phase_inc_f1;
		if (phase_f1 > (float) 2.0 * Math.PI) {
			phase_f1 -= (float) 2.0 * Math.PI;
		}

		float cr = sum(c0_real, j_corr);
		float ci = sum(c0_imag, j_corr);
		float c0 = (float) Math.sqrt(cr * cr + ci * ci);

		cr = sum(c1_real, j_corr);
		ci = sum(c1_imag, j_corr);
		float c1 = (float) Math.sqrt(cr * cr + ci * ci);

		diff[j_cd] = c0 - c1;
		float fdiff = Filter.filter(diff, j_cd, cd_filter);

		if (previous_fdiff * fdiff < 0 || previous_fdiff == 0) {

			// we found a transition
			int p = (int) (t - last_transition);
			last_transition = t;

			int bits = (int) Math.round((double) p / (double) samplesPerBaud);

			if (bits == 7) {
				retVal = true;
			}
		}

		previous_fdiff = fdiff;

		t++;

		j_cd++;
		if (j_cd == cd_filter.length) {
			j_cd = 0;
		}

		j_corr++;
		if (j_corr == c0_real.length) {// samples_per_bit
			j_corr = 0;
		}

		return retVal;
	}
}
