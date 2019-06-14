package cash.torque.test.lmdb;

import java.io.File;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.fusesource.lmdbjni.BufferCursor;
import org.fusesource.lmdbjni.ByteUnit;
import org.fusesource.lmdbjni.Constants;
import org.fusesource.lmdbjni.Database;
import org.fusesource.lmdbjni.Env;
import org.fusesource.lmdbjni.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import cash.torque.config.DbProperties;
import cash.torque.util.TorqueUtils;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestLmdb {
	
	@Autowired
	private DbProperties props;
	
	private Env envTest;
	private Database dbTest;
	
	@Before
	public void setup() {
		envTest = new Env();
		envTest.open(props.getTorqueHome() + File.separator + "lmdb_test", envTest.getFlags()|Constants.CREATE);
		envTest.setMapSize(8L, ByteUnit.GIBIBYTES);
		//envTest.setMaxDbs(1L);
		//envTest.setMaxReaders(2L);
		System.out.println(envTest.info());
		System.out.println(envTest.stat());
		
		dbTest = envTest.openDatabase("test");
	}
	
	@After
	public void finish() {
		if(dbTest!=null) dbTest.close();
		if(envTest!=null) envTest.close();
	}
	
	@Test
	public void testLmdb() {
		// fill an ordered map with 100.000 elts
		long n=100000L;//
		System.out.println("fill a map with " + n + " elements");
		TreeMap<Long,Long> data = new TreeMap<>();
		for(long i=0; i<n; i++) {
			data.put(i, i);
		}
		
		System.out.println("**** write test ****");
		try(Transaction tx = envTest.createWriteTransaction(); BufferCursor cursor = dbTest.bufferCursor(tx)) {
			if(cursor.last()) {
				System.out.println("at the end");
			} else {
				System.out.println("no data : append");
			}
			int i=0;
			for(Entry<Long, Long> r : data.entrySet()) {
				byte[] k = TorqueUtils.encodeUint64(r.getKey());
				byte[] v = TorqueUtils.encodeUint64(r.getValue());
				dbTest.put(tx, k, v);
				if(i>65533 && i<65540) {
					System.out.print(String.format("k=%s, v=%s;", Hex.encodeHexString(k), Hex.encodeHexString(v) ));
				}
				i++;
			}
			System.out.println("** write test finished **");
			tx.commit();
			tx.close();
		}
		
		System.out.println("**** read test ****");
		try(Transaction tx = envTest.createReadTransaction(); BufferCursor cursor = dbTest.bufferCursor(tx)) {
			if(cursor.last()) {
				for(int i=0; i<data.size(); i++) {
					if(i>65000 && i<65540) {
						System.out.println(String.format("key=%s val=%s", TorqueUtils.decodeUint64(cursor.keyBytes()), TorqueUtils.decodeUint64(cursor.valBytes())));
					}
					if(!cursor.prev()) {
						System.out.println("no more records");
						break;
					}
				}
			}
			System.out.println("** read test finished **");
			tx.commit();
			tx.close();
		}
	}
	
	@Test
	public void testDecoding() throws DecoderException {
		String[] tests = new String[] {
				"5963000000000000", "5963010000000000",
				"7f00000000000000", "8000000000000000",
				"ff00000000000000", "0001000000000000",
				"ffff000000000000", "0000010000000000",
				"4ead010000000000", "0000020000000000",
				"ffffffffffffff7f", "0000000000000080"};
		long[] results = new long[]{ 25433, 90969, 127, 128, 255, 256, 65535, 65536, 0x01_ad_4e, 131072, Long.MAX_VALUE, Long.MIN_VALUE };
		
		byte[] bytes;
		int i = 0;
		for(String s : tests) {
			bytes = Hex.decodeHex(s.toCharArray());
			System.out.println(bytes);
			long a = TorqueUtils.decodeUint64(bytes);
			System.out.println("s=" + s + " => " + a);
			assert results[i]==a;
			i++;
		}
	}
	
	@Test
	public void testEncoding() {
		long[] tests = new long[]{ 25433, 90969, 127, 128, 255, 256, 65535, 65536, 0x01_ad_4e, 131072, Long.MAX_VALUE, Long.MIN_VALUE };
		String[] results = new String[] {
				"5963000000000000", "5963010000000000",
				"7f00000000000000", "8000000000000000",
				"ff00000000000000", "0001000000000000",
				"ffff000000000000", "0000010000000000",
				"4ead010000000000", "0000020000000000",
				"ffffffffffffff7f", "0000000000000080"};
		byte[] bytes;
		int i = 0;
		for(long a : tests) {
			bytes = TorqueUtils.encodeUint64(a);
			System.out.println("a=" + a + " => " + Hex.encodeHexString(bytes));
			assert results[i].equals(Hex.encodeHexString(bytes));
			i++;
		}
	}
	
}
