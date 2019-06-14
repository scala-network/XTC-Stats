package cash.torque.task;

import java.util.Date;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Hex;
import org.fusesource.lmdbjni.BufferCursor;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.Env;
import org.fusesource.lmdbjni.LMDBException;
import org.fusesource.lmdbjni.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cash.torque.config.DbProperties;
import cash.torque.db.DbEnv;
import cash.torque.service.BlockRewardDao;
import cash.torque.util.TorqueUtils;

@Component
public class UpdateStatsReward {
	
	@Autowired
	private DbProperties props;
	
	@Autowired
	private DbEnv dbEnv;
	
	@Autowired
	private BlockRewardDao blockRewardDao;
	
	
	/**
	 * read block_info,
	 * detect the delta between current height and height in reward stats,
	 * update reward stats
	 */
	//@Scheduled(cron="0/15 * * * * ?")
	//@Scheduled(cron="0 * */12 * * ?")
	public void update() {
		
		System.out.println("-------- update -----" + (new Date()).toString());	
		String LMDB_STATS_REWARDS_BY_HEIGHT = props.getStats().getRewardByHeight();
		String LMDB_BLOCK_INFO = props.getBlockchain().getBlockInfo();
		
		Database dbi = dbEnv.getDbBlockInfo();
		Database dbStats = dbEnv.getDbRewardByHeight();
		
		// check last block height in db
		byte[] lastStatsKey = null;
		byte[] lastStatsValue = null;
		
		// find last block height in stats db
		try(Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction(); BufferCursor cursor = dbStats.bufferCursor(tx)) {
			if(cursor.last()) {
				lastStatsKey = cursor.keyBytes();
				//System.out.println("lastStatsKey.length=" + lastStatsKey.length);
				lastStatsValue = cursor.valBytes();
				System.out.println("lastStatsValue.length=" + lastStatsValue.length);
				System.out.println(String.format("last stats key/value = %s / %s", Hex.encodeHexString(lastStatsKey), Hex.encodeHexString(lastStatsValue)));
			} else {
				System.out.println(LMDB_STATS_REWARDS_BY_HEIGHT + " is empty!");
			}
			tx.close();
			cursor.close();
		}
		
		// find updates
		byte[] lastKey = null;
		byte[] lastCoins = null;
		byte[] lastDiff = null;
		TreeMap<Long, LmdbRecord> rewardUpdates = new TreeMap<>();
		try (Transaction tx = dbEnv.getLmdbEnv().createReadTransaction(); BufferCursor cursorDbi = dbi.bufferCursor(tx)) {
			if(cursorDbi.last()) {
				// get last block_info
				lastKey = cursorDbi.valBytes(0, 8);
				lastCoins = cursorDbi.valBytes(16, 24);
				lastDiff = cursorDbi.valBytes(32, 40);
				System.out.println(String.format("last key/value = %s / %s",
						Hex.encodeHexString(lastKey),
						"c=" + Hex.encodeHexString(lastCoins)+"|d=" + Hex.encodeHexString(lastDiff)));
				
				// compare last block_info height and last stats height
				Long lastHeight = TorqueUtils.decodeUint64(lastKey);
				System.out.println("lastHeight=" + lastHeight);
				
				Long lastStatsHeight = lastStatsKey==null ? 0L : TorqueUtils.decodeUint64(lastStatsKey);
				System.out.println("lastStatsHeight=" + lastStatsHeight);
				
				if(lastHeight>lastStatsHeight) {
					int numUpdates = (int)(lastHeight-lastStatsHeight);
					System.out.println(String.format("there is %d updates to do", numUpdates ));
					//rewardUpdates.put(lastHeight, new LmdbRecord(lastKey, lastCoins));
					//blockRewardDao.putBlockReward(lastHeight, TorqueUtils.decodeUint64(lastCoins));
					for(int i=0; i < numUpdates; i++) {
						lastKey = cursorDbi.valBytes(0, 8);
						lastCoins = cursorDbi.valBytes(16, 24);
						
						lastHeight = TorqueUtils.decodeUint64(lastKey);
						Long cumulCoins = TorqueUtils.decodeUint64(lastCoins);
						if(i%10000==0) System.out.println(String.format("h=%d, coins=%d", lastHeight, cumulCoins));
						rewardUpdates.put(lastHeight, new LmdbRecord(lastKey, lastCoins));
						blockRewardDao.putBlockInfo(lastHeight, TorqueUtils.decodeUint64(lastCoins), TorqueUtils.decodeUint64(lastDiff));
						if(!cursorDbi.prev()) {
							System.out.println("no more data");
						}
					}
				} else if(lastHeight == lastStatsHeight) {
					System.out.println("all is up to date!");
				} else {
					System.err.println("impossible to have a stats db with height > blok_info height");
				}
			} else {
				System.err.println(LMDB_BLOCK_INFO + " is empty!");
			}
			cursorDbi.close();
		}
		
		// writes updates in db
		Env.pushMemoryPool(1024*1024*1024); // demande mémoire de 1GB
		int maxUpdateSize = props.getStats().getMaxUpdateSize(); 
		System.out.println(String.format("%d updates to do", rewardUpdates.size()));
		try(Transaction tx = dbEnv.getLmdbStatsEnv().createWriteTransaction(); BufferCursor cursor = dbStats.bufferCursor(tx)) {
			System.out.println("lmdb_stats flags = " + dbEnv.getLmdbStatsEnv().getFlags());
			System.out.println(String.format("start writing %d update(s)", rewardUpdates.size()));
			int i = 0;
			if(!cursor.last()) {
				System.out.println("lmdb_stats is empty");
			}
			for(Entry<Long,LmdbRecord> r : rewardUpdates.entrySet()) {
				cursor.keyWriteBytes(r.getValue().getKey());
				cursor.valWriteBytes(r.getValue().getValue());
				cursor.overwrite();
				cursor.next();
				System.out.print(String.format("h=%d|c=%d;", TorqueUtils.decodeUint64(r.getValue().getKey()), TorqueUtils.decodeUint64(r.getValue().getValue())));
				i++;
				if(i == maxUpdateSize) break;
//				if(!cursor.next()) {
//					System.out.println("no more data to write");
//				}
			}
			System.out.println();
			tx.commit();
			tx.close();
		} catch(LMDBException e) {
			e.printStackTrace();
		}
		Env.popMemoryPool();
		/*
		
		// block_info lookup
		try (Database db = dbEnv.getLmdbEnv().openDatabase("block_info"); Transaction tx2 = dbEnv.getLmdbEnv().createReadTransaction();
				EntryIterator it = db.iterate(tx2)) {
			System.out.println("search cumulDiff in block_info");
			long count = 0L;
			for(Entry next : it.iterable() ) {
				if(count >= start.longValue() && count <= end.longValue()) {
					System.out.println("count=" + count);
					System.out.println(Hex.encodeHexString(next.getKey()) + "|" + Hex.encodeHexString(next.getValue()));
					
					// block height
					byte[] height = Arrays.copyOfRange(next.getValue(), 0, 8);
					Long h = TorqueUtils.decodeUint64(height);
					System.out.println(""+Hex.encodeHexString(height));
					System.out.println("height=blockId=" + h);
					
					// block tstamp
					byte[] tstamp = Arrays.copyOfRange(next.getValue(), 8, 16);
					Long t = TorqueUtils.decodeUint64(tstamp);
					System.out.println(""+Hex.encodeHexString(tstamp));
					System.out.println("tstamp=" + t);
					
					// coins
					byte[] coins = Arrays.copyOfRange(next.getValue(), 16, 24);
					Long cumulCoins = TorqueUtils.decodeUint64(coins);
					
					// block cumulDiff
					byte[] diffCum = Arrays.copyOfRange(next.getValue(), 32, 40);
					System.out.println(""+Hex.encodeHexString(diffCum));
					Long diff =  TorqueUtils.decodeUint64(diffCum);
					System.out.println("diff=" + diff);
					//System.out.println("diff(LEB128)=" + Leb128.readUnsignedLeb128(ByteBuffer.wrap(diffCum)));
					byte[] hash = Arrays.copyOfRange(next.getValue(), 40, next.getValue().length);
					System.out.println("hash="+Hex.encodeHexString(hash));
					
//					blocksByBlockHeight.get(h).setCumulativeCoins(cumulCoins);
//					blocksByBlockHeight.get(h).setBlockHash(hash);
//					blocksByBlockHeight.get(h).setCumulativeDifficulty(diff);
				}
				count++;
			}
			
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		*/
	}
	
	class LmdbRecord {
		
		private byte[] key;
		private byte[] value;
		
		public LmdbRecord(byte[] key, byte[] value) {
			super();
			this.key = key;
			this.value = value;
		}
		
		public byte[] getKey() {
			return key;
		}
		public void setKey(byte[] key) {
			this.key = key;
		}
		public byte[] getValue() {
			return value;
		}
		public void setValue(byte[] value) {
			this.value = value;
		}
		
	}
}
