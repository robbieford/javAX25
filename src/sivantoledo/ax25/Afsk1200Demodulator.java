/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * 
 * Copyright (C) Sivan Toledo, 2012
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

public class Afsk1200Demodulator extends PacketDemodulator // implements
// HalfduplexSoundcardClient
{
	/**
	 * Class name items...
	 */
	private final static String CORRELATION = "Correlation";
	public String getDemodulatorName() {
		return CORRELATION + "-w_emphasis_" + emphasis;
	}

	/***/
	private float[] cd_filter;
	private float samples_per_bit;
	private float[] c0_real, c0_imag, c1_real, c1_imag;
	private float[] diff;
	private float previous_fdiff;
	private int last_transition;
	private float phase_inc_f0, phase_inc_f1;
	private float max_period_error;

	public Afsk1200Demodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public Afsk1200Demodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate, filter_length, emphasis, h);
		
		cd_filter = Afsk1200Filters.corr_diff_filter[filter_index][rate_index];

        this.samples_per_bit = (float) sample_rate / 1200.0f;
		// System.out.printf("filter lengths are %d and %d\n",filter.length,cd_filter.length);

		c0_real = new float[(int) Math.floor(samples_per_bit)];
		c0_imag = new float[(int) Math.floor(samples_per_bit)];
		c1_real = new float[(int) Math.floor(samples_per_bit)];
		c1_imag = new float[(int) Math.floor(samples_per_bit)];

		diff = new float[cd_filter.length];

		phase_inc_f0 = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
		phase_inc_f1 = (float) (2.0 * Math.PI * 2200.0 / sample_rate);

		// System.out.printf("Size of symbol sync filter is %d\n",
		// symbol_sync_filter.length);
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
	private int t; // running sample counter

	protected void addSamplePrivate(float sample) {

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
			int p = t - last_transition;
			last_transition = t;

			int bits = (int) Math.round((double) p / (double) samples_per_bit);

			// collect statistics
			if (fdiff < 0) { // last period was high, meaning f0
				double err = Math.abs(bits
						- ((double) p / (double) samples_per_bit));
				if (err > max_period_error) {
					max_period_error = (float) err;
				}
			} else {
				double err = Math.abs(bits
						- ((double) p / (double) samples_per_bit));
				if (err > max_period_error) {
					max_period_error = (float) err;
				}
			}

			handleDemodulatedBits(bits);
		}

		previous_fdiff = fdiff;

		t++;

		j_cd++;
		if (j_cd == cd_filter.length) {
			j_cd = 0;
		}

		j_corr++;
		if (j_corr == c0_real.length /* samples_per_bit */) {
			j_corr = 0;
		}

	}
}
