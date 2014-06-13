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

public class PreClockingDemodulator
        extends PacketDemodulator //implements HalfduplexSoundcardClient 
{

    public enum Frequency {
    	f_1200,
    	f_2200
	}
    
	private float[] td_filter;
    private float[] cd_filter;
    private int rate_index;
    private int sampleRate;
    //private int samples_per_bit;
    private float samplesPerBaud;
    //private float[] u1, u2, x, f0_cos, f0_sin, f1_cos, f1_sin;
    private float[] u1, x;
    private float[] c0_real, c0_imag, c1_real, c1_imag;
    private float[] diff;
    //private float[] fdiff;
    private float previous_fdiff;
    private int f0_i = 0, f1_i = 0;
    private long last_transition;
    private int data, bitcount;
    private int vox_countdown = 0;
    private float vox_threshold = 0.1f;
    private float phase_inc_f0, phase_inc_f1;
    private float phase_inc_symbol;
    private Packet packet; // received packet
    private PacketHandler handler;
    private int samplesInFlag;
    
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
    float ZERO_CROSSING_THRESHOLD;

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
        //System.out.printf("%ddB avg(f0_extremes)/avg(f1_extremes) = %.2f period_rel_err = %.02f\n",
        //			          emphasis,f0_max / -f1_min, max_period_error);
    }

    public PreClockingDemodulator(int sample_rate, int filter_length) throws Exception {
        this(sample_rate, filter_length, 6, null);
    }

    public PreClockingDemodulator(int sample_rate, int filter_length, int emphasis, PacketHandler h) throws Exception {
        super(sample_rate == 8000 ? 16000 : sample_rate);
        
        filteredSampleArray = new ArrayList<Float>(); //Needs to big enough for the filter

        if (sample_rate == 8000) {
            interpolate = true;
            sample_rate = 16000;
        }

        this.emphasis = emphasis;
        //transmit_controller = c;
        for (rate_index = 0; rate_index < Afsk1200Filters.sample_rates.length; rate_index++) {
            if (Afsk1200Filters.sample_rates[rate_index] == sample_rate) {
                break;
            }
        }
        if (rate_index == Afsk1200Filters.sample_rates.length) {
            throw new Exception("Sample rate " + sample_rate + " not supported");
        }

        handler = h;
        this.sampleRate = sample_rate;
        this.samplesPerBaud = (float) sample_rate / 1200.0f;
        samplesInFlag = Math.round(samplesPerBaud * FLAG_LENGTH_IN_BAUD);
        System.err.printf("samples per bit = %.3f\n", this.samplesPerBaud);
        //this.samples_per_bit = Afsk1200Filters.bit_periods[rate_index]; // this needs to be computed locally

        //if (samples_per_bit * 1200 != sample_rate) {
        //	throw new Exception("Sample rate must be divisible by 1200");
        //}
        //System.out.printf("%d samples per bit\n",samples_per_bit);
        //x      = new float[samples_per_bit];    
        //u1     = new float[samples_per_bit];    
        //u2     = new float[samples_per_bit];  

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

        //System.out.printf("filter lengths are %d and %d\n",td_filter.length,cd_filter.length);

        x = new float[td_filter.length];
        u1 = new float[td_filter.length];
        //u2     = new float[samples_per_bit];    

        //f0_cos = new float[samples_per_bit];    // f0=1200Hz so we have exactly one cycle.
        //f0_sin = new float[samples_per_bit];
        //f1_cos = new float[6*samples_per_bit]; // f0=2200Hz so 11 cycles fit exactly in 6 bit periods.
        //f1_sin = new float[6*samples_per_bit]; // otherwise we would have had to compute sin/cos

        c0_real = new float[(int) Math.floor(samplesPerBaud)];
        c0_imag = new float[(int) Math.floor(samplesPerBaud)];
        c1_real = new float[(int) Math.floor(samplesPerBaud)];
        c1_imag = new float[(int) Math.floor(samplesPerBaud)];

        //diff    = new float[samples_per_bit];
        //fdiff   = new float[samples_per_bit];
        diff = new float[cd_filter.length];
        //fdiff   = new float[corr_diff_filter[rate_index].length]; // can be length 2

        phase_inc_f0 = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
        phase_inc_f1 = (float) (2.0 * Math.PI * 2200.0 / sample_rate);
        phase_inc_symbol = (float) (2.0 * Math.PI * 1200.0 / sample_rate);
        //time_inc = (float) (2.0*Math.PI*i/sample_rate);

        //for (int i=0; i<6*samples_per_bit; i++) {
        //	float time = (float) (2.0*Math.PI*i/sample_rate);
        //	f1_cos[i] = (float) Math.cos(2200.0*time);
        //	f1_sin[i] = (float) Math.sin(2200.0*time);
        //	if (i>=samples_per_bit) continue;
        //	f0_cos[i] = (float) Math.cos(1200.0*time);
        //	f0_sin[i] = (float) Math.sin(1200.0*time);
        //}

        //System.out.printf("Size of symbol sync filter is %d\n", symbol_sync_filter.length);

        sampleArray = new ArrayList<Float>(td_filter.length);
        for (int i= 0; i < td_filter.length; i++) {
        	sampleArray.add(0.0f);
        }
        
        ZERO_CROSSING_THRESHOLD = sampleRate/2200.0f/4.0f;
        
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
    //private int j;    // sample index, rolls over each bit period 
    //private int j_f1; // sample index
    private long t; // running sample counter
    private float f1cos, f1sin, f0cos, f0sin;
    //public void addSamples(float[] s) {
    //	addSamples(s,s.length);
    //}
    private int flag_count = 0;
    private boolean flag_separator_seen = false; // to process the single-bit separation period between flags
    private int decode_count = 0;
    private boolean vox_state = false;

    /**
     * 1.) Filter the data with the "emphasis" filter
     * 2.) Capture a whole packet (look for flags)
     * 3.) Post process the captured packet...
     * 	a.) Derivative to find vvv (Removes DC bias problem and emphasis problem)
     * 	b.) Zero crossing to see transitions
     * 	c.) Figure out which sample corresponds to clocking
     * 	d.) Use clocking to do correlation. (per baud)
     */
    
	/**
	 * Add sample to filter buffer and filter the sample
	 * Start looking for flags...
	 *	If flag found store the location and look at distance since last flag
	 * If flags 
	 */
	
    private ArrayList<Float> sampleArray; //Needs to big enough for the filter
    private ArrayList<Float> filteredSampleArray; //Slightly bigger than max packet
    private static final int MINIMUM_PACKET_BYTES = 17;
    private static final int MAXIMUM_PACKET_BYTES = 256; //MTU for AX25
	private static final int FLAG_LENGTH_IN_BAUD = 9; //0x7E
    
    protected void addSamplesPrivate(float[] s, int n) {
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
                    sample = (s[i] + interpolate_last) / 2.0f;
                    interpolate_original = true;
                }
            } else {
                sample = s[i];
                i++;
            }
            
            addSamplePrivate(sample);
        }
    }
    
    private boolean containsStartFlag = false;
    
    private static final int BUFFER_SAMPLES_AFTER_FLAG = 10;
    
	protected void addSamplePrivate(float sample) {
		//Add sample to filter input array
		sampleArray.add(0, sample);
		sampleArray.remove(sampleArray.size() -1);

		//Filter
		float filteredSample = Filter.filter(sampleArray, td_filter);
		//sample store filtered sample
		/**
		 * _______________________________________________________
		 * WE ARENT FILTERING ANYTHING!!!!!!! 
		 * _______________________________________________________
		 */
		//filteredSampleArray.add(sample);
		filteredSampleArray.add(filteredSample);
		
		//Look for flags in samples
		boolean flagInSamples = containsFlag(filteredSample);
		if (flagInSamples) {
			//something
			//System.out.println("Saw a flag! " + t);
			if (containsStartFlag) {
				int bytesBetweenFlags =(int) ((filteredSampleArray.size() - samplesInFlag*2 - BUFFER_SAMPLES_AFTER_FLAG)/samplesPerBaud)/8;
				
				if(bytesBetweenFlags >= MINIMUM_PACKET_BYTES && bytesBetweenFlags <= MAXIMUM_PACKET_BYTES ) {
//					System.out.println("Number of Potential Packtes: " + ++numPotentialPackets + " with size: " + bytesBetweenFlags);
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
		//Look in area since previous flag
		//Flag handling logic
		ArrayList<Float> packetSamples;
		//processPacket(packetSamples);
	}
	
	int numPotentialPackets = 0;
	
	private void processPacket() {
		ArrayList<Float> derivativeOfSamples = getDerivativeOfFilteredData();
		ArrayList<Float> derivativeCrossingPositions = calulateZeroCrossings(derivativeOfSamples);
		ArrayList<Float> frequenciesFromDerivative = getFrequenciesSampleList(derivativeCrossingPositions);
		ArrayList<Float> detectedFreqTransitions = getTransitionsFromFreqData(frequenciesFromDerivative);
		int combOffset = getCombIndex(detectedFreqTransitions);
		ArrayList<Frequency> frequencySymbolList = getFrequencySymbolList(combOffset, frequenciesFromDerivative, derivativeCrossingPositions);
		generatePacket(frequencySymbolList);
	}
	
	private void generatePacket(ArrayList<Frequency> frequencySymbolList) {
		Frequency lastFreq = frequencySymbolList.get(0);
		int bauds = 0;
		
		for (int i = 0; i < frequencySymbolList.size(); i++) {
			bauds++;
			if (frequencySymbolList.get(i) != lastFreq) {
//				System.out.println(bauds);// + " " + i);
				//if(bauds == 8) bauds =7;
				handleDemodulatedBits(bauds);
				bauds = 0;
				lastFreq = frequencySymbolList.get(i);
			}
		}
	}

	private ArrayList<Frequency> getFrequencySymbolList(int combOffset, ArrayList<Float> frequenciesFromDerivative, ArrayList<Float> derivativeCrossingPositions) {
		ArrayList<Frequency> frequencies = new ArrayList<Frequency>();
		
		//Handle First
		//frequencies.add(getFrequency(0, combOffset, frequenciesFromDerivative, derivativeCrossingPositions));
		
		for (Float i = new Float(combOffset); i < filteredSampleArray.size(); i+=samplesPerBaud) {
			Frequency freq = getFrequency(i + 1, i+samplesPerBaud, frequenciesFromDerivative, derivativeCrossingPositions);
			if (freq != null){
				frequencies.add(freq);
			}
		}
		
		return frequencies;
	}

	private Frequency getFrequency(float start, float end, ArrayList<Float> frequenciesFromDerivative,ArrayList<Float> derivativeCrossingPositions) {
		int startIdx=-1, endIdx=-1;
		float sum = 0.0f;
		
		for (int i = 0; i < derivativeCrossingPositions.size(); i ++) {
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
		
		Frequency freq = sum / (endIdx + 1 - startIdx) > 1700 ? Frequency.f_2200: Frequency.f_1200;
		//Frequency freq =  frequenciesFromDerivative.get((endIdx + startIdx) /2) > 1850 ? Frequency.f_2200: Frequency.f_1200;
		//System.out.println(freq + " actual value: " + frequenciesFromDerivative.get((endIdx + startIdx+1) /2)  + " num of freqs in avg: " + (endIdx + 1 - startIdx));
		//System.out.println(frequenciesFromDerivative.get((endIdx + startIdx) /2));
//		System.out.println(sum / (endIdx + 1 - startIdx));
		return freq;
	}

	private int getCombIndex(ArrayList<Float> detectedFreqTransitions) {
		long minimumSquaredDistance = -1;
		int sampleCombPosition = 0;
		
		ArrayList<Integer> modulusValues = new ArrayList<Integer>(detectedFreqTransitions.size());
		
		for (int i = 0; i < detectedFreqTransitions.size(); i ++) {
			modulusValues.add(Math.round(detectedFreqTransitions.get(i)) % Math.round(samplesPerBaud));
		}
		
		for (int i = 0; i < samplesPerBaud; i ++) {
			long squaredDistance = 0;
			for (int j = 0; j < detectedFreqTransitions.size(); j++){
				squaredDistance += Math.pow(modulusValues.get(j) - i, 2);
			}
			if (minimumSquaredDistance == -1 || minimumSquaredDistance > squaredDistance) {
				minimumSquaredDistance = squaredDistance;
				sampleCombPosition = i;
			}
		}
		
		return sampleCombPosition;
	}

	private ArrayList<Float> getTransitionsFromFreqData(ArrayList<Float> freqs) {
		ArrayList<Float> sampleNumbers = new ArrayList<Float>();
		
		for (int i = 1; i < freqs.size(); i ++){
			if (isZeroCrossing(freqs.get(i - 1) - 1700.0f, freqs.get(i) - 1700.0f)) {
				sampleNumbers.add(i - 1 + zeroCrossingPercentage(freqs.get(i-1) - 1700.0f, freqs.get(i) - 1700.0f));
			}
		}
		
		return sampleNumbers;
	}

	private ArrayList<Float> getFrequenciesSampleList(ArrayList<Float> data) {
		ArrayList<Float> freqs = new ArrayList<Float>();
		
		for (int i = 1; i < data.size(); i++){
			freqs.add(sampleRate / (data.get(i) - data.get(i-1)) / 2.0f);
		}
		
		return freqs;
	}

	private ArrayList<Float> calulateZeroCrossings(ArrayList<Float> samples) {
//		float ZERO_CROSSING_THRESHOLD = 5f;
		int sinceLastCrossing = Math.round(samplesPerBaud);
		ArrayList<Float> crossingList = new ArrayList<Float>();
		
		for (int i = 1; i < samples.size(); i++) {
			sinceLastCrossing++;
			if (sinceLastCrossing > ZERO_CROSSING_THRESHOLD && isZeroCrossing(samples.get(i-1), samples.get(i))){
				crossingList.add(i - 1 + zeroCrossingPercentage(samples.get(i-1), samples.get(i)));
				sinceLastCrossing = 0;
			}
		}
		
		return crossingList;
	}
	
    protected boolean isZeroCrossing(float firstSample, float secondSample) {
    	return ((firstSample <= 0 && secondSample > 0) || //Going from below zero to above OR
		(firstSample >= 0 && secondSample < 0)) ;			// Going from above zer to below
    }
    
    protected float zeroCrossingPercentage(float firstSample, float secondSample) {
    	//x = x1 + (x2  - x1) * ((0-y1)/(y2-y1))
    	return (-firstSample /(secondSample - firstSample));
    }

	private ArrayList<Float> getDerivativeOfFilteredData() {
		ArrayList<Float> derivative = new ArrayList<Float>(filteredSampleArray.size());
		
		for (int i = 1; i < filteredSampleArray.size() - 1; i++){
			derivative.add(filteredSampleArray.get(i+1) - filteredSampleArray.get(i-1));
			if (derivative.size() == 1){
				derivative.add(filteredSampleArray.get(i+1) - filteredSampleArray.get(i-1));
			}
		}
		derivative.add(derivative.get(derivative.size() - 1));
		
		return derivative;
	}
	
	private void shrinkFilteredSampleArray() {
		int index = filteredSampleArray.size() - 1 - samplesInFlag*2 - BUFFER_SAMPLES_AFTER_FLAG;
		if(index < 0 ) index = 0;
		filteredSampleArray = new ArrayList<Float> (filteredSampleArray.subList(index,
				filteredSampleArray.size() - 1));
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
			int p = (int)(t - last_transition);
			last_transition = t;

			int bits = (int) Math.round((double) p / (double) samplesPerBaud);
			//System.out.printf("$ %f %d\n",(double) p / (double)samples_per_bit,bits);

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
		if (j_corr == c0_real.length ) {// samples_per_bit
			j_corr = 0;
		}
		
		return retVal;
	}
	
	/**
    protected void addSamplePrivate(float sample) {
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
            //System.out.printf("$ %f %d\n",(double) p / (double)samples_per_bit,bits);

            // collect statistics
            if (fdiff < 0) { // last period was high, meaning f0
                f0_period_count++;
                f0_max += f0_current_max;
                double err = Math.abs(bits - ((double) p / (double) samples_per_bit));
                //System.out.printf(")) %.02f %d %.02f\n",(double) p / (double)samples_per_bit,bits,err);
                if (err > max_period_error) {
                    max_period_error = (float) err;
                }

                // prepare for the period just starting now
                f1_current_min = fdiff;
            } else {
                f1_period_count++;
                f1_min += f1_current_min;
                double err = Math.abs(bits - ((double) p / (double) samples_per_bit));
                //System.out.printf(")) %.02f %d %.02f\n",(double) p / (double)samples_per_bit,bits,err);
                if (err > max_period_error) {
                    max_period_error = (float) err;
                }

                // prepare for the period just starting now
                f0_current_max = fdiff;
            }

            handleDemodulatedBits(bits);
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
        if (j_corr == c0_real.length ) {// samples_per_bit
            j_corr = 0;
        }
    }
	*/
    
    private void handleDemodulatedBits(int bits) {
    	//System.out.println(bits);
        if (bits == 0 || bits > 7) {
            state = State.WAITING;
            data_carrier = false;
            flag_count = 0;
        } else {
            if (bits == 7) {
                flag_count++;
                flag_separator_seen = false;
                //System.out.printf("Seen %d flags in a row\n",flag_count);

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
                    	
                    	if (packet != null) {
                    		try {
                    			System.out.println("+++++++" + packet.toString());
                    		} catch (Exception e) {
                    			
                    		}
                    	}
                        
                    	if (packet != null && packet.terminate()) {
                            statisticsFinalize();
                            packet.statistics(new float[]{emphasis, f0_max / -f1_min, max_period_error});
                            //System.out.print(String.format("%ddB:%.02f:%.02f\n", 
                            //			              emphasis,f0_max/-f1_min,max_period_error));
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
                                System.out.println("New Packet");
                            }
                            //if (data==0xAA) packet.terminate();

                            if (!packet.addByte((byte) data)) { //if the packet is too large
                                state = State.WAITING;
                                data_carrier = false;
                                System.err.println("Packet too Large. Throwing out");
                            }
                            //System.out.printf(">>> %02x %c %c\n", data, (char)data, (char)(data>>1));
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
                            //System.out.printf(">>> %02x %c %c\n", data, (char)data, (char)(data>>1));
                            data = 0;
                            bitcount = 0;
                        }
                    }
                }
            }
        }
    	
    }
}
