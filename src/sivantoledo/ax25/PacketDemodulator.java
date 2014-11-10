/*
 * 
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

import java.util.ArrayList;

import sivantoledo.soundcard.SoundcardConsumer;

public abstract class PacketDemodulator extends SoundcardConsumer {
	private static int DEBUG = 1;
	
	boolean filterData = false;
	float[] filter;
	ArrayList<Float> sampleBuffer;
	int rate_index, filter_index;

	public abstract String getDemodulatorName();

	public PacketDemodulator(int sample_rate, int filter_length, int emphasis, PacketHandler h)
	throws Exception
	{
		this(sample_rate, h);
		
		this.emphasis = emphasis;
		//Determine the rate index, this plays into how many coefficients will be needed.
		for (rate_index = 0; rate_index < Afsk1200Filters.sample_rates.length; rate_index++) {
			if (Afsk1200Filters.sample_rates[rate_index] == sample_rate) {
				break;
			}
		}
		if (rate_index == Afsk1200Filters.sample_rates.length) {
			throw new Exception("Sample rate " + sample_rate + " not supported");
		}

		//Get the filter set, there are two, one for an even bandpass filter, and another for
		//a 6db filter to add emphasis.
		float[][][] tdf = null;
		switch (emphasis) {
		case 0:
			tdf = Afsk1200Filters.time_domain_filter_none;
			filterData = true;
			break;
		case 6:
			tdf = Afsk1200Filters.time_domain_filter_full;
			filterData = true;
			break;
		default:
			if (DEBUG > 0)
				System.err
						.printf("Filter for de-emphasis of %ddB is not availabe, using no filtering",
								emphasis);
			filterData = false;
			break;
		}

		if(filterData)
		{
			for (filter_index = 0; filter_index < tdf.length; filter_index++) {
				if (filter_length == tdf[filter_index][rate_index].length) {
					//Found the filter length requested so lets use it.
					if (DEBUG > 0)
						System.err
								.printf("Using filter length %d\n", filter_length);
					break;
				}
			}
			//If the requested filter length is not supported use the longest one available.
			if (filter_index == tdf.length) {
				filter_index = tdf.length - 1;
				System.err.printf(
						"Filter length %d not supported, using max length length %d\n",
						filter_length, tdf[filter_index][rate_index].length);
			}
	
			filter = tdf[filter_index][rate_index];
			sampleBuffer = new ArrayList<Float>();
			for (int i = 0; i < filter.length; i++)
				sampleBuffer.add(0f);
		}
	}
	
	public PacketDemodulator(int sample_rate, PacketHandler h) {
		super(sample_rate == 8000 ? 16000 : sample_rate);

		if (sample_rate == 8000) {
			interpolate = true;
			sample_rate = 16000;
		}

		handler = h;
	}

	private boolean interpolate = false;
	private float interpolate_last;
	private boolean interpolate_original;

	protected void addSamplesPrivate(float[] s, int n) {
		int i = 0;
		while (i < n)
		{
			float sample;
			if (interpolate) {
				if (interpolate_original) {
					sample = s[i];
					interpolate_last = sample;
					interpolate_original = false;
					i++;
				} else {
					sample = 0.5f * (s[i] + interpolate_last);
					interpolate_original = true;
				}
			} else {
				sample = s[i];
				i++;
			}
			
			if(filterData)
			{
				sampleBuffer.add(0, sample); //Stick it up front
				sample = Filter.filter(sampleBuffer,  filter);
				sampleBuffer.remove(sampleBuffer.size() - 1); //Remove from the end.
			}
			
			addSamplePrivate(sample);
		}
	}

	abstract protected void addSamplePrivate(float s);

	public boolean dcd() {
		return data_carrier;
	}

	public enum Frequency {
		f_1200, f_2200
	}

	private enum State {
		WAITING, JUST_SEEN_FLAG, DECODING
	};

	// Packet Construction variables
	private int data, bitcount;
	private Packet packet; // received packet
	private PacketHandler handler;
	private State state = State.WAITING;
	protected int emphasis;
	private int flag_count = 0;
	private boolean flag_separator_seen = false; // to process the single-bit
													// separation period between
													// flags
	private int decode_count = 0;

	// Diagnostic variables for estimating packet quality
	private int f0_period_count, f1_period_count;
	private float f0_max, f1_min; // to collect average max, min in the filtered
	private float max_period_error;
	private boolean data_carrier;

	private void statisticsInit() {
		dcd();
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

	/**
	 * Once bits have been demodualted pass to this function to construct the
	 * packet.
	 * 
	 * @param bits
	 *            - number of bits at this symbol
	 */
	protected void handleDemodulatedBits(int bits) {
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
