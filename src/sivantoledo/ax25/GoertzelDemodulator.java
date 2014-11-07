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
	private final static String ZEROCROSSING = "ZeroCrossing";

	public String getDemodulatorName() {
		return ZEROCROSSING + "-w_emphasis_" + emphasis;
	}

	/***/

	// Old Variables
	private float[] td_filter;
	private float[] cd_filter;
	private int rate_index;
	private int sample_rate;

	// private float[] u1, u2, x, f0_cos, f0_sin, f1_cos, f1_sin;
	private float[] u1, x;
	private float[] c0_real, c0_imag, c1_real, c1_imag;
	private float[] diff;
	private float previous_fdiff;
	private int f0_i = 0, f1_i = 0;
	private int last_transition;
	private int data, bitcount;
	private float phase_inc_f0, phase_inc_f1;
	private Packet packet; // received packet
	private PacketHandler handler;

	private static enum State {
		WAITING, JUST_SEEN_FLAG, DECODING
	};

	private State state = State.WAITING;
	private int filter_index;
	private int emphasis;
	private boolean interpolate = false;
	private float interpolate_last;
	private boolean interpolate_original;

	// Move Variables from in front of the add Samples Private Method
	private int j_td; // time domain index
	private int j_cd; // time domain index
	private int j_corr; // correlation index
	private float phase_f0, phase_f1;
	private int t; // running sample counter
	private float f1cos, f1sin, f0cos, f0sin;
	private int flag_count = 0;
	private boolean flag_separator_seen = false; // to process the single-bit
													// separation period between
													// flags
	private int decode_count = 0;

	/*
	 * Diagnostic variables for estimating packet quality
	 */
	private int f0_period_count, f1_period_count;
	private float f0_max, f1_min; // to collect average max, min in the filtered
									// diff signal
	private float f0_current_max, f1_current_min;
	private float max_period_error;

	// New Variables for Zero Crossing (migrate old ones here as we realize we
	// need them
	// -----------------------------------------------------------------------------------vvv
	private static final int DEBUG = -1;

	// Structure Declarations
	private enum Freq {
		f_1200, f_2200;
	};

	// Variables
	private long samplesReceived;
	private float samplesPerBit;
	private ArrayList<Float> window;
	private int windowSize;
    private float normalized1200Freq;
    private float normalized2200Freq;
    private double coeff1200;
    private double coeff2200;
    private Frequency currentFreq;
    private int samplesThisFreq;

	// -----------------------------------------------------------------------------------^^^

	private void statisticsInit() {
		f0_period_count = 0;
		f1_period_count = 0;
		f0_max = 0.0f;
		f1_min = 0.0f;
		max_period_error = 0.0f;
	}

	private void statisticsFinalize() {
		f0_max = f0_max / f0_period_count;
		f1_min = f1_min / f1_period_count;
	}

	public GoertzelDemodulator(int sample_rate, int filter_length)
			throws Exception {
		this(sample_rate, filter_length, 6, null);
	}

	public GoertzelDemodulator(int sample_rate, int filter_length,
			int emphasis, PacketHandler h) throws Exception {
		super(sample_rate == 8000 ? 16000 : sample_rate);

		this.sample_rate = sample_rate;
		this.samplesPerBit = (float) sample_rate / 1200.0f;
		this.windowSize = (int)Math.round(Math.floor(samplesPerBit));
		window = new ArrayList<Float>(windowSize);
	    this.normalized1200Freq = 1200f/sample_rate;
	    this.normalized2200Freq = 2200f/sample_rate;
	    this.coeff1200 = 2*Math.cos(2*Math.PI*normalized1200Freq);
	    this.coeff2200 = 2*Math.cos(2*Math.PI*normalized2200Freq);
	    this.currentFreq = Frequency.f_1200;

		// End of new constructor code

		if (sample_rate == 8000) {
			interpolate = true;
			sample_rate = 16000;
		}

		this.emphasis = emphasis;
		for (rate_index = 0; rate_index < Afsk1200Filters.sample_rates.length; rate_index++) {
			if (Afsk1200Filters.sample_rates[rate_index] == sample_rate) {
				break;
			}
		}
		if (rate_index == Afsk1200Filters.sample_rates.length) {
			throw new Exception("Sample rate " + sample_rate + " not supported");
		}

		handler = h;

		if (DEBUG > 0)
			System.err.printf("samples per bit = %.3f\n", this.samplesPerBit);

		float[][][] tdf;
		switch (emphasis) {
		case 0:
			tdf = Afsk1200Filters.time_domain_filter_none;
			break;
		case 6:
			tdf = Afsk1200Filters.time_domain_filter_full;
			break;
		default:
			if (DEBUG > 0)
				System.err
						.printf("Filter for de-emphasis of %ddB is not availabe, using 6dB\n",
								emphasis);
			tdf = Afsk1200Filters.time_domain_filter_full;
			break;
		}

		for (filter_index = 0; filter_index < tdf.length; filter_index++) {
			if (DEBUG > 0)
				System.err.printf("Available filter length %d\n",
						tdf[filter_index][rate_index].length);
			if (filter_length == tdf[filter_index][rate_index].length) {
				if (DEBUG > 0)
					System.err
							.printf("Using filter length %d\n", filter_length);
				break;
			}
		}

		if (filter_index == tdf.length) {
			filter_index = tdf.length - 1;
			System.err.printf(
					"Filter length %d not supported, using length %d\n",
					filter_length, tdf[filter_index][rate_index].length);
		}

		td_filter = tdf[filter_index][rate_index];
		cd_filter = Afsk1200Filters.corr_diff_filter[filter_index][rate_index];

		x = new float[td_filter.length];
		u1 = new float[td_filter.length];

		diff = new float[cd_filter.length];
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

	protected void addSamplesPrivate(float[] s, int n) {

		for (int i = 0; i < s.length; i++) {
			addSamplePrivate(s[i]);
		}
	}

	private void addSamplePrivate(float sample)
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
	
	private void handleDemodulatedBits(int bits) {

		if (bits == 0 || bits > 7) {
			state = State.WAITING;
			data_carrier = false;
			flag_count = 0;
		} else {
			if (bits == 7) {
				flag_count++;
				flag_separator_seen = false;

				data = 0;
				bitcount = 0;
				switch (state) {
				case WAITING:
					state = State.JUST_SEEN_FLAG;
					data_carrier = true;

					statisticsInit(); // start measuring a new packet
					break;
				case JUST_SEEN_FLAG:
					break;
				case DECODING:
					if (packet != null && packet.terminate()) {
						statisticsFinalize();
						packet.statistics(new float[] { emphasis,
								f0_max / -f1_min, max_period_error });

						if (DEBUG > 0) {
							System.out.println("Last Sample Count of Packet: "
									+ samplesReceived);
						}

						if (handler != null) {
							handler.handlePacket(packet.bytesWithoutCRC());
						} else {
							System.out.println("" + (++decode_count) + ": "
									+ packet);
						}
					}
					packet = null;
					state = State.JUST_SEEN_FLAG;
					break;
				}
			} else {
				switch (state) {
				case WAITING:
					break;
				case JUST_SEEN_FLAG:
					state = State.DECODING;
					break;
				case DECODING:
					break;
				}
				if (state == State.DECODING) {
					if (bits != 1) {
						flag_count = 0;
					} else {
						if (flag_count > 0 && !flag_separator_seen) {
							flag_separator_seen = true;
						} else {
							flag_count = 0;
						}
					}

					for (int k = 0; k < bits - 1; k++) { // only loop when
															// multiple bits
															// have been seen
						bitcount++;
						data >>= 1;
						data += 128;
						if (bitcount == 8) {
							if (packet == null) {
								packet = new Packet();
							}

							if (!packet.addByte((byte) data)) { // if the packet
																// is too large
								state = State.WAITING;
								data_carrier = false;
								System.err
										.println("Packet too Large. Throwing out");
							}
							data = 0;
							bitcount = 0;
						}
					}
					if (bits - 1 != 5) { // the zero after the ones is not a
											// stuffing
						bitcount++;
						data >>= 1;
						if (bitcount == 8) {
							if (packet == null) {
								packet = new Packet();
							}
							// if (data==0xAA) packet.terminate();
							if (!packet.addByte((byte) data)) {
								state = State.WAITING;
								data_carrier = false;
								System.err
										.println("Packet too Large. Throwing out");

							}
							data = 0;
							bitcount = 0;
						}
					}
				}
			}
		}
	}
}
