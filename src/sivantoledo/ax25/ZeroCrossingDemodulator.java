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

import java.util.ArrayList;

public class ZeroCrossingDemodulator
        extends PacketDemodulator //implements HalfduplexSoundcardClient 
{

    private float[] td_filter;
    private float[] cd_filter;
    private int rate_index;
    private int sample_rate;
    //private int samples_per_bit;
    private float samples_per_bit;
    
    private float samples_per_1200_zCrossing;
    private float samples_per_2200_zCrossing;
    
    //private float[] u1, u2, x, f0_cos, f0_sin, f1_cos, f1_sin;
    private float[] u1, x;
    private float[] c0_real, c0_imag, c1_real, c1_imag;
    private float[] diff;
    //private float[] fdiff;
    private float previous_fdiff;
    private int f0_i = 0, f1_i = 0;
    private int last_transition;
    private int data, bitcount;
    private int vox_countdown = 0;
    private float vox_threshold = 0.1f;
    private float phase_inc_f0, phase_inc_f1;
    private float phase_inc_symbol;
    private Packet packet; // received packet
    private PacketHandler handler;

    private static enum State {

        WAITING,
        JUST_SEEN_FLAG,
        DECODING
    };
    private State state = State.WAITING;
    //TransmitController transmit_controller;
    private int filter_index;
    private int emphasis;
    private boolean interpolate = false;
    private float interpolate_last;
    private boolean interpolate_original;
    /*
     * Diagnostic variables for estimating packet quality
     */
    private int f0_period_count, f1_period_count;
    private float f0_max, f1_min; // to collect average max, min in the filtered diff signal
    private float f0_current_max, f1_current_min;
    private float max_period_error;

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

    public ZeroCrossingDemodulator(int sample_rate, int filter_length) throws Exception {
        this(sample_rate, filter_length, 6, null);
    }

    public ZeroCrossingDemodulator(int sample_rate, int filter_length, int emphasis, PacketHandler h) throws Exception {
        super(sample_rate == 8000 ? 16000 : sample_rate);

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
        this.sample_rate = sample_rate;
        this.samples_per_bit = (float) sample_rate / 1200.0f;
        
        samples_per_1200_zCrossing = samples_per_bit/4.0f;
        samples_per_2200_zCrossing = (float) sample_rate /2200.0f/4.0f;
        
        
        
        System.err.printf("samples per bit = %.3f\n", this.samples_per_bit);

        float[][][] tdf;
        switch (emphasis) {
            case 0:
                tdf = Afsk1200Filters.time_domain_filter_none;
                break;
            case 6:
                tdf = Afsk1200Filters.time_domain_filter_full;
                break;
            default:
                System.err.printf("Filter for de-emphasis of %ddB is not availabe, using 6dB\n",
                        emphasis);
                tdf = Afsk1200Filters.time_domain_filter_full;
                break;
        }

        for (filter_index = 0; filter_index < tdf.length; filter_index++) {
            System.err.printf("Available filter length %d\n", tdf[filter_index][rate_index].length);
            if (filter_length == tdf[filter_index][rate_index].length) {
                System.err.printf("Using filter length %d\n", filter_length);
                break;
            }
        }
        if (filter_index == tdf.length) {
            filter_index = tdf.length - 1;
            System.err.printf("Filter length %d not supported, using length %d\n",
                    filter_length,
                    tdf[filter_index][rate_index].length);
        }

        td_filter = tdf[filter_index][rate_index];
        cd_filter = Afsk1200Filters.corr_diff_filter[filter_index][rate_index];


        x = new float[td_filter.length];
        u1 = new float[td_filter.length];

        c0_real = new float[(int) Math.floor(samples_per_bit)];
        c0_imag = new float[(int) Math.floor(samples_per_bit)];
        c1_real = new float[(int) Math.floor(samples_per_bit)];
        c1_imag = new float[(int) Math.floor(samples_per_bit)];

        diff = new float[cd_filter.length];

        phase_inc_f0 = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
        phase_inc_f1 = (float) (2.0 * Math.PI * 2200.0 / sample_rate);
        phase_inc_symbol = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
    }
    private volatile boolean data_carrier = false;

    public boolean dcd() {
        return data_carrier;
    }

    private float correlation(float[] x, float[] y, int j) {
        float c = (float) 0.0;
        for (int i = 0; i < x.length; i++) {
            c += x[j] * y[j];
            j--;
            if (j == -1) {
                j = x.length - 1;
            }
        }
        return c;
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
    private int j_td;   // time domain index 
    private int j_cd;   // time domain index 
    private int j_corr; // correlation index 
    private float phase_f0, phase_f1;
    private int t; // running sample counter
    private float f1cos, f1sin, f0cos, f0sin;
    private int flag_count = 0;
    private boolean flag_separator_seen = false; // to process the single-bit separation period between flags
    private int decode_count = 0;
    private boolean vox_state = false;
    
    private ArrayList<Float> samples = new ArrayList<Float> ();
    
    private int number_of_samples_since_zero_crossing;
    
    boolean is_1200_last_seen = false;
    
    private enum Freq {
		f_1200,
		f_2200;
    };
    
    private Freq last_freq_seen;

    protected void addSamplesPrivate(float[] s, int n) {
       
    	
    	int distance = 0;
    	
    	Freq freq;
    		
    	for(int i = 0 ;i<s.length; i++) {
    		samples.add(s[i]);
    		//calculate zero crossing distance in samples
    		distance = 0;
    		
    		if(distance > samples_per_1200_zCrossing) { //round down slightly
    			freq = Freq.f_1200;
    			
    		} else {
    			freq = Freq.f_2200;
    		}
    		
    		if(freq != last_freq_seen) {
    			
    		}
    		
    		
    	}
    	
    	
    	int i = 0;
        while (i < n) {
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

            u1[j_td] = sample;
            x[j_td] = Filter.filter(u1, j_td, td_filter);

            c0_real[j_corr] = x[j_td] * (float) Math.cos(phase_f0);
            c0_imag[j_corr] = x[j_td] * (float) Math.sin(phase_f0);

            c1_real[j_corr] = x[j_td] * (float) Math.cos(phase_f1);
            c1_imag[j_corr] = x[j_td] * (float) Math.sin(phase_f1);

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
                    f0_period_count++;
                    f0_max += f0_current_max;
                    double err = Math.abs(bits - ((double) p / (double) samples_per_bit));
                    if (err > max_period_error) {
                        max_period_error = (float) err;
                    }

                    // prepare for the period just starting now
                    f1_current_min = fdiff;
                } else {
                    f1_period_count++;
                    f1_min += f1_current_min;
                    double err = Math.abs(bits - ((double) p / (double) samples_per_bit));
                    if (err > max_period_error) {
                        max_period_error = (float) err;
                    }

                    // prepare for the period just starting now
                    f0_current_max = fdiff;
                }

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
                                    packet.statistics(new float[]{emphasis, f0_max / -f1_min, max_period_error});
                                    if (handler != null) {
                                        handler.handlePacket(packet.bytesWithoutCRC());
                                    } else {
                                        System.out.println("" + (++decode_count) + ": " + packet);
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

                            for (int k = 0; k < bits - 1; k++) { //only loop when multiple bits have been seen
                                bitcount++;
                                data >>= 1;
                                data += 128;
                                if (bitcount == 8) {
                                    if (packet == null) {
                                        packet = new Packet();
                                    }

                                    if (!packet.addByte((byte) data)) { //if the packet is too large
                                        state = State.WAITING;
                                        data_carrier = false;
                                        System.err.println("Packet too Large. Throwing out");
                                    }
                                    data = 0;
                                    bitcount = 0;
                                }
                            }
                            if (bits - 1 != 5) { // the zero after the ones is not a stuffing
                                bitcount++;
                                data >>= 1;
                                if (bitcount == 8) {
                                    if (packet == null) {
                                        packet = new Packet();
                                    }
                                    //if (data==0xAA) packet.terminate();
                                    if (!packet.addByte((byte) data)) {
                                        state = State.WAITING;
                                        data_carrier = false;
                                        System.err.println("Packet too Large. Throwing out");

                                    }
                                    data = 0;
                                    bitcount = 0;
                                }
                            }
                        }
                    }
                }
            }

            previous_fdiff = fdiff;

            t++;

            j_td++;
            if (j_td == td_filter.length) {
                j_td = 0;
            }

            j_cd++;
            if (j_cd == cd_filter.length) {
                j_cd = 0;
            }

            j_corr++;
            if (j_corr == c0_real.length /* samples_per_bit*/) {
                j_corr = 0;
            }

        }
    }
}
