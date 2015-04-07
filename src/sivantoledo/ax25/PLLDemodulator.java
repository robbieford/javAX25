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

public class PLLDemodulator extends PacketDemodulator // implements
															// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String PLL = "PLL";

	public String getDemodulatorName() {
		return PLL + pll_loop_gain + "_" + filtFreq + "-w_emphasis_" + emphasis;
	}

	private static final int DEBUG = -1;

	private BiQuadFilter biQuad;
	private float samplesPerBit;
    private Frequency currentFreq;
    private int samplesSinceLastTransition;
    private float pll_cf, pll_loop_gain, ref_sig;
    private double pll_integral;
    private boolean lastGreaterThanZero;
    private int sampleNum;
    private float filtFreq;

	public PLLDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public PLLDemodulator(int sample_rate, int filter_length, int emphasis,
			PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);
		pll_integral = 0;
		pll_cf = 1700;
		pll_loop_gain = 6f;//0.68f;
		ref_sig = 0;
		this.samplesPerBit = (float) sample_rate / 1200.0f;
		this.currentFreq = Frequency.f_1200;
		this.filtFreq = 475;
		biQuad = new BiQuadFilter(500, 48000, (float)(1/Math.sqrt(2)));
		sampleNum = 0;
	}

	public PLLDemodulator(int sample_rate, int filter_length, int emphasis,
			PacketHandler h, float gain, float filtFreq) throws Exception {
		this( sample_rate,  filter_length,  emphasis, h);
		
		biQuad = new BiQuadFilter(filtFreq, 48000, (float)(1/Math.sqrt(2)));
		pll_loop_gain = gain;
	    this.filtFreq = filtFreq;
	}

	protected void addSamplePrivate(float sample) {
		
		float t = ((float)sampleNum) / sample_rate;
		
		// BEGIN PLL block
		double pll_loop_control = sample * ref_sig * pll_loop_gain;
		double output = biQuad.filter(pll_loop_control);
		//System.out.println(ref_sig + "  " + output);
		pll_integral += pll_loop_control / sample_rate;
		ref_sig = (float) Math.sin(2 * Math.PI * pll_cf * (t + pll_integral));
		// END PLL block

		// output
		int bits = Math.round(samplesSinceLastTransition / samplesPerBit);
		if ((bits > 0) && ((output > 0 && !lastGreaterThanZero) || (output < 0 && lastGreaterThanZero))) {
			//System.out.println(bits + " " + output);
			handleDemodulatedBits(bits);
			samplesSinceLastTransition = 0;
		}
		
		lastGreaterThanZero = output > 0;
		sampleNum++;
		samplesSinceLastTransition++;
	}
	
	class BiQuadFilter {
		double a0, a1, a2, b0, b1, b2, x1, x2, y1, y2 = 0f;

		public BiQuadFilter(float freq, float sRate, float Q) {
			double omega = 2 * Math.PI * freq / sRate;
			double sn = Math.sin(omega);
			double cs = Math.cos(omega);
			double alpha = sn / (2 * Q);
			b0 = (1 - cs) / 2;
			b1 = 1 - cs;
			b2 = (1 - cs) / 2;
			a0 = 1 + alpha;
			a1 = -2 * cs;
			a2 = 1 - alpha;
			// prescale constants
			b0 /= a0;
			b1 /= a0;
			b2 /= a0;
			a1 /= a0;
			a2 /= a0;
		}
		
		public double filter(double x)
		{
			double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
		    x2 = x1;
		    x1 = x;
		    y2 = y1;
		    y1 = y;
			return y;
		}
	}
}
