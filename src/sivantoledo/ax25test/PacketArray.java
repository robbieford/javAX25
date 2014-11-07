package sivantoledo.ax25test;

import java.util.ArrayList;

public class PacketArray extends ArrayList<byte[]> {

	/**
	 * Randomly Generated
	 */
	private static final long serialVersionUID = -5932719560958109010L;
	
	@Override
	public boolean contains(Object other) {
		if (other instanceof byte[]){
			byte[] otherPacket = (byte[]) other;
			for (byte[] packet : this) {
				if (areSamePacket(packet, otherPacket)){
					return true;
				}
			}
		} else {
			return false;
		}
		
		return false;
	}

	private boolean areSamePacket(byte[] a, byte[] b) {
		if (a.length == b.length) {
			for (int i = 0; i < a.length; i++) {
				if (a[i] != b[i]) {
					return false;
				}
			}
		} else {
			return false;
		}
		
		return true;
	}
}
