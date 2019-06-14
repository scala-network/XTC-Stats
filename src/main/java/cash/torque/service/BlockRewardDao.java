package cash.torque.service;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import cash.torque.db.CachingBlockReward;
import cash.torque.rest.beans.BlockInfo;

@Repository
public class BlockRewardDao {

	@Autowired
	private CachingBlockReward cachingBlockReward;
	
	@CacheResult(cacheName="reward_by_height")
	public BlockInfo putBlockInfo(@CacheKey Long height, Long coins, Long difficulty) {
		BlockInfo b = new BlockInfo();
		b.setHeight(height);
		b.setCoins(coins);
		b.setCumulDiff(difficulty);
		return b;
	}
	
	public BlockInfo getBlockInfo(Long height) {
		BlockInfo b = (BlockInfo)cachingBlockReward.getCache().get(height);
		return b;
	}
}
