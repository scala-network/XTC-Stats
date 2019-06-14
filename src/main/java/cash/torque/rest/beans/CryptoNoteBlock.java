package cash.torque.rest.beans;

import java.nio.ByteBuffer;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cash.torque.util.Leb128;

public class CryptoNoteBlock {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CryptoNoteBlock.class);

	/*
	 * https://monero.stackexchange.com/questions/5664/size-requirements-for-different-pieces-of-a-monero-transaction
	Transaction version: 1 VarInt
	Unlock time: 1 VarInt
	Per Input: 1 byte (type: either coinbase or regular spend) + 1 VarInt (pre-ringct amount) + 1 VarInt (the ring size) + Ring_Size VarInts (input offsets) + 32 bytes (key image)
	Per Output: 1 VarInt (pre-ringct amount) + 1 byte (type: only regular spend type currently implemented) + 32 bytes (output one-time public key)
	Tx extra: Includes 32 bytes for the txpubkey, may contain an encrypted or unencrypted payment id, a nonce if a coinbase tx, and can contain other arbitrary user defined information
	Tx type: 1 byte (Version 2 transactions only)
	Tx fee: VarInt
	Version 1 transactions only: 1 LSAG ring signature per input, each ring signature is 32 bytes * Ring_Size
	Version 1 transactions end here. The remainder of the transaction structure applies to Version 2 (RingCT) transactions only:
	PsuedoOuts (newly generated commitments for the real input amounts, so that your real inputs are not revealed): Per input: 32 bytes (PseudoOuts are present for tx type 2 RCTType2Simple transactions only)
	EcdhInfos (encrypted output amounts and OutPk commitment masks): 64 bytes per output
	OutPks (output amount commitments): 32 bytes per output
	Range proofs: 6176 bytes per output
	MLSAG ring signatures: Per Input: (64 bytes * Ring_Size) + 32 bytes
	Therefore, transaction size is most sensitive to increasing the number of outputs, because of the large range proof size requirements. The transaction is also sensitive to increasing the ring size and increasing the number of inputs.
	*/
	private byte[] blockId;
	private byte[] blockHash;
	private String blockInfos;
	private Long cumulativeDifficulty;
	private Long cumulativeCoins;
	
	// header
	private Integer majorVersion;
	private Integer minorVersion;
	private Date timestamp;
	private byte[] previousBlockHash;
	private Long nonce;
	
	// base tx body
//	private Integer txFormatVersion; // 1 varint sur un octet
//	private Date unlockTime;	// 1 varint
//	private Long inputNum;		// always 1 for base tx
//	private String inputType; 	// always 0xff for base tx
	private Long height;		// height of the block which contains the tx
//	private Long outputNum; 	// number of outputs
//	private Long[] outputs;		// array of outputs
//	private Long extraSize;		// number of bytes in extra field
//	private Byte[] extra;		// additional data
	
	// list of transaction identifiers
//	private Integer tx_num; 	// number of tx identifiers
//	private String[] identifiers; // array of hashes
	
	public static final CryptoNoteBlock parseBlockBlob(ByteBuffer blob) throws Exception {
		
		if(blob == null) return null;
		System.out.println("creation new block");
		CryptoNoteBlock block = new CryptoNoteBlock();
		
		int blobSize = blob.limit();
		LOGGER.debug("buffer limit={}", blobSize);
		System.out.println("buf limit=" + blobSize);
		
		// block header
		block.majorVersion = (int)blob.get();
		LOGGER.debug("majorVersion:{}", block.majorVersion);
		System.out.println("major="+block.majorVersion);
		//System.out.println("pos=" + blob.position());
		block.minorVersion = (int)blob.get();
		LOGGER.debug("minorVersion:{}", block.minorVersion);
		System.out.println("minor="+block.minorVersion);
		//System.out.println("pos=" + blob.position());
		long tstamp = Leb128.readUnsignedLeb128(blob);
		LOGGER.debug("tstamp:{}", tstamp);
		System.out.println("tstamp=" + tstamp);
		//System.out.println("pos=" + blob.position());
		DateTime dt = new DateTime(tstamp);
		block.timestamp = dt.toDate();
		byte[] prevBlockHash = new byte[32];
		for(int i=0; i<32; i++) {
			prevBlockHash[i] = blob.get(blob.position()+i);
		}
		block.previousBlockHash = prevBlockHash;
		System.out.println("previousBlockHash=" + Hex.encodeHexString(prevBlockHash));
		//System.out.println("pos=" + blob.position());
		return block;
	}
	
	public Long getCumulativeDifficulty() {
		return cumulativeDifficulty;
	}
	public void setCumulativeDifficulty(Long difficulty) {
		this.cumulativeDifficulty = difficulty;
	}
	public byte[] getBlockId() {
		return blockId;
	}
	public void setBlockId(byte[] blockId) {
		this.blockId = blockId;
	}
	public byte[] getBlockHash() {
		return blockHash;
	}
	public void setBlockHash(byte[] blockHash) {
		this.blockHash = blockHash;
	}
	public String getBlockInfos() {
		return blockInfos;
	}
	public void setBlockInfos(String blockInfos) {
		this.blockInfos = blockInfos;
	}
	public Integer getMajorVersion() {
		return majorVersion;
	}

	public void setMajorVersion(Integer majorVersion) {
		this.majorVersion = majorVersion;
	}

	public Integer getMinorVersion() {
		return minorVersion;
	}

	public void setMinorVersion(Integer minorVersion) {
		this.minorVersion = minorVersion;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public byte[] getPreviousBlockHash() {
		return previousBlockHash;
	}

	public void setPreviousBlockHash(byte[] previousBlockHash) {
		this.previousBlockHash = previousBlockHash;
	}

	public Long getNonce() {
		return nonce;
	}

	public void setNonce(Long nonce) {
		this.nonce = nonce;
	}

//	public Integer getTxFormatVersion() {
//		return txFormatVersion;
//	}
//
//	public void setTxFormatVersion(Integer txFormatVersion) {
//		this.txFormatVersion = txFormatVersion;
//	}
//
//	public Date getUnlockTime() {
//		return unlockTime;
//	}
//
//	public void setUnlockTime(Date unlockTime) {
//		this.unlockTime = unlockTime;
//	}
//
//	public Long getInputNum() {
//		return inputNum;
//	}
//
//	public void setInputNum(Long inputNum) {
//		this.inputNum = inputNum;
//	}
//
//	public String getInputType() {
//		return inputType;
//	}
//
//	public void setInputType(String inputType) {
//		this.inputType = inputType;
//	}
//
	public Long getHeight() {
		return height;
	}

	public void setHeight(Long height) {
		this.height = height;
	}

	public Long getCumulativeCoins() {
		return cumulativeCoins;
	}
	public void setCumulativeCoins(Long cumulativeCoins) {
		this.cumulativeCoins = cumulativeCoins;
	}

//	public Long getOutputNum() {
//		return outputNum;
//	}
//
//	public void setOutputNum(Long outputNum) {
//		this.outputNum = outputNum;
//	}
//
//	public Long[] getOutputs() {
//		return outputs;
//	}
//
//	public void setOutputs(Long[] outputs) {
//		this.outputs = outputs;
//	}
//
//	public Long getExtraSize() {
//		return extraSize;
//	}
//
//	public void setExtraSize(Long extraSize) {
//		this.extraSize = extraSize;
//	}
//
//	public Byte[] getExtra() {
//		return extra;
//	}
//
//	public void setExtra(Byte[] extra) {
//		this.extra = extra;
//	}
//
//	public Integer getTx_num() {
//		return tx_num;
//	}
//
//	public void setTx_num(Integer tx_num) {
//		this.tx_num = tx_num;
//	}
//
//	public String[] getIdentifiers() {
//		return identifiers;
//	}
//
//	public void setIdentifiers(String[] identifiers) {
//		this.identifiers = identifiers;
//	}
	
}
