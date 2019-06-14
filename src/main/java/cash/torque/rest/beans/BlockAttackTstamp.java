package cash.torque.rest.beans;

import java.util.Arrays;
import java.util.Date;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.ArrayUtils;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cash.torque.util.TorqueUtils;

/**
 * BlockInfo with delta tstamp extra-data for attack with tstamp in the past or in the future.
 * 
 * @author loofacoman
 *
 */
public class BlockAttackTstamp extends BlockInfo {
	
	private static final long serialVersionUID = 1L;
	
	private Logger LOGGER = LoggerFactory.getLogger(BlockAttackTstamp.class);
			
	private Date date; // tstamp in human readable format
	private Long deltaTstamp;
	
	public BlockAttackTstamp(Long height, Long tstamp, Long coins, Long size, Long difficulty, String hash, Long deltaTstamp) throws DecoderException {
		super(height, tstamp, coins, size, difficulty, hash);
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			LOGGER.error(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
		this.deltaTstamp = deltaTstamp;
		// add delta tstamp to block_info data
		for(byte b : TorqueUtils.encodeUint64(deltaTstamp)) {
			lmbdRecord.setValue(ArrayUtils.add(lmbdRecord.getValue(), b));
		}
	}
	
	public BlockAttackTstamp(BlockInfo bi, Long deltaTstamp) throws DecoderException {
		super(bi.getHeight(), bi.getTstamp(), bi.getCoins(), bi.getSize(), bi.getCumulDiff(), bi.getHash());
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			LOGGER.error(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
		this.deltaTstamp = deltaTstamp;
		// add delta tstamp to block_info data
		for(byte b : TorqueUtils.encodeUint64(deltaTstamp)) {
			lmbdRecord.setValue(ArrayUtils.add(lmbdRecord.getValue(), b));
		}
	}
	
	public BlockAttackTstamp(byte[] key, byte[] value) {
		super(key,value);
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			LOGGER.error(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
		// WARNING : add the deltatstamp at the end of a block_info => after 72th byte
		if(value!=null && value.length>=72) { 
			this.deltaTstamp = TorqueUtils.decodeUint64(Arrays.copyOfRange(value, 72, 80));
		}
	}
	
	public Long getDeltaTstamp() {
		return deltaTstamp;
	}
	public void setDeltaTstamp(Long deltaTstamp) {
		this.deltaTstamp = deltaTstamp;
	}
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
}
