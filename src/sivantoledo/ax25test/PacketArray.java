package sivantoledo.ax25test;

import java.util.ArrayList;
import java.util.Arrays;

public class PacketArray extends ArrayList<byte[]> {

	public int packet_count = 0;
	public int dupCount = 0;
	
	/**
	 * Randomly Generated
	 */
	private static final long serialVersionUID = -5932719560958109010L;
	private ArrayList<byte[]> lastPackets = new ArrayList<byte[]>();
	
	@Override
	public boolean contains(Object other) {
		if (other instanceof byte[]){
			byte[] otherPacket = (byte[]) other;
			for (byte[] packet : this) {
				if (Arrays.equals(packet, otherPacket))
				{
					return true;
				}
			}
		} else {
			return false;
		}
		
		return false;
	}

	public boolean addWithReturn(byte[] packet) {

		if (this.contains(packet) ) {
			dupCount++;
			return false;
		} else {
			packet_count++;
			this.add(packet);
			return true;
		}
	}
}
