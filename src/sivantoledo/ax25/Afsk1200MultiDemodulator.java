/*
 * Audio FSK modem for AX25 (1200 Baud, 1200/2200Hz).
 * This class combines two demodulators into one packet stream,
 * to handle both de-emphasized and flat (discriminator) audio.
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

//import java.util.Arrays;

public class Afsk1200MultiDemodulator extends PacketDemodulator {
	private final static String MULTI = "Multi";

	public String getDemodulatorName() {
		return MULTI;
	}

	private class InnerHandler implements PacketHandler {
		int d;

		public InnerHandler(int demodulator) {
			d = demodulator;
		}

		public void handlePacket(byte[] bytes) {
			Afsk1200MultiDemodulator.this.handlePacket(bytes, d);
		}

	}

	private byte[] last;
	private int last_demod;

	public void handlePacket(byte[] bytes, int d) {
		if (last != null && d != last_demod
				&& java.util.Arrays.equals(last, bytes)) {
		} else {

			if (d == 0) {
			} else {
			}
			last_demod = d;
			// System.out.println(""+packet_count);
			last = Arrays.copyOf(bytes, bytes.length);
			// last_sample_count = sample_count;

			if (h != null)
				h.handlePacket(bytes);
		}
		// System.err.printf("d0=%d d6=%d both=%d total=%d\n",d0_count,d6_count,both_count,packet_count);
	}

	private PacketHandler h;
	PacketDemodulator d0, d6;

	public Afsk1200MultiDemodulator(int sample_rate, PacketHandler h)
			throws Exception {
		super(sample_rate, h);
		// this.sample_rate = sample_rate;
		this.h = h;
		// max_sample_delay = (10 * 8 * sample_rate) / 1200; // a 10 byte delay
		d0 = new Afsk1200Demodulator(sample_rate, 1, 0, new InnerHandler(0));
		d6 = new Afsk1200Demodulator(sample_rate, 1, 6, new InnerHandler(6));
	}

	public boolean dcd() {
		return d6.dcd() || d0.dcd();
	}

	@Override
	protected void addSamplePrivate(float s) {
		d0.addSamplePrivate(s);
		d6.addSamplePrivate(s);
	}
}
