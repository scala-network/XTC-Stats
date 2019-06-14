package cash.torque;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cash.torque.config.DetectAttackProperties;
import cash.torque.config.GlobalProperties;
import cash.torque.db.DbEnv;
import cash.torque.rest.beans.BlockAttackSize;
import cash.torque.rest.beans.BlockAttackTstamp;
import cash.torque.rest.beans.BlockInfo;
import cash.torque.rest.beans.CryptoNoteBlock;
import cash.torque.util.TorqueUtils;

@SpringBootApplication
@RestController
@EnableScheduling
public class XtlStatsApplication implements CommandLineRunner {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(XtlStatsApplication.class);
	
	@Autowired
	private DbEnv dbEnv;
	
	@Autowired
	private GlobalProperties globalProps;
	
	@Autowired
	private DetectAttackProperties attackProps;
	
	public static void main(String[] args) {
		// launch app
		System.out.println("********************* start XTC Stats App **********************");
		SpringApplication.run(XtlStatsApplication.class, args);
	}
	
	@Override
	public void run(String... args) throws Exception {
	}
	
	@RequestMapping("/")
	public String home() {
		return "<h1>Welcome to XTC Stats application v" + globalProps.getVersion() +" !</h1>"
				+ "<hr/>"
				+ "<p>/bi : the latest block_info</p>"
				+ "<p>/bi/128820 : the latest block_info from 128820</p>"
				+ "<hr/>"
				+ "<p>/attack : the 20 latest detected attacks (past + future + size)</p>"
				+ "<p>/attack/N : the N latest attacks</p>"
				+ "<p>/attack/N/p : the N latest attacks in the past</p>"
				+ "<p>/attack/N/f : the N latest attacks in the future</p>"
				+ "<p>/attack/N/s : the N latest attacks on block size</p>"
				+ "<hr/>"
				+ "<p> Made with love by loofaconman and brought to you by the Torque Team.";
		
	}
	
	@GetMapping(value={"/bi", "/bi/{from}"})
	public List<BlockInfo> readLastBlockInfo(@PathVariable(name="from",required=false) Long from) throws Exception {
		long now = System.nanoTime();
		List<BlockInfo> result = new ArrayList<>();
		try (Transaction tx = dbEnv.getLmdbEnv().createReadTransaction(); BufferCursor cursor = dbEnv.getDbBlockInfo().bufferCursor(tx)) {
			if(from!=null) {
				System.out.println("from block height=" + from);
				if(cursor.last()) {
					do {
						BlockInfo bi = new BlockInfo(cursor.keyBytes(), cursor.valBytes());
						result.add(bi);
						if(bi.getHeight().compareTo(from) == 0) {
							break;
						}
					} while(cursor.prev());
				}
				long execTimeNs = System.nanoTime() - now;
				System.out.println("execTime(ms)=" + (execTimeNs/1e6));
			} else {
				if(cursor.last()) {
					// get last block_info
					BlockInfo bi = new BlockInfo(cursor.keyBytes(), cursor.valBytes());
					result.add(bi);
					System.out.print(String.format("h=%d|c=%d|d=%d;", bi.getHeight(), bi.getCoins(), bi.getCumulDiff()));
					//System.out.println(String.format("last key/value = %s / %s", Hex.encodeHexString(lastKey), Hex.encodeHexString(lastCoins)));
				}
				long execTimeNs = System.nanoTime() - now;
				System.out.println("execTime(ms)=" + (execTimeNs/1e6));
			}
		}
		System.out.println();
		return result;
	}
	
	@GetMapping(value={"/attack", "/attack/{n}", "/attack/{n}/{t}"})
	public Attacks readLastAttacks(@PathVariable(name="t",required=false) String attackType, @PathVariable(name="n",required=false) Integer n) throws Exception {
		boolean isPast = false;
		boolean isFuture = false;
		boolean isSize = false;
		if(attackType == null) {
			isPast = true;
			isFuture = true;
			isSize = true;
			LOGGER.info("### last attack in past & future service ###");
		} else if("p".equalsIgnoreCase(attackType)) {
			isPast = true;
			LOGGER.info("### last attack in past service ###");
		} else if("f".equalsIgnoreCase(attackType)) {
			isFuture = true;
			LOGGER.info("### last attack in future service ###");
		} else if("s".equalsIgnoreCase(attackType)) {
			isSize = true;
			LOGGER.info("### last attack on block size service ###");
		} else {
			throw new Exception("unknow attack type (try 'p'=past or 'f'=future or 's'=size or nothing for all");
		}
		long now = System.nanoTime();
		
		Attacks attacks = new Attacks();
		Attack ap = new Attack();
		Attack af = new Attack();
		Attack as = new Attack();
		TreeMap<Long,BlockAttackTstamp> attackInPast = new TreeMap<>();
		TreeMap<Long,BlockAttackTstamp> attackInFuture = new TreeMap<>();
		TreeMap<Long,BlockAttackSize> attackOnSize = new TreeMap<>();
		
		// impossible to navigate in attack_future with the cursor.last() cursor.prev() <= not ordered like at insertion
		//try (Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction(); BufferCursor cursor = dbEnv.getDbAttacksInFuture().bufferCursor(tx)) {
		if(n==null) { n = 20; }
		if(isFuture) {
			af.setBlockInfos(new ArrayList<>());
			try (Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction(); EntryIterator it = dbEnv.getDbAttacksInFuture().iterateBackward(tx) ) {
				LOGGER.debug(String.format("last %d block with attack in future", n));
				// get all data
				for(Entry next : it.iterable()) {
					BlockAttackTstamp ba = new BlockAttackTstamp(next.getKey(), next.getValue());
					attackInFuture.put(ba.getHeight(), ba);
				}
			}
			// take last n records
			int i = 0;
			for(Long h : attackInFuture.descendingKeySet()) {
				af.getBlockInfos().add(attackInFuture.get(h));
				i++;
				if(i == n) break;
			}
			af.setTotalNumAttacks(attackInFuture.size());
			if(!attackInFuture.isEmpty()) {
				af.setFirstHeight(attackInFuture.firstKey());
				af.setLastHeight(attackInFuture.lastKey());
			}
			if(attackProps.getAttackDelaySec()!=null) {
				as.setParams(new HashMap<>());
				as.getParams().put("attackDelaySec", attackProps.getAttackDelaySec().toString());
			}
			attacks.setAttacksInFuture(af);
		}
		if(isPast) {
			ap.setBlockInfos(new ArrayList<>());
			try (Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction(); EntryIterator it = dbEnv.getDbAttacksInPast().iterateBackward(tx) ) {
				LOGGER.debug(String.format("last %d block with attack in past", n));
				// get all data
				for(Entry next : it.iterable()) {
					BlockAttackTstamp ba = new BlockAttackTstamp(next.getKey(), next.getValue());
					attackInPast.put(ba.getHeight(), ba);
				}
			}
			// take last n records
			int i = 0;
			for(Long h : attackInPast.descendingKeySet()) {
				ap.getBlockInfos().add(attackInPast.get(h));
				i++;
				if(i == n) break;
			}
			ap.setTotalNumAttacks(attackInPast.size());
			if(!attackInPast.isEmpty()) {
				ap.setFirstHeight(attackInPast.firstKey());
				ap.setLastHeight(attackInPast.lastKey());
			}
			attacks.setAttacksInPast(ap);
		}
		if(isSize) {
			as.setBlockInfos(new ArrayList<>());
			try (Transaction tx = dbEnv.getLmdbStatsEnv().createReadTransaction(); EntryIterator it = dbEnv.getDbAttacksOnSize().iterateBackward(tx) ) {
				LOGGER.debug(String.format("last %d block with attack on size", n));
				// get all data
				for(Entry next : it.iterable()) {
					BlockAttackSize bs = new BlockAttackSize(next.getKey(), next.getValue());
					attackOnSize.put(bs.getHeight(), bs);
				}
			}
			// take last n records
			int i = 0;
			for(Long h : attackOnSize.descendingKeySet()) {
				as.getBlockInfos().add(attackOnSize.get(h));
				i++;
				if(i == n) break;
			}
			as.setTotalNumAttacks(attackOnSize.size());
			if(!attackOnSize.isEmpty()) {
				as.setFirstHeight(attackOnSize.firstKey());
				as.setLastHeight(attackOnSize.lastKey());
			}
			if(attackProps.getAttackBlockSizeBytes()!=null) {
				as.setParams(new HashMap<>());
				as.getParams().put("attackMinSizeBytes", attackProps.getAttackBlockSizeBytes().toString());
			}
			attacks.setAttacksOnSize(as);
		}
		
		long execTimeNs = System.nanoTime() - now;
		LOGGER.info("execTime(ms)={}", (execTimeNs/1e6));
		return attacks;
	}
	
	@SuppressWarnings("unused")
	private static class Attack {
		
		private Integer totalNumAttacks;
		private Long firstHeight;
		private Long lastHeight;
		private Map<String,String> params;
		private List<BlockInfo> blockInfos;
		
		
		public Integer getTotalNumAttacks() {
			return totalNumAttacks;
		}
		public void setTotalNumAttacks(Integer totalNumAttacks) {
			this.totalNumAttacks = totalNumAttacks;
		}
		public Long getFirstHeight() {
			return firstHeight;
		}
		public void setFirstHeight(Long firstHeight) {
			this.firstHeight = firstHeight;
		}
		public Long getLastHeight() {
			return lastHeight;
		}
		public void setLastHeight(Long lastHeight) {
			this.lastHeight = lastHeight;
		}
		public List<BlockInfo> getBlockInfos() {
			return blockInfos;
		}
		public void setBlockInfos(List<BlockInfo> blockInfos) {
			this.blockInfos = blockInfos;
		}
		public Map<String, String> getParams() {
			return params;
		}
		public void setParams(Map<String, String> params) {
			this.params = params;
		}
	}
	
	@SuppressWarnings("unused")
	private static class Attacks {
		
		private Attack attacksOnSize;
		private Attack attacksInFuture;
		private Attack attacksInPast;
		
		public Attack getAttacksOnSize() {
			return attacksOnSize;
		}
		public void setAttacksOnSize(Attack attacksOnSize) {
			this.attacksOnSize = attacksOnSize;
		}
		public Attack getAttacksInFuture() {
			return attacksInFuture;
		}
		public void setAttacksInFuture(Attack attacksInFuture) {
			this.attacksInFuture = attacksInFuture;
		}
		public Attack getAttacksInPast() {
			return attacksInPast;
		}
		public void setAttacksInPast(Attack attacksInPast) {
			this.attacksInPast = attacksInPast;
		}
	}
	
	/**
	 * méthode pour lire des blocks de la base LMDB torque
	 * @param from
	 * @param to
	 * @return
	 * @throws Exception 
	 */
	@Deprecated
	@RequestMapping(path="/readBlocks", method=RequestMethod.GET)
	public Map<Long,CryptoNoteBlock> readBlocks(@RequestParam Long from, @RequestParam Long to) throws Exception {
		Long start = 0L;
		Long end = Long.MAX_VALUE;
		if(from != null) {
			start = from.longValue();
		}
		if(to != null) {
			end = to.longValue();
		}
		TreeMap<Long,CryptoNoteBlock> blocksByBlockHeight = new TreeMap<>();
		try (Database dbBlocks = dbEnv.getLmdbEnv().openDatabase("blocks"); Transaction tx = dbEnv.getLmdbEnv().createReadTransaction(); EntryIterator it = dbBlocks.iterate(tx)) {
			long count = 0L;
			for(Entry next : it.iterable() ) {
				if(count >= start.longValue() && count <= end.longValue()) {
					System.out.println("count=" + count);
					long blockHeight = TorqueUtils.decodeUint64(next.getKey());
					System.out.println("blockheight=" + blockHeight);
					CryptoNoteBlock blk = CryptoNoteBlock.parseBlockBlob(ByteBuffer.wrap(next.getValue()));
					blk.setBlockId(next.getKey());
					blk.setHeight(new Long(blockHeight));
					//LOGGER.debug("block {} : {}", blockHeight, ToStringBuilder.reflectionToString(blk, ToStringStyle.SHORT_PREFIX_STYLE));
					System.out.println("" + blockHeight + " : " + ToStringBuilder.reflectionToString(blk, ToStringStyle.SHORT_PREFIX_STYLE));
					blocksByBlockHeight.put(blockHeight, blk);
				}
				count++;
			}
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		
		// block_info lookup
		try (Database dbBi = dbEnv.getLmdbEnv().openDatabase("block_info");
				Transaction tx2 = dbEnv.getLmdbEnv().createReadTransaction();
				EntryIterator it = dbBi.iterate(tx2)) {
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
					
					blocksByBlockHeight.get(h).setCumulativeCoins(cumulCoins);
					blocksByBlockHeight.get(h).setBlockHash(hash);
					blocksByBlockHeight.get(h).setCumulativeDifficulty(diff);
				}
				count++;
			}
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		
		// récupération de la difficulté pour chaque bloc
		// block_info = height, timestamp, coins, size, cumulDiff, hash
		// block_height = block hash, height
		/*
		try (Database dbBi = LMDB_ENV.openDatabase("block_info"); Transaction tx2 = LMDB_ENV.createReadTransaction(); BufferCursor cursor = dbBi.bufferCursor(tx2)) {
			System.out.println("search cumulDiff in block_info");
			byte[] zeroValk = new byte[]{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
			if(cursor.seekRange(zeroValk)) {
				System.out.println("ça c'est couillon");
			}
			if(cursor.seekRange(blocksByBlockHeight.firstEntry().getValue().getBlockId())) {
				int k=0;
				Long h = blocksByBlockHeight.firstKey();
				System.out.println("h=" + h);
				do {
					CryptoNoteBlock cbn = blocksByBlockHeight.get(h);
					String bi = Hex.encodeHexString(cursor.valBytes());
					System.out.println("found blockInfo=" + bi);
					cbn.setBlockInfos(bi);
					h++;
					k++;
				} while(cursor.next() && k<blocksByBlockHeight.size());
			} else {
				System.out.println("first key " + blocksByBlockHeight.firstEntry().getValue().getHeight() + " not found");
			}
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
		*/
//			boolean isLast = true;
//			CryptoNoteBlock cnBlk = null;
//			byte[] previousHash ;
//			for(Long h : blocksByBlockHeight.descendingKeySet()) {
//				System.out.println("h=" + h);
//				cnBlk = blocksByBlockHeight.get(h);
//				previousHash = cnBlk.getPreviousBlockHash();
//				if(isLast) {
//					isLast = false;
//				} else {
//					cnBlk.setBlockHash(previousHash);
//					blocks.put(h, cnBlk);
//				}
//			}
//			blocks.forEach((k,v)->System.out.println("" + k.longValue() + " | "+ v));
//			
//			// récupération de la difficulté pour chaque bloc
//			try (Database dbBi = LMDB_ENV.openDatabase("block_info"); Transaction tx2 = LMDB_ENV.createReadTransaction(); BufferCursor cursor = dbBi.bufferCursor(tx2)) {
//				for(Long h : blocks.keySet()) {
//					if(cursor.seekRange(blocks.get(h).getBlockHash())) {
//						String bi = Hex.encodeHexString(cursor.valBytes());
//						blocks.get(h).setBlockInfos(bi);
//					}
//				}
//			}
//		} catch(Exception e) {
//			LOGGER.error(e.getMessage());
//			System.out.println(e.getMessage());
//		}
		return blocksByBlockHeight;
	}
	

}
