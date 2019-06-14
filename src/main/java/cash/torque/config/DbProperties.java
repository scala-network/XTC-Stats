package cash.torque.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="lmdb")
public class DbProperties {
	
	private String torqueHome;
	private final BlockChainConfig blockchain = new BlockChainConfig();
	private final StatsConfig stats = new StatsConfig();
	
	public static class BlockChainConfig {
		
		private String envPath;
		private String blockInfo;
		
		public String getEnvPath() {
			return envPath;
		}
		public void setEnvPath(String dbPath) {
			this.envPath = dbPath;
		}
		public String getBlockInfo() {
			return blockInfo;
		}
		public void setBlockInfo(String blockInfo) {
			this.blockInfo = blockInfo;
		}
	}
	
	public static class StatsConfig {
		
		private String envPath;
		private Integer mapSizeGb;
		private Integer maxDb;
		private Integer maxReader;
		private Integer maxUpdateSize;
		
		//databases ~= CQL CF ~= SQL table
		private String rewardByHeight;
		private String attacksInFuture;
		private String attacksInPast;
		private String attacksOnSize;
		
		public String getEnvPath() {
			return envPath;
		}
		public void setEnvPath(String dbPath) {
			this.envPath = dbPath;
		}
		public Integer getMapSizeGb() {
			return mapSizeGb;
		}
		public void setMapSizeGb(Integer mapSizeGb) {
			this.mapSizeGb = mapSizeGb;
		}
		public Integer getMaxDb() {
			return maxDb;
		}
		public void setMaxDb(Integer maxDb) {
			this.maxDb = maxDb;
		}
		public Integer getMaxReader() {
			return maxReader;
		}
		public void setMaxReader(Integer maxReader) {
			this.maxReader = maxReader;
		}
		public String getRewardByHeight() {
			return rewardByHeight;
		}
		public void setRewardByHeight(String rewardByHeight) {
			this.rewardByHeight = rewardByHeight;
		}
		public Integer getMaxUpdateSize() {
			return maxUpdateSize;
		}
		public void setMaxUpdateSize(Integer maxUpdateSize) {
			this.maxUpdateSize = maxUpdateSize;
		}
		public String getAttacksInFuture() {
			return attacksInFuture;
		}
		public void setAttacksInFuture(String attacksInFuture) {
			this.attacksInFuture = attacksInFuture;
		}
		public String getAttacksInPast() {
			return attacksInPast;
		}
		public void setAttacksInPast(String attacksInPast) {
			this.attacksInPast = attacksInPast;
		}
		public String getAttacksOnSize() {
			return attacksOnSize;
		}
		public void setAttacksOnSize(String attacksOnSize) {
			this.attacksOnSize = attacksOnSize;
		}
	}
	
	public String getTorqueHome() {
		return torqueHome;
	}

	public void setTorqueHome(String torque_home) {
		this.torqueHome = torque_home;
	}

	public BlockChainConfig getBlockchain() {
		return blockchain;
	}

	public StatsConfig getStats() {
		return stats;
	}
	
}
