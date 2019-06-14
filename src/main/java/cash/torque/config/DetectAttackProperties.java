package cash.torque.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="attack")
public class DetectAttackProperties {
	
	private Integer attackDelaySec;
	private Integer attackBlockSizeBytes;
	
	public Integer getAttackDelaySec() {
		return attackDelaySec;
	}
	public void setAttackDelaySec(Integer attackDelaySec) {
		this.attackDelaySec = attackDelaySec;
	}
	
	public Integer getAttackBlockSizeBytes() {
		return attackBlockSizeBytes;
	}
	public void setAttackBlockSizeBytes(Integer blockMinSize) {
		this.attackBlockSizeBytes = blockMinSize;
	}
}
