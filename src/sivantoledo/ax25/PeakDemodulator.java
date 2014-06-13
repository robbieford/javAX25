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

public class PeakDemodulator
        extends PacketDemodulator //implements HalfduplexSoundcardClient 
{

	//Old Variables
    private float[] td_filter;
    private float[] cd_filter;
    private int rate_index;
    private int sample_rate;
    //private int samples_per_bit;
    
    //private float[] u1, u2, x, f0_cos, f0_sin, f1_cos, f1_sin;
    private float[] u1, x;
    private float[] c0_real, c0_imag, c1_real, c1_imag;
    private float[] diff;
    //private float[] fdiff;
    private float previous_fdiff;
    private int f0_i = 0, f1_i = 0;
    private int last_transition;
    private int data, bitcount;
    private float phase_inc_f0, phase_inc_f1;
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
    
    //Move Variables from in front of the add Samples Private Method
    private int j_td;   // time domain index 
    private int j_cd;   // time domain index 
    private int j_corr; // correlation index 
    private float phase_f0, phase_f1;
    private int t; // running sample counter
    private float f1cos, f1sin, f0cos, f0sin;
    private int flag_count = 0;
    private boolean flag_separator_seen = false; // to process the single-bit separation period between flags
    private int decode_count = 0;
    
    /*
     * Diagnostic variables for estimating packet quality
     */
    private int f0_period_count, f1_period_count;
    private float f0_max, f1_min; // to collect average max, min in the filtered diff signal
    private float f0_current_max, f1_current_min;
    private float max_period_error;

    //New Variables for Zero Crossing (migrate old ones here as we realize we need them
    //-----------------------------------------------------------------------------------vvv
    private static final int DEBUG = -1;
    //Structure Declarations
    private enum Freq {
		f_1200,
		f_2200;
    };
    
    //Variables
    private long samplesReceived;
    private float samplesPerBit;
    private boolean isFirstPeak;

    private static final float SAMPLE_BUFFER_AMOUNT = 4.0f;
    private float samplesPer1200Period;
    private float samplesPer2200Period;
    private int samplesSinceLastPeak;
    private int samplesSinceTransition;
    private int minimumZeroXingSamples;
    
    private Freq lastFrequencySeen;

    private float sampleLength;
    private ArrayList<Float> samples = new ArrayList<Float> ();
    private int samplesSinceLastFreqChange;
    private float sampleAverage;
    
    /*private static final int SAMPLES_BETWEEN_HISTORY_STATS_RECALC = 5;
    private static final int BIT_PERIODS_IN_HISTORY = 2;
    private int sampleHistoryLength;
    private int samplesSinceLastRecalc;
    //private float minValueInHistory;
    //private float maxValueInHistory;
    private float monotonicThreshold;
    private static final float ZERO_CROSSING_THRESHOLD_PERCENTAGE = 0.5f;
	private static final float MONOTONIC_THRESHOLD_PERCENTAGE = 0.05f;
    private float averageValueInHistory;
    private float zeroCrossingThreshold;
    private ArrayList<Float> sampleHistory = new ArrayList<Float> ();*/
    
    private int sampleHistoryLength;
    private float previousSample;
    private float secPreviousSample;
    private boolean isIncreasing = false;
    private float localPeak;
    private float localPeakSampleNum;
    private float prevLocalPeakSampleNum;
    private float prevSamplesBtPeaks;
    
    private enum Spacing {
    	FREQUENCY, TRANSITION
    };
    
    private Spacing prevSpacing;
    
    boolean isLastTran = true;
    
    //-----------------------------------------------------------------------------------^^^
    
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

    public PeakDemodulator(int sample_rate, int filter_length) throws Exception {
        this(sample_rate, filter_length, 6, null);
    }

    public PeakDemodulator(int sample_rate, int filter_length, int emphasis, PacketHandler h) throws Exception {
        super(sample_rate == 8000 ? 16000 : sample_rate);
    	
        this.sample_rate = sample_rate;
        this.samplesPerBit = (float) sample_rate / 1200.0f;
        
        sampleAverage = 0;
        samplesPer1200Period = samplesPerBit;
        samplesPer2200Period = (float) sample_rate /2200.0f;
        samplesSinceLastPeak = 0;
        //minValueInHistory = 0;
        //maxValueInHistory = 0;
        minimumZeroXingSamples = (int) (samplesPer2200Period - 1);
        previousSample = 0;
        secPreviousSample = 0;
//        sampleHistoryLength = (int)(Math.round(samplesPerBit)) * BIT_PERIODS_IN_HISTORY;
//        sampleLength = samplesPer2200ZeroXing / 2;
        samplesReceived = 0;
        isFirstPeak = true;
        samplesSinceTransition = 0;
        localPeakSampleNum = 0;
        localPeak = 0;
        prevLocalPeakSampleNum = 0;
        prevSamplesBtPeaks = 0;
        prevSpacing = Spacing.FREQUENCY;
        
        //End of new constructor code
    	

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

        c0_real = new float[(int) Math.floor(samplesPerBit)];
        c0_imag = new float[(int) Math.floor(samplesPerBit)];
        c1_real = new float[(int) Math.floor(samplesPerBit)];
        c1_imag = new float[(int) Math.floor(samplesPerBit)];

        diff = new float[cd_filter.length];

        phase_inc_f0 = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
        phase_inc_f1 = (float) (2.0 * Math.PI * 2200.0 / sample_rate);
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
    
    protected boolean isZeroCrossing(float firstSample, float secondSample) {
    	return ((firstSample <= 0 && secondSample > 0) || //Going from below zero to above OR
		(firstSample >= 0 && secondSample < 0)) ;			// Going from above zer to below
    }
    
    protected boolean isBelowZero(float sample) {
    	return sample <=0;
    }
    
    protected boolean isAboveZero(float sample) {
    	return sample >=0;
    }

    protected void addSamplesPrivate(float[] s, int n) {
    	
    	float sample;
    	
    	for (int i = 0; i < s.length; i ++) {
    		
    		samplesSinceLastPeak++;
    		samplesReceived++;
    		samplesSinceTransition++;
    		
    		sample = Math.abs(s[i]);
    		
    		sample = (sample + previousSample + secPreviousSample)/3.0f;
    		//sample = (s[i] + previousSample)/2.0f;// + secPreviousSample)/3.0f;
    		
    		if (DEBUG > 9) {
    			System.out.println("Sample number: " + samplesReceived + " /Value: " + s[i]);
    		}
    	
    		if(isIncreasing) {
    			if(sample > localPeak) {
    				localPeak = sample;
    				localPeakSampleNum = samplesReceived;
    			}
    			if((sample < previousSample) && (previousSample < secPreviousSample)) {
    				isIncreasing = false;
    				
    				if(DEBUG > 2) System.out.println("Local Peak at " + samplesReceived + " of " + localPeak + " Packets since last: " + samplesSinceLastPeak);
    				
    				handlePeakDetection();
    				prevLocalPeakSampleNum = localPeakSampleNum;
    				localPeak = -1000;
    				localPeakSampleNum = 0;
    				samplesSinceLastPeak=0;
    			}
    		} else {
    			if((sample > previousSample) && (previousSample > secPreviousSample)) {
    				isIncreasing = true;
    				localPeak = sample;
    				localPeakSampleNum = samplesSinceLastPeak;
    			}
    		}
    			
    		secPreviousSample = previousSample;
    		previousSample = sample;
    		
    	}
    }
    	
    private Spacing getCurrentSpacing() {
    	
    	float samplesBtPeaks = localPeakSampleNum - prevLocalPeakSampleNum;
    	
    	if(samplesBtPeaks < samplesPer2200Period + SAMPLE_BUFFER_AMOUNT || 
    			samplesBtPeaks > samplesPer1200Period - SAMPLE_BUFFER_AMOUNT	) {
    		return Spacing.FREQUENCY;
    	} else {
    		return Spacing.TRANSITION;
    	}
    	
    	
    }
    
    private void handlePeakDetection() {

    	Freq freq;

    	
    	
    	/*if(isFirstPeak) {
    		prevSamplesBtPeaks = samplesBtPeaks;
    		isFirstPeak = false;
    	}
    	
    	Spacing currentSpacing = getCurrentSpacing();
    	
    	if(currentSpacing == Spacing.TRANSITION) {
    	*/
    	
    	float samplesBtPeaks = localPeakSampleNum - prevLocalPeakSampleNum;
    	
    	if(isLastTran) prevSamplesBtPeaks = samplesBtPeaks;
    	
    	float diff = Math.abs(samplesBtPeaks - prevSamplesBtPeaks);
    	
    	//The last zero crossing is semi-recently then lets change to the processing stage...
		//Presumably we are in the decoding phase...
	    	/*if(diff > ((samplesPer2200Period + samplesPer1200Period)/2 - SAMPLE_BUFFER_AMOUNT)) { //round down slightly
	    		freq = Freq.f_1200;
	    	} else {
	    		freq = Freq.f_2200;
	    	}

    		if (DEBUG > 2) {
    			System.out.println("Frequency is:" + freq);
    		}*/
	
    	float test = samplesPer2200Period + samplesPer1200Period;
    	
    	//transition!
    	if(diff >= SAMPLE_BUFFER_AMOUNT){// && samplesSinceTransition > samplesPerBit/2.0f) {

    		isLastTran = true;
    		
    		int bits = (int) Math.round((double) samplesSinceTransition / (double) samplesPerBit);
    		
    		/*int bits = 0;
    		if(prevSpacing == Spacing.FREQUENCY && currentSpacing == Spacing.TRANSITION) {
    			bits = (int) Math.ceil((double) samplesSinceTransition / (double) samplesPerBit);
    		} else {
    			bits = (int) Math.round((double) samplesSinceTransition / (double) samplesPerBit);	
    		}*/
    		
    		
    		if (DEBUG > 1) {
    			System.out.println(samplesReceived + " " + bits + " -Transition Occurred. Prev Tran occurred " + samplesSinceTransition + " diff: " + diff);
    			//System.out.println(bits);
    		}
    		
    		samplesSinceTransition = 0;

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
                                
                                if (DEBUG > 0) {
                        			System.out.println("Last Sample Count of Packet: " + samplesReceived);
                        		}
                                
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
                                    if(DEBUG > 1) System.out.println("Created new Packet");
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
                                    if(DEBUG > 1) System.out.println("Created new Packet");
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
    	} else {
    		isLastTran = false;
    	}
    	samplesSinceLastPeak = 0;
    	prevSamplesBtPeaks = samplesBtPeaks;
    	//prevSpacing = currentSpacing;
    }
    
}
