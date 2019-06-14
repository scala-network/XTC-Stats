package cash.torque.util;

import java.nio.ByteBuffer;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes DWARFv3 LEB 128 signed and unsigned integers. See DWARF v3
 * section 7.6.
 */
public final class Leb128 {

	private static final Logger LOGGER = LoggerFactory.getLogger(Leb128.class);

	public static void main(String[] args) {
		try {
			// decode a varint
			ByteBuffer inputBuf = ByteBuffer.wrap(new byte[] { (byte) 0xc0, (byte) 0x84, (byte) 0x3d });
			LOGGER.debug("inputBuf={}", Hex.encodeHexString(inputBuf.array()));
			int resultInt = readUnsignedLeb128(inputBuf);
			LOGGER.debug("result={}", resultInt);

			// encode an int
			int inputInt = 1378388396;
			LOGGER.debug("inputInt={}", inputInt);
			ByteBuffer resultBuf = ByteBuffer.wrap(new byte[unsignedLeb128Size(inputInt)]);
			writeUnsignedLeb128(resultBuf, inputInt);
			LOGGER.debug("resultBuf={}", Hex.encodeHexString(resultBuf.array()));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Gets the number of bytes in the unsigned LEB128 encoding of the given
	 * value.
	 *
	 * @param value
	 *            the value in question
	 * @return its write size, in bytes
	 */
	public static int unsignedLeb128Size(int value) {
		// TODO: This could be much cleverer.

		int remaining = value >> 7;
		int count = 0;

		while (remaining != 0) {
			remaining >>= 7;
			count++;
		}

		return count + 1;
	}

	/**
	 * Gets the number of bytes in the signed LEB128 encoding of the given
	 * value.
	 *
	 * @param value
	 *            the value in question
	 * @return its write size, in bytes
	 */
	public static int signedLeb128Size(int value) {
		// TODO: This could be much cleverer.

		int remaining = value >> 7;
		int count = 0;
		boolean hasMore = true;
		int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;

		while (hasMore) {
			hasMore = (remaining != end) || ((remaining & 1) != ((value >> 6) & 1));

			value = remaining;
			remaining >>= 7;
			count++;
		}

		return count;
	}

	/**
	 * Reads an signed integer from {@code in}.
	 * 
	 * @throws Exception
	 */
	public static int readSignedLeb128(ByteBuffer in) throws Exception {
		int result = 0;
		int cur;
		int count = 0;
		int signBits = -1;

		do {
			cur = in.get() & 0xff;
			result |= (cur & 0x7f) << (count * 7);
			signBits <<= 7;
			count++;
		} while (((cur & 0x80) == 0x80) && count < 5);

		if ((cur & 0x80) == 0x80) {
			throw new Exception("invalid LEB128 sequence");
		}

		// Sign extend if appropriate
		if (((signBits >> 1) & result) != 0) {
			result |= signBits;
		}

		return result;
	}

	/**
	 * Reads an unsigned integer from {@code in}.
	 * 
	 * @throws Exception
	 */
	public static int readUnsignedLeb128(ByteBuffer in) throws Exception {
		int result = 0;
		int cur;
		int count = 0;
		do {
			cur = in.get() & 0xff;
			result |= (cur & 0x7f) << (count * 7);
			count++;
		} while (((cur & 0x80) == 0x80) && count < 5);

		if ((cur & 0x80) == 0x80) {
			throw new Exception("invalid LEB128 sequence");
		}
		return result;
	}

	/**
	 * Writes {@code value} as an unsigned integer to {@code out}, starting at
	 * {@code offset}. Returns the number of bytes written.
	 */
	public static void writeUnsignedLeb128(ByteBuffer out, int value) {
		int remaining = value >>> 7;
		while (remaining != 0) {
			out.put((byte) ((value & 0x7f) | 0x80));
			value = remaining;
			remaining >>>= 7;
		}
		out.put((byte) (value & 0x7f));
	}

	/**
	 * Writes {@code value} as a signed integer to {@code out}, starting at
	 * {@code offset}. Returns the number of bytes written.
	 */
	public static void writeSignedLeb128(ByteBuffer out, int value) {
		int remaining = value >> 7;
		boolean hasMore = true;
		int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;

		while (hasMore) {
			hasMore = (remaining != end) || ((remaining & 1) != ((value >> 6) & 1));
			out.put((byte) ((value & 0x7f) | (hasMore ? 0x80 : 0)));
			value = remaining;
			remaining >>= 7;
		}
	}
}
