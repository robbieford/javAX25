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

public class GoertzelDemodulator extends PacketDemodulator // implements
															// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String GOERTZEL = "Goertzel";

	public String getDemodulatorName() {
		return GOERTZEL + "-w_emphasis_" + emphasis;
	}

	private static final int DEBUG = -1;

	private float samplesPerBit;
	private ArrayList<Float> window;
	private int windowSize;
    private float normalized1200Freq;
    private float normalized2200Freq;
    private double coeff1200;
    private double coeff2200;
    private Frequency currentFreq;
    private int samplesThisFreq;


	public GoertzelDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public GoertzelDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);

		this.samplesPerBit = (float) sample_rate / 1200.0f;
		this.windowSize = (int)Math.round(Math.floor(samplesPerBit));
		window = new ArrayList<Float>(windowSize);
	    this.normalized1200Freq = 1200f/sample_rate;
	    this.normalized2200Freq = 2200f/sample_rate;
	    this.coeff1200 = 2*Math.cos(2*Math.PI*normalized1200Freq);
	    this.coeff2200 = 2*Math.cos(2*Math.PI*normalized2200Freq);
	    this.currentFreq = Frequency.f_1200;
	}

	protected void addSamplePrivate(float sample)
	{
		window.add(sample);
		if (window.size() >= windowSize)
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
		    
		    if (Math.round(samplesThisFreq/samplesPerBit) >=1 && freq != currentFreq)
		    {
		    	handleDemodulatedBits(Math.round(samplesThisFreq / samplesPerBit));
		    	samplesThisFreq = 0;
		    	currentFreq = freq;
		    }
		    samplesThisFreq++;
		}
	}
}
