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


public class GoertzelMaxClockingDemodulator extends PacketDemodulator // implements
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
	float ZERO_CROSSING_THRESHOLD;
	private float normalized1200Freq;
    private float normalized2200Freq;
    private float coeff1200;
    private float coeff2200;
    private int maxPacketLengthInSamples;
    private int minPacketLengthInSamples;
    private int samplesSinceLastProcess;

	public GoertzelMaxClockingDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public GoertzelMaxClockingDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

	    this.normalized1200Freq = 1200f/sample_rate;
	    this.normalized2200Freq = 2200f/sample_rate;
	    this.coeff1200 = (float) (2*Math.cos(2*Math.PI*normalized1200Freq));
	    this.coeff2200 = (float) (2*Math.cos(2*Math.PI*normalized2200Freq));
		sampleArray = new ArrayList<Float>();

		this.samplesPerBaud = (float) sample_rate / 1200.0f;
	    this.maxPacketLengthInSamples = Math.round(samplesPerBaud) * MAXIMUM_PACKET_BYTES * 8;
	    this.minPacketLengthInSamples = Math.round(samplesPerBaud) * MINIMUM_PACKET_BYTES * 8;
	    samplesSinceLastProcess = 0;
	}

	/**
	 * 1.) Filter the data with the "emphasis" filter 2.) Capture a whole packet
	 * (look for flags) 3.) Post process the captured packet... a.) Derivative
	 * to find vvv (Removes DC bias problem and emphasis problem) b.) Zero
	 * crossing to see transitions c.) Figure out which sample corresponds to
	 * clocking d.) Use clocking to do correlation. (per baud)
	 */

	private ArrayList<Float> sampleArray;
	private static final int MINIMUM_PACKET_BYTES = 16;
	private static final int MAXIMUM_PACKET_BYTES = 256; // MTU for AX25
	protected void addSamplePrivate(float sample) {
		sampleArray.add(sample);
		samplesSinceLastProcess++;

		if (sampleArray.size() > maxPacketLengthInSamples)
		{
			if(samplesSinceLastProcess > minPacketLengthInSamples)
			{
				processPacket(new ArrayList<Float>(sampleArray));
				samplesSinceLastProcess = 0;
			}
			sampleArray.remove(0);
		}
	}

	
	/**
	 * Once we have enough data actually try and demodulate the packet using this overly
	 * complicated method.
	 */
	private void processPacket(ArrayList<Float> samples) {
		ArrayList<Frequency> frequencySymbolList;
		for (int i = 0; i <= Math.round(samplesPerBaud); i ++)
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
//				System.out.println(bauds + "," + lastFreq);
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
			Frequency freq = getFrequency(i, i + samplesPerBaud, sampleData);
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
}
