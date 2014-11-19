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

import sivantoledo.ax25.PacketDemodulator.Frequency;

public class GoertzelPreClockingDemodulator extends PacketDemodulator // implements
																// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String PRECLOCKING = "GoertzelPreclockingDemodulator";
	public String getDemodulatorName() {
		return PRECLOCKING + "-w_emphasis_" + emphasis;
	}

	private float samplesPerBaud;
	private int samplesInFlag;
	float ZERO_CROSSING_THRESHOLD;
	private ArrayList<Float> window;
	private float normalized1200Freq;
    private float normalized2200Freq;
    private float coeff1200;
    private float coeff2200;

	public GoertzelPreClockingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public GoertzelPreClockingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		window = new ArrayList<Float>();
	    this.normalized1200Freq = 1200f/sample_rate;
	    this.normalized2200Freq = 2200f/sample_rate;
	    this.coeff1200 = (float) (2*Math.cos(2*Math.PI*normalized1200Freq));
	    this.coeff2200 = (float) (2*Math.cos(2*Math.PI*normalized2200Freq));

		new ArrayList<Float>();
														// the filter
		sampleArray = new ArrayList<Float>();

		this.samplesPerBaud = (float) sample_rate / 1200.0f;
		samplesInFlag = Math.round(samplesPerBaud * FLAG_LENGTH_IN_BAUD);
	}

	/**
	 * 1.) Filter the data with the "emphasis" filter 2.) Capture a whole packet
	 * (look for flags) 3.) Post process the captured packet... a.) Derivative
	 * to find vvv (Removes DC bias problem and emphasis problem) b.) Zero
	 * crossing to see transitions c.) Figure out which sample corresponds to
	 * clocking d.) Use clocking to do correlation. (per baud)
	 */

	private ArrayList<Float> sampleArray;
	private static final int MINIMUM_PACKET_BYTES = 17;
	private static final int MAXIMUM_PACKET_BYTES = 256; // MTU for AX25
	private static final int FLAG_LENGTH_IN_BAUD = 9; // 0x7E
	private boolean containsStartFlag = false;

	private static final int BUFFER_SAMPLES_AFTER_FLAG = 10;

	protected void addSamplePrivate(float sample) {
		sampleArray.add(sample);

		// Look for flags in samples
		boolean flagInSamples = containsFlag(sample);
		if (flagInSamples) {
			// something
			// System.out.println("Saw a flag! " + t);
			if (containsStartFlag) {
				int bytesBetweenFlags = (int) ((sampleArray.size()
						- samplesInFlag * 2 - BUFFER_SAMPLES_AFTER_FLAG) / samplesPerBaud) / 8;
				//If the size is right, try to decode it otherwise clean up local storage
				if (bytesBetweenFlags >= MINIMUM_PACKET_BYTES
						&& bytesBetweenFlags <= MAXIMUM_PACKET_BYTES) {
					// System.out.println("Number of Potential Packtes: " +
					// ++numPotentialPackets + " with size: " +
					// bytesBetweenFlags);
					processPacket(new ArrayList<Float>(sampleArray));
					shrinkSampleArray();
				} else {
					shrinkSampleArray();
				}

			} else {
				containsStartFlag = true;
				shrinkSampleArray();
			}
		}
	}

	
	/**
	 * Once we have enough data actually try and demodulate the packet using this overly
	 * complicated method.
	 */
	private void processPacket(ArrayList<Float> samples) {
		ArrayList<Float> frequencyData = getPacketFrequencies(samples);
		ArrayList<Float> detectedFreqTransitions = getTransitionsFromFreqData(frequencyData);
		int combOffset = getCombIndex(detectedFreqTransitions);
//		System.out.println(combOffset);
		ArrayList<Frequency> frequencySymbolList = getFrequencySymbolList(combOffset, samples);
		generatePacket(frequencySymbolList);
		for (int i = 0; i < Math.round(samplesPerBaud); i ++)
		{
			frequencySymbolList = getFrequencySymbolList(i, samples);
			generatePacket(frequencySymbolList);
		}
	}

	/**
	 * Taking a frequency list f_1200/f_2200 actually put together the packet...
	 * @param frequencySymbolList
	 */
	private void generatePacket(ArrayList<Frequency> frequencySymbolList) {
		Frequency lastFreq = frequencySymbolList.get(0);
		int bauds = 0;

		for (int i = 1; i < frequencySymbolList.size(); i++) {
			bauds++;
			if (frequencySymbolList.get(i) != lastFreq) {
//				System.out.println(bauds + "," + frequencySymbolList.get(i));
				handleDemodulatedBits(bauds);
				bauds = 0;
				lastFreq = frequencySymbolList.get(i);
			}
		}
	}

	/**
	 * Using the calculated data compose a list of the best guess of frequencies
	 * for each bit period of this packet. 
	 * @param combOffset
	 * @param frequenciesFromDerivative
	 * @param derivativeCrossingPositions
	 * @return
	 */
	private ArrayList<Frequency> getFrequencySymbolList(int combOffset, ArrayList<Float> sampleData) {
		ArrayList<Frequency> frequencies = new ArrayList<Frequency>();

		for (Float i = new Float(combOffset); i < sampleData.size(); i += samplesPerBaud) {
			Frequency freq = getFrequency(i + 1, i + samplesPerBaud, sampleData);
			if (freq != null) {
				frequencies.add(freq);
			}
		}

		return frequencies;
	}

	/**
	 * Get the average frequency for this bit period. Using zero crossings.
	 * 
	 * @param start
	 * @param end
	 * @param frequenciesFromDerivative
	 * @param derivativeCrossingPositions
	 * @return
	 */
	private Frequency getFrequency(float start, float end,
			ArrayList<Float> data) {
		int startIdx = Math.round(start);
		int endIdx = Math.round(end);

		float s1200 = 0;
		float s2200 = 0;
	    float s1200_prev = 0;
	    float s1200_prev2 = 0;
	    float s2200_prev = 0;
	    float s2200_prev2 = 0;
	    for (int i = startIdx; i <= endIdx && i < data.size(); i ++)
	    {
	       s1200 = data.get(i)+coeff1200 * s1200_prev - s1200_prev2;
	       s1200_prev2 = s1200_prev;
	       s1200_prev = s1200;
	       s2200 = data.get(i)+coeff2200 * s2200_prev - s2200_prev2;
	       s2200_prev2 = s2200_prev;
	       s2200_prev = s2200;
	    }
	    
	    float power = (s1200_prev2*s1200_prev2+s1200_prev*s1200_prev-coeff1200*s1200_prev*s1200_prev2) -
	    		(s2200_prev2*s2200_prev2+s2200_prev*s2200_prev-coeff2200*s2200_prev*s2200_prev2);
	    //System.out.println(power + "  ");
	    
	    Frequency freq = power > 0 ? Frequency.f_1200 : Frequency.f_2200;
	    
		return freq;
	}

	/**
	 * Using the data for the detected frequency transition indexes, figure out which
	 * index into the packet data best corresponds to the start of a bit period.
	 * 
	 * @param detectedFreqTransitions
	 * @return
	 */
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

	/**
	 * Take a list of frequency data and return a list of indexes into this list
	 * which correspond to transitions through 1700Hz (half way between 1200 & 2200)
	 * 
	 * @param frequencyData
	 * @return
	 */
	private ArrayList<Float> getTransitionsFromFreqData(ArrayList<Float> frequencyData) {
		ArrayList<Float> sampleNumbersOfFrequencyTransitions = new ArrayList<Float>();

		for (int i = 1; i < frequencyData.size(); i++) {
			if (isZeroCrossing(frequencyData.get(i - 1), frequencyData.get(i))) {
				sampleNumbersOfFrequencyTransitions.add(i - 1 +
						zeroCrossingPercentage(frequencyData.get(i - 1), frequencyData.get(i)));
			}
		}

		return sampleNumbersOfFrequencyTransitions;
	}

	/**
	 * Take a list of zero crossing locations and convert those to frequencies
	 * 
	 * @param data
	 * @return
	 */
	private ArrayList<Float> getPacketFrequencies(ArrayList<Float> data) {
		ArrayList<Float> freqs = new ArrayList<Float>();
		ArrayList<Float> slidingWindow = new ArrayList<Float>();

		for (float sample : data)
		{
			slidingWindow.add(sample);
			if (slidingWindow.size() >= samplesPerBaud)
			{
				slidingWindow.remove(0);
				float s1200 = 0;
				float s2200 = 0;
				float s1200_prev = 0;
				float s1200_prev2 = 0;
				float s2200_prev = 0;
				float s2200_prev2 = 0;
				for (int i = 0; i < slidingWindow.size(); i ++)
				{
					s1200 = slidingWindow.get(i)+coeff1200 * s1200_prev - s1200_prev2;
					s1200_prev2 = s1200_prev;
					s1200_prev = s1200;
					s2200 = slidingWindow.get(i)+coeff2200 * s2200_prev - s2200_prev2;
					s2200_prev2 = s2200_prev;
					s2200_prev = s2200;
				}
				float power = (s1200_prev2*s1200_prev2+s1200_prev*s1200_prev-coeff1200*s1200_prev*s1200_prev2) -
						(s2200_prev2*s2200_prev2+s2200_prev*s2200_prev-coeff2200*s2200_prev*s2200_prev2);
				//System.out.println(power + "  ");
				

			    //Frequency freq = power > 0 ? Frequency.f_1200 : Frequency.f_2200;
				freqs.add(power);
			}
		}
		return freqs;
	}

	/**
	 * If the first sample has the opposite sign of the second sample return true
	 * @param firstSample
	 * @param secondSample
	 * @return
	 */
	protected boolean isZeroCrossing(float firstSample, float secondSample) {
		return ((firstSample <= 0 && secondSample > 0) || // Going from below
															// zero to above OR
		(firstSample >= 0 && secondSample < 0)); // Going from above zero to
													// below
	}

	/**
	 * Interpolation
	 * @param firstSample
	 * @param secondSample
	 * @return
	 */
	protected float zeroCrossingPercentage(float firstSample, float secondSample) {
		// x = x1 + (x2 - x1) * ((0-y1)/(y2-y1))
		return (-firstSample / (secondSample - firstSample));
	}

	/**
	 * Since we process one packet at a time we need to shrink the internal buffer after
	 * we processes a potential packet or know that there isn't a packet contained within
	 * the data due to the size.
	 */
	private void shrinkSampleArray() {
		int index = sampleArray.size() - 1 - samplesInFlag * 2
				- Math.round(samplesPerBaud);
		if (index < 0)
			index = 0;
		sampleArray = new ArrayList<Float>(sampleArray.subList(index, sampleArray.size() - 1));
	}

	private int samplesThisFreq;
	private Frequency currentFreq;
	
	/**
	 * Once we receive a sample that completes six consecutive symbols that are the same
	 * then we have seen a flag. 0x7E = 01111110
	 * 
	 * @param sample
	 * @return
	 */
	private boolean containsFlag(float sample) {
		boolean retVal = false;
		window.add(sample);
		if (window.size() >= samplesPerBaud)
		{
			window.remove(0);
			double s1200 = 0;
			double s2200 = 0;
		    double s1200_prev = 0;
		    double s1200_prev2 = 0;
		    double s2200_prev = 0;
		    double s2200_prev2 = 0;
		    for (int i = 0; i < window.size(); i ++)
		    {
		       s1200 = window.get(i)+coeff1200 * s1200_prev - s1200_prev2;
		       s1200_prev2 = s1200_prev;
		       s1200_prev = s1200;
		       s2200 = window.get(i)+coeff2200 * s2200_prev - s2200_prev2;
		       s2200_prev2 = s2200_prev;
		       s2200_prev = s2200;
		    }
		    double power = (s1200_prev2*s1200_prev2+s1200_prev*s1200_prev-coeff1200*s1200_prev*s1200_prev2) -
		    		(s2200_prev2*s2200_prev2+s2200_prev*s2200_prev-coeff2200*s2200_prev*s2200_prev2);
		    //System.out.println(power + "  ");
		    Frequency freq = power > 0 ? Frequency.f_1200 : Frequency.f_2200;
		    
		    if (Math.round(samplesThisFreq/samplesPerBaud) >=1 && freq != currentFreq)
		    {
		    	int bits = Math.round(samplesThisFreq / samplesPerBaud);
		    	samplesThisFreq = 0;
		    	currentFreq = freq;
		    	

				if (bits == 7) {
					retVal = true;
				}
		    }
		    samplesThisFreq++;
		}

		return retVal;
	}
}
