package cash.torque.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.fusesource.lmdbjni.BufferCursor;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.Entry;
import org.fusesource.lmdbjni.EntryIterator;
import org.fusesource.lmdbjni.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cash.torque.config.DbProperties;
import cash.torque.config.DetectAttackProperties;
import cash.torque.db.DbEnv;
import cash.torque.rest.beans.BlockAttackSize;
import cash.torque.rest.beans.BlockAttackTstamp;
import cash.torque.rest.beans.BlockInfo;
import cash.torque.util.TorqueUtils;

@Component
public class DetectAttacksTask {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DetectAttacksTask.class);
	
	@Autowired
	private DbProperties props;
	
	@Autowired
	private DetectAttackProperties attackProps;
	
	@Autowired
	private DbEnv dbEnv;
	
	// track last height viewed by the app
	private static Long LAST_HEIGHT_BI = 0L;
	private static Long LAST_HEIGHT_ATTACK = 0L;
	
	private static Comparator<byte[]> keyByteComparator = new Comparator<byte[]>() {
		@Override
		public int compare(byte[] key1, byte[] key2) {
			if(key1 != null) {
				if(key2 != null) {
					Long h1 = TorqueUtils.decodeUint64(key1);
					Long h2 = TorqueUtils.decodeUint64(key2);
					return (int)(h1-h2);
				} else {
					return Integer.MAX_VALUE;
				}
			} else {
				if(key2 != null) {
					return Integer.MIN_VALUE;
				} else {
					return 0;
				}
			}
		}
	};
	
	/**
	 * read block_info,
	 * detect attack and store them in attack_future attack_past db
	 */
	@Scheduled(cron="0/10 * * * * ?")
	public void detectAttacks() {
		
		long start = System.nanoTime();
		
		LOGGER.info("-------- try to detect attacks @{}--------", (new Date()).toString() );
		String LMDB_STATS_ATTACKS_FUTURE = props.getStats().getAttacksInFuture();
		String LMDB_STATS_ATTACKS_PAST = props.getStats().getAttacksInPast();
		String LMDB_STATS_ATTACKS_SIZE = props.getStats().getAttacksOnSize();
		//String LMDB_BLOCK_INFO = props.getBlockchain().getBlockInfo();
		
		Database dbi = dbEnv.getDbBlockInfo();
		Database dbFuture = dbEnv.getDbAttacksInFuture();
		Database dbPast = dbEnv.getDbAttacksInPast();
		Database dbSize = dbEnv.getDbAttacksOnSize();
		
		long now = 0;
		Long last = null;
		boolean attackFuture = false;
		boolean attackPast = false;
		boolean attackSize = false;
		List<BlockAttackTstamp> futureAttacks = new ArrayList<>();
		List<BlockAttackTstamp> pastAttacks = new ArrayList<>();
		List<BlockAttackSize> sizeAttacks = new ArrayList<>();
		
		TreeMap<Long, BlockInfo> newBlockInfos = new TreeMap<>();
		
		// find last block height stored in stats db
		try(Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction()) {
			// find last attack in future & last attack in past
			LOGGER.info("*** find last block height in db attack_future");
			Long lastFuture = 0L;
			Long lastPast = 0L;
			Long lastBSize = 0L;
			try(EntryIterator it = dbFuture.iterate(tx)) {
				Long h = null;
				for (Entry next : it.iterable()) {
					BlockAttackTstamp ba = new BlockAttackTstamp(next.getKey(), next.getValue());
					h = ba.getHeight();
					//LOGGER.info(String.format("attack_future height=%d", h));
					if(h > lastFuture) {
						lastFuture = h;
					}
				}
				LOGGER.info(String.format("last height future h=%d", lastFuture));
			}
			
			LOGGER.info("*** find last block height in db attack_past");
			try(EntryIterator it = dbPast.iterate(tx)) {
				Long h = null;
				for (Entry next : it.iterable()) {
					BlockAttackTstamp ba = new BlockAttackTstamp(next.getKey(), next.getValue());
					h = ba.getHeight();
					//LOGGER.info(String.format("attack_past height=%d", h));
					if(h > lastPast) {
						lastPast = h;
					}
				}
				LOGGER.info(String.format("last height past h=%d", lastPast));
			}
			
			LOGGER.info("*** find last block height in db attack_size");
			try(EntryIterator it = dbSize.iterate(tx)) {
				Long h = null;
				for (Entry next : it.iterable()) {
					BlockAttackSize bs = new BlockAttackSize(next.getKey(), next.getValue());
					h = bs.getHeight();
					//LOGGER.info(String.format("attack_past height=%d", h));
					if(h > lastBSize) {
						lastBSize = h;
					}
				}
				LOGGER.info(String.format("last height bsize h=%d", lastBSize));
			}
			
			last = Long.max(Long.max(lastPast, lastFuture), lastBSize);
			if(last>LAST_HEIGHT_ATTACK) {
				LAST_HEIGHT_ATTACK = last;
			}
			LOGGER.info(String.format("last known attack height h=%d", last));
		}
		
		// find last height in block_info
		try(Transaction tx = dbEnv.getLmdbEnv().createReadTransaction(); BufferCursor cursor = dbi.bufferCursor(tx) ) {	
			LOGGER.info("*** check last block_info");
			if(cursor.last()) {
				BlockInfo lastBi = new BlockInfo(cursor.keyBytes(), cursor.valBytes());
				LOGGER.debug("last block_info is " + ToStringBuilder.reflectionToString(lastBi,ToStringStyle.SHORT_PREFIX_STYLE));
				now = System.currentTimeMillis();
				// if new height, detect the new blocks > LAST_HEIGHT_ATTACK
				if(lastBi.getHeight()>LAST_HEIGHT_BI) {
					LOGGER.info(String.format("previous height = %d => NEW HEIGHT IN BLOCKCHAIN = %d", LAST_HEIGHT_BI, lastBi.getHeight()));
					BlockInfo bi = null;
					do {
						bi= new BlockInfo(cursor.keyBytes(), cursor.valBytes());
						newBlockInfos.put(bi.getHeight(), bi);
						if(lastBi.getHeight()-LAST_HEIGHT_BI>10000) {
							if(bi.getHeight() % 10000 == 0) {
								LOGGER.info(String.format("detected new block height=%d", bi.getHeight()));
							}
						} else {
							LOGGER.info(String.format("detected new block height=%d", bi.getHeight()));
						}
						if(!cursor.prev()) break;
					} while(bi.getHeight()>=LAST_HEIGHT_BI);
					LAST_HEIGHT_BI = lastBi.getHeight();
				}
			}
		}
		
		if(!newBlockInfos.isEmpty()) {
			LOGGER.info(String.format("found %d new block info to analyse from h=%d to h=%d", newBlockInfos.size()-1, newBlockInfos.firstKey()+1, newBlockInfos.lastKey()));
		} else {
			LOGGER.info("no new blocks detected : nothing to analyse");
		}
		
		try {
			// detect attacks on new blocks
			long deltaTstampFuture = 0;
			long deltaTstampPast = 0;
			int i=0;
			for(BlockInfo bi : newBlockInfos.values()) {
				if(i>0) { // exclude first record wich is the last block in future/past and user for past detection
					// detect attack future : if the tstamp of the block 20 ahead of now => it's an attack => save in attack_future
					deltaTstampFuture = bi.getTstamp()-now;
					if(deltaTstampFuture > attackProps.getAttackDelaySec().longValue() * 1000) {
						LOGGER.info(String.format("attack in the future detected : block %d is %d ms ahead now", bi.getHeight(), deltaTstampFuture));
						attackFuture = true;
						BlockAttackTstamp ba = new BlockAttackTstamp(bi, deltaTstampFuture);
						futureAttacks.add(ba);
					}
					
					// detect attack past
					// if it is the first block => need to get previous block
					BlockInfo prevBi = newBlockInfos.get(bi.getHeight()-1);
					deltaTstampPast = bi.getTstamp() - prevBi.getTstamp();
					if(prevBi.getHeight().longValue()>0L && deltaTstampPast<0) {
						LOGGER.info(String.format("attack in the past detected : block #%d is %d ms behind block #%d", bi.getHeight(), -deltaTstampPast, prevBi.getHeight()));
						attackPast = true;
						BlockAttackTstamp ba = new BlockAttackTstamp(bi, deltaTstampPast);
						pastAttacks.add(ba);
					}
					
					// detect attack on block size
					if(bi.getHeight().longValue()>1L && bi.getSize().intValue() < attackProps.getAttackBlockSizeBytes().intValue()) {
						LOGGER.info(String.format("attack on block size detected : block #%d has size %d < %d bytes", bi.getHeight(), bi.getSize(), attackProps.getAttackBlockSizeBytes()));
						attackSize = true;
						BlockAttackSize bs = new BlockAttackSize(bi);
						sizeAttacks.add(bs);
					}
				}
				i++;
			}
		} catch(DecoderException e) {
			LOGGER.error("impossible to detect attack", e);
		}
		
		if(attackFuture) {
			try(Transaction tx = dbEnv.getLmdbStatsEnv().createWriteTransaction(); BufferCursor cursor = dbFuture.bufferCursor(tx)) {
				if(!cursor.last()) {
					LOGGER.info("{} is empty", LMDB_STATS_ATTACKS_FUTURE);
				} else {
					if(cursor.lastDup()) {
						LOGGER.debug("{} at lastDup", LMDB_STATS_ATTACKS_FUTURE);
					}
				}
				for(BlockAttackTstamp ba : futureAttacks) {
					// store height tstamp
					LOGGER.debug(String.format("attack future : %s %s %s",
														ToStringBuilder.reflectionToString(ba, ToStringStyle.SHORT_PREFIX_STYLE),
														Hex.encodeHexString(ba.getLmbdRecord().getKey()),
														Hex.encodeHexString(ba.getLmbdRecord().getValue())
														)
													);
					//dbFuture.put(ba.getLmbdRecord().getKey(), ba.getLmbdRecord().getValue(), Constants.APPENDDUP);
					cursor.keyWriteBytes(ba.getLmbdRecord().getKey());
					cursor.valWriteBytes(ba.getLmbdRecord().getValue());
					cursor.overwrite();
					cursor.nextDup();
				}
				tx.commit();
			} catch(Exception e) {
				//e.printStackTrace();
				LOGGER.error(e.getMessage());
			}
		}
		
		if(attackPast) {
			try(Transaction tx = dbEnv.getLmdbStatsEnv().createWriteTransaction(); BufferCursor cursor = dbPast.bufferCursor(tx)) {
				if(!cursor.last()) {
					LOGGER.info(LMDB_STATS_ATTACKS_PAST + " is empty");
				} else {
					if(cursor.lastDup()) {
						LOGGER.debug(LMDB_STATS_ATTACKS_PAST + " at lastDup");
					} else {
						LOGGER.info(LMDB_STATS_ATTACKS_PAST + " : impossible to be at lastDup");
					}
				}
				for(BlockAttackTstamp ba : pastAttacks) {
					LOGGER.debug(String.format("attack past : %s %s %s",
														ToStringBuilder.reflectionToString(ba, ToStringStyle.SHORT_PREFIX_STYLE),
														Hex.encodeHexString(ba.getLmbdRecord().getKey()),
														Hex.encodeHexString(ba.getLmbdRecord().getValue())
													)
										);
					//dbPast.put(ba.getLmbdRecord().getKey(), ba.getLmbdRecord().getValue(), Constants.APPENDDUP);
					cursor.keyWriteBytes(ba.getLmbdRecord().getKey());
					cursor.valWriteBytes(ba.getLmbdRecord().getValue());
					cursor.overwrite();
					cursor.nextDup();
				}
				tx.commit();
			} catch(Exception e) {
				//e.printStackTrace();
				LOGGER.error(e.getMessage());
			}
		}
		
		if(attackSize) {
			try(Transaction tx = dbEnv.getLmdbStatsEnv().createWriteTransaction(); BufferCursor cursor = dbSize.bufferCursor(tx)) {
				if(!cursor.last()) {
					LOGGER.info(LMDB_STATS_ATTACKS_SIZE + " is empty");
				} else {
					if(cursor.lastDup()) {
						LOGGER.debug(LMDB_STATS_ATTACKS_SIZE + " at lastDup");
					} else {
						LOGGER.info(LMDB_STATS_ATTACKS_SIZE + " : impossible to be at lastDup");
					}
				}
				for(BlockAttackSize bs : sizeAttacks) {
					LOGGER.debug(String.format("attack past : %s %s %s",
														ToStringBuilder.reflectionToString(bs, ToStringStyle.SHORT_PREFIX_STYLE),
														Hex.encodeHexString(bs.getLmbdRecord().getKey()),
														Hex.encodeHexString(bs.getLmbdRecord().getValue())
													)
										);
					//dbPast.put(ba.getLmbdRecord().getKey(), ba.getLmbdRecord().getValue(), Constants.APPENDDUP);
					cursor.keyWriteBytes(bs.getLmbdRecord().getKey());
					cursor.valWriteBytes(bs.getLmbdRecord().getValue());
					cursor.overwrite();
					cursor.nextDup();
				}
				tx.commit();
			} catch(Exception e) {
				//e.printStackTrace();
				LOGGER.error(e.getMessage());
			}
		}
		long execTime = System.nanoTime() - start;
		LOGGER.info("detect attacks task : execTime(ms)={}", (execTime/1e6) );
	}
	
}
