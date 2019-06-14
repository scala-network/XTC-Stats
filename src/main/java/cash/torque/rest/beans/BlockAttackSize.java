package cash.torque.rest.beans;

import java.util.Date;

import org.apache.commons.codec.DecoderException;
import org.joda.time.LocalDateTime;

/**
 * BlockInfo for attack with size < 1k
 * 
 * @author loofacoman
 *
 */
public class BlockAttackSize extends BlockInfo {
	
	private static final long serialVersionUID = 1L;
	
	private Date date; // tstamp in human readable format
	
	public BlockAttackSize(Long height, Long tstamp, Long coins, Long size, Long difficulty, String hash, Long deltaTstamp) throws DecoderException {
		super(height, tstamp, coins, size, difficulty, hash);
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			System.err.println(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
	}
	
	public BlockAttackSize(BlockInfo bi) throws DecoderException {
		super(bi.getHeight(), bi.getTstamp(), bi.getCoins(), bi.getSize(), bi.getCumulDiff(), bi.getHash());
		//System.out.println(ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE));
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			System.err.println(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
	}
	
	public BlockAttackSize(byte[] key, byte[] value) {
		super(key,value);
		try {
			LocalDateTime dt = new LocalDateTime(this.tstamp*1000);
			this.date = dt.toDate();
		} catch(IllegalArgumentException e) {
			System.err.println(String.format("impossible to convert tstamp=%d in date",this.tstamp));
		}
	}
	
	public Date getDate() {
		return date;
	}
	public void setDate(Date date) {
		this.date = date;
	}
}
