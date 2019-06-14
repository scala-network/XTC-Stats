package cash.torque.rest.beans;

import java.beans.Transient;
import java.io.Serializable;
import java.util.Arrays;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cash.torque.db.LmdbRecord;
import cash.torque.util.TorqueUtils;

public class BlockInfo implements Serializable {
	
	private Logger LOGGER = LoggerFactory.getLogger(BlockInfo.class);
	
	private static final long serialVersionUID = 1L;
	
	public static byte[] KEY_ZERO = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	
	protected LmdbRecord lmbdRecord;
	
	protected Long height;
	protected Long tstamp;
	protected Long coins;
	protected Long size;
	protected Long cumulDiff;
	protected String hash;
	
	public BlockInfo() {
		super();
	}
	
	public BlockInfo(byte[] key, byte[] value) {
		LmdbRecord r = new LmdbRecord();
		r.setKey(key);
		r.setValue(value);
		lmbdRecord = r;
		if(value!=null && value.length >= 8) {
			height = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 0, 8));
			if(value.length>=16) {
				tstamp = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 8, 16));
				if(value.length>=24) {
					coins = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 16, 24));
					if(value.length>=32) {
						size = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 24, 32));
						if(value.length>=40) {
							cumulDiff = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 32, 40));
							if(value.length>=72) {
								hash = Hex.encodeHexString(Arrays.copyOfRange(value, 40, 72));
							}
						}
					}
				}
			}
		}
	}
	
	public BlockInfo(Long height, Long tstamp, Long coins, Long size, Long difficulty, String hash) throws DecoderException {
		//LOGGER.info("BlockInfo constructor : {},{},{},{},{},{}", height, tstamp, coins, size, difficulty, hash);
		this.setHeight(height);
		this.setTstamp(tstamp);
		this.setCoins(coins);
		this.setSize(size);
		this.setCumulDiff(difficulty);
		this.setHash(hash);
		
		this.lmbdRecord = new LmdbRecord();
		this.lmbdRecord.setKey(KEY_ZERO);
		//FIXME check null values
		byte[] value = null;
		for(byte b : TorqueUtils.encodeUint64(height)) {
			value = ArrayUtils.add(value, b);
		}
		for(byte b : TorqueUtils.encodeUint64(tstamp)) {
			value = ArrayUtils.add(value, b);
		}
		for(byte b : TorqueUtils.encodeUint64(coins)) {
			value = ArrayUtils.add(value, b);
		}
		for(byte b : TorqueUtils.encodeUint64(size)) {
			value = ArrayUtils.add(value, b);
		}
		for(byte b : TorqueUtils.encodeUint64(difficulty)) {
			value = ArrayUtils.add(value, b);
		}
		try {
			for(byte b : Hex.decodeHex(hash.toCharArray()) ) {
				value = ArrayUtils.add(value, b);
			}
		} catch (DecoderException e) {
			LOGGER.error("impossible to encode hash : ", e.getMessage());
			throw e;
		} finally {
			//FIXME the length should be the same if encoding problem?
		}
		this.lmbdRecord.setValue(value);
	}
	
	@Transient
	public LmdbRecord getLmbdRecord() {
		return lmbdRecord;
	}
	public void setLmbdRecord(LmdbRecord lmbdRecord) {
		this.lmbdRecord = lmbdRecord;
	}
	
	public Long getHeight() {
		return height;
	}
	public void setHeight(Long height) {
		this.height = height;
	}
	public Long getCoins() {
		return coins;
	}
	public void setCoins(Long coins) {
		this.coins = coins;
	}
	public Long getCumulDiff() {
		return cumulDiff;
	}
	public void setCumulDiff(Long difficulty) {
		this.cumulDiff = difficulty;
	}
	public Long getTstamp() {
		return tstamp;
	}
	public void setTstamp(Long tstamp) {
		this.tstamp = tstamp;
	}
	public Long getSize() {
		return size;
	}
	public void setSize(Long size) {
		this.size = size;
	}
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
}
