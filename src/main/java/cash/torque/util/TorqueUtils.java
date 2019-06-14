package cash.torque.util;

public class TorqueUtils {
	/**
	 * Decode the key use to store data in LMDB DB.
	 * 
	 * Eg : block height = 4ead010000000000 => 0x01ad4e = 109902L
	 * 
	 * @param bytes : block key in LMDB as array of 8 bytes
	 * @return int : height of block
	 */
	public static final long decodeUint64(byte[] bytes) {
		long res = 0;
		int i=0;
		for(byte b : bytes) {
			long byteToShift = (b<0 ? b+256 : b);
			res +=  byteToShift << (8*i);
			i++;
		}
		//System.out.println("decode = " + res);
		return res;
	}
	
	/**
	 * Encode a (unsigned) long 64bits into an 8-bytes array for storage in LMDB
	 * 
	 * @param lnum : the long to encode
	 * @return byte[] : the encoded array
	 */
	public static final byte[] encodeUint64(long lnum) {
		byte[] bytes = new byte[8];
		int n = 0;
		long b;
		do {
			b = (lnum >> 8 * n) & 0xff;
			bytes[n] = (byte)b;
			n++;
		} while(n<8);
		//System.out.println(String.format("encode %d = %s", lnum, Hex.encodeHexString(bytes)) );
		return bytes;
	}
}
