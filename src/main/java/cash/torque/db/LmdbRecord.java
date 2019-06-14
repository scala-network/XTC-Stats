package cash.torque.db;

public class LmdbRecord {
	
	public byte[] key;
	public byte[] value;
	
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