package cash.torque.db;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.TouchedExpiryPolicy;
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer;
import org.springframework.stereotype.Component;

@Component
public class CachingBlockReward implements JCacheManagerCustomizer {

	private CacheManager cacheManager;
	private Cache<Object, Object> cache;
	public Cache<Object, Object> getCache() {
		return cache;
	}
	public CacheManager getCacheManager() {
		return cacheManager;
	}
	
	@Override
	public void customize(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
		cache = cacheManager.createCache("reward_by_height",
			new MutableConfiguration<>()
				//.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new Duration(TimeUnit.SECONDS, 10)))
				.setExpiryPolicyFactory(TouchedExpiryPolicy.factoryOf(new Duration(null, 0))) // infinite policy
				.setStoreByValue(true)
				.setStatisticsEnabled(true));
	}

}
